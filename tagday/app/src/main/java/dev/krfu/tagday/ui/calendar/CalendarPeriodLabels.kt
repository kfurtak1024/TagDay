package dev.krfu.tagday.ui.calendar

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Names the period a zoom level is currently showing — "July 2026", "21 – 27 July 2026",
 * "2026". Week/Month/Year had no such label at all before ADR-033: a Month grid is bare day
 * numbers, and a Year grid is month abbreviations with the year nowhere on screen.
 *
 * Pure and locale-parameterised so it can be unit-tested, since the week case has real
 * boundary conditions (a week straddling a month or a year end).
 */
object CalendarPeriodLabels {

    fun format(
        date: LocalDate,
        zoomLevel: ZoomLevel,
        locale: Locale = Locale.getDefault(),
    ): String = when (zoomLevel) {
        ZoomLevel.DAY -> date.format(pattern("EEEE, d MMMM yyyy", locale))
        ZoomLevel.WEEK -> weekLabel(date, locale)
        ZoomLevel.MONTH -> date.format(pattern("MMMM yyyy", locale))
        ZoomLevel.YEAR -> date.format(pattern("yyyy", locale))
    }

    /**
     * Both ends of the week, with whatever the two share said once: `21 – 27 July 2026`,
     * `28 Jul – 3 Aug 2026`, `29 Dec 2025 – 4 Jan 2026`.
     */
    private fun weekLabel(date: LocalDate, locale: Locale): String {
        val range = CalendarDateRanges.weekRange(date)
        val start = range.start
        val end = range.endInclusive
        val startPattern = when {
            start.year != end.year -> "d MMM yyyy"
            start.month != end.month -> "d MMM"
            else -> "d"
        }
        val endPattern = if (start.year != end.year || start.month != end.month) {
            "d MMM yyyy"
        } else {
            "d MMMM yyyy"
        }
        return "${start.format(pattern(startPattern, locale))} – " +
            end.format(pattern(endPattern, locale))
    }

    private fun pattern(pattern: String, locale: Locale): DateTimeFormatter =
        DateTimeFormatter.ofPattern(pattern, locale)
}
