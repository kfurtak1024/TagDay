package dev.krfu.tagday.ui.tags

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import dev.krfu.tagday.R
import dev.krfu.tagday.data.local.entity.Tag
import dev.krfu.tagday.data.model.TagName

@Composable
fun RenameTagDialog(
    tag: Tag,
    onDismiss: () -> Unit,
    onRename: (tag: Tag, newName: String, onResult: (Boolean) -> Unit) -> Unit,
) {
    var name by rememberSaveable(tag.id) { mutableStateOf(tag.name) }
    var showDuplicateError by rememberSaveable(tag.id) { mutableStateOf(false) }
    // Sanitizing every keystroke means the only ways to be invalid here are "empty" and
    // "ends with a separator" — plus a pre-existing name that predates these rules, which is
    // shown as-is and only normalized once the user actually edits it. It also subsumes the
    // old defence against a ':***'/':value' suffix typed out of quick-entry-bar habit
    // (ParsedTagInput.kt): ':' simply can't be entered. See ADR-028.
    val isValid = TagName.isValid(name)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tags_rename_dialog_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { raw ->
                    name = TagName.sanitize(raw)
                    showDuplicateError = false
                },
                label = { Text(stringResource(R.string.tags_rename_dialog_label)) },
                singleLine = true,
                isError = showDuplicateError,
                supportingText = when {
                    showDuplicateError -> {
                        { Text(stringResource(R.string.tags_rename_dialog_error_duplicate)) }
                    }
                    // Explains why Save is disabled, rather than leaving it inert.
                    !isValid -> {
                        { Text(stringResource(R.string.tags_name_rule_hint)) }
                    }
                    else -> null
                },
            )
        },
        confirmButton = {
            TextButton(
                enabled = isValid,
                // The write itself runs on the ViewModel's scope, so dismissing here can't
                // cancel it half-done (BACKLOG F22).
                onClick = {
                    onRename(tag, name) { renamed ->
                        if (renamed) onDismiss() else showDuplicateError = true
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
