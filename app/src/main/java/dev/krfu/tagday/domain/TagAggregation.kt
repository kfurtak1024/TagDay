package dev.krfu.tagday.domain

import dev.krfu.tagday.model.GlobalTag
import dev.krfu.tagday.model.TagEntry
import kotlin.math.roundToInt

data class TagSummary(
    val name: String,
    val colorArgb: Int,
    val label: String,
    val rating: Int? = null,
    val ratingCount: Int = 0
)

fun aggregateDayTags(
    entries: List<TagEntry>,
    globalTags: Map<String, GlobalTag>,
    showHiddenTags: Boolean
): List<TagSummary> {
    return entries
        .groupBy { it.name }
        .toSortedMap()
        .mapNotNull { (name, groupedEntries) ->
            val globalTag = globalTags[name]
            if (!showHiddenTags && globalTag?.hidden == true) {
                return@mapNotNull null
            }

            val colorArgb = globalTag?.colorArgb ?: DEFAULT_COLOR_ARGB
            val values = groupedEntries.mapNotNull { it.value }.distinct()
            val ratings = groupedEntries.mapNotNull { it.rating }

            if (ratings.isNotEmpty()) {
                val roundedRating = ratings.average().roundToInt().coerceIn(1, 5)
                return@mapNotNull TagSummary(
                    name = name,
                    colorArgb = colorArgb,
                    label = name,
                    rating = roundedRating,
                    ratingCount = ratings.size
                )
            }

            if (values.isNotEmpty()) {
                return@mapNotNull TagSummary(
                    name = name,
                    colorArgb = colorArgb,
                    label = "$name (${values.joinToString(", ")})"
                )
            }

            val countSuffix = if (groupedEntries.size > 1) " (${groupedEntries.size})" else ""
            TagSummary(
                name = name,
                colorArgb = colorArgb,
                label = "$name$countSuffix"
            )
        }
}

const val DEFAULT_COLOR_ARGB: Int = 0xFF4B5563.toInt()
