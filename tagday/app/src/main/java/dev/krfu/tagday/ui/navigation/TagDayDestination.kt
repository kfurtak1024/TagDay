package dev.krfu.tagday.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.ui.graphics.vector.ImageVector
import dev.krfu.tagday.R

enum class TagDayDestination(
    val route: String,
    @param:StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    CALENDAR(route = "calendar", labelRes = R.string.nav_calendar_label, icon = Icons.Filled.DateRange),
    TAGS(route = "tags", labelRes = R.string.nav_tags_label, icon = Icons.AutoMirrored.Filled.List),
}
