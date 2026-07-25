package dev.krfu.tagday.ui.tags

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.krfu.tagday.data.local.entity.Tag

@Composable
fun TagsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TagsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var renamingTag by remember { mutableStateOf<Tag?>(null) }
    var recoloringTag by remember { mutableStateOf<Tag?>(null) }

    TagsContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onQueryChange = viewModel::onQueryChange,
        onRenameClick = { tag -> renamingTag = tag },
        onColorClick = { tag -> recoloringTag = tag },
        onDeleteClick = { tag -> viewModel.requestDelete(tag) },
        onConfirmDelete = viewModel::confirmDelete,
        onCancelDelete = viewModel::cancelDelete,
        modifier = modifier,
    )

    renamingTag?.let { tag ->
        RenameTagDialog(
            tag = tag,
            onDismiss = { renamingTag = null },
            onRename = { t, newName -> viewModel.renameTag(t, newName) },
        )
    }

    recoloringTag?.let { tag ->
        ColorPickerDialog(
            initialColor = tag.color,
            onDismiss = { recoloringTag = null },
            onSave = { color ->
                viewModel.updateColor(tag, color)
                recoloringTag = null
            },
        )
    }
}
