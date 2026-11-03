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
import kotlin.math.*

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
        if (shape.isContainer) { drawMedicationObject(shape, first); return@Canvas }
        if (shape == PillShape.CAPSULE || shape == PillShape.SOFTGEL) {
            drawCapsule(first, if (shape == PillShape.CAPSULE) second else first, shape == PillShape.CAPSULE)
            return@Canvas
        }
        val unit = min(size.width, size.height)
        val width = min(size.width * .84f, unit * if (shape == PillShape.ROUND) .76f else 1.3f)
        val height = width * when (shape) {
            PillShape.ROUND -> .78f
            PillShape.DIAMOND, PillShape.TRIANGLE, PillShape.HEXAGON -> .7f
            PillShape.OVAL -> .56f
            else -> .43f
        }
        val depth = height * .14f
        val bounds = Rect(center.x - width / 2, center.y - height / 2 - depth / 2,
            center.x + width / 2, center.y + height / 2 - depth / 2)
        val body = pillPath(shape, bounds)
        val grooves = if (shape.supportsScore) scoreCuts(bounds, medication.score) else emptyList()
        val face = grooves.fold(body) { path, cut -> Path.combine(PathOperation.Difference, path, cut) }
        rotate(if (shape == PillShape.ROUND) -16f else -24f) {
            val shadowCenter = Offset(center.x + width * .03f, bounds.bottom + depth * 1.8f)
            scale(1f, .3f, pivot = shadowCenter) {
                drawCircle(Brush.radialGradient(listOf(Color.Black.copy(alpha = .2f), Color.Transparent),
                    shadowCenter, width * .56f), width * .56f, shadowCenter)
            }
            for (layer in 8 downTo 1) {
                translate(top = depth * layer / 8) {
                    drawPath(body, lerp(first, Color.Black, .2f + .13f * layer / 8))
                }
            }
            clipPath(body) {
                drawRect(lerp(first, Color.Black, .38f))
                grooves.forEachIndexed { index, cut ->
                    val half = width * .026f
                    val shades = arrayOf(
                        0f to lerp(first, Color.Black, .14f), .25f to lerp(first, Color.Black, .46f),
                        .48f to lerp(first, Color.Black, .5f), .58f to lerp(first, Color.Black, .28f),
                        .9f to lerp(first, Color.White, .36f), 1f to first)
                    drawPath(cut, if (index == 0) Brush.horizontalGradient(*shades,
                        startX = bounds.center.x - half, endX = bounds.center.x + half)
                    else Brush.verticalGradient(*shades,
                        startY = bounds.center.y - half, endY = bounds.center.y + half))
                }
            }
            clipPath(face) {
                drawRect(pillLighting(first, bounds))
                drawRect(Brush.linearGradient(
                    listOf(Color.White.copy(alpha = .14f), Color.Transparent, Color.Black.copy(alpha = .05f)),
                    bounds.topLeft, bounds.bottomRight))
            }
            drawPath(face, Brush.linearGradient(listOf(Color.White.copy(alpha = .7f),
                Color.White.copy(alpha = .1f), Color.Black.copy(alpha = .12f)), bounds.topLeft, bounds.bottomRight),
                style = Stroke((width * .009f).coerceAtLeast(.7f)))
        }
    }
}

private fun scoreCuts(b: Rect, score: Int): List<Path> {
    if (score == 0) return emptyList()
    val half = b.width * .026f
    val flare = half * 1.8f
    val inset = b.height * .13f
    val vertical = Path().apply {
        moveTo(b.center.x - flare, b.top - inset)
        lineTo(b.center.x - flare, b.top)
        lineTo(b.center.x - half, b.top + inset)
        lineTo(b.center.x - half, b.bottom - inset)
        lineTo(b.center.x - flare, b.bottom); lineTo(b.center.x - flare, b.bottom + inset)
        lineTo(b.center.x + flare, b.bottom + inset); lineTo(b.center.x + flare, b.bottom)
        lineTo(b.center.x + half, b.bottom - inset)
        lineTo(b.center.x + half, b.top + inset)
        lineTo(b.center.x + flare, b.top); lineTo(b.center.x + flare, b.top - inset); close()
    }
    if (score == 1) return listOf(vertical)
    val horizontal = Path().apply {
        moveTo(b.left - inset, b.center.y - flare); lineTo(b.left, b.center.y - flare)
        lineTo(b.left + inset, b.center.y - half); lineTo(b.right - inset, b.center.y - half)
        lineTo(b.right, b.center.y - flare); lineTo(b.right + inset, b.center.y - flare)
        lineTo(b.right + inset, b.center.y + flare); lineTo(b.right, b.center.y + flare)
        lineTo(b.right - inset, b.center.y + half); lineTo(b.left + inset, b.center.y + half)
        lineTo(b.left, b.center.y + flare); lineTo(b.left - inset, b.center.y + flare); close()
    }
    return listOf(vertical, horizontal)
}

private fun pillLighting(color: Color, bounds: Rect) = Brush.verticalGradient(
    0f to lerp(color, Color.White, .34f), .24f to lerp(color, Color.White, .12f),
    .57f to color, 1f to lerp(color, Color.Black, .16f),
    startY = bounds.top, endY = bounds.bottom)

private fun pillPath(shape: PillShape, bounds: Rect): Path {
    if (shape in listOf(PillShape.DIAMOND, PillShape.TRIANGLE, PillShape.HEXAGON)) {
        val count = when (shape) { PillShape.TRIANGLE -> 3; PillShape.HEXAGON -> 6; else -> 4 }
        val points = (0 until count).map { i ->
            val angle = -PI / 2 + i * 2 * PI / count
            Offset(bounds.center.x + cos(angle).toFloat() * bounds.width / 2,
                bounds.center.y + sin(angle).toFloat() * bounds.height / 2)
        }
        return Path().apply {
            points.forEachIndexed { index, vertex ->
                val previous = points[(index + count - 1) % count]
                val next = points[(index + 1) % count]
                val before = vertex + (previous - vertex) * .18f
                val after = vertex + (next - vertex) * .18f
                if (index == 0) moveTo(before.x, before.y) else lineTo(before.x, before.y)
                quadraticTo(vertex.x, vertex.y, after.x, after.y)
            }
            close()
        }
    }
    return Path().apply {
        if (shape in listOf(PillShape.ROUND, PillShape.OVAL)) addOval(bounds)
        else addRoundRect(RoundRect(bounds, CornerRadius(bounds.height * .34f)))
    }
}
