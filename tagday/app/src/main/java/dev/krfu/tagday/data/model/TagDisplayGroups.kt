package dev.krfu.tagday.data.model

import dev.krfu.tagday.data.local.entity.TagInstance
import dev.krfu.tagday.data.local.entity.TagType
import kotlin.math.roundToInt

/**
 * Grouping/aggregation for [TagDisplayGroup] — shared by `TagInstanceRepositoryImpl`
 * (building groups from Room rows) and `CalendarViewModel`'s optimistic pending-removal
 * filtering (ADR-019), so both apply the exact same summarizing rule instead of two
 * copies that could drift.
 */
object TagDisplayGroups {
    fun summarize(instances: List<TagInstance>, name: String, type: TagType): String = when (type) {
        TagType.SIMPLE -> if (instances.size > 1) "$name (${instances.size})" else name

        TagType.RATED -> {
            val rated = instances.mapNotNull { it.rating }
            // Instances are created unrated and rated later (DATA_MODEL.md § TagInstance) —
            // a group can be entirely unrated, so it summarizes like Simple until rated
            // (ADR-008).
            if (rated.isEmpty()) {
                if (instances.size > 1) "$name (${instances.size})" else name
            } else {
                val stars = "★".repeat(rated.average().roundToInt().coerceIn(0, 5))
                if (instances.size > 1) "$name: $stars (${instances.size})" else "$name: $stars"
            }
        }

        TagType.VALUED -> {
            val counts = instances.mapNotNull { it.value }.groupingBy { it }.eachCount()
            val values = counts.entries.joinToString(", ") { (value, count) ->
                if (count > 1) "$value ($count)" else value
            }
            "$name: [$values]"
        }
    }

    /**
     * Drops the given instance ids from every group (recomputing `summary` for whichever
     * group actually lost an instance), and drops any group left with none — the
     * client-side counterpart to an actual delete, used to optimistically hide a
     * not-yet-committed removal. See ADR-019.
     */
    fun List<TagDisplayGroup>.excludingInstances(instanceIds: Set<Long>): List<TagDisplayGroup> {
        if (instanceIds.isEmpty()) return this
        return mapNotNull { group ->
            val remaining = group.instances.filterNot { it.id in instanceIds }
            when {
                remaining.size == group.instances.size -> group
                remaining.isEmpty() -> null
                else -> group.copy(
                    instances = remaining,
                    summary = summarize(remaining, group.tagName, group.type),
                )
            }
        }
    }
}
