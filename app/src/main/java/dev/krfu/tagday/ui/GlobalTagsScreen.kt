package dev.krfu.tagday.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.krfu.tagday.domain.TagValidation

@Composable
fun GlobalTagsScreen(
    uiState: MainUiState,
    onUpdateTag: (currentName: String, newName: String, colorArgb: Int, hidden: Boolean) -> Unit,
    onDeleteTag: (String) -> Unit
) {
    var editingTag by remember { mutableStateOf<GlobalTagUi?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
    ) {
        uiState.globalTagError?.let { error ->
            item {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        if (uiState.globalTags.isEmpty()) {
            item {
                Text("No global tags yet. Add a day tag first.")
            }
        }

        items(uiState.globalTags, key = { it.name }) { tag ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { editingTag = tag },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = tag.name,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(if (tag.hidden) "Hidden" else "Visible")
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(Color(tag.colorArgb))
                                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        )
                        Text("Edit")
                    }
                }
            }
        }
    }

    editingTag?.let { tag ->
        EditGlobalTagDialog(
            tag = tag,
            palette = uiState.colorPalette,
            existingTagNames = uiState.globalTags.map { it.name },
            onDismiss = { editingTag = null },
            onSave = { newName, color, hidden ->
                onUpdateTag(tag.name, newName, color, hidden)
                editingTag = null
            },
            onDelete = {
                onDeleteTag(tag.name)
                editingTag = null
            }
        )
    }
}

@Composable
private fun EditGlobalTagDialog(
    tag: GlobalTagUi,
    palette: List<Int>,
    existingTagNames: List<String>,
    onDismiss: () -> Unit,
    onSave: (newName: String, colorArgb: Int, hidden: Boolean) -> Unit,
    onDelete: () -> Unit
) {
    var name by remember(tag.name) { mutableStateOf(tag.name) }
    var hidden by remember(tag.hidden) { mutableStateOf(tag.hidden) }
    var selectedColor by remember(tag.colorArgb) { mutableIntStateOf(tag.colorArgb) }
    var validationError by remember(tag.name, existingTagNames) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit global tag") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { value ->
                        name = value
                        validationError = validateGlobalTagName(
                            proposedName = value.trim(),
                            currentName = tag.name,
                            existingTagNames = existingTagNames
                        )
                    },
                    label = { Text("Name") },
                    singleLine = true,
                    isError = validationError != null,
                    supportingText = {
                        validationError?.let { error ->
                            Text(error, color = MaterialTheme.colorScheme.error)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Hidden")
                    Switch(checked = hidden, onCheckedChange = { hidden = it })
                }

                Text("Color")
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    palette.chunked(6).forEach { rowColors ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowColors.forEach { colorArgb ->
                                val isSelected = colorArgb == selectedColor
                                Box(
                                    modifier = Modifier
                                        .size(if (isSelected) 30.dp else 26.dp)
                                        .clip(CircleShape)
                                        .background(Color(colorArgb))
                                        .border(
                                            width = if (isSelected) 3.dp else 1.dp,
                                            color = if (isSelected) {
                                                MaterialTheme.colorScheme.onSurface
                                            } else {
                                                MaterialTheme.colorScheme.outline
                                            },
                                            shape = CircleShape
                                        )
                                        .clickable { selectedColor = colorArgb }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val trimmedName = name.trim()
                    val error = validateGlobalTagName(
                        proposedName = trimmedName,
                        currentName = tag.name,
                        existingTagNames = existingTagNames
                    )
                    validationError = error
                    if (error == null) {
                        onSave(trimmedName, selectedColor, hidden)
                    }
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDelete) {
                    Text("Delete")
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}

private fun validateGlobalTagName(
    proposedName: String,
    currentName: String,
    existingTagNames: List<String>
): String? {
    if (!TagValidation.isValidName(proposedName)) {
        return "Invalid tag name. Use letters only with single '-' separators (e.g., dinner-with-family)."
    }

    val alreadyExists = existingTagNames.any { existing ->
        existing != currentName && existing.equals(proposedName, ignoreCase = true)
    }
    if (alreadyExists) {
        return "A tag with this name already exists."
    }

    return null
}
