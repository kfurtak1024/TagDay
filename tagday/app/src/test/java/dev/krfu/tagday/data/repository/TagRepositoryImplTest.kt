package dev.krfu.tagday.data.repository

import dev.krfu.tagday.data.local.TagDao
import dev.krfu.tagday.data.local.entity.Tag
import dev.krfu.tagday.data.local.entity.TagType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class TagRepositoryImplTest {
    private class FakeTagDao : TagDao {
        var lastUpdated: Tag? = null

        override fun observeAll(): Flow<List<Tag>> = throw NotImplementedError()
        override fun observeFiltered(query: String): Flow<List<Tag>> = throw NotImplementedError()
        override suspend fun nameExists(name: String, excludingId: Long): Boolean = throw NotImplementedError()
        override suspend fun insert(tag: Tag): Long = throw NotImplementedError()
        override suspend fun update(tag: Tag) {
            lastUpdated = tag
        }
        override suspend fun delete(tag: Tag) = throw NotImplementedError()
        override suspend fun instanceCount(tagId: Long): Int = throw NotImplementedError()
    }

    private fun tag() = Tag(id = 1, name = "walk", type = TagType.SIMPLE, color = 0xFF000000.toInt(), createdAt = 1_000L)

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
