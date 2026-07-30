package dev.krfu.tagday.data.repository

import dev.krfu.tagday.data.local.TagInstanceDao
import dev.krfu.tagday.data.local.entity.TagInstance
import dev.krfu.tagday.data.local.entity.TagInstanceWithTag
import dev.krfu.tagday.data.model.TagDisplayGroup
import dev.krfu.tagday.data.model.TagDisplayGroups
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class TagInstanceRepositoryImpl @Inject constructor(
    private val tagInstanceDao: TagInstanceDao,
) : TagInstanceRepository {
    override fun observeDayGroups(date: Int): Flow<List<TagDisplayGroup>> =
        tagInstanceDao.observeForDay(date).map { it.toDisplayGroups() }

    override fun observeRangeGroups(start: Int, end: Int): Flow<Map<Int, List<TagDisplayGroup>>> =
        tagInstanceDao.observeForRange(start, end).map { rows ->
            rows.groupBy { it.instance.date }.mapValues { (_, dayRows) -> dayRows.toDisplayGroups() }
        }

    override fun observeTagInstanceCounts(tagId: Long, start: Int, end: Int): Flow<Map<Int, Int>> =
        tagInstanceDao.observeForTagInRange(tagId, start, end).map { instances ->
            instances.groupingBy { it.date }.eachCount()
        }

    override suspend fun addInstance(tagId: Long, date: Int, rating: Int?, value: String?) {
        val now = System.currentTimeMillis()
        tagInstanceDao.insert(
            TagInstance(
                tagId = tagId,
                date = date,
                rating = rating,
                value = value,
                createdAt = now,
                sortOrder = now,
            ),
        )
    }

    override suspend fun updateInstance(instance: TagInstance) {
        tagInstanceDao.update(instance)
    }

    override suspend fun updateInstances(instances: List<TagInstance>) {
        tagInstanceDao.updateAll(instances)
    }

    override suspend fun removeInstances(instances: List<TagInstance>) {
        tagInstanceDao.deleteAll(instances)
    }
}

// Grouping/aggregation happens here in Kotlin, not SQL — see DATA_MODEL.md § Grouping & aggregation.
private fun List<TagInstanceWithTag>.toDisplayGroups(): List<TagDisplayGroup> =
    groupBy { it.instance.tagId }
        .map { (_, rows) ->
            val tag = rows.first().tag
            val instances = rows.map { it.instance }
            TagDisplayGroup(
                tagId = tag.id,
                tagName = tag.name,
                color = tag.color,
                type = tag.type,
                instances = instances,
                summary = TagDisplayGroups.summarize(instances, tag.name, tag.type),
            )
        }
        .sortedBy { it.tagName.lowercase() }
