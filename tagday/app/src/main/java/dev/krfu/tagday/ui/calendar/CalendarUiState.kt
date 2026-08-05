package dev.krfu.tagday.ui.calendar

import dev.krfu.tagday.data.local.entity.Tag
import java.time.LocalDate

data class CalendarUiState(
    val isLoading: Boolean = true,
    val zoomLevel: ZoomLevel = ZoomLevel.DAY,
    val focusedDate: LocalDate = LocalDate.now(),
    val selectedTagId: Long? = null,
    val allTags: List<Tag> = emptyList(),
    val periodData: CalendarPeriodData = CalendarPeriodData.Day(emptyList()),
    val pendingRemoval: PendingRemoval? = null,
    /**
     * Today's date as the ViewModel currently understands it, re-emitted when the day rolls
     * over. The zoom levels take their today-highlights from this rather than calling
     * `LocalDate.now()` during composition, which never updated — see `CalendarViewModel.today`
     * and BACKLOG F6.
     */
    val today: LocalDate = LocalDate.now(),
)
