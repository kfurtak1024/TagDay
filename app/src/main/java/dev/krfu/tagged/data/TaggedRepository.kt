package dev.krfu.tagged.data

import dev.krfu.tagged.model.RepositoryState
import java.time.LocalDate
import kotlinx.coroutines.flow.StateFlow

interface TaggedRepository {
    val state: StateFlow<RepositoryState>

    fun palette(): List<Int>

    suspend fun addTag(date: LocalDate, rawInput: String): Result<Unit>

    suspend fun removeEntry(entryId: Long)

    suspend fun renameGlobalTag(currentName: String, newName: String): Result<Unit>

    suspend fun deleteGlobalTag(name: String)

    suspend fun updateGlobalTagColor(name: String, colorArgb: Int)

    suspend fun setGlobalTagHidden(name: String, hidden: Boolean)

    suspend fun setShowHiddenTags(show: Boolean)
}
