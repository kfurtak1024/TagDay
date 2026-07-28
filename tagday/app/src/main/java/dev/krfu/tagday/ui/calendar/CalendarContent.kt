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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    onNavigateToSettings: () -> Unit,
    onAddExistingTag: (tagId: Long) -> Unit,
    onCreateTag: (name: String, type: TagType, rating: Int?, value: String?) -> Unit,
    onGroupClick: (TagDisplayGroup) -> Unit,
    onGroupQuickRemove: (TagDisplayGroup) -> Unit,
    onUndoRemoval: () -> Unit,
    onCommitPendingRemoval: () -> Unit,
    onDayClick: (LocalDate) -> Unit,
    onMonthClick: (LocalDate) -> Unit,
    onTagPicked: (Long) -> Unit,
    onStepTime: (Int) -> Unit,
    onStepZoom: (Int) -> Unit,
    onZoomLevelPicked: (ZoomLevel) -> Unit,
    onJumpToToday: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Vertical (zoom) is a real `scrollable`, not a raw pointerInput drag, so it can
    // properly negotiate with Day's tag list, which is itself `scrollable` (via
    // `verticalScroll`): a nested `scrollable` only ever grabs the *leftover* delta its
    // descendant didn't consume (Modifier.scrollable's built-in nested-scroll
    // participation hooks onPostScroll/onPostFling, never onPreScroll) — so the tag list
    // still scrolls normally first, and only once it's out of room (or on zoom levels
    // with nothing scrollable at all, e.g. Week/Month/Year — see ADR-016 for Year's
    // fixed, no-scroll grid) does the remaining drag reach this zoom-swipe tracking.
    // See ADR-012.
    //
    // Horizontal and vertical are two independent detection mechanisms (raw pointerInput
    // vs. scrollable), so an imprecise diagonal swipe could otherwise cross both
    // thresholds and fire both a zoom change *and* a time step from one gesture. Both
    // accumulators are shared across the two closures below so each side can check the
    // other's current magnitude before firing, and both reset together once either does
    // — a lightweight approximation of "only the dominant axis wins" without unifying
    // the two mechanisms outright.
    var horizontalAccumulator by remember { mutableFloatStateOf(0f) }
    var verticalAccumulator by remember { mutableFloatStateOf(0f) }
    val verticalZoomScrollState = rememberScrollableState { delta ->
        verticalAccumulator += delta
        if (abs(verticalAccumulator) > SWIPE_THRESHOLD_PX && abs(verticalAccumulator) > abs(horizontalAccumulator)) {
            onStepZoom(if (verticalAccumulator < 0) 1 else -1)
            verticalAccumulator = 0f
            horizontalAccumulator = 0f
        }
        delta
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val undoActionLabel = stringResource(R.string.day_undo_snackbar_action)
    val undoMessage = uiState.pendingRemoval?.let {
        stringResource(R.string.day_undo_snackbar_message, it.tagName)
    }
    // Keyed on the pending removal itself (not just "is one pending"), so starting a new
    // removal while one is already showing cancels this coroutine — which dismisses the
    // stale snackbar — rather than leaving it to resolve on its own. See ADR-019.
    LaunchedEffect(uiState.pendingRemoval) {
        if (undoMessage == null) return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = undoMessage,
            actionLabel = undoActionLabel,
            // Explicit: showSnackbar defaults to Indefinite whenever actionLabel is
            // non-null, but delay-delete needs the commit to actually fire on its own
            // after a few seconds of inaction, not sit there forever. See ADR-019.
            duration = SnackbarDuration.Short,
        )
        if (result == SnackbarResult.ActionPerformed) onUndoRemoval() else onCommitPendingRemoval()
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ZoomLevelPicker(
                            zoomLevel = uiState.zoomLevel,
                            onZoomLevelPicked = onZoomLevelPicked,
                        )
                        // Day zoom only, and only once actually away from today — see ADR-017.
                        if (uiState.zoomLevel == ZoomLevel.DAY && uiState.focusedDate != LocalDate.now()) {
                            IconButton(onClick = onJumpToToday) {
                                Icon(
                                    imageVector = ImageVector.vectorResource(R.drawable.ic_target),
                                    contentDescription = stringResource(R.string.calendar_jump_to_today_content_description),
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToTags) {
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.ic_label),
                            contentDescription = stringResource(R.string.day_edit_tags_content_description),
                        )
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.calendar_settings_content_description),
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
                    detectDragGestures(
                        orientationLock = Orientation.Horizontal,
                        onDragStart = { _, _, _ -> horizontalAccumulator = 0f },
                        onDrag = { change, delta ->
                            change.consume()
                            horizontalAccumulator += delta.x
                        },
                        onDragEnd = {
                            if (abs(horizontalAccumulator) > SWIPE_THRESHOLD_PX &&
                                abs(horizontalAccumulator) >= abs(verticalAccumulator)
                            ) {
                                onStepTime(if (horizontalAccumulator < 0) 1 else -1)
                            }
                            horizontalAccumulator = 0f
                            verticalAccumulator = 0f
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
                        onMonthClick = onMonthClick,
                    )
                }
            }
        }
    }
}
