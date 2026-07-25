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
import dev.krfu.tagday.ui.calendar.day.InstanceListSheet

@Composable
fun CalendarScreen(
    onNavigateToTags: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CalendarViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var selectedGroupKey by remember { mutableStateOf<Long?>(null) }
    val dayGroups = (uiState.periodData as? CalendarPeriodData.Day)?.groups.orEmpty()
    val selectedGroup = selectedGroupKey?.let { tagId -> dayGroups.find { it.tagId == tagId } }
    LaunchedEffect(dayGroups) {
        // The group's last instance may have just been removed — nothing left to show.
        if (selectedGroupKey != null && selectedGroup == null) {
            selectedGroupKey = null
        }
    }

    CalendarContent(
        uiState = uiState,
        onNavigateToTags = onNavigateToTags,
        onAddExistingTag = { tagId -> viewModel.addExistingTag(tagId) },
        onCreateTag = { name, type, rating, value -> viewModel.createTagAndAdd(name, type, rating, value) },
        onGroupClick = { group -> selectedGroupKey = group.tagId },
        onGroupQuickRemove = { group -> viewModel.removeGroup(group) },
        onDayClick = { date -> viewModel.jumpToDay(date) },
        onTagPicked = { tagId -> viewModel.selectHeatmapTag(tagId) },
        onStepTime = { direction -> viewModel.stepTime(direction) },
        onStepZoom = { direction -> viewModel.stepZoom(direction) },
        onZoomLevelPicked = { zoomLevel -> viewModel.setZoom(zoomLevel) },
        modifier = modifier,
    )

    selectedGroup?.let { group ->
        InstanceListSheet(
            group = group,
            onDismiss = { selectedGroupKey = null },
            onUpdateInstance = { instance -> viewModel.updateInstance(instance) },
            onRemoveInstance = { instance -> viewModel.removeInstance(instance) },
        )
    }
}
