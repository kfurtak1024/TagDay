package dev.krfu.tagday.ui.theme

/**
 * Fixed colors for the Day zoom's past/today/future label (see `UI_UX.md` § Day zoom).
 * Deliberately independent of the Material color scheme, same rationale as `TagPalette`:
 * dynamic color varies per device wallpaper, which would make a semantic past/today/future
 * signal inconsistent or low-contrast depending on the user's system theme.
 */
object TemporalColors {
    const val PAST: Int = 0xFFB8860B.toInt() // gold
    const val TODAY: Int = 0xFF2E7D32.toInt() // green
    const val FUTURE: Int = 0xFF6A1B9A.toInt() // violet
}
