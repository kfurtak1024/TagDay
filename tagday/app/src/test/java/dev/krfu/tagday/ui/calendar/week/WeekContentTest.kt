package dev.krfu.tagday.ui.calendar.week

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import dev.krfu.tagday.data.local.entity.TagType
import dev.krfu.tagday.data.model.TagDisplayGroup
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The project's first Compose test. It runs on the JVM under Robolectric — no device, no
 * emulator — as part of `testDebugUnitTest` (ADR-040).
 *
 * It targets ADR-038's Week semantics specifically, because that's the fix nothing else could
 * check: `WeekContent` renders one coloured dot per tag and no text at all, so a screen reader
 * saw nothing until the row started merging its descendants (BACKLOG F13). No plain unit test
 * reaches that — the description exists only in a composed semantics tree, so something has to
 * actually compose it.
 */
@RunWith(RobolectricTestRunner::class)
// Pinned to a realistic phone. Robolectric's default screen is much shorter, and `WeekContent`
// is a non-scrolling Column of seven rows — on a short screen the last days are clipped off the
// bottom with no way to reach them (BACKLOG F25, found by exactly this test). Pinning keeps
// these assertions about semantics rather than about whatever screen the runner defaults to.
@Config(qualifiers = "w411dp-h891dp")
class WeekContentTest {
    // v2: the non-v2 factory is deprecated. v2 runs on a StandardTestDispatcher rather than an
    // Unconfined one, so work is queued rather than run eagerly — fine here, since these tests
    // only compose and then assert, but worth knowing before adding anything time-dependent.
    @get:Rule
    val compose = createComposeRule()

    private val monday = LocalDate.of(2026, 7, 20)

    private fun group(id: Long, name: String, summary: String) = TagDisplayGroup(
        tagId = id,
        tagName = name,
        color = 0xFF81C784.toInt(),
        type = TagType.SIMPLE,
        instances = emptyList(),
        summary = summary,
    )

    private fun setWeek(groupsByDate: Map<Int, List<TagDisplayGroup>>) {
        compose.setContent {
            WeekContent(
                focusedDate = monday,
                today = monday,
                groupsByDate = groupsByDate,
                onDayClick = {},
            )
        }
    }

    @Test
    fun aDayWithTags_announcesItsDateAndEveryTagOnIt() {
        setWeek(
            mapOf(
                monday.toEpochDay().toInt() to listOf(
                    group(1, "walk", "walk"),
                    group(2, "reading", "reading (2)"),
                ),
            ),
        )

        // Exactly what TalkBack reads for that row — the dots themselves carry no text.
        compose.onNodeWithContentDescription("Monday 20 July: walk, reading (2)").assertIsDisplayed()
    }

    @Test
    fun aDayWithNothingTagged_saysSoRatherThanBeingSilent() {
        setWeek(emptyMap())

        compose.onNodeWithContentDescription("Tuesday 21 July: nothing tagged").assertIsDisplayed()
    }

    @Test
    fun everyDayOfTheWeekIsLabelled() {
        setWeek(emptyMap())

        // assertExists, not assertIsDisplayed: the question here is whether all seven rows are
        // in the semantics tree with the right label. Whether the last one is within the
        // viewport is a layout concern, and a real one — see F25.
        (0..6).map { monday.plusDays(it.toLong()) }.forEach { day ->
            val label = "${day.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }} " +
                "${day.dayOfMonth} July"
            compose.onNodeWithContentDescription("$label: nothing tagged").assertExists()
        }
    }
}
