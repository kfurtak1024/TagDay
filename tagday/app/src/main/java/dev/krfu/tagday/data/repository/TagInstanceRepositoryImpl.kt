package dev.krfu.tagday.data.repository

import dev.krfu.tagday.data.local.TagInstanceDao
import dev.krfu.tagday.data.local.entity.TagInstance
import dev.krfu.tagday.data.local.entity.TagInstanceWithTag
import dev.krfu.tagday.data.local.entity.TagType
import dev.krfu.tagday.data.model.TagDisplayGroup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import kotlin.math.roundToInt

class TagInstanceRepositoryImpl @Inject constructor(
    private val tagInstanceDao: TagInstanceDao,
) : TagInstanceRepository {
    override fun observeDayGroups(date: Int): Flow<List<TagDisplayGroup>> =
        tagInstanceDao.observeForDay(date).map { it.toDisplayGroups() }

    override suspend fun addInstance(tagId: Long, date: Int) {
        tagInstanceDao.insert(
            TagInstance(
                tagId = tagId,
                date = date,
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun removeInstance(instance: TagInstance) {
        tagInstanceDao.delete(instance)
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
                summary = instances.summarize(tag.name, tag.type),
            )
        }
        .sortedBy { it.tagName.lowercase() }

private fun List<TagInstance>.summarize(name: String, type: TagType): String = when (type) {
    TagType.SIMPLE -> if (size > 1) "$name ($size)" else name

    TagType.RATED -> {
        val average = mapNotNull { it.rating }.average()
        val stars = "★".repeat(average.roundToInt().coerceIn(0, 5))
        if (size > 1) "$name: $stars ($size)" else "$name: $stars"
    }

    TagType.VALUED -> {
        val counts = mapNotNull { it.value }.groupingBy { it }.eachCount()
        val values = counts.entries.joinToString(", ") { (value, count) ->
            if (count > 1) "$value ($count)" else value
        }
        "$name: [$values]"
    }
}
