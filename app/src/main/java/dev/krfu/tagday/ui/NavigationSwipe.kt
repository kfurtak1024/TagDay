package dev.krfu.tagday.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import kotlin.math.abs

fun Modifier.navigationSwipe(
    vararg keys: Any?,
    thresholdPx: Float = 100f,
    ignoreConsumedByChild: Boolean = true,
    onSwipeLeft: (() -> Unit)? = null,
    onSwipeRight: (() -> Unit)? = null,
    onSwipeUp: (() -> Unit)? = null,
    onSwipeDown: (() -> Unit)? = null
): Modifier {
    return pointerInput(*keys) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val pointerId = down.id
            val startPosition = down.position
            var endPosition = startPosition
            var pointerUp = false
            var gestureConsumedByChild = false

            while (!pointerUp) {
                val event = awaitPointerEvent(pass = PointerEventPass.Final)
                if (event.changes.any { it.positionChanged() && it.isConsumed }) {
                    gestureConsumedByChild = true
                }
                val trackedChange = event.changes.firstOrNull { it.id == pointerId }
                    ?: event.changes.firstOrNull()
                    ?: continue
                endPosition = trackedChange.position
                pointerUp = !trackedChange.pressed || event.changes.none { it.pressed }
            }

            if (ignoreConsumedByChild && gestureConsumedByChild) return@awaitEachGesture

            val totalX = endPosition.x - startPosition.x
            val totalY = endPosition.y - startPosition.y

            if (abs(totalX) > abs(totalY) && abs(totalX) > thresholdPx) {
                if (totalX < 0f) {
                    onSwipeLeft?.invoke()
                } else {
                    onSwipeRight?.invoke()
                }
                return@awaitEachGesture
            }

            if (abs(totalY) > thresholdPx) {
                if (totalY < 0f) {
                    onSwipeUp?.invoke()
                } else {
                    onSwipeDown?.invoke()
                }
            }
        }
    }
}
