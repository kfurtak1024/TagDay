package dev.krfu.tagday.data.repository

import dev.krfu.tagday.data.local.entity.TagInstance
import dev.krfu.tagday.data.model.TagDisplayGroup
import kotlinx.coroutines.flow.Flow

interface TagInstanceRepository {
    fun observeDayGroups(date: Int): Flow<List<TagDisplayGroup>>

    fun observeRangeGroups(start: Int, end: Int): Flow<Map<Int, List<TagDisplayGroup>>>

    fun observeTagInstanceCounts(tagId: Long, start: Int, end: Int): Flow<Map<Int, Int>>

    suspend fun addInstance(tagId: Long, date: Int, rating: Int? = null, value: String? = null)

    suspend fun updateInstance(instance: TagInstance)

    suspend fun removeInstance(instance: TagInstance)

    suspend fun removeInstances(instances: List<TagInstance>)
}
