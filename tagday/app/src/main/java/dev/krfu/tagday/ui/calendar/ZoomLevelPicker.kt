package dev.krfu.tagday.ui.calendar

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Top-bar control for jumping directly to a zoom level, alongside (not instead of) the
 * vertical swipe gesture — see ADR-012 amendments and ADR-014 in `DECISIONS.md`.
 */
@Composable
fun ZoomLevelPicker(
    zoomLevel: ZoomLevel,
    onZoomLevelPicked: (ZoomLevel) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    TextButton(
        onClick = { expanded = true },
        modifier = modifier,
    ) {
        Text(zoomLevel.label())
        Icon(
            imageVector = Icons.Default.ArrowDropDown,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        ZoomLevel.entries.forEach { entry ->
            DropdownMenuItem(
                text = { Text(entry.label()) },
                leadingIcon = {
                    if (entry == zoomLevel) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = null)
                    } else {
                        Spacer(Modifier.size(24.dp))
                    }
                },
                onClick = {
                    onZoomLevelPicked(entry)
                    expanded = false
                },
            )
        }
    }
}
