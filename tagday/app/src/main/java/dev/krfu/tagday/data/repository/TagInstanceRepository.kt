package dev.krfu.tagday.data.repository

import dev.krfu.tagday.data.local.entity.TagInstance
import dev.krfu.tagday.data.local.entity.TagInstanceType
import dev.krfu.tagday.data.model.TagDisplayGroup
import kotlinx.coroutines.flow.Flow

interface TagInstanceRepository {
    fun observeDayGroups(date: Int): Flow<List<TagDisplayGroup>>

    suspend fun addInstance(tagId: Long, date: Int, type: TagInstanceType)

    suspend fun removeInstance(instance: TagInstance)
}
