package dev.krfu.tagday.data.repository

import dev.krfu.tagday.data.local.entity.TagInstance
import dev.krfu.tagday.data.model.TagDisplayGroup
import kotlinx.coroutines.flow.Flow

interface TagInstanceRepository {
    /**
     * Groups for one day, sorted by tag name. Each group's `instances` are in **display
     * order** (`TagInstance.sortOrder`, see `TagInstanceDao`) — consumers render them as
     * given rather than re-sorting, so a manual reorder shows up identically in the
     * capsule summary and in the instance-list sheet.
     */
    fun observeDayGroups(date: Int): Flow<List<TagDisplayGroup>>

    /** Same ordering guarantees as [observeDayGroups], keyed by epoch day. */
    fun observeRangeGroups(start: Int, end: Int): Flow<Map<Int, List<TagDisplayGroup>>>

    fun observeTagInstanceCounts(tagId: Long, start: Int, end: Int): Flow<Map<Int, Int>>

    suspend fun addInstance(tagId: Long, date: Int, rating: Int? = null, value: String? = null)

    /**
     * Adds one instance per entry in [values], each with a distinct `sortOrder` so they keep
     * the given order (a plain [addInstance] loop would stamp them with the same
     * millisecond and leave `ORDER BY sortOrder` to break the tie arbitrarily).
     */
    suspend fun addValues(tagId: Long, date: Int, values: List<String>)

    suspend fun updateInstance(instance: TagInstance)

    suspend fun updateInstances(instances: List<TagInstance>)

    suspend fun removeInstances(instances: List<TagInstance>)
}
