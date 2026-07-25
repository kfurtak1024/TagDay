package dev.krfu.tagday.ui.tags

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import dev.krfu.tagday.R
import dev.krfu.tagday.data.local.entity.Tag
import kotlinx.coroutines.launch

@Composable
fun RenameTagDialog(
    tag: Tag,
    onDismiss: () -> Unit,
    onRename: suspend (tag: Tag, newName: String) -> Boolean,
) {
    var name by rememberSaveable(tag.id) { mutableStateOf(tag.name) }
    var showDuplicateError by rememberSaveable(tag.id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // Rename is name-only (FEATURES.md § Tag types) — a ':***'/':value' suffix typed out
    // of quick-entry-bar habit (ParsedTagInput.kt) must not leak into the stored name.
    val trimmedName = name.substringBefore(':').trim()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tags_rename_dialog_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    showDuplicateError = false
                },
                label = { Text(stringResource(R.string.tags_rename_dialog_label)) },
                singleLine = true,
                isError = showDuplicateError,
                supportingText = if (showDuplicateError) {
                    { Text(stringResource(R.string.tags_rename_dialog_error_duplicate)) }
                } else {
                    null
                },
            )
        },
        confirmButton = {
            TextButton(
                enabled = trimmedName.isNotEmpty(),
                onClick = {
                    scope.launch {
                        if (onRename(tag, trimmedName)) onDismiss() else showDuplicateError = true
                    }
                },
            ) {
                Text(stringResource(R.string.tags_rename_dialog_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.tags_rename_dialog_cancel))
            }
        },
    )
}
