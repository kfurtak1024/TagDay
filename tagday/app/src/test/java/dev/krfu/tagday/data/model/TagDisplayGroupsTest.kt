package dev.krfu.tagday.data.model

import dev.krfu.tagday.data.local.entity.TagInstance
import dev.krfu.tagday.data.local.entity.TagType
import dev.krfu.tagday.data.model.TagDisplayGroups.excludingInstances
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class TagDisplayGroupsTest {
    private fun group(
        tagId: Long,
        tagName: String,
        type: TagType,
        instances: List<TagInstance>,
    ) = TagDisplayGroup(
        tagId = tagId,
        tagName = tagName,
        color = 0,
        type = type,
        instances = instances,
        summary = TagDisplayGroups.summarize(instances, tagName, type),
    )

    @Test
    fun excludingInstances_emptyExclusionSet_returnsSameList() {
        val groups = listOf(
            group(1, "walk", TagType.SIMPLE, listOf(TagInstance(id = 1, tagId = 1, date = 0, createdAt = 0))),
        )

        assertSame(groups, groups.excludingInstances(emptySet()))
    }

    @Test
    fun excludingInstances_noMatchingIds_leavesGroupUntouched() {
        val groups = listOf(
            group(1, "walk", TagType.SIMPLE, listOf(TagInstance(id = 1, tagId = 1, date = 0, createdAt = 0))),
        )

        val result = groups.excludingInstances(setOf(999))

        assertEquals(groups, result)
    }

    @Test
    fun excludingInstances_lastInstanceOfGroup_dropsGroupEntirely() {
        val groups = listOf(
            group(1, "walk", TagType.SIMPLE, listOf(TagInstance(id = 1, tagId = 1, date = 0, createdAt = 0))),
            group(2, "reading", TagType.SIMPLE, listOf(TagInstance(id = 2, tagId = 2, date = 0, createdAt = 0))),
        )

        val result = groups.excludingInstances(setOf(1))

        assertEquals(listOf(groups[1]), result)
    }

    @Test
    fun excludingInstances_oneOfSeveral_recomputesSummaryAndCount() {
        val groups = listOf(
            group(
                1,
                "walk",
                TagType.SIMPLE,
                listOf(
                    TagInstance(id = 1, tagId = 1, date = 0, createdAt = 0),
                    TagInstance(id = 2, tagId = 1, date = 0, createdAt = 1),
                ),
            ),
        )

        val result = groups.excludingInstances(setOf(1))

        assertEquals(1, result.single().instances.size)
        assertEquals("walk", result.single().summary)
    }

    @Test
    fun excludingInstances_ratedGroup_recomputesAverageAfterRemoval() {
        val groups = listOf(
            group(
                1,
                "freediving",
                TagType.RATED,
                listOf(
                    TagInstance(id = 1, tagId = 1, date = 0, rating = 1, createdAt = 0),
                    TagInstance(id = 2, tagId = 1, date = 0, rating = 5, createdAt = 1),
                ),
            ),
        )

        val result = groups.excludingInstances(setOf(1))

        assertEquals("freediving: ★★★★★", result.single().summary)
    }

    @Test
    fun excludingInstances_valuedGroup_recomputesValueListAfterRemoval() {
        val groups = listOf(
            group(
                1,
                "movie",
                TagType.VALUED,
                listOf(
                    TagInstance(id = 1, tagId = 1, date = 0, value = "dune", createdAt = 0),
                    TagInstance(id = 2, tagId = 1, date = 0, value = "dune", createdAt = 1),
                    TagInstance(id = 3, tagId = 1, date = 0, value = "terminator", createdAt = 2),
                ),
            ),
        )

        val result = groups.excludingInstances(setOf(1))

        assertEquals("movie: [dune, terminator]", result.single().summary)
    }
}
