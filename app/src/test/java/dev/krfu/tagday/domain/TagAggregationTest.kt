package dev.krfu.tagday.domain

import dev.krfu.tagday.model.GlobalTag
import dev.krfu.tagday.model.TagEntry
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TagAggregationTest {
    @Test
    fun aggregateDayTags_whenDuplicateBasicTag_showsCount() {
        val date = LocalDate.of(2026, 2, 15)
        val entries = listOf(
            TagEntry(1, date, "dinner-with-family"),
            TagEntry(2, date, "dinner-with-family")
        )

        val result = aggregateDayTags(entries, emptyMap(), showHiddenTags = false)

        assertEquals(1, result.size)
        assertEquals("dinner-with-family (2)", result.first().label)
    }

    @Test
    fun aggregateDayTags_whenValueEntries_mergesValues() {
        val date = LocalDate.of(2026, 2, 15)
        val entries = listOf(
            TagEntry(1, date, "watching-movie", value = "tron"),
            TagEntry(2, date, "watching-movie", value = "dune")
        )

        val result = aggregateDayTags(entries, emptyMap(), showHiddenTags = false)

        assertEquals("watching-movie (tron, dune)", result.first().label)
    }

    @Test
    fun aggregateDayTags_whenRatings_usesRoundedAverageAndCount() {
        val date = LocalDate.of(2026, 2, 15)
        val entries = listOf(
            TagEntry(1, date, "workout", rating = 5),
            TagEntry(2, date, "workout", rating = 4),
            TagEntry(3, date, "workout", rating = 2)
        )

        val result = aggregateDayTags(entries, emptyMap(), showHiddenTags = false).first()

        assertEquals("workout", result.label)
        assertEquals(4, result.rating)
        assertEquals(3, result.ratingCount)
    }

    @Test
    fun aggregateDayTags_whenTagHidden_filtersBySetting() {
        val date = LocalDate.of(2026, 2, 15)
        val entries = listOf(TagEntry(1, date, "vacation"))
        val globalTags = mapOf(
            "vacation" to GlobalTag(name = "vacation", colorArgb = 0xFF0000, hidden = true)
        )

        val hiddenOff = aggregateDayTags(entries, globalTags, showHiddenTags = false)
        val hiddenOn = aggregateDayTags(entries, globalTags, showHiddenTags = true)

        assertTrue(hiddenOff.isEmpty())
        assertEquals(1, hiddenOn.size)
    }
}
