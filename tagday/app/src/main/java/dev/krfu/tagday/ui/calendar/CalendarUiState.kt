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
)
