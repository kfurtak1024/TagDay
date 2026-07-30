package dev.krfu.tagday.ui.calendar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
    val pendingValuedTagEdit by viewModel.pendingValuedTagEdit.collectAsStateWithLifecycle()

    var selectedGroupKey by remember { mutableStateOf<Long?>(null) }
    // True only for a tag just created via createValuedTagForEditing, opened before it
    // has any instance — lets selectedGroup fall back to a synthetic empty group below
    // instead of null. Any other selection (tapping a real capsule) leaves this false, so
    // removing an existing group's last instance still auto-dismisses the sheet as before.
    var selectedGroupIsFreshValuedTag by remember { mutableStateOf(false) }
    val dayGroups = (uiState.periodData as? CalendarPeriodData.Day)?.groups.orEmpty()
    val selectedGroup = selectedGroupKey?.let { tagId ->
        dayGroups.find { it.tagId == tagId } ?: if (selectedGroupIsFreshValuedTag) {
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
    LaunchedEffect(pendingValuedTagEdit) {
        pendingValuedTagEdit?.let { tagId ->
            selectedGroupKey = tagId
            selectedGroupIsFreshValuedTag = true
            viewModel.consumePendingValuedTagEdit()
        }
    }

    CalendarContent(
        uiState = uiState,
        onNavigateToTags = onNavigateToTags,
        onNavigateToSettings = onNavigateToSettings,
        onAddExistingTag = { tagId -> viewModel.addExistingTag(tagId) },
        onCreateTag = { name, type, rating, value -> viewModel.createTagAndAdd(name, type, rating, value) },
        onCreateValuedTag = { name -> viewModel.createValuedTagForEditing(name) },
        onGroupClick = { group ->
            selectedGroupKey = group.tagId
            selectedGroupIsFreshValuedTag = false
        },
        onGroupQuickRemove = { group -> viewModel.removeGroup(group) },
        onUndoRemoval = { viewModel.undoRemoval() },
        onCommitPendingRemoval = { viewModel.commitPendingRemoval() },
        onDayClick = { date -> viewModel.jumpToDay(date) },
        onMonthClick = { date -> viewModel.jumpToMonth(date) },
        onTagPicked = { tagId -> viewModel.selectHeatmapTag(tagId) },
        onStepTime = { direction -> viewModel.stepTime(direction) },
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
            onReorderValues = { instances -> viewModel.reorderValues(instances) },
        )
    }
}
