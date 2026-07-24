package dev.krfu.tagday.ui.calendar.day

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.krfu.tagday.R
import dev.krfu.tagday.data.local.entity.Tag
import dev.krfu.tagday.ui.theme.TagPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTagSheet(
    allTags: List<Tag>,
    onDismiss: () -> Unit,
    onExistingTagSelected: (tagId: Long) -> Unit,
    onCreateTag: (name: String, color: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by rememberSaveable { mutableStateOf("") }
    var selectedColor by rememberSaveable { mutableIntStateOf(TagPalette.colors.first()) }

    val trimmedQuery = query.trim()
    val filteredTags = remember(allTags, trimmedQuery) {
        if (trimmedQuery.isEmpty()) {
            allTags
        } else {
            allTags.filter { it.name.contains(trimmedQuery, ignoreCase = true) }
        }
    }
    val canCreate = trimmedQuery.isNotEmpty() &&
        filteredTags.none { it.name.equals(trimmedQuery, ignoreCase = true) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, modifier = modifier) {
        Column(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text(text = stringResource(R.string.day_add_sheet_title), style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                placeholder = {
                    Text(
                        text = stringResource(
                            if (allTags.isEmpty()) {
                                R.string.day_add_sheet_create_first_placeholder
                            } else {
                                R.string.day_add_sheet_search_placeholder
                            },
                        ),
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 8.dp),
            )
            LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                items(filteredTags, key = { it.id }) { tag ->
                    ListItem(
                        headlineContent = { Text(tag.name) },
                        leadingContent = { ColorDot(color = tag.color) },
                        modifier = Modifier.clickable { onExistingTagSelected(tag.id) },
                    )
                }
                if (canCreate) {
                    item {
                        Column {
                            Text(
                                text = stringResource(R.string.day_add_sheet_color_label),
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                            ColorPaletteRow(
                                selectedColor = selectedColor,
                                onColorSelected = { selectedColor = it },
                            )
                            ListItem(
                                headlineContent = {
                                    Text(stringResource(R.string.day_add_sheet_create_label, trimmedQuery))
                                },
                                leadingContent = { Icon(Icons.Filled.Add, contentDescription = null) },
                                modifier = Modifier.clickable { onCreateTag(trimmedQuery, selectedColor) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorDot(color: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(Color(color)),
    )
}

@Composable
private fun ColorPaletteRow(
    selectedColor: Int,
    onColorSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TagPalette.colors.forEach { color ->
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(color))
                    .border(
                        width = if (color == selectedColor) 3.dp else 0.dp,
                        color = MaterialTheme.colorScheme.onSurface,
                        shape = CircleShape,
                    )
                    .clickable { onColorSelected(color) },
            )
        }
    }
}
