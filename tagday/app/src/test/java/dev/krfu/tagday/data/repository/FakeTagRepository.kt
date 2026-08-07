package dev.krfu.tagday.data.repository

import dev.krfu.tagday.data.local.entity.Tag
import dev.krfu.tagday.data.local.entity.TagType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Hand-written repository fake for the ViewModel tests — the same "fake the collaborator
 * you own, don't mock it" pattern `TESTING.md` documents for the DAO-level repository
 * tests, one layer up: a ViewModel's collaborators are the repository *interfaces*, so
 * those are what get faked. Data lives in a [MutableStateFlow] so writes show up in later
 * emissions the way Room's `Flow`s would, letting a test assert both what the ViewModel
 * emitted and what it wrote.
 */
class FakeTagRepository(initialTags: List<Tag> = emptyList()) : TagRepository {
    val tags = MutableStateFlow(initialTags)
    var taggedDayCountResult: Int = 0
    val deletedTags = mutableListOf<Tag>()

    override fun observeAll(): Flow<List<Tag>> = tags

    override fun observeFiltered(query: String): Flow<List<Tag>> =
        tags.map { all -> all.filter { it.name.contains(query, ignoreCase = true) } }

    /** Mirrors the real impl's collision behaviour: an existing name resolves to that tag. */
    override suspend fun createTag(name: String, color: Int, type: TagType): Long? {
        tags.value.find { it.name.equals(name, ignoreCase = true) }?.let { return it.id }
        val id = (tags.value.maxOfOrNull { it.id } ?: 0L) + 1
        tags.value += Tag(id = id, name = name, type = type, color = color, createdAt = id)
        return id
    }

    override suspend fun nameExists(name: String, excludingId: Long): Boolean =
        tags.value.any { it.name.equals(name, ignoreCase = true) && it.id != excludingId }

    override suspend fun renameTag(tag: Tag, newName: String) {
        tags.value = tags.value.map { if (it.id == tag.id) it.copy(name = newName) else it }
    }

    override suspend fun updateColor(tag: Tag, color: Int) {
        tags.value = tags.value.map { if (it.id == tag.id) it.copy(color = color) else it }
    }

    override suspend fun taggedDayCount(tagId: Long): Int = taggedDayCountResult

    override suspend fun deleteTag(tag: Tag) {
        deletedTags += tag
        tags.value = tags.value.filterNot { it.id == tag.id }
    }
}
