package dev.dadyarri.medtracker.medications

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.dadyarri.medtracker.R
import kotlin.math.roundToInt

@Composable
internal fun SwipeableMedicationCard(medication: Medication, enabled: Boolean,
                                    onClick: () -> Unit, onEdit: () -> Unit, onArchive: () -> Unit,
                                    onRegimens: (() -> Unit)? = null) {
    val width = with(LocalDensity.current) { (if (onRegimens != null) 168.dp else 112.dp).toPx() }
    var revealed by rememberSaveable(medication.id, medication.archived) { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var target by remember(medication.id, medication.archived, width) {
        mutableFloatStateOf(if (revealed) -width else 0f)
    }
    val offset by animateFloatAsState(target, if (dragging) snap() else spring(), label = "Medication actions")
    val archiveLabel = if (medication.archived) "Вернуть из архива" else "В архив"
    fun edit() { revealed = false; target = 0f; onEdit() }
    fun archive() { revealed = false; target = 0f; onArchive() }
    fun regimens() { revealed = false; target = 0f; onRegimens?.invoke() }
    Box(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraLarge)) {
        if (revealed || dragging || offset < -1f) {
            Row(Modifier.align(Alignment.CenterEnd).width((if (onRegimens != null) 168.dp else 112.dp)), horizontalArrangement = Arrangement.SpaceEvenly) {
                MedicationCardAction(R.drawable.ic_edit, "Изменить препарат", enabled, ::edit)
                if (onRegimens != null) MedicationCardAction(R.drawable.ic_calendar, "Назначения", enabled, ::regimens)
                MedicationCardAction(if (medication.archived) R.drawable.ic_unarchive else R.drawable.ic_archive,
                    archiveLabel, enabled, ::archive)
            }
        }
        Box(Modifier.offset { IntOffset(offset.roundToInt(), 0) }
            .draggable(
                state = rememberDraggableState { delta -> target = (target + delta).coerceIn(-width, 0f) },
                orientation = Orientation.Horizontal, enabled = enabled,
                onDragStarted = { dragging = true },
                onDragStopped = { velocity ->
                    revealed = when {
                        velocity < -500f -> true
                        velocity > 500f -> false
                        else -> target < -width / 2
                    }
                    dragging = false
                    target = if (revealed) -width else 0f
                })
            .semantics {
                if (enabled) customActions = listOf(
                    CustomAccessibilityAction("Изменить препарат") { edit(); true },
                    CustomAccessibilityAction(archiveLabel) { archive(); true }) +
                    if (onRegimens != null) listOf(CustomAccessibilityAction("Назначения") { regimens(); true }) else emptyList()
            }) {
            MedicationCard(medication, onClick = {
                if (revealed) { revealed = false; target = 0f } else onClick()
            })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MedicationCardAction(icon: Int, label: String, enabled: Boolean, onClick: () -> Unit) {
    TooltipBox(positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(label) } }, state = rememberTooltipState()) {
        FilledTonalIconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(48.dp)) {
            Icon(painterResource(icon), label, Modifier.size(24.dp))
        }
    }
}
