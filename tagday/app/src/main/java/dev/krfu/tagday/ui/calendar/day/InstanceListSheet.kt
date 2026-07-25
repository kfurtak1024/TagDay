package dev.krfu.tagday.ui.calendar.day

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.krfu.tagday.R
import dev.krfu.tagday.data.local.entity.TagInstance
import dev.krfu.tagday.data.local.entity.TagType
import dev.krfu.tagday.data.model.TagDisplayGroup
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstanceListSheet(
    group: TagDisplayGroup,
    onDismiss: () -> Unit,
    onUpdateInstance: (TagInstance) -> Unit,
    onRemoveInstance: (TagInstance) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, modifier = modifier) {
        Column(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text(text = group.tagName, style = MaterialTheme.typography.titleLarge)
            group.instances.sortedBy { it.createdAt }.forEach { instance ->
                ListItem(
                    headlineContent = {
                        InstanceEditor(
                            type = group.type,
                            instance = instance,
                            timeFormatter = timeFormatter,
                            onUpdateInstance = onUpdateInstance,
                        )
                    },
                    supportingContent = if (group.type != TagType.SIMPLE) {
                        { Text(text = formatInstant(instance.createdAt, timeFormatter)) }
                    } else {
                        null
                    },
                    trailingContent = {
                        IconButton(onClick = { onRemoveInstance(instance) }) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = stringResource(
                                    R.string.day_instances_sheet_remove_content_description,
                                ),
                            )
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun InstanceEditor(
    type: TagType,
    instance: TagInstance,
    timeFormatter: DateTimeFormatter,
    onUpdateInstance: (TagInstance) -> Unit,
) {
    when (type) {
        TagType.SIMPLE -> Text(text = formatInstant(instance.createdAt, timeFormatter))

        TagType.RATED -> StarInput(
            rating = instance.rating ?: 0,
            onRatingSelected = { rating -> onUpdateInstance(instance.copy(rating = rating)) },
        )

        TagType.VALUED -> OutlinedTextField(
            value = instance.value ?: "",
            onValueChange = { value -> onUpdateInstance(instance.copy(value = value)) },
            singleLine = true,
            placeholder = { Text(stringResource(R.string.day_instances_sheet_value_placeholder)) },
        )
    }
}

private fun formatInstant(epochMillis: Long, formatter: DateTimeFormatter): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalTime().format(formatter)
