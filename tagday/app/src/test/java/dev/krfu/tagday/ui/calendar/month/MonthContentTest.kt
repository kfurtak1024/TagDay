package dev.krfu.tagday.ui.calendar.month

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.krfu.tagday.R
import dev.krfu.tagday.data.local.entity.Tag
import dev.krfu.tagday.data.local.entity.TagType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The Month heatmap: its "pick a tag first" state, the per-cell semantics that carry the count
 * a sighted user reads out of the shading alone (BACKLOG F14), and the Monday-aligned grid.
 *
 * `CalendarDateRangesTest` already covers `monthGridCells`' leading blanks as arithmetic. What
 * it can't reach is whether the *weekday header* agrees with those columns — the header used to
 * be derived from the month's own first seven days, so for any month not starting on a Monday
 * the letters and the cells below them disagreed. That needs something to actually lay the grid
 * out, which is what the bounds assertion here does.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class MonthContentTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()

    // July 2026 starts on a Wednesday — a month whose columns and header can disagree.
    private val july = LocalDate.of(2026, 7, 15)
    private val walk = Tag(id = 1, name = "walk", type = TagType.SIMPLE, color = 0xFF81C784.toInt(), createdAt = 0)

    private var clickedDay: LocalDate? = null

    private fun epochDay(date: LocalDate) = date.toEpochDay().toInt()

    private fun setMonth(
        selectedTagId: Long? = walk.id,
        countsByDate: Map<Int, Int> = emptyMap(),
        today: LocalDate = july,
    ) {
        compose.setContent {
            MonthContent(
                focusedDate = july,
                today = today,
                allTags = listOf(walk),
                selectedTagId = selectedTagId,
                countsByDate = countsByDate,
                onTagPicked = {},
                onDayClick = { clickedDay = it },
            )
        }
    }

    @Test
    fun withNoTagPicked_saysSoRatherThanDrawingABlankGrid() {
        setMonth(selectedTagId = null)

        compose.onNodeWithText(context.getString(R.string.calendar_heatmap_pick_tag_message)).assertIsDisplayed()
        compose.onNodeWithContentDescription("Wednesday 1 July, none").assertDoesNotExist()
    }

    @Test
    fun aDayWithInstances_announcesHowMany() {
        // The count reaches the screen only through background alpha otherwise (BACKLOG F14),
        // and both plural forms are separate strings, so both are worth pinning.
        setMonth(countsByDate = mapOf(epochDay(july) to 2, epochDay(july.withDayOfMonth(3)) to 1))

        compose.onNodeWithContentDescription("Wednesday 15 July, 2 times").assertExists()
        compose.onNodeWithContentDescription("Friday 3 July, 1 time").assertExists()
    }

    @Test
    fun aDayWithNothingTagged_saysNoneRatherThanBeingSilent() {
        setMonth()

        compose.onNodeWithContentDescription("Wednesday 1 July, none").assertExists()
    }

    @Test
    fun theGridHoldsThisMonthOnly() {
        setMonth()

        compose.onNodeWithContentDescription("Friday 31 July, none").assertExists()
        // The leading blanks are blanks, not the tail of June — they carry no semantics at all.
        compose.onNodeWithContentDescription("Tuesday 30 June, none").assertDoesNotExist()
        compose.onNodeWithContentDescription("Saturday 1 August, none").assertDoesNotExist()
    }

    @Test
    fun tappingADay_reportsThatExactDate() {
        setMonth()

        compose.onNodeWithContentDescription("Wednesday 22 July, none").performClick()
        compose.waitForIdle()

        assertEquals(LocalDate.of(2026, 7, 22), clickedDay)
    }

    @Test
    fun theWeekdayHeaderSitsOverTheColumnItLabels() {
        // Columns are weight-based, so their geometry is real even though Robolectric's text
        // metrics aren't — the header letter is centred in its column whatever it measures.
        // 1 July 2026 is a Wednesday, so it must land under the third header letter.
        setMonth()

        val wednesdayHeader = compose.onNodeWithText("W").getUnclippedBoundsInRoot()
        val firstOfMonth = compose.onNodeWithContentDescription("Wednesday 1 July, none")
            .getUnclippedBoundsInRoot()
        val headerCentre = (wednesdayHeader.left + wednesdayHeader.right) / 2

        assertTrue(
            "the 'W' header is centred at $headerCentre, outside 1 July's column " +
                "(${firstOfMonth.left}..${firstOfMonth.right})",
            headerCentre >= firstOfMonth.left && headerCentre <= firstOfMonth.right,
        )
    }
}
