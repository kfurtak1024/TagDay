package dev.krfu.tagday.ui.components

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp

private val ThumbWidth = 4.dp

/**
 * A thumb drawn down the right edge of a scrollable area, shown only while the content
 * actually overflows. Hand-drawn rather than pulled from a library: it reads
 * `ScrollableState.scrollIndicatorState`, the `scrollOffset`/`contentSize`/`viewportSize`
 * trio `androidx.compose.foundation` ships for exactly this — same "self-contained UI piece"
 * precedent as ADR-011's color picker. See ADR-021 (original) and ADR-030 (extraction).
 *
 * Meant to be laid out as an **overlay sibling** of the scrollable, sized by the caller —
 * `Box { LazyColumn(...); VerticalScrollbar(state, Modifier.matchParentSize()) }`. Two
 * reasons it can't simply be chained onto the scrollable node itself:
 *  - Chaining puts the thumb's draw call inside the part of the modifier chain that the
 *    scroll offsets, so it scrolls away with the content instead of staying put as a
 *    viewport overlay — it ends up clipped against the top edge as you scroll down, which
 *    reads as "shrinks and barely moves".
 *  - `matchParentSize` (rather than `fillMaxHeight`) is what keeps it from influencing the
 *    parent's measurement: a fill-height child is measured against the Box's *incoming* max
 *    height, which would stretch a content-sized Box to it.
 *
 * The node is transparent apart from the thumb and has no pointer input, so it neither
 * affects layout nor intercepts touches from the list underneath. It's an indicator, not a
 * draggable control.
 */
@Composable
fun VerticalScrollbar(state: ScrollableState, modifier: Modifier = Modifier) {
    val thumbColor = MaterialTheme.colorScheme.outline
    Box(
        modifier = modifier.drawBehind {
            // Read reactively, so the thumb redraws on every scroll frame. For lazy layouts
            // these are estimates derived from the average visible item size, which is exact
            // for a uniform-height list and good enough for a thumb either way.
            val indicator = state.scrollIndicatorState ?: return@drawBehind
            val viewportSize = indicator.viewportSize
            val contentSize = indicator.contentSize
            val scrollOffset = indicator.scrollOffset
            if (
                viewportSize <= 0 ||
                contentSize == Int.MAX_VALUE ||
                viewportSize == Int.MAX_VALUE ||
                scrollOffset == Int.MAX_VALUE ||
                // Nothing to scroll: no scrollbar. This is the "only when needed" part.
                contentSize <= viewportSize
            ) {
                return@drawBehind
            }
            val thumbWidth = ThumbWidth.toPx()
            val trackHeight = size.height
            val thumbHeight = (trackHeight * viewportSize / contentSize.toFloat())
                .coerceAtLeast(thumbWidth * 4)
            val maxScroll = (contentSize - viewportSize).toFloat()
            val startY = (trackHeight - thumbHeight) *
                (scrollOffset / maxScroll).coerceIn(0f, 1f)
            drawRoundRect(
                color = thumbColor,
                topLeft = Offset(size.width - thumbWidth, startY),
                size = Size(thumbWidth, thumbHeight),
                cornerRadius = CornerRadius(thumbWidth / 2),
            )
        },
    )
}
