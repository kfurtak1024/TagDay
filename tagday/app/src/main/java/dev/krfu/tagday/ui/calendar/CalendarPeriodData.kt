package dev.krfu.tagday.ui.calendar

import dev.krfu.tagday.data.model.TagDisplayGroup

sealed interface CalendarPeriodData {
    data class Day(val groups: List<TagDisplayGroup>) : CalendarPeriodData

    data class Week(val groupsByDate: Map<Int, List<TagDisplayGroup>>) : CalendarPeriodData

    /** Month and Year share this shape: per-day instance count for one picked tag. */
    data class Heatmap(val countsByDate: Map<Int, Int>) : CalendarPeriodData
}
