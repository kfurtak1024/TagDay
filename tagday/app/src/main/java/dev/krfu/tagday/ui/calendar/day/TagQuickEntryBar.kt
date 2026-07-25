package dev.krfu.tagday.ui.calendar.day

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.krfu.tagday.R
import dev.krfu.tagday.data.local.entity.Tag
import dev.krfu.tagday.data.local.entity.TagType

private const val MAX_VISIBLE_SUGGESTIONS = 4

@Composable
fun TagQuickEntryBar(
    allTags: List<Tag>,
    onAddExistingTag: (tagId: Long) -> Unit,
    onCreateTag: (name: String, type: TagType, rating: Int?, value: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val parsed = ParsedTagInput.parse(query)
    val trimmedName = parsed?.name.orEmpty()
    val exactMatch = if (trimmedName.isEmpty()) {
        null
    } else {
        allTags.find { it.name.equals(trimmedName, ignoreCase = true) }
    }
    val suggestions = if (trimmedName.isEmpty()) {
        emptyList()
    } else {
        allTags.filter { it.name.contains(trimmedName, ignoreCase = true) }
    }

    fun submit() {
        when {
            trimmedName.isEmpty() -> return
            exactMatch != null -> onAddExistingTag(exactMatch.id)
            parsed is ParsedTagInput.Rated -> onCreateTag(trimmedName, TagType.RATED, parsed.rating, null)
            parsed is ParsedTagInput.Valued -> onCreateTag(trimmedName, TagType.VALUED, null, parsed.value)
            else -> return
        }
        query = ""
    }

    Surface(modifier = modifier.fillMaxWidth().imePadding(), tonalElevation = 3.dp) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            when {
                suggestions.isNotEmpty() -> Column {
                    // Capped by count, not height, and forced single-line: a partial,
                    // clipped row (e.g. a height cap that lands mid-item) reads as the
                    // input field overlapping the list rather than as "scroll for more".
                    suggestions.take(MAX_VISIBLE_SUGGESTIONS).forEach { tag ->
                        ListItem(
                            headlineContent = {
                                Text(text = tag.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            leadingContent = { ColorDot(color = tag.color) },
                            modifier = Modifier.clickable {
                                onAddExistingTag(tag.id)
                                query = ""
                            },
                        )
                    }
                }

                exactMatch == null && parsed is ParsedTagInput.Ambiguous -> Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 8.dp),
                ) {
                    TagType.entries.forEach { type ->
                        FilterChip(
                            selected = false,
                            onClick = {
                                onCreateTag(trimmedName, type, null, null)
                                query = ""
                            },
                            label = { Text(type.label()) },
                        )
                    }
                }

                exactMatch == null && (parsed is ParsedTagInput.Rated || parsed is ParsedTagInput.Valued) -> {
                    val type = if (parsed is ParsedTagInput.Rated) TagType.RATED else TagType.VALUED
                    Text(
                        text = stringResource(R.string.day_quick_entry_type_hint, type.label()),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.day_quick_entry_placeholder)) },
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { submit() }) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.day_quick_entry_submit_content_description),
                    )
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
