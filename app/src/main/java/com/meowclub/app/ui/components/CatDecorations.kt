package com.meowclub.app.ui.components

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.meowclub.app.data.AppearanceMode
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

object NavCatSprites {
    @Volatile
    private var cached: List<ImageBitmap>? = null

    fun sprites(context: Context): List<ImageBitmap> {
        cached?.let { return it }
        return synchronized(this) {
            cached ?: run {
                val options = BitmapFactory.Options().apply { inSampleSize = 2 }
                val sheet = context.assets.open("nav_cats.png").use { input ->
                    BitmapFactory.decodeStream(input, null, options)
                }
                val list = if (sheet != null) {
                    val w = sheet.width / 2
                    val h = sheet.height / 2
                    List(4) { i ->
                        val cell = Bitmap.createBitmap(sheet, (i % 2) * w, (i / 2) * h, w, h)
                        cell.asImageBitmap()
                    }.also { sheet.recycle() }
                } else {
                    emptyList()
                }
                cached = list
                list
            }
        }
    }
}

private enum class FloatingMotif {
    KITTY_FACE,
    SLEEPING_CAT,
    PAW_PRINT,
    FISH_TREAT,
    YARN_BALL
}

private data class FloatingSpec(
    val motif: FloatingMotif,
    val xFraction: Float,
    val yFraction: Float,
    val sizeDp: Float,
    val driftDp: Float,
    val phase: Float,
    val speedMult: Float,
    val baseTilt: Float
)

private val FloatingSpecs = listOf(
    FloatingSpec(FloatingMotif.KITTY_FACE, 0.12f, 0.14f, 44f, 10f, 0.0f, 1f, -8f),
    FloatingSpec(FloatingMotif.PAW_PRINT, 0.86f, 0.11f, 32f, 8f, 1.3f, 1f, 14f),
    FloatingSpec(FloatingMotif.FISH_TREAT, 0.89f, 0.34f, 36f, 9f, 2.4f, 1f, -18f),
    FloatingSpec(FloatingMotif.SLEEPING_CAT, 0.10f, 0.43f, 48f, 11f, 3.1f, 1f, 5f),
    FloatingSpec(FloatingMotif.YARN_BALL, 0.85f, 0.60f, 34f, 10f, 4.2f, 1f, 10f),
    FloatingSpec(FloatingMotif.PAW_PRINT, 0.15f, 0.72f, 30f, 9f, 5.1f, 1f, -15f),
    FloatingSpec(FloatingMotif.KITTY_FACE, 0.83f, 0.82f, 42f, 12f, 1.9f, 1f, 7f),
    FloatingSpec(FloatingMotif.PAW_PRINT, 0.48f, 0.25f, 26f, 7f, 3.8f, 1f, 12f)
)

