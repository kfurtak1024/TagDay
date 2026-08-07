package dev.krfu.tagday.data.local

import androidx.room.Room
import dev.krfu.tagday.data.local.entity.Tag
import dev.krfu.tagday.data.local.entity.TagInstance
import dev.krfu.tagday.data.local.entity.TagType
import dev.krfu.tagday.data.repository.TagRepositoryImpl
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Exercises `TagDao`'s **actual SQL** against a real in-memory SQLite, which nothing did
 * before: every other test in the project fakes the DAO, so the queries themselves were only
 * ever compiler-checked strings. That left the `COLLATE NOCASE` rules, the `ESCAPE` clause, the
 * `IGNORE` conflict strategy, `COUNT(DISTINCT …)` and the cascading delete all unverified —
 * several of which are load-bearing decisions (ADR-037, BACKLOG F9).
 *
 * Runs on the JVM under Robolectric, which ships a real SQLite (ADR-040).
 */
@RunWith(RobolectricTestRunner::class)
class TagDaoTest {
    private lateinit var db: TagDayDatabase
    private lateinit var dao: TagDao
    private lateinit var instanceDao: TagInstanceDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), TagDayDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.tagDao()
        instanceDao = db.tagInstanceDao()
    }

    @After
    fun tearDown() = db.close()

    private fun tag(name: String, type: TagType = TagType.SIMPLE) =
        Tag(name = name, type = type, color = 0x111111, createdAt = 0)

    private fun instance(tagId: Long, date: Int) =
        TagInstance(tagId = tagId, date = date, createdAt = 0, sortOrder = 0)

    @Test
    fun observeAll_ordersByNameIgnoringCase() = runBlocking {
        listOf("zebra", "Apple", "mango").forEach { dao.insert(tag(it)) }

        assertEquals(listOf("Apple", "mango", "zebra"), dao.observeAll().first().map { it.name })
    }

    @Test
    fun insert_duplicateNameDifferingOnlyInCase_isRejectedRatherThanThrowing() = runBlocking {
        // `tags.name` is a NOCASE unique index. Under the default ABORT this threw
        // SQLiteConstraintException out of the calling coroutine and crashed the app; ADR-037
        // switched it to IGNORE so the caller gets -1 and can resolve to the existing tag.
        assertEquals(1L, dao.insert(tag("walk")))

        assertEquals(-1L, dao.insert(tag("WALK")))
        assertEquals(1, dao.observeAll().first().size)
    }

    @Test
    fun findByName_ignoresCase() = runBlocking {
        dao.insert(tag("fast-food"))

        assertEquals("fast-food", dao.findByName("FAST-FOOD")?.name)
        assertNull(dao.findByName("slow-food"))
    }

    @Test
    fun nameExists_ignoresCaseAndSkipsTheExcludedId() = runBlocking {
        val id = dao.insert(tag("walk"))

        assertTrue(dao.nameExists("WALK"))
        // Renaming a tag to a re-cased version of its own name must stay allowed.
        assertFalse(dao.nameExists("WALK", excludingId = id))
    }

    @Test
    fun taggedDayCount_countsDistinctDays_notInstances() = runBlocking {
        // The bug BACKLOG F9 fixed: this was COUNT(*), so a tag applied twice in one day
        // reported two "tagged days" in the deletion dialog.
        val id = dao.insert(tag("walk"))
        instanceDao.insert(instance(id, date = 100))
        instanceDao.insert(instance(id, date = 100))
        instanceDao.insert(instance(id, date = 101))

        assertEquals(2, dao.taggedDayCount(id))
    }

    @Test
    fun taggedDayCount_withNoInstances_isZero() = runBlocking {
        // Reachable: quick-entry can create a Rated/Valued tag with no instance at all
        // (ADR-031), and deleting it must not report a nonsense count.
        assertEquals(0, dao.taggedDayCount(dao.insert(tag("unused"))))
    }

    @Test
    fun delete_cascadesToTheTagsInstances() = runBlocking {
        val id = dao.insert(tag("walk"))
        val other = dao.insert(tag("reading"))
        instanceDao.insert(instance(id, date = 100))
        instanceDao.insert(instance(other, date = 100))

        dao.delete(dao.findByName("walk")!!)

        // The foreign key's ON DELETE CASCADE, which nothing had ever actually executed.
        val remaining = instanceDao.observeForDay(100).first()
        assertEquals(listOf(other), remaining.map { it.instance.tagId })
    }

    @Test
    fun observeFiltered_treatsTypedWildcardsAsLiterals() = runBlocking {
        // End-to-end over the pair that makes this work: TagRepositoryImpl escapes the input,
        // and TagDao's query declares ESCAPE '\'. Without both, a typed % matched every tag.
        //
        // The assertion has to *discriminate*, which an earlier version of this test didn't:
        // "searching % finds nothing" holds whether the escape works or is mangled into a
        // pattern that matches nothing, so it passed with the ESCAPE clause deleted. Storing a
        // name that really contains % and requiring it to be found is what separates the two.
        // (Names from the UI can't contain % — TagName forbids it — but the DAO is what's
        // under test, and pre-rule names in the wild aren't guaranteed to conform.)
        listOf("a%b", "axb", "a_b", "azb", "walk").forEach { dao.insert(tag(it)) }
        val repository = TagRepositoryImpl(dao)

        assertEquals(listOf("a%b"), repository.observeFiltered("a%b").first().map { it.name })
        assertEquals(listOf("a_b"), repository.observeFiltered("a_b").first().map { it.name })
        // And a bare wildcard is a literal too, so it matches only the name containing one.
        assertEquals(listOf("a%b"), repository.observeFiltered("%").first().map { it.name })
        assertEquals(listOf("walk"), repository.observeFiltered("wal").first().map { it.name })
    }

    @Test
    fun observeFiltered_isCaseInsensitiveSubstringSearch() = runBlocking {
        listOf("Walk", "sidewalk").forEach { dao.insert(tag(it)) }

        assertEquals(
            listOf("sidewalk", "Walk"),
            TagRepositoryImpl(dao).observeFiltered("WALK").first().map { it.name },
        )
    }
}
