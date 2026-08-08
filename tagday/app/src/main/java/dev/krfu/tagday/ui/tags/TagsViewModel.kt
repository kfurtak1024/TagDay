package dev.krfu.tagday.ui.tags

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.krfu.tagday.data.local.entity.Tag
import dev.krfu.tagday.data.model.TagName
import dev.krfu.tagday.data.repository.TagRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TagsViewModel @Inject constructor(
    private val tagRepository: TagRepository,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val pendingDelete = MutableStateFlow<TagsUiState.PendingDelete?>(null)

    val uiState: StateFlow<TagsUiState> = combine(
        query,
        query.flatMapLatest { q -> tagRepository.observeFiltered(q) },
        pendingDelete,
    ) { q, tags, pending ->
        TagsUiState(isLoading = false, query = q, tags = tags, pendingDelete = pending)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TagsUiState())

    fun onQueryChange(newQuery: String) {
        query.value = newQuery
    }

    fun requestDelete(tag: Tag) {
        viewModelScope.launch {
            val count = tagRepository.taggedDayCount(tag.id)
            pendingDelete.value = TagsUiState.PendingDelete(tag, count)
        }
    }

    fun confirmDelete() {
        val pending = pendingDelete.value ?: return
        viewModelScope.launch {
            tagRepository.deleteTag(pending.tag)
            pendingDelete.value = null
        }
    }

    fun cancelDelete() {
        pendingDelete.value = null
    }

    fun updateColor(tag: Tag, color: Int) {
        viewModelScope.launch {
            tagRepository.updateColor(tag, color)
        }
    }

    /**
     * Renames on [viewModelScope] and reports the outcome through [onResult] — `false` means the
     * name is taken. It used to be a plain `suspend fun` the dialog called from its own
     * `rememberCoroutineScope`, which tied the *write* to the composition: dismissing the dialog
     * or rotating between the duplicate check and the update cancelled it, and the rename
     * silently didn't happen (BACKLOG F22). Every other mutation here already goes through
     * `viewModelScope`; this one now matches.
     *
     * [onResult] may fire after the dialog is gone, which is harmless — it only drives the
     * dialog's own state, and nothing observes it once that state is discarded.
     */
    fun renameTag(tag: Tag, newName: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val trimmed = newName.trim()
            // ADR-028's naming rule was enforced *only* by the two UI call sites that sanitize
            // as you type (this dialog and the quick-entry bar), so nothing below the UI
            // stopped a malformed name being written. Checking here makes the rule hold for
            // any caller rather than for the two that happen to be careful.
            //
            // Note what `false` means to the dialog: it renders "a tag with this name already
            // exists", which would be the wrong explanation for an invalid name. That's
            // tolerable only because the dialog disables Save unless `TagName.isValid`, so it
            // can't produce this branch — this is a backstop, not a user-facing path. A second
            // caller that *can* hit it is the point at which `onResult` should carry a reason
            // rather than a Boolean.
            if (!TagName.isValid(trimmed) || tagRepository.nameExists(trimmed, excludingId = tag.id)) {
                onResult(false)
                return@launch
            }
            tagRepository.renameTag(tag, trimmed)
            onResult(true)
        }
    }
}