@Composable
fun FloatingCatsBackground(
    appearance: AppearanceMode,
    modifier: Modifier = Modifier
) {
    val dark = when (appearance) {
        AppearanceMode.DARK -> true
        AppearanceMode.LIGHT -> false
        AppearanceMode.SYSTEM -> isSystemInDarkTheme()
    }
    val motionEnabled = remember { ValueAnimator.areAnimatorsEnabled() }
    val transition = rememberInfiniteTransition(label = "floatingCats")
    val tick by if (motionEnabled) {
        transition.animateFloat(
            initialValue = 0f,
            targetValue = (2 * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 12000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "floatPhase"
        )
    } else {
        remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    }

    val catTint = if (dark) Color(0xFFE69278).copy(alpha = 0.13f) else Color(0xFFC86D51).copy(alpha = 0.14f)
    val accentTint = if (dark) Color(0xFF8EC5B9).copy(alpha = 0.13f) else Color(0xFF3E6B62).copy(alpha = 0.12f)
    val detailTint = if (dark) Color(0xFFF4F1EC).copy(alpha = 0.22f) else Color(0xFF6E4E42).copy(alpha = 0.20f)
    val blushTint = if (dark) Color(0xFFF2A699).copy(alpha = 0.20f) else Color(0xFFE88B7B).copy(alpha = 0.22f)

    Canvas(modifier = modifier) {
        FloatingSpecs.forEachIndexed { idx, spec ->
            val angle = tick * spec.speedMult + spec.phase
            val dy = sin(angle) * spec.driftDp.dp.toPx()
            val dx = cos(angle * 0.7f) * (spec.driftDp * 0.45f).dp.toPx()
            val cx = size.width * spec.xFraction + dx
            val cy = size.height * spec.yFraction + dy
            val r = spec.sizeDp.dp.toPx() / 2f
            val rot = spec.baseTilt + sin(angle) * 5f
            val fill = if (idx % 2 == 0) catTint else accentTint

            rotate(degrees = rot, pivot = Offset(cx, cy)) {
                when (spec.motif) {
                    FloatingMotif.KITTY_FACE -> drawKittyFace(Offset(cx, cy), r, fill, detailTint, blushTint)
                    FloatingMotif.SLEEPING_CAT -> drawSleepingKitty(Offset(cx, cy), r, fill, detailTint, blushTint)
                    FloatingMotif.PAW_PRINT -> drawPawPrint(Offset(cx, cy), r, fill)
                    FloatingMotif.FISH_TREAT -> drawFishTreat(Offset(cx, cy), r, accentTint, detailTint)
                    FloatingMotif.YARN_BALL -> drawYarnBall(Offset(cx, cy), r, fill, detailTint)
                }
            }
        }
    }
}

private fun DrawScope.drawKittyFace(
    center: Offset,
    r: Float,
    fill: Color,
    line: Color,
    blush: Color
) {
    val leftEar = Path().apply {
        moveTo(center.x - r * 0.78f, center.y - r * 0.18f)
        lineTo(center.x - r * 0.85f, center.y - r * 0.92f)
        lineTo(center.x - r * 0.22f, center.y - r * 0.62f)
        close()
    }
    val rightEar = Path().apply {
        moveTo(center.x + r * 0.78f, center.y - r * 0.18f)
        lineTo(center.x + r * 0.85f, center.y - r * 0.92f)
        lineTo(center.x + r * 0.22f, center.y - r * 0.62f)
        close()
    }
    drawPath(leftEar, color = fill)
    drawPath(rightEar, color = fill)
    drawOval(
        color = fill,
        topLeft = Offset(center.x - r * 0.92f, center.y - r * 0.65f),
        size = Size(r * 1.84f, r * 1.48f)
    )
    // Inner ears
    drawCircle(blush, radius = r * 0.15f, center = Offset(center.x - r * 0.58f, center.y - r * 0.54f))
    drawCircle(blush, radius = r * 0.15f, center = Offset(center.x + r * 0.58f, center.y - r * 0.54f))
    // Eyes
    drawCircle(line, radius = r * 0.11f, center = Offset(center.x - r * 0.34f, center.y - r * 0.02f))
    drawCircle(line, radius = r * 0.11f, center = Offset(center.x + r * 0.34f, center.y - r * 0.02f))
    // Blush cheeks
    drawCircle(blush, radius = r * 0.14f, center = Offset(center.x - r * 0.54f, center.y + r * 0.20f))
    drawCircle(blush, radius = r * 0.14f, center = Offset(center.x + r * 0.54f, center.y + r * 0.20f))
    // Whiskers & smile
    val stroke = Stroke(width = (r * 0.08f).coerceAtLeast(1.5f), cap = StrokeCap.Round)
    val mouth = Path().apply {
        moveTo(center.x - r * 0.16f, center.y + r * 0.20f)
        quadraticTo(center.x - r * 0.06f, center.y + r * 0.32f, center.x, center.y + r * 0.18f)
        quadraticTo(center.x + r * 0.06f, center.y + r * 0.32f, center.x + r * 0.16f, center.y + r * 0.20f)
    }
    drawPath(mouth, color = line, style = stroke)
    drawLine(line, Offset(center.x - r * 0.72f, center.y + r * 0.04f), Offset(center.x - r * 1.02f, center.y - r * 0.04f), strokeWidth = stroke.width, cap = StrokeCap.Round)
    drawLine(line, Offset(center.x - r * 0.72f, center.y + r * 0.20f), Offset(center.x - r * 1.04f, center.y + r * 0.24f), strokeWidth = stroke.width, cap = StrokeCap.Round)
    drawLine(line, Offset(center.x + r * 0.72f, center.y + r * 0.04f), Offset(center.x + r * 1.02f, center.y - r * 0.04f), strokeWidth = stroke.width, cap = StrokeCap.Round)
    drawLine(line, Offset(center.x + r * 0.72f, center.y + r * 0.20f), Offset(center.x + r * 1.04f, center.y + r * 0.24f), strokeWidth = stroke.width, cap = StrokeCap.Round)
}

private fun DrawScope.drawSleepingKitty(
    center: Offset,
    r: Float,
    fill: Color,
    line: Color,
    blush: Color
) {
    // Curled body
    drawOval(
        color = fill,
        topLeft = Offset(center.x - r * 0.96f, center.y - r * 0.35f),
        size = Size(r * 1.92f, r * 1.15f)
    )
    // Ears
    val ear1 = Path().apply {
        moveTo(center.x - r * 0.68f, center.y - r * 0.20f)
        lineTo(center.x - r * 0.62f, center.y - r * 0.74f)
        lineTo(center.x - r * 0.22f, center.y - r * 0.34f)
        close()
    }
    val ear2 = Path().apply {
        moveTo(center.x - r * 0.14f, center.y - r * 0.32f)
        lineTo(center.x + r * 0.18f, center.y - r * 0.72f)
        lineTo(center.x + r * 0.28f, center.y - r * 0.24f)
        close()
    }
    drawPath(ear1, color = fill)
    drawPath(ear2, color = fill)
    // Curled tail
    val stroke = Stroke(width = (r * 0.22f).coerceAtLeast(3f), cap = StrokeCap.Round)
    val tail = Path().apply {
        moveTo(center.x + r * 0.78f, center.y + r * 0.34f)
        quadraticTo(center.x + r * 1.24f, center.y + r * 0.18f, center.x + r * 1.04f, center.y - r * 0.25f)
    }
    drawPath(tail, color = fill, style = stroke)
    // Closed sleepy eyes
    val eyeStroke = Stroke(width = (r * 0.08f).coerceAtLeast(1.5f), cap = StrokeCap.Round)
    val leftEye = Path().apply {
        moveTo(center.x - r * 0.56f, center.y + r * 0.08f)
        quadraticTo(center.x - r * 0.42f, center.y + r * 0.20f, center.x - r * 0.28f, center.y + r * 0.08f)
    }
    val rightEye = Path().apply {
        moveTo(center.x - r * 0.12f, center.y + r * 0.08f)
        quadraticTo(center.x + r * 0.02f, center.y + r * 0.20f, center.x + r * 0.16f, center.y + r * 0.08f)
    }
    drawPath(leftEye, color = line, style = eyeStroke)
    drawPath(rightEye, color = line, style = eyeStroke)
    drawCircle(blush, radius = r * 0.11f, center = Offset(center.x - r * 0.58f, center.y + r * 0.24f))
}

private fun DrawScope.drawPawPrint(center: Offset, r: Float, fill: Color) {
    drawOval(
        color = fill,
        topLeft = Offset(center.x - r * 0.46f, center.y - r * 0.06f),
        size = Size(r * 0.92f, r * 0.72f)
    )
    val toes = listOf(
        Offset(center.x - r * 0.52f, center.y - r * 0.26f),
        Offset(center.x - r * 0.18f, center.y - r * 0.52f),
        Offset(center.x + r * 0.22f, center.y - r * 0.50f),
        Offset(center.x + r * 0.54f, center.y - r * 0.22f)
    )
    toes.forEach { toe ->
        drawCircle(color = fill, radius = r * 0.19f, center = toe)
    }
}

private fun DrawScope.drawFishTreat(center: Offset, r: Float, fill: Color, line: Color) {
    drawOval(
        color = fill,
        topLeft = Offset(center.x - r * 0.68f, center.y - r * 0.42f),
        size = Size(r * 1.15f, r * 0.84f)
    )
    val tail = Path().apply {
        moveTo(center.x + r * 0.36f, center.y)
        lineTo(center.x + r * 0.88f, center.y - r * 0.42f)
        lineTo(center.x + r * 0.88f, center.y + r * 0.42f)
        close()
    }
    drawPath(tail, color = fill)
    drawCircle(color = line, radius = r * 0.09f, center = Offset(center.x - r * 0.32f, center.y - r * 0.08f))
}

private fun DrawScope.drawYarnBall(center: Offset, r: Float, fill: Color, line: Color) {
    drawCircle(color = fill, radius = r * 0.74f, center = center)
    val stroke = Stroke(width = (r * 0.09f).coerceAtLeast(1.5f), cap = StrokeCap.Round, join = StrokeJoin.Round)
    drawArc(
        color = line,
        startAngle = 20f,
        sweepAngle = 150f,
        useCenter = false,
        topLeft = Offset(center.x - r * 0.54f, center.y - r * 0.48f),
        size = Size(r * 1.08f, r * 0.96f),
        style = stroke
    )
    drawArc(
        color = line,
        startAngle = 200f,
        sweepAngle = 140f,
        useCenter = false,
        topLeft = Offset(center.x - r * 0.48f, center.y - r * 0.30f),
        size = Size(r * 0.96f, r * 0.82f),
        style = stroke
    )
    val tail = Path().apply {
        moveTo(center.x + r * 0.52f, center.y + r * 0.50f)
        quadraticTo(center.x + r * 0.95f, center.y + r * 0.76f, center.x + r * 1.05f, center.y + r * 0.38f)
    }
    drawPath(tail, color = fill, style = stroke)
}

@Composable
fun CuteKittyBadge(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    val blush = MaterialTheme.colorScheme.primaryContainer
    val eye = MaterialTheme.colorScheme.onPrimaryContainer
    Canvas(modifier = modifier) {
        val r = size.minDimension / 2f
        val c = Offset(size.width / 2f, size.height / 2f + r * 0.1f)
        drawKittyFace(c, r * 0.86f, color, eye.copy(alpha = 0.75f), blush)
    }
}

@Composable
fun CatPawBadge(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onPrimary
) {
    Canvas(modifier = modifier) {
        val r = size.minDimension / 2f
        val c = Offset(size.width / 2f, size.height / 2f + r * 0.12f)
        drawPawPrint(c, r * 0.85f, color)
    }
}

@Composable
fun SleepingKittyIllustration(
    modifier: Modifier = Modifier
) {
    val bodyColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
    val lineColor = MaterialTheme.colorScheme.primary
    val blushColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    Canvas(modifier = modifier) {
        val r = size.minDimension / 2f
        drawRoundRect(
            color = bodyColor.copy(alpha = 0.14f),
            topLeft = Offset(size.width * 0.12f, size.height * 0.68f),
            size = Size(size.width * 0.76f, size.height * 0.18f),
            cornerRadius = CornerRadius(r, r)
        )
        drawSleepingKitty(
            center = Offset(size.width / 2f, size.height / 2f + r * 0.08f),
            r = r * 0.76f,
            fill = bodyColor,
            line = lineColor,
            blush = blushColor
        )
    }
}
