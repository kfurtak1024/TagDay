package dev.krfu.tagday.ui.calendar

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

object CalendarDateRanges {
    fun weekRange(date: LocalDate): ClosedRange<LocalDate> {
        val start = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return start..start.plusDays(6)
    }

    fun monthRange(date: LocalDate): ClosedRange<LocalDate> {
        val start = date.withDayOfMonth(1)
        return start..start.withDayOfMonth(start.lengthOfMonth())
    }

    fun yearRange(date: LocalDate): ClosedRange<LocalDate> {
        val start = date.withDayOfYear(1)
        return start..start.withDayOfYear(start.lengthOfYear())
    }

    fun step(date: LocalDate, zoomLevel: ZoomLevel, direction: Int): LocalDate = when (zoomLevel) {
        ZoomLevel.DAY -> date.plusDays(direction.toLong())
        ZoomLevel.WEEK -> date.plusWeeks(direction.toLong())
        ZoomLevel.MONTH -> date.plusMonths(direction.toLong())
        ZoomLevel.YEAR -> date.plusYears(direction.toLong())
    }
}
