package dev.krfu.tagday.data.repository

import dev.krfu.tagday.data.local.entity.Tag
import kotlinx.coroutines.flow.Flow

interface TagRepository {
    fun observeAll(): Flow<List<Tag>>

    fun observeFiltered(query: String): Flow<List<Tag>>

    suspend fun createTag(name: String, color: Int): Long
}
