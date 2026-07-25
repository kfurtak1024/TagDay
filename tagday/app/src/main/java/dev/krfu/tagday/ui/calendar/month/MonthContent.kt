package dev.krfu.tagday.ui.calendar.month

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.krfu.tagday.R
import dev.krfu.tagday.data.local.entity.Tag
import dev.krfu.tagday.data.local.entity.TagType
import dev.krfu.tagday.ui.calendar.CalendarDateRanges
import dev.krfu.tagday.ui.calendar.HeatmapDayCell
import dev.krfu.tagday.ui.calendar.TagPickerDropdown
import dev.krfu.tagday.ui.theme.TagDayTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val dayOfWeekFormatter = DateTimeFormatter.ofPattern("EEEEE")

@Composable
fun MonthContent(
    focusedDate: LocalDate,
    allTags: List<Tag>,
    selectedTagId: Long?,
    countsByDate: Map<Int, Int>,
    onTagPicked: (Long) -> Unit,
    onDayClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        TagPickerDropdown(allTags = allTags, selectedTagId = selectedTagId, onTagPicked = onTagPicked)
        val selectedTag = allTags.find { it.id == selectedTagId }
        if (selectedTag == null) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = stringResource(R.string.calendar_heatmap_pick_tag_message))
            }
        } else {
            MonthGrid(
                focusedDate = focusedDate,
                tagColor = selectedTag.color,
                countsByDate = countsByDate,
                onDayClick = onDayClick,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

/** Shared by Month (one grid) and Year (12 small grids) — reused as `internal` across zoom-level packages. */
@Composable
internal fun MonthGrid(
    focusedDate: LocalDate,
    tagColor: Int,
    countsByDate: Map<Int, Int>,
    onDayClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val range = CalendarDateRanges.monthRange(focusedDate)
    val firstOfMonth = range.start
    // ISO weekday: Monday=1 .. Sunday=7 — leading blanks align the 1st to its column.
    val leadingBlanks = firstOfMonth.dayOfWeek.value - 1
    val days = generateSequence(range.start) { it.plusDays(1) }
        .takeWhile { it <= range.endInclusive }
        .toList()
    val cells: List<LocalDate?> = List(leadingBlanks) { null } + days

    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            days.take(7).forEach { day ->
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(text = day.format(dayOfWeekFormatter), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        cells.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { day ->
                    if (day == null) {
                        Box(modifier = Modifier.weight(1f))
                    } else {
                        HeatmapDayCell(
                            dayOfMonth = day.dayOfMonth,
                            count = countsByDate[day.toEpochDay().toInt()] ?: 0,
                            tagColor = tagColor,
                            onClick = { onDayClick(day) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                repeat(7 - week.size) { Box(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MonthContentPreview() {
    TagDayTheme {
        MonthContent(
            focusedDate = LocalDate.of(2026, 7, 22),
            allTags = listOf(
                Tag(id = 1, name = "walk", type = TagType.SIMPLE, color = 0xFF81C784.toInt(), createdAt = 0),
            ),
            selectedTagId = 1,
            countsByDate = mapOf(
                LocalDate.of(2026, 7, 3).toEpochDay().toInt() to 1,
                LocalDate.of(2026, 7, 10).toEpochDay().toInt() to 2,
                LocalDate.of(2026, 7, 15).toEpochDay().toInt() to 4,
            ),
            onTagPicked = {},
            onDayClick = {},
        )
    }
}
