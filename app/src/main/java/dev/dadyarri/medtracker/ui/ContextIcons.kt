package dev.dadyarri.medtracker.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContextChoice(icon: Int, label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    TooltipBox(positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(label) } }, state = rememberTooltipState()) {
        FilledIconToggleButton(checked = selected, enabled = enabled, onCheckedChange = { onClick() },
            modifier = Modifier.size(56.dp)) {
            Icon(painterResource(icon), label, Modifier.size(28.dp))
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContextBadge(icon: Int, label: String) {
    TooltipBox(positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(label) } }, state = rememberTooltipState()) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer) {
            Icon(painterResource(icon), label, Modifier.padding(10.dp).size(24.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}
