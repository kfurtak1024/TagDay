package dev.krfu.tagday.ui.calendar

import androidx.lifecycle.SavedStateHandle
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import javax.inject.Inject

private data class CalendarQuery(
    val zoomLevel: ZoomLevel,
    val focusedDate: LocalDate,
    val selectedTagId: Long?,
)

private fun LocalDate.epochDay(): Int = toEpochDay().toInt()

/**
 * How long a delay-delete waits before it actually deletes (ADR-019). Mirrors
 * `SnackbarDuration.Short`, which is what the undo snackbar is shown for — the two are
 * independent timers over the same window, so the snackbar disappearing and the deletion
 * landing look like one event.
 */
private const val UNDO_WINDOW_MS = 4_000L

private const val KEY_ZOOM = "zoomLevel"
private const val KEY_FOCUSED_DATE = "focusedDate"
private const val KEY_SELECTED_TAG = "selectedTagId"

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val tagRepository: TagRepository,
    private val tagInstanceRepository: TagInstanceRepository,
    private val clock: Clock,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    // Zoom level, focused date and heatmap tag survive process death (ADR-035, BACKLOG F10 and
    // F12). Stored as three primitives rather than a parcelable `CalendarQuery`: the date goes
    // as an epoch day, which is already this project's wire format for a date
    // (`DATA_MODEL.md` § `TagInstance`), and the zoom level as its ordinal.
    private val query = MutableStateFlow(
        CalendarQuery(
            zoomLevel = savedState.get<Int>(KEY_ZOOM)
                ?.let { ZoomLevel.entries.getOrNull(it) }
                ?: ZoomLevel.DAY,
            focusedDate = savedState.get<Long>(KEY_FOCUSED_DATE)
                ?.let(LocalDate::ofEpochDay)
                ?: LocalDate.now(clock),
            selectedTagId = savedState.get<Long>(KEY_SELECTED_TAG),
        ),
    )

    init {
        // One writer, rather than a save call scattered through every mutator — a new
        // navigation method can't then forget to persist.
        viewModelScope.launch {
            query.collect {
                savedState[KEY_ZOOM] = it.zoomLevel.ordinal
                savedState[KEY_FOCUSED_DATE] = it.focusedDate.toEpochDay()
                savedState[KEY_SELECTED_TAG] = it.selectedTagId
            }
        }
    }

    // Delay-delete undo for Day-zoom removals (capsule "x", instance-list sheet's
    // per-instance delete) — see ADR-019. At most one removal is ever pending: starting a
    // new one commits whichever was already pending first (see beginPendingRemoval).
    private val pendingRemoval = MutableStateFlow<PendingRemoval?>(null)
    private var pendingRemovalJob: Job? = null

    // Single-shot signal for createTagForEditing — CalendarScreen opens the sheet for this
    // tag id once, then calls consumePendingTagEdit.
    private val _pendingTagEdit = MutableStateFlow<Long?>(null)
    val pendingTagEdit: StateFlow<Long?> = _pendingTagEdit.asStateFlow()

    /**
     * Today's date, re-emitted when the day actually rolls over. Every "is this today?"
     * decision in the UI reads this instead of calling `LocalDate.now()` itself — the Day
     * header's past/today/future pill, Week's row highlight, Month's today-ring, Year's
     * current-month border and the jump-to-today button's visibility. Read ad-hoc during
     * composition, none of those ever updated: leave the app open past midnight and it went on
     * calling yesterday "Today" until something unrelated happened to recompose (BACKLOG F6).
     *
     * `WhileSubscribed` restarts this whenever the UI resubscribes, so returning to the app
     * also re-reads the date — which covers the cases the timer alone doesn't, including a
     * timezone change while the app was in the background.
     */
    private val today: Flow<LocalDate> = flow {
        while (true) {
            val date = LocalDate.now(clock)
            emit(date)
            val nextMidnight = date.plusDays(1).atStartOfDay(clock.zone).toInstant()
            // coerceAtLeast(1) so a clock already past the computed midnight can't spin this
            // loop — it just re-emits and recomputes.
            delay(Duration.between(clock.instant(), nextMidnight).toMillis().coerceAtLeast(1))
        }
    }

    // The query travels *with* the data it produced, rather than being combined alongside it.
    // Collecting `query` as its own source meant `combine` fired the moment the query changed,
    // pairing the new query with the *previous* query's data — one emission showing the new
    // date under yesterday's capsules, or `zoomLevel = WEEK` while `periodData` was still
    // `Day` (which `CalendarContent`'s `as?` casts then rendered as an empty screen rather
    // than a crash). Emitting only once the matching data arrives makes every `CalendarUiState`
    // internally consistent by construction. See BACKLOG F5 and ADR-036.
    val uiState: StateFlow<CalendarUiState> = combine(
        query.flatMapLatest { q -> periodDataFlow(q).map { data -> q to data } },
        tagRepository.observeAll(),
        pendingRemoval,
        today,
    ) { (q, periodData), allTags, pending, today ->
        CalendarUiState(
            isLoading = false,
            zoomLevel = q.zoomLevel,
            focusedDate = q.focusedDate,
            selectedTagId = q.selectedTagId,
            allTags = allTags,
            periodData = periodData.withPendingRemovalApplied(pending),
            pendingRemoval = pending,
            today = today,
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

    /** Moves the focused date without changing zoom level — the period row's date picker. */
    fun setFocusedDate(date: LocalDate) {
        query.update { it.copy(focusedDate = date) }
    }

    fun jumpToToday() {
        setFocusedDate(LocalDate.now(clock))
    }

    fun selectHeatmapTag(tagId: Long) {
        query.update { it.copy(selectedTagId = tagId) }
    }

    /**
     * One more instance of an existing tag on the focused day, carrying no rating or value.
     * Quick-entry uses it for a name that already exists; the instance sheet's Simple count
     * editor uses it as its "+" (ADR-031).
     */
    fun addInstance(tagId: Long) {
        viewModelScope.launch {
            tagInstanceRepository.addInstance(tagId, query.value.focusedDate.epochDay())
        }
    }

    /** Instance-list sheet's add-value row for Valued groups — see ADR-020. */
    fun addValue(tagId: Long, value: String) {
        addValues(tagId, listOf(value))
    }

    /**
     * One instance per value on the focused day — quick-entry's `film:dune,tenet` against a tag
     * that already exists (ADR-034). Distinct `sortOrder`s come from the repository, so a batch
     * keeps the order it was typed in.
     */
    fun addValues(tagId: Long, values: List<String>) {
        viewModelScope.launch {
            tagInstanceRepository.addValues(tagId, query.value.focusedDate.epochDay(), values)
        }
    }

    /** Instance-list sheet's add-rating row for Rated groups — see ADR-028. */
    fun addRating(tagId: Long, rating: Int?) {
        viewModelScope.launch {
            tagInstanceRepository.addInstance(tagId, query.value.focusedDate.epochDay(), rating)
        }
    }

    /**
     * Creates the tag and seeds it with one instance per entry in [values] — quick-entry's
     * `film:dune,tenet` shorthand (ADR-028). An empty [values] seeds a single instance with
     * no value, which is what Simple and Rated creation both want.
     */
    fun createTagAndAdd(
        name: String,
        type: TagType,
        rating: Int? = null,
        values: List<String> = emptyList(),
    ) {
        viewModelScope.launch {
            val color = TagPalette.nextColor(uiState.value.allTags.map { it.color })
            // Null only if the name both failed to insert and then failed to be found, which
            // means it was deleted in between — nothing sensible to add to, so drop the input
            // rather than inventing a tag (BACKLOG F3).
            val tagId = tagRepository.createTag(name, color, type) ?: return@launch
            val date = query.value.focusedDate.epochDay()
            if (values.isEmpty()) {
                tagInstanceRepository.addInstance(tagId, date, rating)
            } else {
                tagInstanceRepository.addValues(tagId, date, values)
            }
        }
    }

    /**
     * Creates a Rated or Valued tag with **no** instance and opens the instance sheet for it
     * via [pendingTagEdit], so the first rating/value is entered there rather than guessed at
     * creation. Used by quick-entry when the type was picked without a `:***`/`:value` seed:
     * a value-less Valued instance has nothing to display at all, and a rating-less Rated one
     * shows as a bare name, which isn't what someone choosing "Rated" is asking for
     * (ADR-021, ADR-031). Simple has nothing to configure, so it never comes through here.
     */
    fun createTagForEditing(name: String, type: TagType) {
        viewModelScope.launch {
            val color = TagPalette.nextColor(uiState.value.allTags.map { it.color })
            val tagId = tagRepository.createTag(name, color, type) ?: return@launch
            _pendingTagEdit.value = tagId
        }
    }

    /**
     * Opens the instance sheet for a tag that already exists, without creating anything —
     * quick-entry's bare-name case for Rated and Valued (ADR-034). The counterpart to
     * [createTagForEditing], which does the same thing for a tag it has just created.
     */
    fun requestTagEdit(tagId: Long) {
        _pendingTagEdit.value = tagId
    }

    fun consumePendingTagEdit() {
        _pendingTagEdit.value = null
    }

    fun updateInstance(instance: TagInstance) {
        viewModelScope.launch {
            tagInstanceRepository.updateInstance(instance)
        }
    }

    /** Instance-list sheet's drag-to-reorder, for both Valued and Rated groups. */
    fun reorderInstances(instances: List<TagInstance>) {
        viewModelScope.launch {
            tagInstanceRepository.updateInstances(instances)
        }
    }

    /** Instance-list sheet's per-instance delete — see ADR-019. */
    fun removeInstance(instance: TagInstance, tagName: String) {
        beginPendingRemoval(listOf(instance), tagName)
    }

    /**
     * Deletes one instance outright, skipping the delay-delete/undo of [removeInstance]. Used
     * by the Simple count editor's "−": a Simple instance holds nothing but its own existence,
     * so "+" restores an equivalent one exactly, and a snackbar saying the tag was "removed"
     * would be both redundant and wrong for a decrement. See ADR-031.
     */
    fun removeInstanceImmediately(instance: TagInstance) {
        viewModelScope.launch {
            tagInstanceRepository.removeInstances(listOf(instance))
        }
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
        // The undo window is owned here, not by the snackbar. It used to be the snackbar's
        // `LaunchedEffect` that decided when to commit, so navigating to Tags or Settings
        // inside the window cancelled that coroutine with neither branch taken — leaving the
        // instances hidden from Day zoom but never deleted, and re-showing the snackbar on the
        // way back (BACKLOG F7). On `viewModelScope`, the timer survives navigation and
        // rotation, and the snackbar goes back to being only a way to *display* the pending
        // removal and offer Undo.
        pendingRemovalJob = viewModelScope.launch {
            delay(UNDO_WINDOW_MS)
            commitPendingRemoval()
        }
    }

    fun undoRemoval() {
        pendingRemovalJob?.cancel()
        pendingRemovalJob = null
        pendingRemoval.value = null
    }

    /**
     * Deletes the pending removal now, ending its undo window early. Idempotent, so the
     * snackbar calling it on dismissal is harmless once [beginPendingRemoval]'s timer has
     * already fired.
     */
    fun commitPendingRemoval() {
        val pending = pendingRemoval.value ?: return
        pendingRemovalJob?.cancel()
        pendingRemovalJob = null
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
