package dev.krfu.tagday.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.krfu.tagday.data.local.entity.TagInstance
import dev.krfu.tagday.data.local.entity.TagType
import dev.krfu.tagday.data.model.TagDisplayGroup
import dev.krfu.tagday.data.repository.TagInstanceRepository
import dev.krfu.tagday.data.repository.TagRepository
import dev.krfu.tagday.ui.theme.TagPalette
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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

    val uiState: StateFlow<CalendarUiState> = combine(
        query,
        query.flatMapLatest { periodDataFlow(it) },
        tagRepository.observeAll(),
    ) { q, periodData, allTags ->
        CalendarUiState(
            isLoading = false,
            zoomLevel = q.zoomLevel,
            focusedDate = q.focusedDate,
            selectedTagId = q.selectedTagId,
            allTags = allTags,
            periodData = periodData,
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

    fun selectHeatmapTag(tagId: Long) {
        query.update { it.copy(selectedTagId = tagId) }
    }

    fun addExistingTag(tagId: Long) {
        viewModelScope.launch {
            tagInstanceRepository.addInstance(tagId, query.value.focusedDate.epochDay())
        }
    }

    fun createTagAndAdd(name: String, type: TagType, rating: Int? = null, value: String? = null) {
        viewModelScope.launch {
            val color = TagPalette.colors[uiState.value.allTags.size % TagPalette.colors.size]
            val tagId = tagRepository.createTag(name, color, type)
            tagInstanceRepository.addInstance(tagId, query.value.focusedDate.epochDay(), rating, value)
        }
    }

    fun updateInstance(instance: TagInstance) {
        viewModelScope.launch {
            tagInstanceRepository.updateInstance(instance)
        }
    }

    fun removeInstance(instance: TagInstance) {
        viewModelScope.launch {
            tagInstanceRepository.removeInstance(instance)
        }
    }

    fun removeGroup(group: TagDisplayGroup) {
        viewModelScope.launch {
            tagInstanceRepository.removeInstances(group.instances)
        }
    }
}
