package dev.krfu.tagday.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.krfu.tagday.data.local.entity.TagInstance
import dev.krfu.tagday.data.local.entity.TagType
import dev.krfu.tagday.data.model.TagDisplayGroup
import dev.krfu.tagday.data.model.TagDisplayGroups.excludingInstances
import dev.krfu.tagday.data.repository.TagInstanceRepository
import dev.krfu.tagday.data.repository.TagRepository
import dev.krfu.tagday.ui.theme.TagPalette
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

private data class CalendarQuery(
    val zoomLevel: ZoomLevel,
    val focusedDate: LocalDate,
    val selectedTagId: Long?,
)

private fun LocalDate.epochDay(): Int = toEpochDay().toInt()

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val tagRepository: TagRepository,
    private val tagInstanceRepository: TagInstanceRepository,
) : ViewModel() {
    private val query = MutableStateFlow(CalendarQuery(ZoomLevel.DAY, LocalDate.now(), null))

    // Delay-delete undo for Day-zoom removals (capsule "x", instance-list sheet's
    // per-instance delete) — see ADR-019. At most one removal is ever pending: starting a
    // new one commits whichever was already pending first (see beginPendingRemoval).
    private val pendingRemoval = MutableStateFlow<PendingRemoval?>(null)

    // Single-shot signal for createValuedTagForEditing — CalendarScreen opens the sheet
    // for this tag id once, then calls consumePendingValuedTagEdit.
    private val _pendingValuedTagEdit = MutableStateFlow<Long?>(null)
    val pendingValuedTagEdit: StateFlow<Long?> = _pendingValuedTagEdit.asStateFlow()

    val uiState: StateFlow<CalendarUiState> = combine(
        query,
        query.flatMapLatest { periodDataFlow(it) },
        tagRepository.observeAll(),
        pendingRemoval,
    ) { q, periodData, allTags, pending ->
        CalendarUiState(
            isLoading = false,
            zoomLevel = q.zoomLevel,
            focusedDate = q.focusedDate,
            selectedTagId = q.selectedTagId,
            allTags = allTags,
            periodData = periodData.withPendingRemovalApplied(pending),
            pendingRemoval = pending,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarUiState())

    private fun periodDataFlow(q: CalendarQuery): Flow<CalendarPeriodData> = when (q.zoomLevel) {
        ZoomLevel.DAY ->
            tagInstanceRepository.observeDayGroups(q.focusedDate.epochDay())
                .map { CalendarPeriodData.Day(it) }

        ZoomLevel.WEEK -> {
            val range = CalendarDateRanges.weekRange(q.focusedDate)
            tagInstanceRepository.observeRangeGroups(range.start.epochDay(), range.endInclusive.epochDay())
                .map { CalendarPeriodData.Week(it) }
        }

        ZoomLevel.MONTH, ZoomLevel.YEAR -> {
            val range = if (q.zoomLevel == ZoomLevel.MONTH) {
                CalendarDateRanges.monthRange(q.focusedDate)
            } else {
                CalendarDateRanges.yearRange(q.focusedDate)
            }
            val tagId = q.selectedTagId
            if (tagId == null) {
                flowOf(CalendarPeriodData.Heatmap(emptyMap()))
            } else {
                tagInstanceRepository.observeTagInstanceCounts(
                    tagId,
                    range.start.epochDay(),
                    range.endInclusive.epochDay(),
                ).map { CalendarPeriodData.Heatmap(it) }
            }
        }
    }

    fun stepTime(direction: Int) {
        query.update { it.copy(focusedDate = CalendarDateRanges.step(it.focusedDate, it.zoomLevel, direction)) }
    }

    fun stepZoom(direction: Int) {
        query.update {
            val nextOrdinal = (it.zoomLevel.ordinal + direction).coerceIn(0, ZoomLevel.entries.lastIndex)
            it.copy(zoomLevel = ZoomLevel.entries[nextOrdinal])
        }
    }

    fun setZoom(zoomLevel: ZoomLevel) {
        query.update { it.copy(zoomLevel = zoomLevel) }
    }

    fun jumpToDay(date: LocalDate) {
        query.update { it.copy(zoomLevel = ZoomLevel.DAY, focusedDate = date) }
    }

    fun jumpToMonth(date: LocalDate) {
        query.update { it.copy(zoomLevel = ZoomLevel.MONTH, focusedDate = date) }
    }

    fun jumpToToday() {
        query.update { it.copy(focusedDate = LocalDate.now()) }
    }

    fun selectHeatmapTag(tagId: Long) {
        query.update { it.copy(selectedTagId = tagId) }
    }

    fun addExistingTag(tagId: Long) {
        viewModelScope.launch {
            tagInstanceRepository.addInstance(tagId, query.value.focusedDate.epochDay())
        }
    }

    /** Instance-list sheet's add-value row for Valued groups — see ADR-020. */
    fun addValue(tagId: Long, value: String) {
        viewModelScope.launch {
            tagInstanceRepository.addInstance(tagId, query.value.focusedDate.epochDay(), value = value)
        }
    }

    fun createTagAndAdd(name: String, type: TagType, rating: Int? = null, value: String? = null) {
        viewModelScope.launch {
            val color = TagPalette.colors[uiState.value.allTags.size % TagPalette.colors.size]
            val tagId = tagRepository.createTag(name, color, type)
            tagInstanceRepository.addInstance(tagId, query.value.focusedDate.epochDay(), rating, value)
        }
    }

    /**
     * Quick-entry's Valued chip for a brand-new tag name: unlike Simple/Rated (which have
     * a sensible "nothing set yet" state — see ADR-008 for Rated), a Valued instance with
     * no value has no meaningful display, so this creates the tag only (no instance) and
     * opens the instance-list sheet for it via [pendingValuedTagEdit], letting the user
     * type the first value through the sheet's existing add-value row.
     */
    fun createValuedTagForEditing(name: String) {
        viewModelScope.launch {
            val color = TagPalette.colors[uiState.value.allTags.size % TagPalette.colors.size]
            val tagId = tagRepository.createTag(name, color, TagType.VALUED)
            _pendingValuedTagEdit.value = tagId
        }
    }

    fun consumePendingValuedTagEdit() {
        _pendingValuedTagEdit.value = null
    }

    fun updateInstance(instance: TagInstance) {
        viewModelScope.launch {
            tagInstanceRepository.updateInstance(instance)
        }
    }

    /** Instance-list sheet's drag-to-reorder for Valued groups. */
    fun reorderValues(instances: List<TagInstance>) {
        viewModelScope.launch {
            tagInstanceRepository.updateInstances(instances)
        }
    }

    /** Instance-list sheet's per-instance delete — see ADR-019. */
    fun removeInstance(instance: TagInstance, tagName: String) {
        beginPendingRemoval(listOf(instance), tagName)
    }

    /** Capsule "x" — whole group at once, see ADR-019. */
    fun removeGroup(group: TagDisplayGroup) {
        beginPendingRemoval(group.instances, group.tagName)
    }

    private fun beginPendingRemoval(instances: List<TagInstance>, tagName: String) {
        // Only one removal is ever pending — flush whatever was already waiting on its
        // own undo window before starting the new one, rather than stacking snackbars.
        commitPendingRemoval()
        pendingRemoval.value = PendingRemoval(instances, tagName)
    }

    fun undoRemoval() {
        pendingRemoval.value = null
    }

    fun commitPendingRemoval() {
        val pending = pendingRemoval.value ?: return
        pendingRemoval.value = null
        viewModelScope.launch {
            tagInstanceRepository.removeInstances(pending.instances)
        }
    }
}

private fun CalendarPeriodData.withPendingRemovalApplied(pending: PendingRemoval?): CalendarPeriodData {
    if (pending == null || this !is CalendarPeriodData.Day) return this
    val excludedIds = pending.instances.map { it.id }.toSet()
    return CalendarPeriodData.Day(groups.excludingInstances(excludedIds))
}
