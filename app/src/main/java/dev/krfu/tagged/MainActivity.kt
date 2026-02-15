@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package dev.krfu.tagged

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.krfu.tagged.ui.MainViewModel
import dev.krfu.tagged.ui.TabScreen
import dev.krfu.tagged.ui.theme.TaggedTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TaggedTheme {
                TaggedApp(viewModel)
            }
        }
    }
}

@Composable
private fun TaggedApp(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showSettings by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Tagged",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                actions = {
                    IconButton(
                        onClick = {
                            val destination = if (uiState.selectedTab == TabScreen.Day) {
                                TabScreen.GlobalTags
                            } else {
                                TabScreen.Day
                            }
                            viewModel.selectTab(destination)
                        }
                    ) {
                        Icon(
                            imageVector = if (uiState.selectedTab == TabScreen.Day) {
                                Icons.AutoMirrored.Filled.Label
                            } else {
                                Icons.AutoMirrored.Filled.ArrowBack
                            },
                            contentDescription = if (uiState.selectedTab == TabScreen.Day) {
                                "Open global tags"
                            } else {
                                "Back to day"
                            }
                        )
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Open settings"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when (uiState.selectedTab) {
                    TabScreen.Day -> DayScreen(
                        uiState = uiState,
                        onInputChange = viewModel::updateTagInput,
                        onAddClick = viewModel::addTagForSelectedDay,
                        onSelectPreviousDay = viewModel::selectPreviousDay,
                        onSelectNextDay = viewModel::selectNextDay,
                        onSelectToday = viewModel::selectToday
                    )

                    TabScreen.GlobalTags -> GlobalTagsScreen(
                        uiState = uiState,
                        onUpdateTag = viewModel::updateGlobalTag,
                        onDeleteTag = viewModel::deleteGlobalTag
                    )
                }
            }
        }
    }

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("Settings") },
            text = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Show hidden tags")
                    Switch(
                        checked = uiState.showHiddenTags,
                        onCheckedChange = viewModel::setShowHiddenTags
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettings = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun DayScreen(
    uiState: dev.krfu.tagged.ui.MainUiState,
    onInputChange: (String) -> Unit,
    onAddClick: () -> Unit,
    onSelectPreviousDay: () -> Unit,
    onSelectNextDay: () -> Unit,
    onSelectToday: () -> Unit
) {
    val monthYearFormatter = remember { DateTimeFormatter.ofPattern("MMMM uuuu") }
    val weekdayFormatter = remember { DateTimeFormatter.ofPattern("EEEE") }
    val today = LocalDate.now()
    val isToday = uiState.selectedDate == today
    val isFuture = uiState.selectedDate.isAfter(today)
    val isPast = uiState.selectedDate.isBefore(today)
    val swipeThresholdPx = 100f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(onSelectPreviousDay, onSelectNextDay, onSelectToday) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val pointerId = down.id
                    val startPosition = down.position
                    var endPosition = startPosition
                    var pointerUp = false

                    while (!pointerUp) {
                        val event = awaitPointerEvent()
                        val trackedChange = event.changes.firstOrNull { it.id == pointerId }
                            ?: event.changes.firstOrNull()
                            ?: continue

                        endPosition = trackedChange.position
                        pointerUp = !trackedChange.pressed || event.changes.none { it.pressed }
                    }

                    val totalX = endPosition.x - startPosition.x
                    val totalY = endPosition.y - startPosition.y

                    if (totalY < -swipeThresholdPx && abs(totalY) > abs(totalX)) {
                        onSelectToday()
                    } else if (abs(totalX) > abs(totalY) && abs(totalX) > swipeThresholdPx) {
                        if (totalX < 0f) {
                            onSelectPreviousDay()
                        } else {
                            onSelectNextDay()
                        }
                    }
                }
            }
    ) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    )
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        if (isToday || isFuture || isPast) {
                            val ribbonText = when {
                                isToday -> "Today"
                                isFuture -> "Future"
                                else -> "Past"
                            }
                            val ribbonColor = when {
                                isToday -> Color(0xFF16A34A)
                                isFuture -> Color(0xFF7C3AED)
                                else -> Color(0xFFD4A017)
                            }
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .offset(x = (-42).dp, y = 6.dp)
                                    .rotate(-45f)
                                    .background(color = ribbonColor)
                                    .width(140.dp)
                                    .padding(vertical = 5.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = ribbonText,
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    style = MaterialTheme.typography.labelLarge,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .offset(x = (-2).dp)
                                )
                            }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = uiState.selectedDate.format(monthYearFormatter),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = uiState.selectedDate.dayOfMonth.toString(),
                                style = MaterialTheme.typography.displayLarge,
                                fontWeight = FontWeight.ExtraBold,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = uiState.selectedDate.format(weekdayFormatter),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item {
                if (uiState.daySummary.isEmpty()) {
                    Text("No tags for this day")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        uiState.daySummary.chunked(2).forEach { rowItems ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowItems.forEach { summary ->
                                    SummaryChip(summary)
                                }
                            }
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            uiState.inputError?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = uiState.tagInput,
                    onValueChange = onInputChange,
                    label = { Text("Add tag") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Button(onClick = onAddClick) {
                    Text("Add")
                }
            }
        }
    }
}

