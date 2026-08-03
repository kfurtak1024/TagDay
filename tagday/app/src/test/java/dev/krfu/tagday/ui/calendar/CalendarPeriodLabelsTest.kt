package dev.krfu.tagday.ui.calendar

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class CalendarPeriodLabelsTest {
    private fun label(date: LocalDate, zoomLevel: ZoomLevel) =
        CalendarPeriodLabels.format(date, zoomLevel, Locale.ENGLISH)

    @Test
    fun month_isMonthAndYear() {
        assertEquals("July 2026", label(LocalDate.of(2026, 7, 25), ZoomLevel.MONTH))
    }

    @Test
    fun year_isJustTheYear() {
        assertEquals("2026", label(LocalDate.of(2026, 7, 25), ZoomLevel.YEAR))
    }

    @Test
    fun day_namesTheWeekdayAndFullDate() {
        assertEquals("Saturday, 25 July 2026", label(LocalDate.of(2026, 7, 25), ZoomLevel.DAY))
    }

    @Test
    fun week_withinOneMonth_saysTheMonthOnce() {
        // Mon 20 – Sun 26 July 2026.
        assertEquals("20 – 26 July 2026", label(LocalDate.of(2026, 7, 25), ZoomLevel.WEEK))
    }

    @Test
    fun week_straddlingTwoMonths_namesBothMonths() {
        // Mon 27 July – Sun 2 August 2026.
        assertEquals("27 Jul – 2 Aug 2026", label(LocalDate.of(2026, 7, 28), ZoomLevel.WEEK))
    }

    @Test
    fun week_straddlingTwoYears_namesBothYears() {
        // Mon 28 Dec 2026 – Sun 3 Jan 2027.
        assertEquals("28 Dec 2026 – 3 Jan 2027", label(LocalDate.of(2026, 12, 31), ZoomLevel.WEEK))
    }

    @Test
    fun week_isTheSameLabelForEveryDayInThatWeek() {
        val week = CalendarDateRanges.weekRange(LocalDate.of(2026, 7, 25))
        val labels = generateSequence(week.start) { it.plusDays(1) }
            .takeWhile { it <= week.endInclusive }
            .map { label(it, ZoomLevel.WEEK) }
            .toSet()

        assertEquals(setOf("20 – 26 July 2026"), labels)
    }
}
