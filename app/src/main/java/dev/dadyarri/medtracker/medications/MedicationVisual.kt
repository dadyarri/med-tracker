package dev.dadyarri.medtracker.medications

import androidx.compose.foundation.Canvas
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.dadyarri.medtracker.R

@Composable
fun MedicationVisual(medication: Medication, modifier: Modifier = Modifier) {
    val shape = PillShape.valueOf(medication.shape)
    if (shape == PillShape.NONE) {
        Icon(painterResource(R.drawable.ic_medication), medication.name, modifier, tint = MaterialTheme.colorScheme.primary)
        return
    }
    val edge = MaterialTheme.colorScheme.onSurface
    val mark = if (Color(medication.color).luminance() > .5f) Color.Black else Color.White
    Canvas(modifier) {
        val width = size.width * if (shape == PillShape.ROUND) .65f else .9f
        val height = size.height * if (shape in listOf(PillShape.ROUND, PillShape.DIAMOND)) .65f else .42f
        val left = (size.width - width) / 2
        val top = (size.height - height) / 2
        val path = Path().apply {
            when (shape) {
                PillShape.ROUND, PillShape.OVAL -> addOval(Rect(left, top, left + width, top + height))
                PillShape.DIAMOND -> {
                    moveTo(size.width / 2, top); lineTo(left + width, size.height / 2)
                    lineTo(size.width / 2, top + height); lineTo(left, size.height / 2); close()
                }
                else -> addRoundRect(RoundRect(Rect(left, top, left + width, top + height), CornerRadius(height / 2)))
            }
        }
        clipPath(path) {
            drawRect(Color(medication.color))
            if (shape == PillShape.CAPSULE) drawRect(Color(medication.secondColor),
                topLeft = Offset(size.width / 2, top), size = Size(width / 2, height))
            if (medication.score > 0 || shape == PillShape.CAPSULE)
                drawLine(mark.copy(alpha = .65f), Offset(size.width / 2, top), Offset(size.width / 2, top + height), 1.5.dp.toPx())
            if (medication.score == 2) drawLine(mark.copy(alpha = .65f),
                Offset(left, size.height / 2), Offset(left + width, size.height / 2), 1.5.dp.toPx())
        }
        drawPath(path, edge.copy(alpha = .65f), style = Stroke(1.5.dp.toPx()))
    }
}
