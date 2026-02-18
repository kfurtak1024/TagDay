@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package dev.krfu.tagday.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun TagDayApp(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showSettings by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "TagDay",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                actions = {
                    val isGlobalTags = uiState.selectedTab == TabScreen.GlobalTags
                    IconButton(
                        onClick = {
                            if (isGlobalTags) {
                                viewModel.showDay()
                            } else {
                                viewModel.selectTab(TabScreen.GlobalTags)
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (isGlobalTags) {
                                Icons.AutoMirrored.Filled.ArrowBack
                            } else {
                                Icons.Default.LocalOffer
                            },
                            contentDescription = if (isGlobalTags) {
                                "Back to calendar"
                            } else {
                                "Open global tags"
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
                        onSelectToday = viewModel::selectToday,
                        onOpenWeek = viewModel::showWeek,
                        onDeleteTagFromSelectedDay = viewModel::removeTagFromSelectedDay
                    )

                    TabScreen.Week -> WeekScreen(
                        uiState = uiState,
                        onOpenDay = viewModel::openDay,
                        onSelectPreviousWeek = viewModel::selectPreviousWeek,
                        onSelectNextWeek = viewModel::selectNextWeek,
                        onSwipeUp = viewModel::showMonth,
                        onSwipeDown = viewModel::showDay
                    )

                    TabScreen.Month -> MonthScreen(
                        uiState = uiState,
                        onOpenDay = viewModel::openDay,
                        onSelectPreviousMonth = viewModel::selectPreviousMonth,
                        onSelectNextMonth = viewModel::selectNextMonth,
                        onSwipeUp = viewModel::showYear,
                        onSwipeDown = viewModel::showWeek
                    )

                    TabScreen.Year -> YearScreen(
                        uiState = uiState,
                        onSelectPreviousYear = viewModel::selectPreviousYear,
                        onSelectNextYear = viewModel::selectNextYear,
                        onSwipeDown = viewModel::showMonth
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
                androidx.compose.foundation.layout.Row(
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
