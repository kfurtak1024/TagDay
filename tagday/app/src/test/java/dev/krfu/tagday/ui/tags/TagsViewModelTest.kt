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

    @Test
    fun renameTag_uniqueName_renamesAndReportsSuccess() = runTest {
        val (viewModel, repository) = viewModelWith()

        val renamed = viewModel.renameTag(walk, "hiking")

        assertTrue(renamed)
        assertEquals("hiking", repository.tags.value.single { it.id == walk.id }.name)
    }

    @Test
    fun renameTag_trimsWhitespaceBeforeSaving() = runTest {
        val (viewModel, repository) = viewModelWith()

        viewModel.renameTag(walk, "  hiking  ")

        assertEquals("hiking", repository.tags.value.single { it.id == walk.id }.name)
    }

    @Test
    fun renameTag_nameTakenByAnotherTag_reportsFailureAndChangesNothing() = runTest {
        val (viewModel, repository) = viewModelWith()

        // Case-insensitively equal to `movie` — tags.name is a NOCASE unique index, so this
        // has to be rejected here rather than reaching the DAO and throwing.
        val renamed = viewModel.renameTag(walk, "MOVIE")

        assertFalse(renamed)
        assertEquals("walk", repository.tags.value.single { it.id == walk.id }.name)
    }

    @Test
    fun renameTag_recasingItsOwnName_isAllowed() = runTest {
        val (viewModel, repository) = viewModelWith()

        val renamed = viewModel.renameTag(walk, "Walk")

        assertTrue(renamed)
        assertEquals("Walk", repository.tags.value.single { it.id == walk.id }.name)
    }

    @Test
    fun requestDelete_exposesTheInstanceCountForTheConfirmationDialog() = runTest {
        val (viewModel, repository) = viewModelWith()
        repository.instanceCountResult = 7
        keepSubscribed(viewModel.uiState)

        viewModel.requestDelete(walk)

        assertEquals(walk, viewModel.uiState.value.pendingDelete?.tag)
        assertEquals(7, viewModel.uiState.value.pendingDelete?.instanceCount)
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
