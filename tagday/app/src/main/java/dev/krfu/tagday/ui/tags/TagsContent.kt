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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
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
    /**
     * The search field's current text. Separate from [TagsUiState.query] on purpose — see
     * `TagsViewModel.searchText`. In short: this one is what the user has typed, `uiState.query`
     * is what the list on screen was filtered by, and only the second belongs in the
     * "no tags match X" message.
     */
    searchText: String,
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
            // Hidden when there's nothing to search: a filter box above "No tags yet" invites
            // the user to narrow an empty list. Gated on `!isLoading` so it doesn't flicker in
            // before the repository has answered — the cost is that someone with zero tags sees
            // it for the frame it takes to find that out, which is the rarer case of the two.
            val repositoryIsEmpty = !uiState.isLoading && uiState.tags.isEmpty() && uiState.query.isBlank()
            if (!repositoryIsEmpty) {
                val focusManager = LocalFocusManager.current
                OutlinedTextField(
                    value = searchText,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    placeholder = { Text(stringResource(R.string.tags_search_placeholder)) },
                    trailingIcon = if (searchText.isEmpty()) {
                        null
                    } else {
                        {
                            IconButton(onClick = { onQueryChange("") }) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = stringResource(
                                        R.string.tags_search_clear_content_description,
                                    ),
                                )
                            }
                        }
                    },
                    // Filtering is live, so there's nothing for the action to submit — it just
                    // gets the keyboard out of the way, which is the whole reason to ask for a
                    // Search key rather than leaving a newline-shaped one on a single-line field.
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                )
            }
            val listState = rememberLazyListState()
            if (uiState.isLoading) {
                // Nothing, deliberately — not a spinner, and above all not the empty state.
                // `isLoading` was previously set by the ViewModel and read by nobody, so the
                // first frame (before the repository's first emission, when `tags` is still
                // the initial empty list) rendered "No tags yet" to anyone who has tags. The
                // load is a local Room query, so the honest length of this state is a frame or
                // two; a spinner would flash more distractingly than a blank does.
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                )
            } else if (uiState.tags.isEmpty()) {
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
            // The dot stays 28dp; the tappable box around it is 48dp, Material's minimum
            // target. `Modifier.clickable` applies no minimum-target enforcement of its own
            // (unlike IconButton, which is why the two trailing icons were already fine), so
            // this was a 28dp target — the smallest in the app, next to a destructive one.
            //
            // Sized explicitly rather than with `minimumInteractiveComponentSize()`: that
            // modifier reserves the space but leaves the `clickable`/`semantics` node beneath
            // it still measuring 28dp, so the thing that actually receives the touch doesn't
            // grow. Here the box that's clickable *is* the box that's 48dp, which is both
            // correct and the thing a test can assert on.
            //
            // Note this is the opposite correction to ADR-026/027, where the capsule ✕ had to
            // opt *out* of target inflation because it overlapped the tag name. The rule isn't
            // "bigger is better": it's that the target should match what the control looks
            // like it covers, and here nothing sits close enough to collide.
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onColorClick)
                    .semantics {
                        contentDescription = colorContentDescription
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(tag.color)),
                )
            }
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
        text = {
            Text(
                if (pending.taggedDayCount == 0) {
                    stringResource(R.string.tags_delete_dialog_message_unused)
                } else {
                    pluralStringResource(
                        R.plurals.tags_delete_dialog_message,
                        pending.taggedDayCount,
                        pending.taggedDayCount,
                    )
                },
            )
        },
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
            searchText = "",
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

/**
 * The delete confirmation, which no test can reach — `AlertDialog` doesn't run under
 * Robolectric (`TESTING.md` § Compose tests), so a Preview is the only verification the two
 * message branches get. This one shows the zero-count branch specifically, since it's the one
 * that reads as a bug when it's wrong.
 */
@Preview(showBackground = true)
@Composable
private fun TagsContentDeletingUnusedTagPreview() {
    TagDayTheme {
        val unused = Tag(id = 1, name = "movie", type = TagType.VALUED, color = 0xFF4FC3F7.toInt(), createdAt = 0)
        TagsContent(
            uiState = TagsUiState(
                isLoading = false,
                tags = listOf(unused),
                pendingDelete = TagsUiState.PendingDelete(unused, taggedDayCount = 0),
            ),
            searchText = "",
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
            searchText = "",
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
