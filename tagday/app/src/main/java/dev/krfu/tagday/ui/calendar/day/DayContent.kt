package dev.krfu.tagday.ui.calendar.day

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.krfu.tagday.R
import dev.krfu.tagday.data.local.entity.TagInstance
import dev.krfu.tagday.data.local.entity.TagType
import dev.krfu.tagday.data.model.TagDisplayGroup
import dev.krfu.tagday.ui.theme.TagDayTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val dayOfMonthFormatter = DateTimeFormatter.ofPattern("d")
private val weekdayFormatter = DateTimeFormatter.ofPattern("EEEE")
private val monthFormatter = DateTimeFormatter.ofPattern("MMMM")
private val yearFormatter = DateTimeFormatter.ofPattern("yyyy")
private val headerContentDescriptionFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")

/**
 * The Day zoom level's body — header card + tag capsules. Owns no `Scaffold`/top/bottom
 * bar of its own; those are shared across zoom levels by `CalendarContent`.
 */
@Composable
fun DayContent(
    date: LocalDate,
    groups: List<TagDisplayGroup>,
    onGroupClick: (TagDisplayGroup) -> Unit,
    onGroupQuickRemove: (TagDisplayGroup) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        CalendarHeaderCard(date = date, modifier = Modifier.padding(16.dp))
        if (groups.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = stringResource(R.string.day_empty_message))
            }
        } else {
            FlowRow(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                groups.forEach { group ->
                    TagGroupCapsule(
                        group = group,
                        onCapsuleClick = { onGroupClick(group) },
                        onRemoveClick = { onGroupQuickRemove(group) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CalendarHeaderCard(date: LocalDate, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = date.format(headerContentDescriptionFormatter)
            },
        shape = RoundedCornerShape(16.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primary)
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "${date.format(monthFormatter).uppercase()} ${date.format(yearFormatter)}",
                style = MaterialTheme.typography.labelLarge,
                letterSpacing = 2.sp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = date.format(dayOfMonthFormatter),
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = date.format(weekdayFormatter),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }
    }
}

@Composable
private fun TagGroupCapsule(
    group: TagDisplayGroup,
    onCapsuleClick: () -> Unit,
    onRemoveClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backgroundColor = Color(group.color)
    val contentColor = if (backgroundColor.luminance() > 0.5f) Color.Black else Color.White

    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable(onClick = onCapsuleClick)
            .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = group.summary,
            color = contentColor,
            style = MaterialTheme.typography.bodyMedium,
        )
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .clickable(onClick = onRemoveClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.day_capsule_remove_content_description, group.tagName),
                tint = contentColor,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DayContentPreview() {
    TagDayTheme {
        DayContent(
            date = LocalDate.of(2026, 7, 25),
            groups = listOf(
                TagDisplayGroup(
                    tagId = 1,
                    tagName = "walk",
                    color = 0xFF81C784.toInt(),
                    type = TagType.SIMPLE,
                    instances = listOf(
                        TagInstance(id = 1, tagId = 1, date = 0, createdAt = 0),
                        TagInstance(id = 2, tagId = 1, date = 0, createdAt = 1),
                    ),
                    summary = "walk (2)",
                ),
                TagDisplayGroup(
                    tagId = 2,
                    tagName = "reading",
                    color = 0xFF4FC3F7.toInt(),
                    type = TagType.SIMPLE,
                    instances = listOf(
                        TagInstance(id = 3, tagId = 2, date = 0, createdAt = 0),
                    ),
                    summary = "reading",
                ),
            ),
            onGroupClick = {},
            onGroupQuickRemove = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DayContentEmptyPreview() {
    TagDayTheme {
        DayContent(
            date = LocalDate.of(2026, 7, 25),
            groups = emptyList(),
            onGroupClick = {},
            onGroupQuickRemove = {},
        )
    }
}
