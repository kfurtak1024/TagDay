package dev.krfu.tagday.ui.tags

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.krfu.tagday.data.local.entity.Tag
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

    suspend fun renameTag(tag: Tag, newName: String): Boolean {
        val trimmed = newName.trim()
        if (tagRepository.nameExists(trimmed, excludingId = tag.id)) return false
        tagRepository.renameTag(tag, trimmed)
        return true
    }
}
