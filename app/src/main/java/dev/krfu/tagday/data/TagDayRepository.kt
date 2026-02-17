package dev.krfu.tagday.data

import dev.krfu.tagday.model.RepositoryState
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate

interface TagDayRepository {
    val state: StateFlow<RepositoryState>

    fun palette(): List<Int>

    suspend fun addTag(date: LocalDate, rawInput: String): Result<Unit>

    suspend fun removeEntry(entryId: Long)

    suspend fun removeTagForDate(date: LocalDate, tagName: String)

    suspend fun renameGlobalTag(currentName: String, newName: String): Result<Unit>

    suspend fun deleteGlobalTag(name: String)

    suspend fun updateGlobalTagColor(name: String, colorArgb: Int)

    suspend fun setGlobalTagHidden(name: String, hidden: Boolean)

    suspend fun setShowHiddenTags(show: Boolean)
}
