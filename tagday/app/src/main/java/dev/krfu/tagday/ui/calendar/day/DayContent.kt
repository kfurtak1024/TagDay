package dev.krfu.tagday.ui.calendar.day

import androidx.annotation.ColorInt
import androidx.annotation.StringRes
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.krfu.tagday.R
import dev.krfu.tagday.data.local.entity.TagInstance
import dev.krfu.tagday.data.local.entity.TagType
import dev.krfu.tagday.data.model.TagDisplayGroup
import dev.krfu.tagday.ui.theme.TagDayTheme
import dev.krfu.tagday.ui.theme.TemporalColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val dayOfMonthFormatter = DateTimeFormatter.ofPattern("d")
private val weekdayFormatter = DateTimeFormatter.ofPattern("EEEE")
private val monthFormatter = DateTimeFormatter.ofPattern("MMMM")
private val yearFormatter = DateTimeFormatter.ofPattern("yyyy")
private val headerContentDescriptionFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")

/**
 * Side of the capsule's ✕ tap area, which also sets the capsule's height. Its touch bounds
 * are exactly this — see the note in [TagGroupCapsule] and ADR-027 — so it's a real minimum,
 * not a visual one: shrink it and the ✕ gets correspondingly harder to hit.
 */
private val CAPSULE_REMOVE_TARGET_SIZE = 32.dp

/**
 * The Day zoom level's body — header card + tag capsules. Owns no `Scaffold`/top/bottom
 * bar of its own; those are shared across zoom levels by `CalendarContent`.
 */
