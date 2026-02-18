package dev.krfu.tagday.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@Composable
fun WeekScreen(
    uiState: MainUiState,
    onOpenDay: (LocalDate) -> Unit,
    onSelectPreviousWeek: () -> Unit,
    onSelectNextWeek: () -> Unit,
    onSwipeUp: () -> Unit,
    onSwipeDown: () -> Unit
) {
    val weekRange = remember(uiState.weekSummary) {
        val first = uiState.weekSummary.firstOrNull()?.date
        val last = uiState.weekSummary.lastOrNull()?.date
        if (first != null && last != null) {
            "${first.format(DateTimeFormatter.ofPattern("MMM d"))} - ${last.format(DateTimeFormatter.ofPattern("MMM d, uuuu"))}"
        } else {
            ""
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("week_screen")
            .navigationSwipe(
                onSelectPreviousWeek, onSelectNextWeek, onSwipeUp, onSwipeDown,
                ignoreConsumedByChild = false,
                onSwipeLeft = onSelectPreviousWeek,
                onSwipeRight = onSelectNextWeek,
                onSwipeUp = onSwipeUp,
                onSwipeDown = onSwipeDown
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text("Week", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (weekRange.isNotBlank()) {
            Text(
                text = weekRange,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(uiState.weekSummary, key = { it.date.toEpochDay() }) { summary ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenDay(summary.date) },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = summary.date.format(DateTimeFormatter.ofPattern("EEEE")),
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = summary.date.format(DateTimeFormatter.ofPattern("MMM d")),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        SummaryPreview(topLabels = summary.topLabels, extraCount = summary.extraCount)
                    }
                }
            }
        }
    }
}

@Composable
fun MonthScreen(
    uiState: MainUiState,
    onOpenDay: (LocalDate) -> Unit,
    onSelectPreviousMonth: () -> Unit,
    onSelectNextMonth: () -> Unit,
    onSwipeUp: () -> Unit,
    onSwipeDown: () -> Unit
) {
    val month = remember(uiState.selectedDate) { YearMonth.from(uiState.selectedDate) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("month_screen")
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("month_swipe_area")
                .navigationSwipe(
                    onSelectPreviousMonth, onSelectNextMonth, onSwipeUp, onSwipeDown,
                    ignoreConsumedByChild = false,
                    onSwipeLeft = onSelectPreviousMonth,
                    onSwipeRight = onSelectNextMonth,
                    onSwipeUp = onSwipeUp,
                    onSwipeDown = onSwipeDown
                )
        ) {
            Text(
                text = month.format(DateTimeFormatter.ofPattern("MMMM uuuu")),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Summary view",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(uiState.monthSummary, key = { it.date.toEpochDay() }) { summary ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f))
                        .clickable { onOpenDay(summary.date) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = summary.date.dayOfMonth.toString(),
                        fontWeight = if (summary.date == uiState.selectedDate) {
                            FontWeight.Bold
                        } else {
                            FontWeight.Normal
                        }
                    )
                    SummaryPreview(topLabels = summary.topLabels, extraCount = summary.extraCount)
                }
            }
        }
    }
}

@Composable
fun YearScreen(
    uiState: MainUiState,
    onSelectPreviousYear: () -> Unit,
    onSelectNextYear: () -> Unit,
    onSwipeDown: () -> Unit
) {
    val year = uiState.selectedDate.year

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("year_screen")
            .navigationSwipe(
                onSelectPreviousYear, onSelectNextYear, onSwipeDown,
                ignoreConsumedByChild = false,
                onSwipeLeft = onSelectPreviousYear,
                onSwipeRight = onSelectNextYear,
                onSwipeDown = onSwipeDown
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = year.toString(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Year summary",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(uiState.yearSummary, key = { it.month }) { summary ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = LocalDate.of(year, summary.month, 1)
                                .format(DateTimeFormatter.ofPattern("MMMM")),
                            fontWeight = FontWeight.SemiBold
                        )
                        SummaryPreview(topLabels = summary.topLabels, extraCount = summary.extraCount)
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryPreview(
    topLabels: List<String>,
    extraCount: Int
) {
    Column(
        horizontalAlignment = Alignment.End
    ) {
        if (topLabels.isEmpty()) {
            Text(
                text = "No tags",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return
        }
        topLabels.forEach { label ->
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (extraCount > 0) {
            Text(
                text = "+$extraCount more",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
