package dev.krfu.tagday.ui.tags

import dev.krfu.tagday.data.local.entity.Tag

data class TagsUiState(
    val isLoading: Boolean = true,
    val query: String = "",
    val tags: List<Tag> = emptyList(),
    val pendingDelete: PendingDelete? = null,
) {
    data class PendingDelete(val tag: Tag, val instanceCount: Int)
}