@Composable
fun DayContent(
    date: LocalDate,
    groups: List<TagDisplayGroup>,
    onGroupClick: (TagDisplayGroup) -> Unit,
    onGroupQuickRemove: (TagDisplayGroup) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        CalendarHeaderCard(date = date, modifier = Modifier.padding(16.dp))
        if (groups.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = stringResource(R.string.day_empty_message))
            }
        } else {
            FlowRow(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                groups.forEach { group ->
                    TagGroupCapsule(
                        group = group,
                        onCapsuleClick = { onGroupClick(group) },
                        onRemoveClick = { onGroupQuickRemove(group) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CalendarHeaderCard(date: LocalDate, modifier: Modifier = Modifier) {
    val (temporalLabelRes, temporalColor) = temporalLabelFor(date)
    val temporalLabelText = stringResource(temporalLabelRes)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = "${date.format(headerContentDescriptionFormatter)}, $temporalLabelText"
            },
        shape = RoundedCornerShape(16.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primary)
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "${date.format(monthFormatter).uppercase()} ${date.format(yearFormatter)}",
                style = MaterialTheme.typography.labelLarge,
                letterSpacing = 2.sp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = date.format(dayOfMonthFormatter),
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = date.format(weekdayFormatter),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            TemporalLabel(
                textRes = temporalLabelRes,
                color = temporalColor,
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }
    }
}

@Composable
private fun TemporalLabel(@StringRes textRes: Int, @ColorInt color: Int, modifier: Modifier = Modifier) {
    val backgroundColor = Color(color)
    val contentColor = contentColorOn(backgroundColor)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(backgroundColor)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(
            text = stringResource(textRes),
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
        )
    }
}

/**
 * Black or white, whichever stays legible on [background]. Tag colors (and the temporal
 * labels') are palette/user-chosen rather than theme colors, so there's no `onColor` pair
 * to reach for.
 */
private fun contentColorOn(background: Color): Color =
    if (background.luminance() > 0.5f) Color.Black else Color.White

/** Past/today/future relative to the device clock — see `UI_UX.md` § Day zoom and ADR-017. */
private fun temporalLabelFor(date: LocalDate): Pair<Int, Int> {
    val today = LocalDate.now()
    return when {
        date.isBefore(today) -> R.string.day_temporal_label_past to TemporalColors.PAST
        date.isAfter(today) -> R.string.day_temporal_label_future to TemporalColors.FUTURE
        else -> R.string.day_temporal_label_today to TemporalColors.TODAY
    }
}

@Composable
private fun TagGroupCapsule(
    group: TagDisplayGroup,
    onCapsuleClick: () -> Unit,
    onRemoveClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backgroundColor = Color(group.color)
    val contentColor = contentColorOn(backgroundColor)

    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(backgroundColor)
            // Every type opens the instance sheet now — Simple's panel edits how many times
            // the tag applies to the day, which is the only state it has (ADR-031, superseding
            // ADR-018). Removing it from the day outright is still the "x" below.
            .clickable(onClick = onCapsuleClick)
            .padding(start = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = group.summary,
            color = contentColor,
            style = MaterialTheme.typography.bodyMedium,
        )
        // Exact touch bounds for the ✕ — and *only* for the ✕. Compose otherwise inflates
        // any pointer-input node under 48dp evenly around itself, and on a capsule this
        // short that inflation reached back over the tag text: on Simple, whose body
        // `clickable` is disabled (ADR-018) and so didn't compete for the overlap, tapping
        // the tag removed it. Sizing the ✕ up to 48dp fixed that but made every capsule
        // 48dp tall, hence this instead — the ✕ is now exactly as tappable as it looks,
        // the full 32dp right end of the capsule. Hit-testing reads
        // `LayoutNode.viewConfiguration`, which comes from this CompositionLocal (see
        // LayoutNode.compositionLocalMap's setter), so scoping the provider to this Box
        // leaves the capsule body's own forgiving target untouched. ADR-026, ADR-027.
        val viewConfiguration = LocalViewConfiguration.current
        val exactTouchBounds = remember(viewConfiguration) {
            object : ViewConfiguration by viewConfiguration {
                override val minimumTouchTargetSize = DpSize.Zero
            }
        }
        CompositionLocalProvider(LocalViewConfiguration provides exactTouchBounds) {
            Box(
                modifier = Modifier
                    .size(CAPSULE_REMOVE_TARGET_SIZE)
                    .clip(CircleShape)
                    .clickable(onClick = onRemoveClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(
                        R.string.day_capsule_remove_content_description,
                        group.tagName,
                    ),
                    tint = contentColor,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DayContentPreview() {
    TagDayTheme {
        DayContent(
            date = LocalDate.of(2026, 7, 25),
            groups = listOf(
                TagDisplayGroup(
                    tagId = 1,
                    tagName = "walk",
                    color = 0xFF81C784.toInt(),
                    type = TagType.SIMPLE,
                    instances = listOf(
                        TagInstance(id = 1, tagId = 1, date = 0, createdAt = 0),
                        TagInstance(id = 2, tagId = 1, date = 0, createdAt = 1),
                    ),
                    summary = "walk (2)",
                ),
                TagDisplayGroup(
                    tagId = 2,
                    tagName = "reading",
                    color = 0xFF4FC3F7.toInt(),
                    type = TagType.SIMPLE,
                    instances = listOf(
                        TagInstance(id = 3, tagId = 2, date = 0, createdAt = 0),
                    ),
                    summary = "reading",
                ),
            ),
            onGroupClick = {},
            onGroupQuickRemove = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DayContentEmptyPreview() {
    TagDayTheme {
        DayContent(
            date = LocalDate.of(2026, 7, 25),
            groups = emptyList(),
            onGroupClick = {},
            onGroupQuickRemove = {},
        )
    }
}

/** Dates relative to `now()` so all three temporal-label states render regardless of when this preview is opened. */
@Preview(showBackground = true)
@Composable
private fun CalendarHeaderCardTemporalStatesPreview() {
    TagDayTheme {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(16.dp)) {
            CalendarHeaderCard(date = LocalDate.now().minusDays(5))
            CalendarHeaderCard(date = LocalDate.now())
            CalendarHeaderCard(date = LocalDate.now().plusDays(5))
        }
    }
}
