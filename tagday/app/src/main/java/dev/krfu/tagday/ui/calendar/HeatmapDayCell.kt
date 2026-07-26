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
import androidx.compose.ui.unit.dp

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
    count: Int,
    tagColor: Int,
    isToday: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val alpha = alphaForCount(count)
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
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = dayOfMonth.toString(), style = MaterialTheme.typography.labelSmall)
    }
}
