package dev.dadyarri.medtracker.medications

import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*

/** Container silhouettes use a common 100 × 100 scene and the same soft lighting. */
internal fun DrawScope.drawMedicationObject(shape: PillShape, color: Color) {
    val factor = size.minDimension / 100f
    withTransform({
        translate(center.x - 50 * factor, center.y - 50 * factor)
        scale(factor, factor, Offset.Zero)
        rotate(if (shape == PillShape.SYRINGE) -24f else -9f, Offset(50f, 50f))
    }) {
        val shadow = Offset(52f, 89f)
        scale(1f, .23f, shadow) {
            drawCircle(Brush.radialGradient(listOf(Color.Black.copy(alpha = .22f), Color.Transparent),
                shadow, 32f), 32f, shadow)
        }
        val dark = lerp(color, Color.Black, .4f)
        val metal = Color(0xFFD8DFE7)
        fun lighting(tint: Color, left: Float = 25f, right: Float = 75f) = Brush.horizontalGradient(
            0f to lerp(tint, Color.Black, .15f), .24f to lerp(tint, Color.White, .48f),
            .55f to tint, 1f to lerp(tint, Color.Black, .23f), startX = left, endX = right)
        fun part(b: Rect, tint: Color = color, radius: Float = 5f) {
            drawRoundRect(lighting(tint, b.left, b.right), b.topLeft, b.size, CornerRadius(radius))
            drawRoundRect(Brush.linearGradient(listOf(Color.White.copy(alpha = .65f), Color.Transparent,
                Color.Black.copy(alpha = .15f)), b.topLeft, b.bottomRight),
                b.topLeft, b.size, CornerRadius(radius), style = Stroke(.65f))
        }
        fun label(b: Rect) {
            part(b, lerp(color, Color.White, .85f), 3f)
            drawRoundRect(dark.copy(alpha = .24f), Offset(b.left + 5, b.top + b.height * .42f),
                Size(b.width - 10, 2f), CornerRadius(1f))
        }
        when (shape) {
            PillShape.BOTTLE -> {
                val p = Path().apply {
                    moveTo(39f, 25f); lineTo(61f, 25f); lineTo(61f, 30f)
                    cubicTo(61f, 34f, 73f, 33f, 73f, 44f); lineTo(73f, 78f)
                    quadraticTo(73f, 86f, 65f, 86f); lineTo(35f, 86f)
                    quadraticTo(27f, 86f, 27f, 78f); lineTo(27f, 44f)
                    cubicTo(27f, 33f, 39f, 34f, 39f, 30f); close()
                }
                drawPath(p, lighting(color))
                part(Rect(35f, 13f, 65f, 29f), dark, 4f)
                for (x in 39..62 step 4) drawLine(Color.White.copy(alpha = .18f), Offset(x.toFloat(), 16f), Offset(x.toFloat(), 26f), .8f)
                label(Rect(32f, 49f, 68f, 71f))
            }
            PillShape.DROPPER -> {
                part(Rect(31f, 47f, 69f, 86f), color, 9f)
                part(Rect(38f, 34f, 62f, 51f), dark, 3f)
                part(Rect(42f, 10f, 58f, 39f), lerp(color, Color.Black, .65f), 8f)
                label(Rect(35f, 60f, 65f, 77f))
            }
            PillShape.TUBE -> {
                val p = Path().apply {
                    moveTo(25f, 16f); lineTo(75f, 16f); lineTo(64f, 76f)
                    quadraticTo(50f, 82f, 36f, 76f); close()
                }
                drawPath(p, lighting(color))
                part(Rect(25f, 12f, 75f, 19f), lerp(color, Color.White, .2f), 2f)
                part(Rect(37f, 76f, 63f, 89f), dark, 4f)
                label(Rect(35f, 34f, 65f, 60f))
            }
            PillShape.SYRINGE -> {
                part(Rect(45f, 14f, 55f, 34f), metal, 2f)
                part(Rect(32f, 10f, 68f, 16f), color, 3f)
                part(Rect(37f, 30f, 63f, 72f), lerp(color, Color.White, .7f), 4f)
                part(Rect(39f, 50f, 61f, 69f), color, 2f)
                part(Rect(30f, 27f, 70f, 33f), color, 2f)
                part(Rect(44f, 71f, 56f, 80f), dark, 2f)
                part(Rect(49f, 79f, 51f, 96f), metal, .8f)
                for (y in 38..65 step 6) drawLine(dark.copy(alpha = .6f), Offset(56f, y.toFloat()), Offset(62f, y.toFloat()), .8f)
            }
            PillShape.INHALER -> {
                part(Rect(32f, 10f, 62f, 44f), metal, 8f)
                val p = Path().apply {
                    moveTo(31f, 27f); lineTo(65f, 27f); quadraticTo(71f, 27f, 71f, 34f)
                    lineTo(71f, 64f); lineTo(84f, 64f); quadraticTo(90f, 64f, 90f, 71f)
                    lineTo(90f, 82f); quadraticTo(90f, 88f, 83f, 88f)
                    lineTo(30f, 88f); quadraticTo(23f, 88f, 23f, 81f)
                    lineTo(23f, 35f); quadraticTo(23f, 27f, 31f, 27f); close()
                }
                drawPath(p, lighting(color, 23f, 90f))
                part(Rect(66f, 67f, 89f, 84f), dark, 4f)
                label(Rect(30f, 44f, 62f, 67f))
            }
            PillShape.SACHET -> {
                part(Rect(22f, 13f, 78f, 88f), color, 4f)
                for (y in listOf(16f, 20f, 81f, 85f))
                    drawLine(dark.copy(alpha = .16f), Offset(25f, y), Offset(75f, y), .8f)
                label(Rect(28f, 35f, 72f, 63f))
            }
            PillShape.AMPOULE -> {
                val p = Path().apply {
                    moveTo(46f, 10f); quadraticTo(50f, 5f, 54f, 10f)
                    lineTo(57f, 24f); quadraticTo(51f, 31f, 58f, 39f)
                    quadraticTo(66f, 44f, 66f, 52f); lineTo(66f, 81f)
                    quadraticTo(66f, 88f, 59f, 88f); lineTo(41f, 88f)
                    quadraticTo(34f, 88f, 34f, 81f); lineTo(34f, 52f)
                    quadraticTo(34f, 44f, 42f, 39f); quadraticTo(49f, 31f, 43f, 24f); close()
                }
                clipPath(p) {
                    drawRect(lighting(lerp(color, Color.White, .65f), 34f, 66f))
                    drawRect(lighting(color, 34f, 66f), Offset(34f, 60f), Size(32f, 28f))
                    drawRoundRect(Color.White.copy(alpha = .6f), Offset(39f, 46f), Size(3f, 35f), CornerRadius(1.5f))
                }
                drawPath(p, Color.White.copy(alpha = .5f), style = Stroke(.7f))
                drawLine(dark.copy(alpha = .45f), Offset(44f, 29f), Offset(56f, 29f), 1.3f)
            }
            else -> Unit
        }
    }
}
