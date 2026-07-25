package dev.krfu.tagday.data.repository

import dev.krfu.tagday.data.local.entity.Tag
import dev.krfu.tagday.data.local.entity.TagType
import kotlinx.coroutines.flow.Flow

interface TagRepository {
    fun observeAll(): Flow<List<Tag>>

    fun observeFiltered(query: String): Flow<List<Tag>>

    suspend fun createTag(name: String, color: Int, type: TagType): Long

    suspend fun nameExists(name: String, excludingId: Long = 0): Boolean

    suspend fun renameTag(tag: Tag, newName: String)

    suspend fun updateColor(tag: Tag, color: Int)

    suspend fun instanceCount(tagId: Long): Int

    suspend fun deleteTag(tag: Tag)
}
