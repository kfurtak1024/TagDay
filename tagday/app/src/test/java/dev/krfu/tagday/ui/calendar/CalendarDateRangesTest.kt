package dev.krfu.tagday.ui.calendar

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarDateRangesTest {
    @Test
    fun weekRange_startsMonday_endsSunday() {
        // Wednesday 2026-07-22 -> week is Mon 2026-07-20 .. Sun 2026-07-26.
        val range = CalendarDateRanges.weekRange(LocalDate.of(2026, 7, 22))
        assertEquals(LocalDate.of(2026, 7, 20), range.start)
        assertEquals(LocalDate.of(2026, 7, 26), range.endInclusive)
    }

    @Test
    fun weekRange_onMonday_isUnchanged() {
        val range = CalendarDateRanges.weekRange(LocalDate.of(2026, 7, 20))
        assertEquals(LocalDate.of(2026, 7, 20), range.start)
        assertEquals(LocalDate.of(2026, 7, 26), range.endInclusive)
    }

    @Test
    fun monthRange_coversWholeMonth() {
        val range = CalendarDateRanges.monthRange(LocalDate.of(2026, 2, 15))
        assertEquals(LocalDate.of(2026, 2, 1), range.start)
        assertEquals(LocalDate.of(2026, 2, 28), range.endInclusive)
    }

    @Test
    fun monthRange_leapYearFebruary() {
        val range = CalendarDateRanges.monthRange(LocalDate.of(2028, 2, 10))
        assertEquals(LocalDate.of(2028, 2, 1), range.start)
        assertEquals(LocalDate.of(2028, 2, 29), range.endInclusive)
    }

    @Test
    fun yearRange_coversWholeYear() {
        val range = CalendarDateRanges.yearRange(LocalDate.of(2026, 7, 22))
        assertEquals(LocalDate.of(2026, 1, 1), range.start)
        assertEquals(LocalDate.of(2026, 12, 31), range.endInclusive)
    }

    @Test
    fun step_day() {
        val date = LocalDate.of(2026, 7, 22)
        assertEquals(LocalDate.of(2026, 7, 23), CalendarDateRanges.step(date, ZoomLevel.DAY, 1))
        assertEquals(LocalDate.of(2026, 7, 21), CalendarDateRanges.step(date, ZoomLevel.DAY, -1))
    }

    @Test
    fun step_week() {
        val date = LocalDate.of(2026, 7, 22)
        assertEquals(LocalDate.of(2026, 7, 29), CalendarDateRanges.step(date, ZoomLevel.WEEK, 1))
        assertEquals(LocalDate.of(2026, 7, 15), CalendarDateRanges.step(date, ZoomLevel.WEEK, -1))
    }

    @Test
    fun step_month_acrossYearBoundary() {
        val date = LocalDate.of(2026, 12, 15)
        assertEquals(LocalDate.of(2027, 1, 15), CalendarDateRanges.step(date, ZoomLevel.MONTH, 1))
    }

    @Test
    fun step_year() {
        val date = LocalDate.of(2026, 7, 22)
        assertEquals(LocalDate.of(2027, 7, 22), CalendarDateRanges.step(date, ZoomLevel.YEAR, 1))
        assertEquals(LocalDate.of(2025, 7, 22), CalendarDateRanges.step(date, ZoomLevel.YEAR, -1))
    }

    // Regression coverage for a real bug: MonthGrid's weekday header used to be derived
    // from the month's own first 7 days instead of a canonical Monday..Sunday sequence,
    // silently misaligning the header against these grid cells for any month that
    // doesn't start on a Monday (i.e. most months).

    @Test
    fun monthGridCells_monthStartingOnMonday_hasNoLeadingBlanks() {
        // June 2026 starts on a Monday.
        val cells = CalendarDateRanges.monthGridCells(LocalDate.of(2026, 6, 15))
        assertEquals(30, cells.size)
        assertEquals(LocalDate.of(2026, 6, 1), cells.first())
        assertEquals(LocalDate.of(2026, 6, 30), cells.last())
        assertEquals(0, cells.takeWhile { it == null }.size)
    }

    @Test
    fun monthGridCells_monthStartingMidweek_alignsFirstDayToItsColumn() {
        // July 2026 starts on a Wednesday (the 3rd weekday column, 0-indexed 2).
        val cells = CalendarDateRanges.monthGridCells(LocalDate.of(2026, 7, 22))
        assertEquals(2, cells.takeWhile { it == null }.size)
        assertEquals(33, cells.size) // 2 leading blanks + 31 days
        assertEquals(LocalDate.of(2026, 7, 1), cells[2])
        assertEquals(LocalDate.of(2026, 7, 31), cells.last())
    }

    @Test
    fun monthGridCells_monthStartingOnSunday_hasSixLeadingBlanks() {
        // February 2026 (non-leap) starts on a Sunday, the last weekday column.
        val cells = CalendarDateRanges.monthGridCells(LocalDate.of(2026, 2, 10))
        assertEquals(6, cells.takeWhile { it == null }.size)
        assertEquals(34, cells.size) // 6 leading blanks + 28 days
        assertEquals(LocalDate.of(2026, 2, 1), cells[6])
        assertEquals(LocalDate.of(2026, 2, 28), cells.last())
    }
}
