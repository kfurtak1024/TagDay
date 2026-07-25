package dev.krfu.tagday.ui.calendar.year

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import dev.krfu.tagday.ui.calendar.TagPickerDropdown
import dev.krfu.tagday.ui.calendar.month.MonthGrid
import dev.krfu.tagday.ui.theme.TagDayTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val monthFormatter = DateTimeFormatter.ofPattern("MMMM")

@Composable
fun YearContent(
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
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                (1..12).forEach { month ->
                    val monthDate = LocalDate.of(focusedDate.year, month, 1)
                    Text(
                        text = monthDate.format(monthFormatter),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                    )
                    MonthGrid(
                        focusedDate = monthDate,
                        tagColor = selectedTag.color,
                        countsByDate = countsByDate,
                        onDayClick = onDayClick,
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun YearContentPreview() {
    TagDayTheme {
        YearContent(
            focusedDate = LocalDate.of(2026, 7, 22),
            allTags = listOf(
                Tag(id = 1, name = "walk", type = TagType.SIMPLE, color = 0xFF81C784.toInt(), createdAt = 0),
            ),
            selectedTagId = 1,
            countsByDate = mapOf(
                LocalDate.of(2026, 1, 3).toEpochDay().toInt() to 1,
                LocalDate.of(2026, 7, 10).toEpochDay().toInt() to 2,
                LocalDate.of(2026, 12, 15).toEpochDay().toInt() to 4,
            ),
            onTagPicked = {},
            onDayClick = {},
        )
    }
}
