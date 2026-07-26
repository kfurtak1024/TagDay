package dev.krfu.tagday.ui.calendar.year

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import dev.krfu.tagday.data.local.entity.Tag
import dev.krfu.tagday.data.local.entity.TagType
import dev.krfu.tagday.ui.calendar.CalendarDateRanges
import dev.krfu.tagday.ui.calendar.TagPickerDropdown
import dev.krfu.tagday.ui.calendar.alphaForCount
import dev.krfu.tagday.ui.theme.TagDayTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val monthAbbrevFormatter = DateTimeFormatter.ofPattern("MMM")
private val monthNameFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")
private const val GRID_ROWS = 4
private const val GRID_COLUMNS = 3
private const val WEEKS_PER_TILE = 6

/**
 * All 12 months as one no-scroll grid — see ADR-016. Cells drop the day-of-month text
 * (unreadable at this density) and per-day tap targets (well under the 48dp minimum);
 * the whole month tile is the tap target instead, jumping to Month zoom rather than
 * straight to Day zoom.
 */
@Composable
fun YearContent(
    focusedDate: LocalDate,
    allTags: List<Tag>,
    selectedTagId: Long?,
    countsByDate: Map<Int, Int>,
    onTagPicked: (Long) -> Unit,
    onMonthClick: (LocalDate) -> Unit,
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
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                (0 until GRID_ROWS).forEach { row ->
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val today = LocalDate.now()
                        (1..GRID_COLUMNS).forEach { column ->
                            val month = row * GRID_COLUMNS + column
                            val monthDate = LocalDate.of(focusedDate.year, month, 1)
                            YearMonthTile(
                                monthDate = monthDate,
                                tagColor = selectedTag.color,
                                countsByDate = countsByDate,
                                isCurrentMonth = monthDate.year == today.year && monthDate.month == today.month,
                                onClick = { onMonthClick(monthDate) },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun YearMonthTile(
    monthDate: LocalDate,
    tagColor: Int,
    countsByDate: Map<Int, Int>,
    isCurrentMonth: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val weeks = remember(monthDate) {
        val cells = CalendarDateRanges.monthGridCells(monthDate).chunked(7).toMutableList()
        while (cells.size < WEEKS_PER_TILE) cells.add(List(7) { null })
        cells
    }
    val shape = RoundedCornerShape(4.dp)

    Column(
        modifier = modifier
            .clip(shape)
            // Per-day cells are well under the 48dp touch target at this density (ADR-016),
            // so the today-indicator lives on the whole tile instead — see ADR-017.
            .let { m -> if (isCurrentMonth) m.border(1.5.dp, MaterialTheme.colorScheme.primary, shape) else m }
            .clickable(onClick = onClick)
            .semantics { contentDescription = monthDate.format(monthNameFormatter) }
            .padding(4.dp),
    ) {
        Text(
            text = monthDate.format(monthAbbrevFormatter),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(bottom = 2.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            weeks.forEach { week ->
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    week.forEach { day ->
                        val alpha = if (day == null) 0f else alphaForCount(countsByDate[day.toEpochDay().toInt()] ?: 0)
                        val cellColor = if (alpha == 0f) Color.Transparent else Color(tagColor).copy(alpha = alpha)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(1.dp))
                                .background(cellColor),
                        )
                    }
                }
            }
        }
    }
}

/** Uses the current year so the current-month tile border is visible whenever this preview is opened. */
@Preview(showBackground = true, heightDp = 700)
@Composable
private fun YearContentPreview() {
    TagDayTheme {
        val focusedDate = LocalDate.now()
        YearContent(
            focusedDate = focusedDate,
            allTags = listOf(
                Tag(id = 1, name = "walk", type = TagType.SIMPLE, color = 0xFF81C784.toInt(), createdAt = 0),
            ),
            selectedTagId = 1,
            countsByDate = mapOf(
                LocalDate.of(focusedDate.year, 1, 3).toEpochDay().toInt() to 1,
                focusedDate.withDayOfMonth(10).toEpochDay().toInt() to 2,
                LocalDate.of(focusedDate.year, 12, 15).toEpochDay().toInt() to 4,
            ),
            onTagPicked = {},
            onMonthClick = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 700)
@Composable
private fun YearContentEmptyPreview() {
    TagDayTheme {
        YearContent(
            focusedDate = LocalDate.of(2026, 7, 22),
            allTags = emptyList(),
            selectedTagId = null,
            countsByDate = emptyMap(),
            onTagPicked = {},
            onMonthClick = {},
        )
    }
}