@Composable
private fun SummaryChip(summary: dev.krfu.tagged.ui.TagSummaryUi) {
    val bgColor = Color(summary.colorArgb).copy(alpha = 0.18f)
    val borderColor = Color(summary.colorArgb)

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(summary.label)
        summary.rating?.let { rating ->
            RatingStars(rating)
            if (summary.ratingCount > 1) {
                Text("(${summary.ratingCount})")
            }
        }
    }
}

@Composable
private fun RatingStars(rating: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
        repeat(5) { index ->
            val color = if (index < rating) Color(0xFFF7B500) else Color(0xFF9CA3AF)
            Text(
                text = if (index < rating) "★" else "☆",
                color = color
            )
        }
    }
}

@Composable
private fun GlobalTagsScreen(
    uiState: dev.krfu.tagged.ui.MainUiState,
    onUpdateTag: (currentName: String, newName: String, colorArgb: Int, hidden: Boolean) -> Unit,
    onDeleteTag: (String) -> Unit
) {
    var editingTag by remember { mutableStateOf<dev.krfu.tagged.ui.GlobalTagUi?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
    ) {
        if (uiState.globalTags.isEmpty()) {
            item {
                Text("No global tags yet. Add a day tag first.")
            }
        }

        items(uiState.globalTags, key = { it.name }) { tag ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { editingTag = tag },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(tag.name, fontWeight = FontWeight.SemiBold)
                        Text(if (tag.hidden) "Hidden" else "Visible")
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(Color(tag.colorArgb))
                                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        )
                        Text("Edit")
                    }
                }
            }
        }
    }

    editingTag?.let { tag ->
        EditGlobalTagDialog(
            tag = tag,
            palette = uiState.colorPalette,
            onDismiss = { editingTag = null },
            onSave = { newName, color, hidden ->
                onUpdateTag(tag.name, newName, color, hidden)
                editingTag = null
            },
            onDelete = {
                onDeleteTag(tag.name)
                editingTag = null
            }
        )
    }
}

@Composable
private fun EditGlobalTagDialog(
    tag: dev.krfu.tagged.ui.GlobalTagUi,
    palette: List<Int>,
    onDismiss: () -> Unit,
    onSave: (newName: String, colorArgb: Int, hidden: Boolean) -> Unit,
    onDelete: () -> Unit
) {
    var name by remember(tag.name) { mutableStateOf(tag.name) }
    var hidden by remember(tag.hidden) { mutableStateOf(tag.hidden) }
    var selectedColor by remember(tag.colorArgb) { mutableStateOf(tag.colorArgb) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit global tag") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Hidden")
                    Switch(checked = hidden, onCheckedChange = { hidden = it })
                }

                Text("Color")
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    palette.chunked(6).forEach { rowColors ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowColors.forEach { colorArgb ->
                                val isSelected = colorArgb == selectedColor
                                Box(
                                    modifier = Modifier
                                        .size(if (isSelected) 30.dp else 26.dp)
                                        .clip(CircleShape)
                                        .background(Color(colorArgb))
                                        .border(
                                            width = if (isSelected) 3.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                                            shape = CircleShape
                                        )
                                        .clickable { selectedColor = colorArgb }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.trim(), selectedColor, hidden) }) {
                Text("Save")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDelete) {
                    Text("Delete")
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}
