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
}
