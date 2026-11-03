package dev.dadyarri.medtracker.medications

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.res.painterResource
import dev.dadyarri.medtracker.R
import kotlin.math.min

@Composable
fun MedicationVisual(medication: Medication, modifier: Modifier = Modifier) {
    val shape = PillShape.valueOf(medication.shape)
    if (shape == PillShape.NONE) {
        Icon(painterResource(R.drawable.ic_medication), medication.name, modifier, tint = MaterialTheme.colorScheme.primary)
        return
    }
    val first by animateColorAsState(Color(medication.color), tween(180), label = "Medication color")
    val second by animateColorAsState(Color(medication.secondColor), tween(180), label = "Capsule color")
    Canvas(modifier) {
        val unit = min(size.width, size.height)
        val width = min(size.width * .84f, unit * if (shape == PillShape.ROUND) .76f else 1.3f)
        val height = width * when (shape) {
            PillShape.ROUND -> .78f
            PillShape.DIAMOND -> .7f
            PillShape.OVAL -> .56f
            else -> .43f
        }
        val depth = height * .14f
        val bounds = Rect(center.x - width / 2, center.y - height / 2 - depth / 2,
            center.x + width / 2, center.y + height / 2 - depth / 2)
        val body = pillPath(shape, bounds)
        rotate(if (shape == PillShape.ROUND) -16f else -24f) {
            val shadowCenter = Offset(center.x + width * .03f, bounds.bottom + depth * 1.8f)
            scale(1f, .3f, pivot = shadowCenter) {
                drawCircle(Brush.radialGradient(listOf(Color.Black.copy(alpha = .2f), Color.Transparent),
                    shadowCenter, width * .56f), width * .56f, shadowCenter)
            }
            // Stacked silhouettes make the side wall, without a bitmap or a native 3D surface.
            for (layer in 8 downTo 1) {
                translate(top = depth * layer / 8) {
                    val darken = .2f + .13f * layer / 8
                    clipPath(body) {
                        drawRect(lerp(first, Color.Black, darken))
                        if (shape == PillShape.CAPSULE) drawRect(lerp(second, Color.Black, darken),
                            topLeft = Offset(center.x, 0f), size = Size(size.width / 2, size.height))
                    }
                }
            }
            clipPath(body) {
                drawRect(pillLighting(first, bounds))
                if (shape == PillShape.CAPSULE) clipRect(left = center.x) { drawRect(pillLighting(second, bounds)) }
                drawRect(Brush.linearGradient(
                    listOf(Color.White.copy(alpha = .14f), Color.Transparent, Color.Black.copy(alpha = .05f)),
                    bounds.topLeft, bounds.bottomRight))
                if (medication.score > 0 || shape == PillShape.CAPSULE) {
                    val groove = (width * .014f).coerceAtLeast(.7f)
                    fun etch(start: Offset, end: Offset) {
                        drawLine(Color.Black.copy(alpha = .22f), start, end, groove, StrokeCap.Round)
                        val shift = Offset(-groove * .65f, -groove * .65f)
                        drawLine(Color.White.copy(alpha = .55f), start + shift, end + shift, groove * .5f, StrokeCap.Round)
                    }
                    etch(Offset(center.x, bounds.top), Offset(center.x, bounds.bottom))
                    if (medication.score == 2) etch(Offset(bounds.left, bounds.center.y), Offset(bounds.right, bounds.center.y))
                }
            }
            drawPath(body, Brush.linearGradient(listOf(Color.White.copy(alpha = .7f),
                Color.White.copy(alpha = .1f), Color.Black.copy(alpha = .12f)), bounds.topLeft, bounds.bottomRight),
                style = Stroke((width * .009f).coerceAtLeast(.7f)))
        }
    }
}

private fun pillLighting(color: Color, bounds: Rect) = Brush.verticalGradient(
    0f to lerp(color, Color.White, .34f), .24f to lerp(color, Color.White, .12f),
    .57f to color, 1f to lerp(color, Color.Black, .16f),
    startY = bounds.top, endY = bounds.bottom)

private fun pillPath(shape: PillShape, bounds: Rect) = Path().apply {
    when (shape) {
        PillShape.ROUND, PillShape.OVAL -> addOval(bounds)
        PillShape.DIAMOND -> {
            val x = bounds.center.x
            val y = bounds.center.y
            val rx = bounds.width / 2
            val ry = bounds.height / 2
            val rounding = .22f
            moveTo(x - rx * rounding, bounds.top + ry * rounding)
            quadraticTo(x, bounds.top, x + rx * rounding, bounds.top + ry * rounding)
            lineTo(bounds.right - rx * rounding, y - ry * rounding)
            quadraticTo(bounds.right, y, bounds.right - rx * rounding, y + ry * rounding)
            lineTo(x + rx * rounding, bounds.bottom - ry * rounding)
            quadraticTo(x, bounds.bottom, x - rx * rounding, bounds.bottom - ry * rounding)
            lineTo(bounds.left + rx * rounding, y + ry * rounding)
            quadraticTo(bounds.left, y, bounds.left + rx * rounding, y - ry * rounding)
            close()
        }
        else -> addRoundRect(RoundRect(bounds, CornerRadius(bounds.height *
            if (shape == PillShape.CAPSULE) .5f else .34f)))
    }
}
