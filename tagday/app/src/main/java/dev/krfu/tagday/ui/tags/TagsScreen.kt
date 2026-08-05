package dev.krfu.tagday.ui.tags

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun TagsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TagsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Ids rather than `Tag` snapshots, so these can be `rememberSaveable` and survive rotation
    // (BACKLOG F10) — and so an open dialog reflects a concurrent edit rather than a stale copy.
    var renamingTagId by rememberSaveable { mutableStateOf<Long?>(null) }
    var recoloringTagId by rememberSaveable { mutableStateOf<Long?>(null) }
    val renamingTag = renamingTagId?.let { id -> uiState.tags.find { it.id == id } }
    val recoloringTag = recoloringTagId?.let { id -> uiState.tags.find { it.id == id } }

    TagsContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onQueryChange = viewModel::onQueryChange,
        onRenameClick = { tag -> renamingTagId = tag.id },
        onColorClick = { tag -> recoloringTagId = tag.id },
        onDeleteClick = { tag -> viewModel.requestDelete(tag) },
        onConfirmDelete = viewModel::confirmDelete,
        onCancelDelete = viewModel::cancelDelete,
        modifier = modifier,
    )

    renamingTag?.let { tag ->
        RenameTagDialog(
            tag = tag,
            onDismiss = { renamingTagId = null },
            onRename = { t, newName -> viewModel.renameTag(t, newName) },
        )
    }

    recoloringTag?.let { tag ->
        ColorPickerDialog(
            initialColor = tag.color,
            onDismiss = { recoloringTagId = null },
            onSave = { color ->
                viewModel.updateColor(tag, color)
                recoloringTagId = null
            },
        )
    }
}
