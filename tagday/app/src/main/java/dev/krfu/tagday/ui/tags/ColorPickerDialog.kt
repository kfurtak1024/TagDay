package dev.krfu.tagday.ui.tags

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.krfu.tagday.R
import dev.krfu.tagday.ui.theme.TagPalette
import kotlin.math.roundToInt

private val HUE_GRADIENT_COLORS = (0..360 step 60).map { hue ->
    Color(AndroidColor.HSVToColor(floatArrayOf(hue.toFloat(), 1f, 1f)))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorPickerDialog(
    initialColor: Int,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit,
) {
    val initialHsv = remember {
        FloatArray(3).also { AndroidColor.colorToHSV(initialColor, it) }
    }
    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember { mutableFloatStateOf(initialHsv[1]) }
    var value by remember { mutableFloatStateOf(initialHsv[2]) }
    var selectedColor by remember { mutableIntStateOf(initialColor) }

    fun updateFromHsv(newHue: Float, newSaturation: Float, newValue: Float) {
        hue = newHue
        saturation = newSaturation
        value = newValue
        selectedColor = AndroidColor.HSVToColor(floatArrayOf(newHue, newSaturation, newValue))
    }

    fun selectPaletteColor(color: Int) {
        selectedColor = color
        val hsv = FloatArray(3)
        AndroidColor.colorToHSV(color, hsv)
        hue = hsv[0]
        saturation = hsv[1]
        value = hsv[2]
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tags_color_dialog_title)) },
        text = {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(selectedColor)),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 12.dp),
                ) {
                    TagPalette.colors.forEach { paletteColor ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(paletteColor))
                                .border(
                                    width = if (paletteColor == selectedColor) 3.dp else 0.dp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    shape = CircleShape,
                                )
                                .clickable { selectPaletteColor(paletteColor) },
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.tags_color_dialog_custom_label),
                    style = MaterialTheme.typography.labelMedium,
                )
                SaturationValueBox(
                    hue = hue,
                    saturation = saturation,
                    value = value,
                    onSaturationValueChange = { s, v -> updateFromHsv(hue, s, v) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .padding(vertical = 12.dp),
                )
                Slider(
                    value = hue,
                    onValueChange = { updateFromHsv(it, saturation, value) },
                    valueRange = 0f..360f,
                    track = {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Brush.horizontalGradient(HUE_GRADIENT_COLORS)),
                        )
                    },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(selectedColor); onDismiss() }) {
                Text(stringResource(R.string.tags_color_dialog_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.tags_color_dialog_cancel))
            }
        },
    )
}

@Composable
private fun SaturationValueBox(
    hue: Float,
    saturation: Float,
    value: Float,
    onSaturationValueChange: (saturation: Float, value: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    val pureHueColor = Color(AndroidColor.HSVToColor(floatArrayOf(hue, 1f, 1f)))

    fun updateFromOffset(offset: Offset) {
        if (boxSize.width == 0 || boxSize.height == 0) return
        val s = (offset.x / boxSize.width).coerceIn(0f, 1f)
        val v = 1f - (offset.y / boxSize.height).coerceIn(0f, 1f)
        onSaturationValueChange(s, v)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Brush.horizontalGradient(listOf(Color.White, pureHueColor)))
            .onSizeChanged { boxSize = it }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    updateFromOffset(down.position)
                    drag(down.id) { change ->
                        change.consume()
                        updateFromOffset(change.position)
                    }
                }
            },
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black))),
        )
        if (boxSize.width > 0 && boxSize.height > 0) {
            val indicatorSize = 20.dp
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = (saturation * boxSize.width).roundToInt() - indicatorSize.roundToPx() / 2,
                            y = ((1f - value) * boxSize.height).roundToInt() - indicatorSize.roundToPx() / 2,
                        )
                    }
                    .size(indicatorSize)
                    .border(width = 2.dp, color = Color.White, shape = CircleShape)
                    .border(width = 3.dp, color = Color.Black.copy(alpha = 0.3f), shape = CircleShape),
            )
        }
    }
}
