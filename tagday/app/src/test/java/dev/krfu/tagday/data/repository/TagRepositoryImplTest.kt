package dev.krfu.tagday.data.repository

import dev.krfu.tagday.data.local.TagDao
import dev.krfu.tagday.data.local.entity.Tag
import dev.krfu.tagday.data.local.entity.TagType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class TagRepositoryImplTest {
    private class FakeTagDao : TagDao {
        var lastUpdated: Tag? = null
        var lastFilterQuery: String? = null

        /** What `insert` returns; -1 is Room's "the unique index rejected it" under IGNORE. */
        var insertResult: Long = 1L
        var existingByName: Tag? = null

        override fun observeAll(): Flow<List<Tag>> = throw NotImplementedError()
        override fun observeFiltered(query: String): Flow<List<Tag>> {
            lastFilterQuery = query
            return emptyFlow()
        }
        override suspend fun nameExists(name: String, excludingId: Long): Boolean = throw NotImplementedError()
        override suspend fun insert(tag: Tag): Long = insertResult
        override suspend fun findByName(name: String): Tag? = existingByName
        override suspend fun update(tag: Tag) {
            lastUpdated = tag
        }
        override suspend fun delete(tag: Tag) = throw NotImplementedError()
        override suspend fun instanceCount(tagId: Long): Int = throw NotImplementedError()
    }

    private fun tag() = Tag(id = 1, name = "walk", type = TagType.SIMPLE, color = 0xFF000000.toInt(), createdAt = 1_000L)

    @Test
    fun escapeLikeWildcards_makesLikeMetacharactersLiteral() {
        // The tag filter is a plain substring search, so a typed % or _ must not act as a
        // wildcard (it did before the ESCAPE clause on TagDao.observeFiltered).
        assertEquals("100\\% done", "100% done".escapeLikeWildcards())
        assertEquals("a\\_b", "a_b".escapeLikeWildcards())
        // The escape character itself is escaped first, so it survives as a literal.
        assertEquals("c\\\\d", "c\\d".escapeLikeWildcards())
        assertEquals("walk", "walk".escapeLikeWildcards())
    }

    @Test
    fun observeFiltered_passesTheEscapedQueryToTheDao() = runBlocking {
        val dao = FakeTagDao()

        TagRepositoryImpl(dao).observeFiltered("50%")

        assertEquals("50\\%", dao.lastFilterQuery)
    }

    @Test
    fun createTag_resolvesToTheExistingTagWhenTheNameIsTaken() = runBlocking {
        // Two "+" presses in quick succession both pass the caller's duplicate check against a
        // stale tag list; the second insert hits the unique index on `name`. Under the default
        // ABORT that threw out of the coroutine and crashed the app (BACKLOG F3). Now it
        // resolves to the tag that won the race.
        val dao = FakeTagDao().apply {
            insertResult = -1L
            existingByName = tag()
        }

        val id = TagRepositoryImpl(dao).createTag("walk", 0, TagType.SIMPLE)

        assertEquals(1L, id)
    }

    @Test
    fun createTag_returnsNullWhenTheNameIsNeitherInsertableNorFindable() = runBlocking {
        // Inserted-and-then-deleted in between. Nothing sensible to attach an instance to.
        val dao = FakeTagDao().apply {
            insertResult = -1L
            existingByName = null
        }

        assertEquals(null, TagRepositoryImpl(dao).createTag("walk", 0, TagType.SIMPLE))
    }

    @Test
    fun renameTag_changesOnlyName() = runBlocking {
        val dao = FakeTagDao()
        val repository = TagRepositoryImpl(dao)
        val original = tag()

        repository.renameTag(original, "jogging")

        val updated = dao.lastUpdated!!
        assertEquals("jogging", updated.name)
        assertEquals(original.id, updated.id)
        assertEquals(original.color, updated.color)
        assertEquals(original.type, updated.type)
        assertEquals(original.createdAt, updated.createdAt)
    }

    @Test
    fun updateColor_changesOnlyColor() = runBlocking {
        val dao = FakeTagDao()
        val repository = TagRepositoryImpl(dao)
        val original = tag()

        repository.updateColor(original, 0xFF123456.toInt())

        val updated = dao.lastUpdated!!
        assertEquals(0xFF123456.toInt(), updated.color)
        assertEquals(original.id, updated.id)
        assertEquals(original.name, updated.name)
        assertEquals(original.type, updated.type)
        assertEquals(original.createdAt, updated.createdAt)
    }
}
