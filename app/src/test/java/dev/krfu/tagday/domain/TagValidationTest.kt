package dev.krfu.tagday.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TagValidationTest {
    @Test
    fun parseInput_whenBasicTag_parsesName() {
        val result = TagValidation.parseInput("dinner-with-family")

        assertTrue(result.isSuccess)
        val parsed = result.getOrThrow()
        assertEquals("dinner-with-family", parsed.name)
        assertEquals(null, parsed.value)
        assertEquals(null, parsed.rating)
    }

    @Test
    fun parseInput_whenValueTag_parsesValue() {
        val result = TagValidation.parseInput("watching-movie:tron")

        assertTrue(result.isSuccess)
        val parsed = result.getOrThrow()
        assertEquals("watching-movie", parsed.name)
        assertEquals("tron", parsed.value)
    }

    @Test
    fun parseInput_whenRatingTag_parsesStars() {
        val result = TagValidation.parseInput("workout:***")

        assertTrue(result.isSuccess)
        val parsed = result.getOrThrow()
        assertEquals("workout", parsed.name)
        assertEquals(3, parsed.rating)
    }

    @Test
    fun parseInput_whenNameInvalid_returnsFailure() {
        val result = TagValidation.parseInput("-a-tag")

        assertFalse(result.isSuccess)
    }

    @Test
    fun parseInput_whenRatingOutOfRange_returnsFailure() {
        val result = TagValidation.parseInput("workout:******")

        assertFalse(result.isSuccess)
    }
}
