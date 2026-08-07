package dev.krfu.tagday.data.repository

import dev.krfu.tagday.data.local.entity.Tag
import dev.krfu.tagday.data.local.entity.TagType
import kotlinx.coroutines.flow.Flow

interface TagRepository {
    fun observeAll(): Flow<List<Tag>>

    fun observeFiltered(query: String): Flow<List<Tag>>

    /**
     * Creates the tag, or returns the id of the one that already has this name. Racing "+"
     * presses can both pass the caller's duplicate check against a stale tag list, and a
     * unique-index violation used to crash rather than resolve (BACKLOG F3).
     */
    suspend fun createTag(name: String, color: Int, type: TagType): Long?

    suspend fun nameExists(name: String, excludingId: Long = 0): Boolean

    suspend fun renameTag(tag: Tag, newName: String)

    suspend fun updateColor(tag: Tag, color: Int)

    /** Number of distinct days this tag appears on — see `TagDao.taggedDayCount`. */
    suspend fun taggedDayCount(tagId: Long): Int

    suspend fun deleteTag(tag: Tag)
}
