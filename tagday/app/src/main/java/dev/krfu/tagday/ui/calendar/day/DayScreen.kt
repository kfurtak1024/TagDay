package dev.krfu.tagday.ui.calendar.day

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun DayScreen(modifier: Modifier = Modifier, viewModel: DayViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var selectedGroupKey by remember { mutableStateOf<Long?>(null) }
    val selectedGroup = selectedGroupKey?.let { tagId ->
        uiState.groups.find { it.tagId == tagId }
    }
    LaunchedEffect(uiState.groups) {
        // The group's last instance may have just been removed — nothing left to show.
        if (selectedGroupKey != null && selectedGroup == null) {
            selectedGroupKey = null
        }
    }

    DayContent(
        uiState = uiState,
        onAddExistingTag = { tagId -> viewModel.addExistingTag(tagId) },
        onCreateTag = { name, type, rating, value -> viewModel.createTagAndAdd(name, type, rating, value) },
        onGroupClick = { group -> selectedGroupKey = group.tagId },
        onGroupQuickRemove = { group ->
            // A single instance can be removed directly; anything more still needs picking.
            if (group.instances.size == 1) {
                viewModel.removeInstance(group.instances.single())
            } else {
                selectedGroupKey = group.tagId
            }
        },
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
