package dev.krfu.tagday.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.krfu.tagday.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val cellContentDescriptionFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM")

/** Shading bucket for the heatmap — instance count only, same rule for every tag type. */
internal fun alphaForCount(count: Int): Float = when {
    count <= 0 -> 0f
    count == 1 -> 0.3f
    count == 2 -> 0.6f
    else -> 1f
}

@Composable
fun HeatmapDayCell(
    dayOfMonth: Int,
    date: LocalDate,
    count: Int,
    tagColor: Int,
    isToday: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val alpha = alphaForCount(count)
    // The count is carried by background alpha alone, which reaches neither a screen reader nor
    // anyone who can't distinguish the shades — only the day number was exposed (BACKLOG F14).
    val dateLabel = date.format(cellContentDescriptionFormatter)
    val cellContentDescription = if (count > 0) {
        pluralStringResource(R.plurals.calendar_heatmap_cell_content_description, count, count, dateLabel)
    } else {
        stringResource(R.string.calendar_heatmap_cell_empty_content_description, dateLabel)
    }
    val backgroundColor = if (alpha == 0f) Color.Transparent else Color(tagColor).copy(alpha = alpha)
    val shape = RoundedCornerShape(4.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(shape)
            .background(backgroundColor)
            .let { m ->
                // A ring rather than a fill, since the background already carries the
                // heat-shading signal — see ADR-017.
                if (isToday) m.border(2.dp, MaterialTheme.colorScheme.primary, shape) else m
            }
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = cellContentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = dayOfMonth.toString(), style = MaterialTheme.typography.labelSmall)
    }
}
