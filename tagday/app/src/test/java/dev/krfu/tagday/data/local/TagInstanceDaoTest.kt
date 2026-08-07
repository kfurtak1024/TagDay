package dev.krfu.tagday.data.local

import androidx.room.Room
import dev.krfu.tagday.data.local.entity.Tag
import dev.krfu.tagday.data.local.entity.TagInstance
import dev.krfu.tagday.data.local.entity.TagType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * `TagInstanceDao`'s real SQL against in-memory SQLite — see [TagDaoTest] for why this layer
 * had no coverage at all until now.
 *
 * The ordering assertions matter most: `ORDER BY sortOrder` is what ADR-023 relies on to make
 * a manual reorder (ADR-022) show up in the day capsule as well as in the sheet, and it was
 * previously guaranteed only by the fakes choosing to sort the same way.
 */
@RunWith(RobolectricTestRunner::class)
class TagInstanceDaoTest {
    private lateinit var db: TagDayDatabase
    private lateinit var dao: TagInstanceDao
    private var walkId = 0L
    private var movieId = 0L

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), TagDayDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.tagInstanceDao()
        walkId = db.tagDao().insert(Tag(name = "walk", type = TagType.SIMPLE, color = 1, createdAt = 0))
        movieId = db.tagDao().insert(Tag(name = "movie", type = TagType.VALUED, color = 2, createdAt = 0))
    }

    @After
    fun tearDown() = db.close()

    private suspend fun add(tagId: Long, date: Int, sortOrder: Long, value: String? = null) =
        dao.insert(TagInstance(tagId = tagId, date = date, value = value, createdAt = 0, sortOrder = sortOrder))

    @Test
    fun observeForDay_returnsOnlyThatDay() = runBlocking {
        add(walkId, date = 100, sortOrder = 0)
        add(walkId, date = 101, sortOrder = 0)

        assertEquals(listOf(100), dao.observeForDay(100).first().map { it.instance.date })
    }

    @Test
    fun observeForDay_ordersBySortOrder_notInsertionOrder() = runBlocking {
        // ADR-023: display order is the DAO's job, so a manual reorder shows up everywhere an
        // instance list does. Inserted deliberately out of order.
        add(movieId, date = 100, sortOrder = 30, value = "third")
        add(movieId, date = 100, sortOrder = 10, value = "first")
        add(movieId, date = 100, sortOrder = 20, value = "second")

        assertEquals(
            listOf("first", "second", "third"),
            dao.observeForDay(100).first().map { it.instance.value },
        )
    }

    @Test
    fun observeForDay_joinsEachInstanceToItsTag() = runBlocking {
        add(walkId, date = 100, sortOrder = 0)
        add(movieId, date = 100, sortOrder = 1, value = "dune")

        val rows = dao.observeForDay(100).first()

        assertEquals(listOf("walk", "movie"), rows.map { it.tag.name })
        assertEquals(listOf(TagType.SIMPLE, TagType.VALUED), rows.map { it.tag.type })
    }

    @Test
    fun observeForRange_includesBothEndpoints() = runBlocking {
        listOf(99, 100, 105, 106).forEach { add(walkId, date = it, sortOrder = 0) }

        assertEquals(
            listOf(100, 105),
            dao.observeForRange(100, 105).first().map { it.instance.date }.sorted(),
        )
    }

    @Test
    fun observeForTagInRange_filtersByTagAndRange() = runBlocking {
        add(walkId, date = 100, sortOrder = 0)
        add(movieId, date = 100, sortOrder = 0, value = "dune")
        add(walkId, date = 200, sortOrder = 0)

        val counts = dao.observeForTagInRange(walkId, 100, 150).first()

        assertEquals(listOf(100), counts.map { it.date })
    }

    @Test
    fun updateAll_writesEveryInstance() = runBlocking {
        // The reorder commit path (ADR-022) writes the whole group's sortOrder in one call.
        add(movieId, date = 100, sortOrder = 10, value = "a")
        add(movieId, date = 100, sortOrder = 20, value = "b")
        val reversed = dao.observeForDay(100).first()
            .map { it.instance }
            .reversed()
            .mapIndexed { index, instance -> instance.copy(sortOrder = index.toLong()) }

        dao.updateAll(reversed)

        assertEquals(listOf("b", "a"), dao.observeForDay(100).first().map { it.instance.value })
    }

    @Test
    fun deleteAll_removesOnlyTheGivenInstances() = runBlocking {
        add(walkId, date = 100, sortOrder = 0)
        add(walkId, date = 100, sortOrder = 1)
        val toDelete = dao.observeForDay(100).first().first().instance

        dao.deleteAll(listOf(toDelete))

        assertEquals(1, dao.observeForDay(100).first().size)
    }
}
