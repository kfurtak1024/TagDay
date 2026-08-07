package dev.krfu.tagday.ui.calendar.day

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import dev.krfu.tagday.R
import dev.krfu.tagday.data.local.entity.TagInstance
import dev.krfu.tagday.data.local.entity.TagType
import dev.krfu.tagday.data.model.TagDisplayGroup
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class DayContentTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private val date = LocalDate.of(2026, 7, 25)

    private var clicked: TagDisplayGroup? = null
    private var removed: TagDisplayGroup? = null

    private fun group(name: String, summary: String = name) = TagDisplayGroup(
        tagId = 1,
        tagName = name,
        color = 0xFF81C784.toInt(),
        type = TagType.SIMPLE,
        instances = listOf(TagInstance(id = 1, tagId = 1, date = 0, createdAt = 0)),
        summary = summary,
    )

    private fun setDay(groups: List<TagDisplayGroup>, today: LocalDate = date) {
        compose.setContent {
            DayContent(
                date = date,
                today = today,
                groups = groups,
                onGroupClick = { clicked = it },
                onGroupQuickRemove = { removed = it },
            )
        }
    }

    /**
     * The capsule body opens the sheet; only the ✕ removes (ADR-031, ADR-018's successor).
     *
     * Note what this does **not** prove. ADR-026/027 is about touch-target *geometry* — the ✕'s
     * 48dp minimum-target inflation reaching back over the tag text, so tapping the name removed
     * the tag. Robolectric has no real font metrics: the "walk" text measures 5px wide here
     * against a 32px ✕, so any coordinate-based assertion would be testing a fictional layout.
     * That regression stays a device check in `UI_UX.md`.
     */
    @Test
    fun tappingTheCapsuleBody_opensTheSheetRatherThanRemoving() {
        setDay(listOf(group("walk")))

        // Deliberately not performClick(): that targets the node's *centre*, and with
        // Robolectric's degenerate text metrics the ✕ occupies most of the capsule, so the
        // centre lands on the remove button. Click near the leading edge, which is the tag
        // name's side of the capsule whatever the text measures.
        compose.onAllNodes(hasClickAction()).onFirst().performTouchInput {
            click(Offset(2f, centerY))
        }
        // v2's rule runs on a StandardTestDispatcher, so a click's effects are queued rather
        // than applied inline — without this the callbacks haven't run yet.
        compose.waitForIdle()

        assertEquals("walk", clicked?.tagName)
        assertNull("the capsule body must never remove the tag", removed)
    }

    @Test
    fun tappingTheRemoveButton_removesTheGroup() {
        setDay(listOf(group("walk")))

        compose.onNodeWithContentDescription(
            context.getString(R.string.day_capsule_remove_content_description, "walk"),
        ).performClick()
        compose.waitForIdle()

        assertEquals("walk", removed?.tagName)
        assertNull(clicked)
    }

    @Test
    fun theCapsuleShowsItsSummary_notJustTheName() {
        setDay(listOf(group("walk", summary = "walk (2)")))

        compose.onNodeWithText("walk (2)").assertExists()
    }

    @Test
    fun anEmptyDayExplainsItself() {
        setDay(emptyList())

        compose.onNodeWithText(context.getString(R.string.day_empty_message)).assertExists()
    }

    /** ADR-017's past/today/future pill, and F6's rule that "today" comes from state. */
    @Test
    fun theHeaderAnnouncesTheDateAndHowItRelatesToToday() {
        setDay(emptyList(), today = date.minusDays(3))

        val future = context.getString(R.string.day_temporal_label_future)
        compose.onNodeWithContentDescription("Saturday, 25 July 2026, $future").assertExists()
    }

    @Test
    fun theHeaderSaysTodayWhenTheFocusedDateIsToday() {
        setDay(emptyList(), today = date)

        val today = context.getString(R.string.day_temporal_label_today)
        compose.onNodeWithContentDescription("Saturday, 25 July 2026, $today").assertExists()
    }
}
