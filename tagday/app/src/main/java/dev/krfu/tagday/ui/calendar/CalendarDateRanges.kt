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

    /**
     * The month grid for [focusedDate]'s month, as a list of cells in row-major order
     * (7 per week), with leading `null`s so the 1st lands in its correct Monday-start
     * weekday column. Callers render one row per 7 cells.
     */
    fun monthGridCells(focusedDate: LocalDate): List<LocalDate?> {
        val range = monthRange(focusedDate)
        // ISO weekday: Monday=1 .. Sunday=7 — leading blanks align the 1st to its column.
        val leadingBlanks = range.start.dayOfWeek.value - 1
        val days = generateSequence(range.start) { it.plusDays(1) }
            .takeWhile { it <= range.endInclusive }
            .toList()
        return List(leadingBlanks) { null } + days
    }
}
