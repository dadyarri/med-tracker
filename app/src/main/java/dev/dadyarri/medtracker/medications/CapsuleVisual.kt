package dev.dadyarri.medtracker.medications

import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import kotlin.math.min

internal fun DrawScope.drawCapsule(first: Color, second: Color, seamed: Boolean = true) {
    val w = min(size.width * .84f, size.minDimension * 1.45f)
    val h = w * if (seamed) .43f else .58f
    val b = Rect(center.x - w / 2, center.y - h / 2, center.x + w / 2, center.y + h / 2)
    val joint = b.left + w * .53f
    val bodyBounds = if (seamed) Rect(b.left + w * .02f, b.top + h * .065f, b.right, b.bottom - h * .065f) else b
    val body = Path().apply { addRoundRect(RoundRect(bodyBounds, CornerRadius(bodyBounds.height / 2))) }
    val cap = Path().apply { addRoundRect(RoundRect(b, CornerRadius(h / 2))) }
    fun sheen(color: Color) = Brush.verticalGradient(
        0f to lerp(color, Color.Black, .2f),
        .18f to lerp(color, Color.White, .4f),
        .42f to lerp(color, Color.White, .1f),
        .67f to color,
        1f to lerp(color, Color.Black, .4f),
        startY = b.top, endY = b.bottom)
    rotate(-28f) {
        val shadow = Offset(center.x + w * .02f, b.bottom + h * .09f)
        scale(1f, .3f, shadow) {
            drawCircle(Brush.radialGradient(listOf(Color.Black.copy(alpha = .2f), Color.Transparent),
                shadow, w * .55f), w * .55f, shadow)
        }
        clipPath(body) { drawRect(sheen(second)) }
        drawPath(body, Brush.verticalGradient(listOf(Color.White.copy(alpha = .5f), Color.Black.copy(alpha = .18f)),
            b.top, b.bottom), style = Stroke(w * .007f))
        if (seamed) {
            val capShell = Path().apply {
                moveTo(joint, b.top)
                lineTo(b.left + h / 2, b.top)
                cubicTo(b.left - h / 6, b.top, b.left - h / 6, b.bottom, b.left + h / 2, b.bottom)
                lineTo(joint, b.bottom)
                cubicTo(joint + w * .035f, b.bottom - h * .24f,
                    joint + w * .035f, b.top + h * .24f, joint, b.top)
                close()
            }
            clipPath(capShell) { drawRect(sheen(first)) }
            drawPath(capShell, Brush.verticalGradient(listOf(Color.White.copy(alpha = .6f), Color.Black.copy(alpha = .25f)),
                b.top, b.bottom), style = Stroke(w * .009f))
        }
        val seam = Path().apply {
            moveTo(joint, b.top)
            cubicTo(joint + w * .035f, b.top + h * .24f,
                joint + w * .035f, b.bottom - h * .24f, joint, b.bottom)
        }
        if (seamed) drawPath(seam, Color.Black.copy(alpha = .3f), style = Stroke(w * .016f))
        if (seamed) translate(left = -w * .012f) {
            drawPath(seam, Color.White.copy(alpha = .6f), style = Stroke(w * .007f))
        }
        clipPath(cap) {
            drawRoundRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = .38f), Color.Transparent),
                b.top + h * .14f, b.top + h * .4f),
                Offset(b.left + w * .16f, b.top + h * .15f), Size(w * .67f, h * .17f), CornerRadius(h * .09f))
        }
    }
}
