package dev.krfu.tagday.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsContent(onNavigateBack = onNavigateBack, modifier = modifier)
}
