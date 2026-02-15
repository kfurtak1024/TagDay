package dev.krfu.tagged.data.room

import androidx.room.withTransaction
import dev.krfu.tagged.data.TaggedRepository
import dev.krfu.tagged.domain.TagValidation
import dev.krfu.tagged.model.AppSettings
import dev.krfu.tagged.model.GlobalTag
import dev.krfu.tagged.model.RepositoryState
import dev.krfu.tagged.model.TagEntry
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlin.math.abs

class RoomTaggedRepository(
    private val database: TaggedDatabase
) : TaggedRepository {
    private val dao = database.taggedDao()

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val palette = listOf(
        0xFF1D4ED8.toInt(),
        0xFF0891B2.toInt(),
        0xFF0F766E.toInt(),
        0xFF4D7C0F.toInt(),
        0xFFB45309.toInt(),
        0xFFBE123C.toInt(),
        0xFF7E22CE.toInt(),
        0xFF374151.toInt()
    )

    override val state: StateFlow<RepositoryState> = combine(
        dao.observeGlobalTags(),
        dao.observeTagEntries(),
        dao.observeSettings()
    ) { globalTags, entries, settings ->
        RepositoryState(
            globalTags = globalTags.associateBy({ it.name }, { it.toModel() }),
            entriesByDate = entries
                .map { it.toModel() }
                .groupBy { it.date },
            settings = settings?.toModel() ?: AppSettings(),
            nextEntryId = 1L
        )
    }.stateIn(
        scope = repositoryScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RepositoryState()
    )

    override fun palette(): List<Int> = palette

    override suspend fun addTag(date: LocalDate, rawInput: String): Result<Unit> {
        val parsed = TagValidation.parseInput(rawInput).getOrElse { return Result.failure(it) }

        database.withTransaction {
            val existing = dao.findGlobalTagByName(parsed.name)
            if (existing == null) {
                dao.insertGlobalTag(
                    GlobalTagEntity(
                        name = parsed.name,
                        colorArgb = defaultColorFor(parsed.name),
                        hidden = false,
                        userSelectedColor = false
                    )
                )
            }

            dao.insertTagEntry(
                TagEntryEntity(
                    dateEpochDay = date.toEpochDay(),
                    name = parsed.name,
                    value = parsed.value,
                    rating = parsed.rating
                )
            )
        }

        return Result.success(Unit)
    }

    override suspend fun removeEntry(entryId: Long) {
        dao.deleteTagEntry(entryId)
    }

    override suspend fun removeTagForDate(date: LocalDate, tagName: String) {
        dao.deleteTagEntriesByDateAndName(
            dateEpochDay = date.toEpochDay(),
            name = tagName
        )
    }

    override suspend fun renameGlobalTag(currentName: String, newName: String): Result<Unit> {
        if (currentName == newName) return Result.success(Unit)
        if (!TagValidation.isValidName(newName)) {
            return Result.failure(
                IllegalArgumentException("Invalid tag name. Use letters with single '-' separators.")
            )
        }

        var success = false
        database.withTransaction {
            val currentTag = dao.findGlobalTagByName(currentName) ?: return@withTransaction
            if (dao.findGlobalTagByName(newName) != null) return@withTransaction

            dao.deleteGlobalTag(currentName)
            dao.insertGlobalTag(currentTag.copy(name = newName))
            dao.renameTagEntries(currentName = currentName, newName = newName)
            success = true
        }

        return if (success) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalArgumentException("Cannot rename. Name may already exist."))
        }
    }

    override suspend fun deleteGlobalTag(name: String) {
        dao.deleteGlobalTag(name)
    }

    override suspend fun updateGlobalTagColor(name: String, colorArgb: Int) {
        val tag = dao.findGlobalTagByName(name) ?: return
        dao.updateGlobalTag(
            tag.copy(
                colorArgb = colorArgb,
                userSelectedColor = true
            )
        )
    }

    override suspend fun setGlobalTagHidden(name: String, hidden: Boolean) {
        val tag = dao.findGlobalTagByName(name) ?: return
        dao.updateGlobalTag(tag.copy(hidden = hidden))
    }

    override suspend fun setShowHiddenTags(show: Boolean) {
        dao.upsertSettings(AppSettingsEntity(showHiddenTags = show))
    }

    private fun defaultColorFor(name: String): Int {
        val idx = name.hashCode().let { if (it == Int.MIN_VALUE) 0 else abs(it) } % palette.size
        return palette[idx]
    }
}

private fun GlobalTagEntity.toModel(): GlobalTag = GlobalTag(
    name = name,
    colorArgb = colorArgb,
    hidden = hidden,
    userSelectedColor = userSelectedColor
)

private fun TagEntryEntity.toModel(): TagEntry = TagEntry(
    id = id,
    date = LocalDate.ofEpochDay(dateEpochDay),
    name = name,
    value = value,
    rating = rating
)

private fun AppSettingsEntity.toModel(): AppSettings = AppSettings(
    showHiddenTags = showHiddenTags
)
