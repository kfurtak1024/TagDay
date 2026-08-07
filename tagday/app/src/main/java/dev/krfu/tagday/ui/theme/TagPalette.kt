package dev.krfu.tagday.ui.theme

/**
 * Fixed palette offered when creating a tag (per FEATURES.md § Tag repository).
 * Values are stored as-is in `Tag.color`; a custom color picker also exists in the
 * Tags view's `ColorPickerDialog` (M3) for recoloring an existing tag.
 */
object TagPalette {
    val colors: List<Int> = listOf(
        0xFFE57373.toInt(), // red
        0xFFFFB74D.toInt(), // orange
        0xFFFFF176.toInt(), // yellow
        0xFF81C784.toInt(), // green
        0xFF4FC3F7.toInt(), // light blue
        0xFF7986CB.toInt(), // indigo
        0xFFBA68C8.toInt(), // purple
        0xFFA1887F.toInt(), // brown
    )

    /**
     * The colour to give a new tag, avoiding one that's already in use.
     *
     * This used to be `colors[allTags.size % colors.size]`, which collides as soon as anything
     * is deleted — eight tags, delete the third, and the ninth gets index 8 % 8 = 0, duplicating
     * the first (BACKLOG F19). A duplicate is worst exactly where the colour is the *only*
     * signal: Week zoom is a row of coloured dots and nothing else (ADR-038).
     *
     * Once every colour is taken, repeats are unavoidable, so it falls back to cycling by
     * count — [existingColors] is passed in rather than read from a repository so the rule
     * stays a pure function.
     */
    fun nextColor(existingColors: List<Int>): Int {
        val used = existingColors.toSet()
        return colors.firstOrNull { it !in used }
            ?: colors[existingColors.size % colors.size]
    }
}
