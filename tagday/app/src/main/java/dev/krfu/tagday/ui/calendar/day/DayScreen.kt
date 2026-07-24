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
import dev.krfu.tagday.data.local.entity.TagInstanceType

@Composable
fun DayScreen(modifier: Modifier = Modifier, viewModel: DayViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var isAddSheetOpen by remember { mutableStateOf(false) }
    var selectedGroupKey by remember { mutableStateOf<Pair<Long, TagInstanceType>?>(null) }
    val selectedGroup = selectedGroupKey?.let { (tagId, type) ->
        uiState.groups.find { it.tagId == tagId && it.type == type }
    }
    LaunchedEffect(uiState.groups) {
        // The group's last instance may have just been removed — nothing left to show.
        if (selectedGroupKey != null && selectedGroup == null) {
            selectedGroupKey = null
        }
    }

    DayContent(
        uiState = uiState,
        onAddTagClick = { isAddSheetOpen = true },
        onGroupClick = { group -> selectedGroupKey = group.tagId to group.type },
        onGroupQuickRemove = { group ->
            // A single instance can be removed directly; anything more still needs picking.
            if (group.instances.size == 1) {
                viewModel.removeInstance(group.instances.single())
            } else {
                selectedGroupKey = group.tagId to group.type
            }
        },
        modifier = modifier,
    )

    if (isAddSheetOpen) {
        AddTagSheet(
            allTags = uiState.allTags,
            onDismiss = { isAddSheetOpen = false },
            onExistingTagSelected = { tagId ->
                viewModel.addExistingTag(tagId)
                isAddSheetOpen = false
            },
            onCreateTag = { name, color ->
                viewModel.createTagAndAdd(name, color)
                isAddSheetOpen = false
            },
        )
    }

    selectedGroup?.let { group ->
        InstanceListSheet(
            group = group,
            onDismiss = { selectedGroupKey = null },
            onRemoveInstance = { instance -> viewModel.removeInstance(instance) },
        )
    }
}
