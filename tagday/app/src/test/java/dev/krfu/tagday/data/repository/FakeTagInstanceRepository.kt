package dev.krfu.tagday.data.repository

import dev.krfu.tagday.data.local.entity.Tag
import dev.krfu.tagday.data.local.entity.TagInstance
import dev.krfu.tagday.data.local.entity.TagType
import dev.krfu.tagday.data.model.TagDisplayGroup
import dev.krfu.tagday.data.model.TagDisplayGroups
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Hand-written repository fake for the ViewModel tests — the same "fake the collaborator
 * you own, don't mock it" pattern `TESTING.md` documents for the DAO-level repository
 * tests, one layer up: a ViewModel's collaborators are the repository *interfaces*, so
 * those are what get faked. Data lives in a [MutableStateFlow] so writes show up in later
 * emissions the way Room's `Flow`s would, letting a test assert both what the ViewModel
 * emitted and what it wrote.
 */
class FakeTagInstanceRepository(
    initialInstances: List<TagInstance> = emptyList(),
    private val tagsById: Map<Long, Tag> = emptyMap(),
) : TagInstanceRepository {
    val instances = MutableStateFlow(initialInstances)

    /** Every list passed to [updateInstances], in call order. */
    val reorderCalls = mutableListOf<List<TagInstance>>()

    override fun observeDayGroups(date: Int): Flow<List<TagDisplayGroup>> =
        instances.map { all -> all.filter { it.date == date }.toDisplayGroups() }

    override fun observeRangeGroups(start: Int, end: Int): Flow<Map<Int, List<TagDisplayGroup>>> =
        instances.map { all ->
            all.filter { it.date in start..end }
                .groupBy { it.date }
                .mapValues { (_, dayInstances) -> dayInstances.toDisplayGroups() }
        }

    override fun observeTagInstanceCounts(tagId: Long, start: Int, end: Int): Flow<Map<Int, Int>> =
        instances.map { all ->
            all.filter { it.tagId == tagId && it.date in start..end }
                .groupingBy { it.date }
                .eachCount()
        }

    override suspend fun addInstance(tagId: Long, date: Int, rating: Int?, value: String?) {
        val nextId = (instances.value.maxOfOrNull { it.id } ?: 0L) + 1
        instances.value += TagInstance(
            id = nextId,
            tagId = tagId,
            date = date,
            rating = rating,
            value = value,
            createdAt = nextId,
            sortOrder = nextId,
        )
    }

    override suspend fun updateInstance(instance: TagInstance) {
        instances.value = instances.value.map { if (it.id == instance.id) instance else it }
    }

    override suspend fun updateInstances(instances: List<TagInstance>) {
        reorderCalls += instances
        val byId = instances.associateBy { it.id }
        this.instances.value = this.instances.value.map { byId[it.id] ?: it }
    }

    override suspend fun removeInstances(instances: List<TagInstance>) {
        val removedIds = instances.map { it.id }.toSet()
        this.instances.value = this.instances.value.filterNot { it.id in removedIds }
    }

    // Mirrors TagInstanceRepositoryImpl.toDisplayGroups closely enough for the ViewModel's
    // purposes: group by tag, summarize, sort by name. Ordering within a group follows the
    // instance list, matching the display-order contract in ADR-023.
    private fun List<TagInstance>.toDisplayGroups(): List<TagDisplayGroup> =
        groupBy { it.tagId }
            .map { (tagId, tagInstances) ->
                val tag = tagsById[tagId] ?: Tag(
                    id = tagId,
                    name = "tag$tagId",
                    type = TagType.SIMPLE,
                    color = 0,
                    createdAt = 0,
                )
                TagDisplayGroup(
                    tagId = tag.id,
                    tagName = tag.name,
                    color = tag.color,
                    type = tag.type,
                    instances = tagInstances.sortedBy { it.sortOrder },
                    summary = TagDisplayGroups.summarize(tagInstances, tag.name, tag.type),
                )
            }
            .sortedBy { it.tagName.lowercase() }
}
