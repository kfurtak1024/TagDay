package dev.krfu.tagday.ui.calendar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.krfu.tagday.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Names the period on screen and steps it: `‹  July 2026  ›`. Shown at Week/Month/Year, which
 * otherwise say nowhere *which* week, month or year they're showing — a Month grid is bare day
 * numbers and a Year grid has the year only in a per-tile content description. Day is left
 * alone for now; its header card already carries the date (ADR-033).
 *
 * The arrows duplicate the horizontal swipe (ADR-012) rather than replacing it, the same way
 * `ZoomLevelPicker` duplicates the vertical one (ADR-014): the gesture stays the fast path, the
 * buttons make it discoverable and give the time axis a non-gesture route for anyone who can't
 * comfortably swipe. Tapping the label opens a date picker for jumps a swipe would never be
 * worth.
 */
@Composable
fun PeriodNavigationRow(
    focusedDate: LocalDate,
    zoomLevel: ZoomLevel,
    onStepTime: (Int) -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    val locale = LocalLocale.current.platformLocale
    val label = remember(focusedDate, zoomLevel, locale) {
        CalendarPeriodLabels.format(focusedDate, zoomLevel, locale)
    }
    val unit = zoomLevel.label()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onStepTime(-1) }) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.calendar_previous_period, unit),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // The whole strip between the arrows is the tap target, not just the glyphs.
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(MaterialTheme.shapes.small)
                .clickable { showDatePicker = true }
                .wrapContentHeight(Alignment.CenterVertically),
        )
        IconButton(onClick = { onStepTime(1) }) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.calendar_next_period, unit),
            )
        }
    }

    if (showDatePicker) {
        PeriodDatePickerDialog(
            focusedDate = focusedDate,
            onDateSelected = onDateSelected,
            onDismiss = { showDatePicker = false },
        )
    }
}

@Composable
private fun PeriodDatePickerDialog(
    focusedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    // DatePicker deals in UTC-midnight millis regardless of the device's zone, so both
    // conversions pin to UTC — going through the local zone would shift the date by a day for
    // anyone west of Greenwich.
    val state = rememberDatePickerState(
        initialSelectedDateMillis = focusedDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { millis ->
                        onDateSelected(
                            Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate(),
                        )
                    }
                    onDismiss()
                },
            ) {
                Text(stringResource(R.string.calendar_date_picker_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.calendar_date_picker_cancel))
            }
        },
    ) {
        DatePicker(state = state)
    }
}
