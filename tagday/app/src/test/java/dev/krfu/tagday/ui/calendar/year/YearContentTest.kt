package dev.krfu.tagday.ui.calendar.year

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.krfu.tagday.R
import dev.krfu.tagday.data.local.entity.Tag
import dev.krfu.tagday.data.local.entity.TagType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Year zoom's 12-tile grid (ADR-016). Its per-day squares deliberately carry no semantics of
 * their own at this density, so the *tile* is both the tap target and the only thing a screen
 * reader gets — which makes the month total it announces the whole of this zoom's non-visual
 * output, and the tile total is arithmetic no other test covers.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class YearContentTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()

    private val focusedDate = LocalDate.of(2026, 7, 15)
    private val walk = Tag(id = 1, name = "walk", type = TagType.SIMPLE, color = 0xFF81C784.toInt(), createdAt = 0)

    private var clickedMonth: LocalDate? = null

    private fun epochDay(date: LocalDate) = date.toEpochDay().toInt()

    private fun setYear(
        selectedTagId: Long? = walk.id,
        countsByDate: Map<Int, Int> = emptyMap(),
        today: LocalDate = focusedDate,
    ) {
        compose.setContent {
            YearContent(
                focusedDate = focusedDate,
                today = today,
                allTags = listOf(walk),
                selectedTagId = selectedTagId,
                countsByDate = countsByDate,
                onTagPicked = {},
                onMonthClick = { clickedMonth = it },
            )
        }
    }

    @Test
    fun withNoTagPicked_saysSoRatherThanDrawingTwelveBlankTiles() {
        setYear(selectedTagId = null)

        compose.onNodeWithText(context.getString(R.string.calendar_heatmap_pick_tag_message)).assertIsDisplayed()
        compose.onNodeWithContentDescription("January 2026, none").assertDoesNotExist()
    }

    @Test
    fun everyMonthOfTheYearGetsATile() {
        setYear()

        (1..12).forEach { month ->
            val label = LocalDate.of(2026, month, 1).month
                .getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH)
            compose.onNodeWithContentDescription("$label 2026, none").assertExists()
        }
    }

    @Test
    fun aTileAnnouncesTheWholeMonthsTotal_notPerDayCounts() {
        // Summed across the days of that month and nothing else: the neighbouring months'
        // instances below are what a range that leaks past the tile edges would pull in.
        setYear(
            countsByDate = mapOf(
                epochDay(LocalDate.of(2026, 7, 1)) to 2,
                epochDay(LocalDate.of(2026, 7, 31)) to 3,
                epochDay(LocalDate.of(2026, 6, 30)) to 5,
                epochDay(LocalDate.of(2026, 8, 1)) to 5,
            ),
        )

        compose.onNodeWithContentDescription("July 2026, 5 times").assertExists()
        compose.onNodeWithContentDescription("June 2026, 5 times").assertExists()
    }

    @Test
    fun aTileWithASingleInstance_usesTheSingularForm() {
        setYear(countsByDate = mapOf(epochDay(LocalDate.of(2026, 3, 4)) to 1))

        compose.onNodeWithContentDescription("March 2026, 1 time").assertExists()
    }

    @Test
    fun tappingATile_jumpsToThatMonthsFirstDay() {
        // Month zoom, not Day: a tile is a whole month and there's no per-day target here
        // to have meant anything more specific (ADR-016).
        setYear()

        compose.onNodeWithContentDescription("October 2026, none").performClick()
        compose.waitForIdle()

        assertEquals(LocalDate.of(2026, 10, 1), clickedMonth)
    }
}
