package dev.krfu.tagday.data.room

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.krfu.tagday.model.RepositoryState
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomTagDayRepositoryInstrumentedTest {
    private lateinit var database: TagDayDatabase
    private lateinit var repository: RoomTagDayRepository

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, TagDayDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomTagDayRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun addTag_whenInputValid_createsGlobalTagAndDayEntry() = runBlocking {
        val date = LocalDate.of(2026, 2, 16)

        val result = repository.addTag(date, "workout:***")

        assertTrue(result.isSuccess)
        val state = awaitState { repo ->
            repo.globalTags.containsKey("workout") &&
                repo.entriesByDate[date].orEmpty().any { it.name == "workout" && it.rating == 3 }
        }
        assertNotNull(state.globalTags["workout"])
        assertEquals(1, state.entriesByDate[date].orEmpty().size)
    }

    @Test
    fun renameGlobalTag_whenTagExists_updatesHistoricalEntries() = runBlocking {
        val dayOne = LocalDate.of(2026, 2, 15)
        val dayTwo = LocalDate.of(2026, 2, 16)
        repository.addTag(dayOne, "old-tag")
        repository.addTag(dayTwo, "old-tag:value")

        val result = repository.renameGlobalTag("old-tag", "new-tag")

        assertTrue(result.isSuccess)
        val state = awaitState { repo ->
            !repo.globalTags.containsKey("old-tag") &&
                repo.globalTags.containsKey("new-tag") &&
                repo.entriesByDate.values.flatten().none { it.name == "old-tag" } &&
                repo.entriesByDate.values.flatten().count { it.name == "new-tag" } == 2
        }
        assertTrue(state.globalTags.containsKey("new-tag"))
    }

    @Test
    fun deleteGlobalTag_whenEntriesExist_removesOnlyGlobalTag() = runBlocking {
        val date = LocalDate.of(2026, 2, 16)
        repository.addTag(date, "vacation")

        repository.deleteGlobalTag("vacation")

        val state = awaitState { repo ->
            !repo.globalTags.containsKey("vacation") &&
                repo.entriesByDate[date].orEmpty().any { it.name == "vacation" }
        }
        assertFalse(state.globalTags.containsKey("vacation"))
        assertEquals(1, state.entriesByDate[date].orEmpty().size)
    }

    @Test
    fun setShowHiddenTags_whenChanged_updatesSettingsState() = runBlocking {
        repository.setShowHiddenTags(true)

        val hiddenOn = awaitState { it.settings.showHiddenTags }
        assertTrue(hiddenOn.settings.showHiddenTags)

        repository.setShowHiddenTags(false)
        val hiddenOff = awaitState { !it.settings.showHiddenTags }
        assertFalse(hiddenOff.settings.showHiddenTags)
    }

    private suspend fun awaitState(predicate: (RepositoryState) -> Boolean): RepositoryState {
        return withTimeout(3_000) {
            repository.state.first(predicate)
        }
    }
}
