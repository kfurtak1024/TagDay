package dev.krfu.tagday.ui.calendar.week

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import dev.krfu.tagday.data.local.entity.TagType
import dev.krfu.tagday.data.model.TagDisplayGroup
import dev.krfu.tagday.ui.calendar.CalendarDateRanges
import dev.krfu.tagday.ui.theme.TagDayTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val weekdayFormatter = DateTimeFormatter.ofPattern("EEE")
private val rowContentDescriptionFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM")

@Composable
fun WeekContent(
    focusedDate: LocalDate,
    today: LocalDate,
    groupsByDate: Map<Int, List<TagDisplayGroup>>,
    onDayClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val range = CalendarDateRanges.weekRange(focusedDate)
    val days = generateSequence(range.start) { it.plusDays(1) }
        .takeWhile { it <= range.endInclusive }
        .toList()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        days.forEach { day ->
            WeekDayRow(
                date = day,
                isToday = day == today,
                groups = groupsByDate[day.toEpochDay().toInt()].orEmpty(),
                onClick = { onDayClick(day) },
            )
        }
    }
}

@Composable
private fun WeekDayRow(
    date: LocalDate,
    isToday: Boolean,
    groups: List<TagDisplayGroup>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The dots carry colour and nothing else — no names, no counts, and until now no
    // semantics at all, which made this whole zoom level invisible to a screen reader and
    // ambiguous by eye once two tags had similar colours (BACKLOG F13). Merging the row's
    // descendants into one node means it reads as "Monday 20 July: walk, reading" rather than
    // as a date followed by silence.
    val dateLabel = date.format(rowContentDescriptionFormatter)
    val rowContentDescription = if (groups.isEmpty()) {
        stringResource(R.string.calendar_week_day_empty_content_description, dateLabel)
    } else {
        stringResource(
            R.string.calendar_week_day_content_description,
            dateLabel,
            groups.joinToString(", ") { it.summary },
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
            .semantics(mergeDescendants = true) { contentDescription = rowContentDescription },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.width(56.dp)) {
            Text(text = date.format(weekdayFormatter), style = MaterialTheme.typography.labelMedium)
            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .let { m ->
                        if (isToday) {
                            m.clip(CircleShape).background(MaterialTheme.colorScheme.primary)
                        } else {
                            m
                        }
                    }
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    text = date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isToday) MaterialTheme.colorScheme.onPrimary else Color.Unspecified,
                )
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f),
        ) {
            groups.forEach { group ->
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Color(group.color)),
                )
            }
        }
    }
}

/** Uses the current week so the today-highlight is visible whenever this preview is opened. */
@Preview(showBackground = true)
@Composable
private fun WeekContentPreview() {
    TagDayTheme {
        val monday = CalendarDateRanges.weekRange(LocalDate.now()).start
        WeekContent(
            focusedDate = LocalDate.now(),
            today = LocalDate.now(),
            groupsByDate = mapOf(
                monday.toEpochDay().toInt() to listOf(
                    TagDisplayGroup(1, "walk", 0xFF81C784.toInt(), TagType.SIMPLE, emptyList(), "walk"),
                    TagDisplayGroup(2, "reading", 0xFF4FC3F7.toInt(), TagType.SIMPLE, emptyList(), "reading"),
                ),
                monday.plusDays(2).toEpochDay().toInt() to listOf(
                    TagDisplayGroup(1, "walk", 0xFF81C784.toInt(), TagType.SIMPLE, emptyList(), "walk"),
                ),
            ),
            onDayClick = {},
        )
    }
}
