package dev.krfu.tagday.data.repository

import dev.krfu.tagday.data.local.TagInstanceDao
import dev.krfu.tagday.data.local.entity.Tag
import dev.krfu.tagday.data.local.entity.TagInstance
import dev.krfu.tagday.data.local.entity.TagInstanceWithTag
import dev.krfu.tagday.data.local.entity.TagType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class TagInstanceRepositoryImplTest {
    private fun repositoryFor(rows: List<TagInstanceWithTag>): TagInstanceRepositoryImpl {
        val dao = object : TagInstanceDao {
            override fun observeForDay(date: Int): Flow<List<TagInstanceWithTag>> = flowOf(rows)
            override fun observeForRange(start: Int, end: Int): Flow<List<TagInstanceWithTag>> =
                throw NotImplementedError()
            override fun observeForTagInRange(tagId: Long, start: Int, end: Int): Flow<List<TagInstance>> =
                throw NotImplementedError()
            override suspend fun insert(instance: TagInstance): Long = throw NotImplementedError()
            override suspend fun update(instance: TagInstance) = throw NotImplementedError()
            override suspend fun delete(instance: TagInstance) = throw NotImplementedError()
        }
        return TagInstanceRepositoryImpl(dao)
    }

    private fun row(tag: Tag, instance: TagInstance) = TagInstanceWithTag(instance = instance, tag = tag)

    private fun tag(id: Long, name: String, type: TagType) =
        Tag(id = id, name = name, type = type, color = 0, createdAt = 0)

    private fun summaryFor(rows: List<TagInstanceWithTag>): String = runBlocking {
        repositoryFor(rows).observeDayGroups(date = 0).first().single().summary
    }

    @Test
    fun simple_singleInstance_hasNoCount() {
        val walk = tag(1, "walk", TagType.SIMPLE)
        val rows = listOf(row(walk, TagInstance(id = 1, tagId = 1, date = 0, createdAt = 0)))

        assertEquals("walk", summaryFor(rows))
    }

    @Test
    fun simple_multipleInstances_showsCount() {
        val walk = tag(1, "walk", TagType.SIMPLE)
        val rows = listOf(
            row(walk, TagInstance(id = 1, tagId = 1, date = 0, createdAt = 0)),
            row(walk, TagInstance(id = 2, tagId = 1, date = 0, createdAt = 1)),
        )

        assertEquals("walk (2)", summaryFor(rows))
    }

    @Test
    fun rated_averagesAcrossInstances() {
        val freediving = tag(1, "freediving", TagType.RATED)
        val rows = listOf(
            row(freediving, TagInstance(id = 1, tagId = 1, date = 0, rating = 3, createdAt = 0)),
            row(freediving, TagInstance(id = 2, tagId = 1, date = 0, rating = 5, createdAt = 1)),
        )

        assertEquals("freediving: ★★★★ (2)", summaryFor(rows))
    }

    @Test
    fun rated_ignoresUnratedInstancesInAverage() {
        val freediving = tag(1, "freediving", TagType.RATED)
        val rows = listOf(
            row(freediving, TagInstance(id = 1, tagId = 1, date = 0, rating = 4, createdAt = 0)),
            row(freediving, TagInstance(id = 2, tagId = 1, date = 0, rating = null, createdAt = 1)),
        )

        assertEquals("freediving: ★★★★ (2)", summaryFor(rows))
    }

    @Test
    fun rated_allUnrated_fallsBackToSimpleStyleWithoutCrashing() {
        val freediving = tag(1, "freediving", TagType.RATED)
        val rows = listOf(row(freediving, TagInstance(id = 1, tagId = 1, date = 0, rating = null, createdAt = 0)))

        assertEquals("freediving", summaryFor(rows))
    }

    @Test
    fun valued_listsDistinctValuesWithCounts() {
        val movie = tag(1, "movie", TagType.VALUED)
        val rows = listOf(
            row(movie, TagInstance(id = 1, tagId = 1, date = 0, value = "dune", createdAt = 0)),
            row(movie, TagInstance(id = 2, tagId = 1, date = 0, value = "dune", createdAt = 1)),
            row(movie, TagInstance(id = 3, tagId = 1, date = 0, value = "terminator", createdAt = 2)),
        )

        assertEquals("movie: [dune (2), terminator]", summaryFor(rows))
    }
}
