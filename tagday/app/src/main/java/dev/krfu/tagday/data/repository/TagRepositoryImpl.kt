package dev.krfu.tagday.data.repository

import dev.krfu.tagday.data.local.TagDao
import dev.krfu.tagday.data.local.entity.Tag
import dev.krfu.tagday.data.local.entity.TagType
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class TagRepositoryImpl @Inject constructor(
    private val tagDao: TagDao,
) : TagRepository {
    override fun observeAll(): Flow<List<Tag>> = tagDao.observeAll()

    override fun observeFiltered(query: String): Flow<List<Tag>> =
        tagDao.observeFiltered(query.escapeLikeWildcards())

    override suspend fun createTag(name: String, color: Int, type: TagType): Long? {
        val inserted = tagDao.insert(
            Tag(name = name, type = type, color = color, createdAt = System.currentTimeMillis()),
        )
        // -1 means the unique index on `name` rejected it — someone got there first. Resolve to
        // that tag rather than failing: for the race this guards (a double-tapped "+"), it's
        // the same tag the caller was trying to make. Its *type* is whatever it was created
        // with, which is the rule everywhere else too (ADR-034, and tag type is immutable).
        return if (inserted != -1L) inserted else tagDao.findByName(name)?.id
    }

    override suspend fun nameExists(name: String, excludingId: Long): Boolean =
        tagDao.nameExists(name, excludingId)

    override suspend fun renameTag(tag: Tag, newName: String) {
        tagDao.update(tag.copy(name = newName))
    }

    override suspend fun updateColor(tag: Tag, color: Int) {
        tagDao.update(tag.copy(color = color))
    }

    override suspend fun instanceCount(tagId: Long): Int = tagDao.instanceCount(tagId)

    override suspend fun deleteTag(tag: Tag) {
        tagDao.delete(tag)
    }
}

/**
 * Makes `%`, `_` and the escape character itself literal, for the tag-filter query's LIKE
 * (which declares `ESCAPE '\'` — see `TagDao.observeFiltered`). The filter is a plain
 * substring search from the user's point of view, so wildcards typed into it are text.
 * Backslash first, or it would escape the escapes added after it.
 */
internal fun String.escapeLikeWildcards(): String =
    replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
