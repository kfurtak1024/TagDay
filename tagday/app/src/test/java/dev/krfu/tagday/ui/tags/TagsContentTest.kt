package dev.krfu.tagday.ui.tags

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import dev.krfu.tagday.R
import dev.krfu.tagday.data.local.entity.Tag
import dev.krfu.tagday.data.local.entity.TagType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The tag-management list: its two different empty states, and the three per-row actions, each
 * of which has to carry the tag it belongs to. The rename/delete/recolor controls are icons with
 * no visible text, so their content descriptions are the only thing naming them — for a screen
 * reader *and* for this test.
 *
 * The delete-confirmation dialog is **not** covered: `AlertDialog` doesn't work under Robolectric
 * here (see `TESTING.md` § Compose tests), so this drives `TagsContent` in its
 * `pendingDelete == null` state only. The ViewModel side of that flow is covered by
 * `TagsViewModelTest`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class TagsContentTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private val clearLabel by lazy { context.getString(R.string.tags_search_clear_content_description) }

    private val walk = Tag(id = 1, name = "walk", type = TagType.SIMPLE, color = 0xFF81C784.toInt(), createdAt = 0)
    private val movie = Tag(id = 2, name = "movie", type = TagType.VALUED, color = 0xFF4FC3F7.toInt(), createdAt = 0)

    private var query: String? = null
    private var renamed: Tag? = null
    private var recolored: Tag? = null
    private var deleteRequested: Tag? = null
    private var navigatedBack = false

    /** The one editable node on this screen — see `withAFilterThatMatchesNothing…` for why. */
    private fun searchField() = compose.onNode(hasSetTextAction())

    private fun setTags(
        tags: List<Tag> = listOf(walk, movie),
        currentQuery: String = "",
        isLoading: Boolean = false,
        searchText: String = currentQuery,
    ) {
        compose.setContent {
            TagsContent(
                uiState = TagsUiState(isLoading = isLoading, query = currentQuery, tags = tags),
                searchText = searchText,
                onNavigateBack = { navigatedBack = true },
                onQueryChange = { query = it },
                onRenameClick = { renamed = it },
                onColorClick = { recolored = it },
                onDeleteClick = { deleteRequested = it },
                onConfirmDelete = {},
                onCancelDelete = {},
            )
        }
    }

    @Test
    fun withNoTagsAtAll_saysThereAreNoneYet() {
        setTags(tags = emptyList())

        compose.onNodeWithText(context.getString(R.string.tags_empty_message)).assertExists()
    }

    @Test
    fun withAQueryThatMatchesNothing_namesTheQueryRatherThanClaimingThereAreNoTags() {
        // Two distinct states behind one empty list: "you have no tags" and "none match this".
        // Collapsing them would tell someone mid-search that their tags are gone.
        setTags(tags = emptyList(), currentQuery = "zzz")

        compose.onNodeWithText(context.getString(R.string.tags_empty_filtered_message, "zzz")).assertExists()
        compose.onNodeWithText(context.getString(R.string.tags_empty_message)).assertDoesNotExist()
    }

    @Test
    fun whileLoading_showsNeitherEmptyMessage() {
        // The first frame has `tags` still empty, so without reading `isLoading` this screen
        // told anyone who *has* tags that they have none, then corrected itself. An empty list
        // is only news once the repository has actually answered.
        setTags(tags = emptyList(), isLoading = true)

        compose.onNodeWithText(context.getString(R.string.tags_empty_message)).assertDoesNotExist()
    }

    @Test
    fun theColorSwatchMeetsTheMinimumTouchTarget() {
        // A bare `Modifier.clickable` gets no minimum-target inflation, unlike IconButton — so
        // this was a 28dp target next to a 48dp delete button. Geometry is trustworthy under
        // Robolectric even though text metrics aren't, as long as no text width feeds into it.
        setTags()

        val swatch = compose.onNodeWithContentDescription(
            context.getString(R.string.tags_color_content_description, "walk"),
        ).getUnclippedBoundsInRoot()

        assertTrue(
            "the colour swatch is ${swatch.width} x ${swatch.height}, under the 48dp minimum",
            swatch.width >= 48.dp && swatch.height >= 48.dp,
        )
    }

    @Test
    fun eachRowNamesTheTagAndItsType() {
        // Type is fixed at creation and can't be changed (CLAUDE.md § Hard rules), so the row
        // stating it is the only place the distinction is visible on this screen.
        setTags()

        compose.onNodeWithText("walk").assertExists()
        compose.onNodeWithText("movie").assertExists()
        compose.onNodeWithText(context.getString(R.string.tag_type_simple)).assertExists()
        compose.onNodeWithText(context.getString(R.string.tag_type_valued)).assertExists()
    }

    @Test
    fun theSearchFieldReportsWhatWasTypedVerbatim() {
        // Deliberately *not* sanitized the way the naming fields are (ADR-028): this is a
        // substring filter, and normalizing it would stop "Fast Fo" ever matching anything.
        setTags()

        searchField().performTextInput("Mov")
        compose.waitForIdle()

        assertEquals("Mov", query)
    }

    // --- search field ------------------------------------------------------------------

    @Test
    fun theClearButtonIsAbsentWhileTheFieldIsEmpty() {
        setTags(searchText = "")

        compose.onNodeWithContentDescription(clearLabel).assertDoesNotExist()
    }

    @Test
    fun theClearButtonAppearsOnceSomethingIsTyped() {
        setTags(searchText = "mov")

        compose.onNodeWithContentDescription(clearLabel).assertExists()
    }

    @Test
    fun theClearButtonEmptiesTheQuery() {
        setTags(currentQuery = "mov", tags = listOf(movie))

        compose.onNodeWithContentDescription(clearLabel).performClick()
        compose.waitForIdle()

        assertEquals("", query)
    }

    @Test
    fun theSearchFieldAsksForASearchKey_notANewline() {
        // Single-line and filtering live, so the IME's action key has nothing to submit — but
        // it shouldn't offer a newline either. Asserted because it's invisible otherwise.
        setTags()

        searchField().assert(SemanticsMatcher.expectValue(SemanticsProperties.ImeAction, ImeAction.Search))
    }

    @Test
    fun withNoTagsAtAll_thereIsNoSearchFieldToNarrowAnEmptyList() {
        setTags(tags = emptyList())

        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
    }

    @Test
    fun withAFilterThatMatchesNothing_theSearchFieldStays() {
        // The distinction the previous test depends on: an empty *result* still needs the field,
        // or there'd be no way to change the query that emptied it.
        setTags(tags = emptyList(), currentQuery = "zzz")

        // By edit action, not by placeholder: the placeholder is gone once the field has text.
        searchField().assertExists()
    }

    @Test
    fun eachRowsActionsCarryTheirOwnTag() {
        // The list renders one identical-looking set of icons per row, so the risk here is a
        // callback closing over the wrong tag — asserted against the *second* row for that
        // reason.
        setTags()

        compose.onNodeWithContentDescription(
            context.getString(R.string.tags_rename_content_description, "movie"),
        ).performClick()
        compose.onNodeWithContentDescription(
            context.getString(R.string.tags_delete_content_description, "movie"),
        ).performClick()
        compose.onNodeWithContentDescription(
            context.getString(R.string.tags_color_content_description, "movie"),
        ).performClick()
        compose.waitForIdle()

        assertEquals(movie, renamed)
        assertEquals(movie, deleteRequested)
        assertEquals(movie, recolored)
    }

    @Test
    fun theBackButtonNavigatesBack() {
        setTags()

        compose.onNodeWithContentDescription(
            context.getString(R.string.nav_back_content_description),
        ).performClick()
        compose.waitForIdle()

        assertEquals(true, navigatedBack)
    }
}
