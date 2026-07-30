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
import org.junit.Assert.assertTrue
import org.junit.Test

class TagInstanceRepositoryImplTest {
    private fun repositoryFor(
        dayRows: List<TagInstanceWithTag> = emptyList(),
        rangeRows: List<TagInstanceWithTag> = emptyList(),
        tagRangeInstances: List<TagInstance> = emptyList(),
    ): TagInstanceRepositoryImpl {
        val dao = object : TagInstanceDao {
            override fun observeForDay(date: Int): Flow<List<TagInstanceWithTag>> = flowOf(dayRows)
            override fun observeForRange(start: Int, end: Int): Flow<List<TagInstanceWithTag>> = flowOf(rangeRows)
            override fun observeForTagInRange(tagId: Long, start: Int, end: Int): Flow<List<TagInstance>> =
                flowOf(tagRangeInstances)
            override suspend fun insert(instance: TagInstance): Long = throw NotImplementedError()
            override suspend fun update(instance: TagInstance) = throw NotImplementedError()
            override suspend fun updateAll(instances: List<TagInstance>) = throw NotImplementedError()
            override suspend fun deleteAll(instances: List<TagInstance>) = throw NotImplementedError()
        }
        return TagInstanceRepositoryImpl(dao)
    }

    /** Captures what [TagInstanceRepositoryImpl.addInstance] actually builds. */
    private class RecordingDao : TagInstanceDao {
        val inserted = mutableListOf<TagInstance>()

        override fun observeForDay(date: Int): Flow<List<TagInstanceWithTag>> = throw NotImplementedError()
        override fun observeForRange(start: Int, end: Int): Flow<List<TagInstanceWithTag>> = throw NotImplementedError()
        override fun observeForTagInRange(tagId: Long, start: Int, end: Int): Flow<List<TagInstance>> =
            throw NotImplementedError()
        override suspend fun insert(instance: TagInstance): Long {
            inserted += instance
            return inserted.size.toLong()
        }
        override suspend fun update(instance: TagInstance) = throw NotImplementedError()
        override suspend fun updateAll(instances: List<TagInstance>) = throw NotImplementedError()
        override suspend fun deleteAll(instances: List<TagInstance>) = throw NotImplementedError()
    }

    private fun row(tag: Tag, instance: TagInstance) = TagInstanceWithTag(instance = instance, tag = tag)

    private fun tag(id: Long, name: String, type: TagType) =
        Tag(id = id, name = name, type = type, color = 0, createdAt = 0)

    private fun summaryFor(rows: List<TagInstanceWithTag>): String = runBlocking {
        repositoryFor(dayRows = rows).observeDayGroups(date = 0).first().single().summary
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

    @Test
    fun valued_summaryFollowsRowOrder_soAManualReorderShowsInTheCapsule() {
        // The DAO orders rows by sortOrder (see TagInstanceDao) and summarize must not
        // re-sort or otherwise lose that order — this is what makes a drag-reorder in the
        // instance sheet show up in the capsule summary too. ADR-022.
        val movie = tag(1, "movie", TagType.VALUED)
        val rows = listOf(
            row(movie, TagInstance(id = 3, tagId = 1, date = 0, value = "terminator", createdAt = 2, sortOrder = 0)),
            row(movie, TagInstance(id = 1, tagId = 1, date = 0, value = "dune", createdAt = 0, sortOrder = 1)),
        )

        assertEquals("movie: [terminator, dune]", summaryFor(rows))
    }

    @Test
    fun addInstance_seedsSortOrderFromCreatedAt_soNewValuesSortAfterReorderedOnes() = runBlocking {
        // sortOrder is seeded with the same epoch-millis as createdAt rather than queried
        // for (ADR-021): a manual reorder rewrites a group's sortOrder to small sequential
        // indices, so any later insert's timestamp is guaranteed to sort after them.
        val dao = RecordingDao()

        TagInstanceRepositoryImpl(dao).addInstance(tagId = 5, date = 100, value = "dune")

        val inserted = dao.inserted.single()
        assertEquals(5L, inserted.tagId)
        assertEquals(100, inserted.date)
        assertEquals("dune", inserted.value)
        assertEquals(inserted.createdAt, inserted.sortOrder)
        assertTrue(inserted.sortOrder > 0)
    }

    @Test
    fun observeRangeGroups_groupsPerDayWithinRange() = runBlocking {
        val walk = tag(1, "walk", TagType.SIMPLE)
        val reading = tag(2, "reading", TagType.SIMPLE)
        val rows = listOf(
            row(walk, TagInstance(id = 1, tagId = 1, date = 100, createdAt = 0)),
            row(walk, TagInstance(id = 2, tagId = 1, date = 100, createdAt = 1)),
            row(reading, TagInstance(id = 3, tagId = 2, date = 102, createdAt = 0)),
        )

        val groupsByDate = repositoryFor(rangeRows = rows).observeRangeGroups(100, 106).first()

        assertEquals(setOf(100, 102), groupsByDate.keys)
        assertEquals("walk (2)", groupsByDate.getValue(100).single().summary)
        assertEquals("reading", groupsByDate.getValue(102).single().summary)
    }

    @Test
    fun observeTagInstanceCounts_countsPerDay_daysWithoutInstancesAbsent() = runBlocking {
        val instances = listOf(
            TagInstance(id = 1, tagId = 5, date = 200, createdAt = 0),
            TagInstance(id = 2, tagId = 5, date = 200, createdAt = 1),
            TagInstance(id = 3, tagId = 5, date = 203, createdAt = 0),
        )

        val counts = repositoryFor(tagRangeInstances = instances).observeTagInstanceCounts(5, 200, 206).first()

        assertEquals(mapOf(200 to 2, 203 to 1), counts)
    }
}
