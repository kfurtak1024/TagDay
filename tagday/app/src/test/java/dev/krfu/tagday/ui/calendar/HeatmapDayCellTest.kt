package dev.krfu.tagday.ui.calendar

import org.junit.Assert.assertEquals
import org.junit.Test

class HeatmapDayCellTest {
    @Test
    fun alphaForCount_zeroOrNegative_isFullyTransparent() {
        assertEquals(0f, alphaForCount(0))
        assertEquals(0f, alphaForCount(-1))
    }

    @Test
    fun alphaForCount_one_isLightShade() {
        assertEquals(0.3f, alphaForCount(1))
    }

    @Test
    fun alphaForCount_two_isMediumShade() {
        assertEquals(0.6f, alphaForCount(2))
    }

    @Test
    fun alphaForCount_threeOrMore_isFullyOpaque() {
        assertEquals(1f, alphaForCount(3))
        assertEquals(1f, alphaForCount(10))
    }
}
