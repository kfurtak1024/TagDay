package dev.krfu.tagday.ui.tags

import dev.krfu.tagday.MainDispatcherRule
import dev.krfu.tagday.data.local.entity.Tag
import dev.krfu.tagday.data.local.entity.TagType
import dev.krfu.tagday.data.repository.FakeTagRepository
import dev.krfu.tagday.keepSubscribed
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TagsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val walk = Tag(id = 1, name = "walk", type = TagType.SIMPLE, color = 0x111111, createdAt = 0)
    private val movie = Tag(id = 2, name = "movie", type = TagType.VALUED, color = 0x222222, createdAt = 0)

    private fun viewModelWith(tags: List<Tag> = listOf(walk, movie)): Pair<TagsViewModel, FakeTagRepository> {
        val repository = FakeTagRepository(tags)
        return TagsViewModel(repository) to repository
    }

    @Test
    fun onQueryChange_filtersTheList() = runTest {
        val (viewModel, _) = viewModelWith()
        keepSubscribed(viewModel.uiState)

        viewModel.onQueryChange("mov")

        assertEquals("mov", viewModel.uiState.value.query)
        assertEquals(listOf(movie.name), viewModel.uiState.value.tags.map { it.name })
    }

    /**
     * `renameTag` reports through a callback now rather than returning, so the write can live
     * on `viewModelScope` instead of the dialog's composition scope (BACKLOG F22). Null means
     * it never reported at all, which is itself a failure worth catching.
     */
    private fun TagsViewModel.renameResultOf(tag: Tag, newName: String): Boolean? {
        var result: Boolean? = null
        renameTag(tag, newName) { result = it }
        return result
    }

    @Test
    fun renameTag_uniqueName_renamesAndReportsSuccess() = runTest {
        val (viewModel, repository) = viewModelWith()

        val renamed = viewModel.renameResultOf(walk, "hiking")

        assertTrue(renamed!!)
        assertEquals("hiking", repository.tags.value.single { it.id == walk.id }.name)
    }

    @Test
    fun renameTag_trimsWhitespaceBeforeSaving() = runTest {
        val (viewModel, repository) = viewModelWith()

        viewModel.renameResultOf(walk, "  hiking  ")

        assertEquals("hiking", repository.tags.value.single { it.id == walk.id }.name)
    }

    @Test
    fun renameTag_nameTakenByAnotherTag_reportsFailureAndChangesNothing() = runTest {
        val (viewModel, repository) = viewModelWith()

        // Case-insensitively equal to `movie` — tags.name is a NOCASE unique index, so this
        // has to be rejected here rather than reaching the DAO and throwing.
        val renamed = viewModel.renameResultOf(walk, "MOVIE")

        assertFalse(renamed!!)
        assertEquals("walk", repository.tags.value.single { it.id == walk.id }.name)
    }

    @Test
    fun renameTag_toTheNameItAlreadyHas_isAllowedRatherThanReadingAsADuplicateOfItself() = runTest {
        // The `excludingId = tag.id` half of the duplicate check. This used to be asserted by
        // renaming "walk" to "Walk", which ADR-028 has since made an invalid name outright —
        // so the casing was doing the work of demonstrating a guard that isn't about casing.
        val (viewModel, repository) = viewModelWith()

        val renamed = viewModel.renameResultOf(walk, "walk")

        assertTrue(renamed!!)
        assertEquals("walk", repository.tags.value.single { it.id == walk.id }.name)
    }

    @Test
    fun renameTag_aNameBreakingTheNamingRule_isRejectedAndWritesNothing() = runTest {
        // ADR-028's rule was enforced only by the dialog sanitizing as you type, so nothing
        // below the UI stopped a malformed name reaching the database. Unreachable through the
        // dialog (Save is disabled), which is exactly why it needs a test rather than trust.
        val (viewModel, repository) = viewModelWith()

        listOf("Walk", "fast--food", "walk-", "walk fast", "walk2", "").forEach { bad ->
            assertFalse("\"$bad\" must not be saved", viewModel.renameResultOf(walk, bad)!!)
        }

        assertEquals("walk", repository.tags.value.single { it.id == walk.id }.name)
    }

    @Test
    fun updateColor_changesThatTagOnly() = runTest {
        val (viewModel, repository) = viewModelWith()
        keepSubscribed(viewModel.uiState)

        viewModel.updateColor(walk, 0xFF123456.toInt())

        assertEquals(0xFF123456.toInt(), repository.tags.value.single { it.id == walk.id }.color)
        assertEquals(movie.color, repository.tags.value.single { it.id == movie.id }.color)
        // And the list the screen renders reflects it, rather than only the repository.
        assertEquals(
            0xFF123456.toInt(),
            viewModel.uiState.value.tags.single { it.id == walk.id }.color,
        )
    }

    @Test
    fun requestDelete_exposesTheInstanceCountForTheConfirmationDialog() = runTest {
        val (viewModel, repository) = viewModelWith()
        repository.taggedDayCountResult = 7
        keepSubscribed(viewModel.uiState)

        viewModel.requestDelete(walk)

        assertEquals(walk, viewModel.uiState.value.pendingDelete?.tag)
        assertEquals(7, viewModel.uiState.value.pendingDelete?.taggedDayCount)
        assertTrue(repository.deletedTags.isEmpty())
    }

    @Test
    fun confirmDelete_deletesTheTagAndClearsThePendingState() = runTest {
        val (viewModel, repository) = viewModelWith()
        keepSubscribed(viewModel.uiState)
        viewModel.requestDelete(walk)

        viewModel.confirmDelete()

        assertEquals(listOf(walk), repository.deletedTags)
        assertNull(viewModel.uiState.value.pendingDelete)
    }

    @Test
    fun cancelDelete_clearsThePendingStateWithoutDeleting() = runTest {
        val (viewModel, repository) = viewModelWith()
        keepSubscribed(viewModel.uiState)
        viewModel.requestDelete(walk)

        viewModel.cancelDelete()

        assertNull(viewModel.uiState.value.pendingDelete)
        assertTrue(repository.deletedTags.isEmpty())
    }

    @Test
    fun confirmDelete_withNothingPending_isANoOp() = runTest {
        val (viewModel, repository) = viewModelWith()
        keepSubscribed(viewModel.uiState)

        viewModel.confirmDelete()

        assertTrue(repository.deletedTags.isEmpty())
    }
}
