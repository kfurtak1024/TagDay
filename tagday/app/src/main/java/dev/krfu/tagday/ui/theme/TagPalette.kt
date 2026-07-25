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
}
