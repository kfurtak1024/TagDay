package dev.krfu.tagday.ui.calendar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import dev.krfu.tagday.R
import dev.krfu.tagday.data.local.entity.TagType
import dev.krfu.tagday.data.model.TagDisplayGroup
import dev.krfu.tagday.ui.calendar.day.DayContent
import dev.krfu.tagday.ui.calendar.day.TagQuickEntryBar
import dev.krfu.tagday.ui.calendar.month.MonthContent
import dev.krfu.tagday.ui.calendar.week.WeekContent
import dev.krfu.tagday.ui.calendar.year.YearContent
import java.time.LocalDate
import kotlin.math.abs

private const val SWIPE_THRESHOLD_PX = 80f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarContent(
    uiState: CalendarUiState,
    onNavigateToTags: () -> Unit,
    onAddExistingTag: (tagId: Long) -> Unit,
    onCreateTag: (name: String, type: TagType, rating: Int?, value: String?) -> Unit,
    onGroupClick: (TagDisplayGroup) -> Unit,
    onGroupQuickRemove: (TagDisplayGroup) -> Unit,
    onDayClick: (LocalDate) -> Unit,
    onTagPicked: (Long) -> Unit,
    onStepTime: (Int) -> Unit,
    onStepZoom: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Vertical (zoom) is a real `scrollable`, not a raw pointerInput drag, so it can
    // properly negotiate with Day's tag list / Year's stacked month grids, which are
    // themselves `scrollable` (via `verticalScroll`): a nested `scrollable` only ever
    // grabs the *leftover* delta its descendant didn't consume (Modifier.scrollable's
    // built-in nested-scroll participation hooks onPostScroll/onPostFling, never
    // onPreScroll) — so the tag list/month grids still scroll normally first, and only
    // once they're out of room (or on zoom levels with nothing scrollable at all, e.g.
    // Week/Month) does the remaining drag reach this zoom-swipe tracking. See ADR-012.
    var verticalAccumulator by remember { mutableFloatStateOf(0f) }
    val verticalZoomScrollState = rememberScrollableState { delta ->
        verticalAccumulator += delta
        if (abs(verticalAccumulator) > SWIPE_THRESHOLD_PX) {
            onStepZoom(if (verticalAccumulator < 0) 1 else -1)
            verticalAccumulator = 0f
        }
        delta
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {},
                actions = {
                    IconButton(onClick = onNavigateToTags) {
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.ic_label),
                            contentDescription = stringResource(R.string.day_edit_tags_content_description),
                        )
                    }
                },
            )
        },
        bottomBar = {
            if (uiState.zoomLevel == ZoomLevel.DAY) {
                TagQuickEntryBar(
                    allTags = uiState.allTags,
                    onAddExistingTag = onAddExistingTag,
                    onCreateTag = onCreateTag,
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .scrollable(state = verticalZoomScrollState, orientation = Orientation.Vertical)
                .pointerInput(Unit) {
                    // Horizontal (move through time) stays a plain drag detector —
                    // nothing in any zoom level scrolls horizontally, so there's no
                    // descendant to negotiate with.
                    var acc = Offset.Zero
                    detectDragGestures(
                        orientationLock = Orientation.Horizontal,
                        onDragStart = { _, _, _ -> acc = Offset.Zero },
                        onDrag = { change, delta ->
                            change.consume()
                            acc += delta
                        },
                        onDragEnd = {
                            if (abs(acc.x) > SWIPE_THRESHOLD_PX) {
                                onStepTime(if (acc.x < 0) 1 else -1)
                            }
                        },
                    )
                },
        ) {
            AnimatedContent(
                targetState = uiState.zoomLevel,
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
                label = "zoom-level",
            ) { zoom ->
                when (zoom) {
                    ZoomLevel.DAY -> DayContent(
                        date = uiState.focusedDate,
                        groups = (uiState.periodData as? CalendarPeriodData.Day)?.groups.orEmpty(),
                        onGroupClick = onGroupClick,
                        onGroupQuickRemove = onGroupQuickRemove,
                    )

                    ZoomLevel.WEEK -> WeekContent(
                        focusedDate = uiState.focusedDate,
                        groupsByDate = (uiState.periodData as? CalendarPeriodData.Week)?.groupsByDate.orEmpty(),
                        onDayClick = onDayClick,
                    )

                    ZoomLevel.MONTH -> MonthContent(
                        focusedDate = uiState.focusedDate,
                        allTags = uiState.allTags,
                        selectedTagId = uiState.selectedTagId,
                        countsByDate = (uiState.periodData as? CalendarPeriodData.Heatmap)?.countsByDate.orEmpty(),
                        onTagPicked = onTagPicked,
                        onDayClick = onDayClick,
                    )

                    ZoomLevel.YEAR -> YearContent(
                        focusedDate = uiState.focusedDate,
                        allTags = uiState.allTags,
                        selectedTagId = uiState.selectedTagId,
                        countsByDate = (uiState.periodData as? CalendarPeriodData.Heatmap)?.countsByDate.orEmpty(),
                        onTagPicked = onTagPicked,
                        onDayClick = onDayClick,
                    )
                }
            }
        }
    }
}
