package dev.krfu.tagday.ui.calendar

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.krfu.tagday.R

@Composable
fun ZoomLevel.label(): String = when (this) {
    ZoomLevel.DAY -> stringResource(R.string.calendar_zoom_level_day)
    ZoomLevel.WEEK -> stringResource(R.string.calendar_zoom_level_week)
    ZoomLevel.MONTH -> stringResource(R.string.calendar_zoom_level_month)
    ZoomLevel.YEAR -> stringResource(R.string.calendar_zoom_level_year)
}
