package dev.krfu.tagday.ui.tags

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.krfu.tagday.R
import dev.krfu.tagday.data.local.entity.Tag
import dev.krfu.tagday.data.local.entity.TagType
import dev.krfu.tagday.ui.calendar.day.label
import dev.krfu.tagday.ui.components.VerticalScrollbar
import dev.krfu.tagday.ui.theme.TagDayTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagsContent(
    uiState: TagsUiState,
    onNavigateBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onRenameClick: (Tag) -> Unit,
    onColorClick: (Tag) -> Unit,
    onDeleteClick: (Tag) -> Unit,
    onConfirmDelete: () -> Unit,
    onCancelDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_tags_label)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.nav_back_content_description),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            OutlinedTextField(
                value = uiState.query,
                onValueChange = onQueryChange,
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                placeholder = { Text(stringResource(R.string.tags_search_placeholder)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            )
            val listState = rememberLazyListState()
            if (uiState.tags.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (uiState.query.isBlank()) {
                            stringResource(R.string.tags_empty_message)
                        } else {
                            stringResource(R.string.tags_empty_filtered_message, uiState.query)
                        },
                    )
                }
            } else {
                Box(modifier = Modifier.weight(1f)) {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        items(uiState.tags, key = { it.id }) { tag ->
                            TagRow(
                                tag = tag,
                                onColorClick = { onColorClick(tag) },
                                onRenameClick = { onRenameClick(tag) },
                                onDeleteClick = { onDeleteClick(tag) },
                            )
                        }
                    }
                    // Overlay sibling, sized to the list — see VerticalScrollbar for why it
                    // can't be chained onto the LazyColumn itself. Draws nothing until the
                    // tag list actually overflows.
                    VerticalScrollbar(state = listState, modifier = Modifier.matchParentSize())
                }
            }
        }
    }

    uiState.pendingDelete?.let { pending ->
        DeleteTagConfirmationDialog(
            pending = pending,
            onConfirm = onConfirmDelete,
            onDismiss = onCancelDelete,
        )
    }
}

@Composable
private fun TagRow(
    tag: Tag,
    onColorClick: () -> Unit,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    val colorContentDescription = stringResource(R.string.tags_color_content_description, tag.name)

    ListItem(
        headlineContent = { Text(tag.name) },
        supportingContent = { Text(tag.type.label()) },
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(tag.color))
                    .clickable(onClick = onColorClick)
                    .semantics {
                        contentDescription = colorContentDescription
                    },
            )
        },
        trailingContent = {
            Row {
                IconButton(onClick = onRenameClick) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = stringResource(R.string.tags_rename_content_description, tag.name),
                    )
                }
                IconButton(onClick = onDeleteClick) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.tags_delete_content_description, tag.name),
                    )
                }
            }
        },
    )
}

@Composable
private fun DeleteTagConfirmationDialog(
    pending: TagsUiState.PendingDelete,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tags_delete_dialog_title, pending.tag.name)) },
        text = { Text(stringResource(R.string.tags_delete_dialog_message, pending.instanceCount)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.tags_delete_dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.tags_delete_dialog_cancel))
            }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun TagsContentPreview() {
    TagDayTheme {
        TagsContent(
            uiState = TagsUiState(
                isLoading = false,
                tags = listOf(
                    Tag(id = 1, name = "walk", type = TagType.SIMPLE, color = 0xFF81C784.toInt(), createdAt = 0),
                    Tag(id = 2, name = "freediving", type = TagType.RATED, color = 0xFF4FC3F7.toInt(), createdAt = 0),
                ),
            ),
            onNavigateBack = {},
            onQueryChange = {},
            onRenameClick = {},
            onColorClick = {},
            onDeleteClick = {},
            onConfirmDelete = {},
            onCancelDelete = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun TagsContentEmptyPreview() {
    TagDayTheme {
        TagsContent(
            uiState = TagsUiState(isLoading = false),
            onNavigateBack = {},
            onQueryChange = {},
            onRenameClick = {},
            onColorClick = {},
            onDeleteClick = {},
            onConfirmDelete = {},
            onCancelDelete = {},
        )
    }
}
