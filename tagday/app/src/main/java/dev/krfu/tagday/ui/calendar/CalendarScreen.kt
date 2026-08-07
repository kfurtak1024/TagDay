package dev.krfu.tagday.ui.calendar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.krfu.tagday.data.model.TagDisplayGroup
import dev.krfu.tagday.ui.calendar.day.InstanceListSheet

@Composable
fun CalendarScreen(
    onNavigateToTags: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CalendarViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pendingTagEdit by viewModel.pendingTagEdit.collectAsStateWithLifecycle()

    // rememberSaveable, not remember: rotating with the sheet open used to close it (BACKLOG
    // F10). A tag id is saveable as-is.
    var selectedGroupKey by rememberSaveable { mutableStateOf<Long?>(null) }
    // True when the selected tag may legitimately have *no* instances on the focused day —
    // either just created via createTagForEditing, or an existing Rated/Valued tag opened by
    // quick-entry with no seed (ADR-034). Lets selectedGroup fall back to a synthetic empty
    // group below instead of null. Any other selection (tapping a real capsule) leaves this
    // false, so removing an existing group's last instance still auto-dismisses the sheet.
    var selectedGroupMayBeEmpty by rememberSaveable { mutableStateOf(false) }
    val dayGroups = (uiState.periodData as? CalendarPeriodData.Day)?.groups.orEmpty()
    val selectedGroup = selectedGroupKey?.let { tagId ->
        dayGroups.find { it.tagId == tagId } ?: if (selectedGroupMayBeEmpty) {
            uiState.allTags.find { it.id == tagId }?.let { tag ->
                TagDisplayGroup(
                    tagId = tag.id,
                    tagName = tag.name,
                    color = tag.color,
                    type = tag.type,
                    instances = emptyList(),
                    summary = "",
                )
            }
        } else {
            null
        }
    }
    LaunchedEffect(dayGroups) {
        // The group's last instance may have just been removed — nothing left to show.
        if (selectedGroupKey != null && selectedGroup == null) {
            selectedGroupKey = null
        }
    }
    LaunchedEffect(pendingTagEdit) {
        pendingTagEdit?.let { tagId ->
            selectedGroupKey = tagId
            selectedGroupMayBeEmpty = true
            viewModel.consumePendingTagEdit()
        }
    }

    CalendarContent(
        uiState = uiState,
        onNavigateToTags = onNavigateToTags,
        onNavigateToSettings = onNavigateToSettings,
        onAddExistingTag = { tagId -> viewModel.addInstance(tagId) },
        onAddRatingToExistingTag = { tagId, rating -> viewModel.addRating(tagId, rating) },
        onAddValuesToExistingTag = { tagId, values -> viewModel.addValues(tagId, values) },
        onEditExistingTag = { tagId -> viewModel.requestTagEdit(tagId) },
        onCreateTag = { name, type, rating, values -> viewModel.createTagAndAdd(name, type, rating, values) },
        onCreateTagForEditing = { name, type -> viewModel.createTagForEditing(name, type) },
        onGroupClick = { group ->
            selectedGroupKey = group.tagId
            selectedGroupMayBeEmpty = false
        },
        onGroupQuickRemove = { group -> viewModel.removeGroup(group) },
        onUndoRemoval = { viewModel.undoRemoval() },
        onCommitPendingRemoval = { viewModel.commitPendingRemoval() },
        onDayClick = { date -> viewModel.jumpToDay(date) },
        onMonthClick = { date -> viewModel.jumpToMonth(date) },
        onTagPicked = { tagId -> viewModel.selectHeatmapTag(tagId) },
        onStepTime = { direction -> viewModel.stepTime(direction) },
        onDateSelected = { date -> viewModel.setFocusedDate(date) },
        onStepZoom = { direction -> viewModel.stepZoom(direction) },
        onZoomLevelPicked = { zoomLevel -> viewModel.setZoom(zoomLevel) },
        onJumpToToday = { viewModel.jumpToToday() },
        modifier = modifier,
    )

    selectedGroup?.let { group ->
        InstanceListSheet(
            group = group,
            onDismiss = { selectedGroupKey = null },
            onUpdateInstance = { instance -> viewModel.updateInstance(instance) },
            onRemoveInstance = { instance -> viewModel.removeInstance(instance, group.tagName) },
            onAddValue = { tagId, value -> viewModel.addValue(tagId, value) },
            onAddRating = { tagId, rating -> viewModel.addRating(tagId, rating) },
            onIncrementCount = { tagId -> viewModel.addInstance(tagId) },
            onDecrementCount = { instance -> viewModel.removeInstanceImmediately(instance) },
            onReorderInstances = { instances -> viewModel.reorderInstances(instances) },
        )
    }
}
