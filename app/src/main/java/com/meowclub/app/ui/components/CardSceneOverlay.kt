package com.meowclub.app.ui.components

import android.animation.ValueAnimator
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import com.meowclub.app.data.AppearanceMode
import com.meowclub.app.data.CardItem
import com.meowclub.app.data.CardSceneRequest
import com.meowclub.app.ui.theme.AppFonts
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class ScenePhase {
    SEALED,
    DRAWING,
    REVEALING,
    SHOWN
}

private data class TierPalette(
    val metal: Color,
    val foil: Color,
    val frame: Color,
    val letter: Color,
    val durationMs: Int
)

private fun tierPalette(tier: String): TierPalette = when (tier) {
    "gold" -> TierPalette(
        metal = Color(0xFFB28A49),
        foil = Color(0xFFFFF5CF),
        frame = Color(0xFFEED598),
        letter = Color(0xFF503C16),
        durationMs = 1100
    )
    "purple" -> TierPalette(
        metal = Color(0xFFA68ABC),
        foil = Color(0xFFFBF0FF),
        frame = Color(0xFFDFCEE9),
        letter = Color(0xFF4D335F),
        durationMs = 900
    )
    "blue" -> TierPalette(
        metal = Color(0xFF789DB7),
        foil = Color(0xFFEFF8FF),
        frame = Color(0xFFC4DEF0),
        letter = Color(0xFF244758),
        durationMs = 760
    )
    else -> TierPalette(
        metal = Color(0xFF769D85),
        foil = Color(0xFFE2EFDE),
        frame = Color(0xFFC4D9C2),
        letter = Color(0xFF284537),
        durationMs = 620
    )
}

@Composable
fun CardSceneDialogOverlay(
    request: CardSceneRequest,
    appearance: AppearanceMode,
    onCommitDraw: (onResult: (Result<Pair<String, Int>>) -> Unit) -> Unit,
    onDismiss: (didDraw: Boolean) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val wenKai = remember(context) { AppFonts.wenKai(context) }
    val motionEnabled = remember { ValueAnimator.areAnimatorsEnabled() }
    val dark = when (appearance) {
        AppearanceMode.DARK -> true
        AppearanceMode.LIGHT -> false
        AppearanceMode.SYSTEM -> isSystemInDarkTheme()
    }

    var phase by remember(request) {
        mutableStateOf(
            when (request) {
                is CardSceneRequest.UnopenedPack -> ScenePhase.SEALED
                is CardSceneRequest.PreviewCard -> ScenePhase.SHOWN
            }
        )
    }
    var drawnCard by remember(request) {
        mutableStateOf(
            when (request) {
                is CardSceneRequest.PreviewCard -> request.tier to request.index
                is CardSceneRequest.UnopenedPack -> null
            }
        )
    }
    var didDrawInScene by remember(request) { mutableStateOf(false) }
    var statusError by remember(request) { mutableStateOf("") }

    // Pack tear progress (0..1) and drag direction
    var tearProgress by remember(request) { mutableFloatStateOf(0f) }
    var tearDirection by remember(request) { mutableFloatStateOf(1f) }
    var packWidthPx by remember { mutableFloatStateOf(1f) }
    val tearAnim = remember { Animatable(0f) }
    val revealAnim = remember { Animatable(if (request is CardSceneRequest.PreviewCard) 1f else 0f) }

    // Card tilt (-1..1) from sensor and pointer drag
    var sensorX by remember { mutableFloatStateOf(0f) }
    var sensorY by remember { mutableFloatStateOf(0f) }
    var fingerActive by remember { mutableStateOf(false) }
    var fingerX by remember { mutableFloatStateOf(0f) }
    var fingerY by remember { mutableFloatStateOf(0f) }

    val targetX = if (fingerActive) fingerX else sensorX
    val targetY = if (fingerActive) fingerY else sensorY
    val tiltX by animateFloatAsState(
        targetValue = if (phase == ScenePhase.SHOWN && motionEnabled) targetX else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "tiltX"
    )
    val tiltY by animateFloatAsState(
        targetValue = if (phase == ScenePhase.SHOWN && motionEnabled) targetY else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "tiltY"
    )

    DisposableEffect(lifecycleOwner, motionEnabled) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val rotation = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        var centered = false
        var pitch = 0f
        var roll = 0f
        val matrix = FloatArray(9)
        val angles = FloatArray(3)
        var listening = false

        val listener = object : SensorEventListener {
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            override fun onSensorChanged(event: SensorEvent) {
                if (!listening || event.values.size < 3) return
                SensorManager.getRotationMatrixFromVector(matrix, event.values)
                SensorManager.getOrientation(matrix, angles)
                if (!centered) {
                    pitch = angles[1]
                    roll = angles[2]
                    centered = true
                }
                sensorX = ((angles[2] - roll) * 2.3f).coerceIn(-1f, 1f)
                sensorY = ((angles[1] - pitch) * 2.3f).coerceIn(-1f, 1f)
            }
        }

        fun startSensors() {
            if (motionEnabled && rotation != null && !listening) {
                centered = false
                listening = sensorManager.registerListener(listener, rotation, 33333)
            }
        }

        fun stopSensors() {
            if (listening) {
                sensorManager?.unregisterListener(listener)
                listening = false
            }
        }

        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            startSensors()
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> startSensors()
                Lifecycle.Event.ON_PAUSE -> stopSensors()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            stopSensors()
        }
    }

    fun triggerOpenPack() {
        if (phase != ScenePhase.SEALED) return
        phase = ScenePhase.DRAWING
        statusError = ""
        onCommitDraw { result ->
            result.onSuccess { (tier, index) ->
                drawnCard = tier to index
                didDrawInScene = true
                phase = ScenePhase.REVEALING
                scope.launch {
                    if (motionEnabled) {
                        tearAnim.snapTo(tearProgress)
                        tearAnim.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
                        val duration = tierPalette(tier).durationMs
                        revealAnim.snapTo(0f)
                        revealAnim.animateTo(1f, tween(duration, easing = FastOutSlowInEasing))
                    } else {
                        tearAnim.snapTo(1f)
                        revealAnim.snapTo(1f)
                    }
                    phase = ScenePhase.SHOWN
                }
            }.onFailure { err ->
                tearProgress = 0f
                phase = ScenePhase.SEALED
                statusError = err.message ?: "拆包失败，请重试"
            }
        }
    }

    val stageBg = if (dark) {
        Brush.radialGradient(
            colors = listOf(Color(0xFF344C43), Color(0xFF152320)),
            center = Offset(300f, 320f),
            radius = 1200f
        )
    } else {
        Brush.radialGradient(
            colors = listOf(Color(0xFFFAFBF3), Color(0xFFE0E9E2)),
            center = Offset(300f, 320f),
            radius = 1200f
        )
    }
    val ringColor = if (dark) Color(0x2695C5A7) else Color(0x21395F4C)
    val hintColor = if (dark) Color(0xFFB6CBC1) else Color(0xFF4C665E)

    Dialog(
        onDismissRequest = {
            if (phase != ScenePhase.DRAWING && phase != ScenePhase.REVEALING) {
                onDismiss(didDrawInScene)
            }
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(stageBg)
                .systemBarsPadding()
        ) {
            // Subtle decorative rings behind pack/card
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val ringRadius = minOf(size.width, size.height) * 0.36f
                drawCircle(color = ringColor, radius = ringRadius, center = center, style = Stroke(width = 1.5f))
                drawCircle(color = ringColor.copy(alpha = ringColor.alpha * 0.55f), radius = ringRadius * 1.05f, center = center, style = Stroke(width = 1.2f))
            }

            // Close button always accessible
            FilledTonalIconButton(
                onClick = {
                    if (phase != ScenePhase.DRAWING) onDismiss(didDrawInScene)
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = if (dark) Color(0xD9283E34) else Color(0xE6FFFFFF),
                    contentColor = if (dark) Color(0xFFEDF3EE) else Color(0xFF1B3932)
                )
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "关闭卡片")
            }

            // Center Stage
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 36.dp)
                    .widthIn(max = 314.dp)
                    .fillMaxWidth()
                    .aspectRatio(1f / 1.45f),
                contentAlignment = Alignment.Center
            ) {
                if (phase == ScenePhase.SEALED || phase == ScenePhase.DRAWING || phase == ScenePhase.REVEALING) {
                    val unwrapFraction = if (phase == ScenePhase.REVEALING) tearAnim.value else tearProgress
                    val pulse = rememberInfiniteTransition(label = "packPulse")
                    val pulseScale by pulse.animateFloat(
                        initialValue = 1f,
                        targetValue = 1.03f,
                        animationSpec = infiniteRepeatable(tween(550), RepeatMode.Reverse),
                        label = "pulseScale"
                    )
                    val packAlpha = if (phase == ScenePhase.REVEALING) (1f - tearAnim.value).coerceIn(0f, 1f) else 1f
                    val packScale = if (phase == ScenePhase.DRAWING) pulseScale else (1f - 0.06f * (if (phase == ScenePhase.REVEALING) tearAnim.value else 0f))

                    SealedCatPack(
                        tearProgress = unwrapFraction,
                        tearDirection = tearDirection,
                        isRevealing = phase == ScenePhase.REVEALING,
                        isDrawing = phase == ScenePhase.DRAWING,
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(packAlpha)
                            .scale(packScale)
                            .onSizeChanged { packWidthPx = it.width.toFloat().coerceAtLeast(1f) }
                            .pointerInput(phase) {
                                if (phase != ScenePhase.SEALED) return@pointerInput
                                detectHorizontalDragGestures(
                                    onDragStart = { statusError = "" },
                                    onHorizontalDrag = { _, dragAmount ->
                                        if (abs(dragAmount) > 0.5f && tearProgress < 0.05f) {
                                            tearDirection = if (dragAmount < 0f) -1f else 1f
                                        }
                                        val next = (tearProgress + abs(dragAmount) / (packWidthPx * 0.85f)).coerceIn(0f, 1f)
                                        tearProgress = next
                                    },
                                    onDragEnd = {
                                        if (tearProgress >= 0.55f) triggerOpenPack()
                                    },
                                    onDragCancel = {}
                                )
                            }
                            .clickable(
                                enabled = phase == ScenePhase.SEALED,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                triggerOpenPack()
                            }
                    )
                }

                val cardPair = drawnCard
                if (cardPair != null && (phase == ScenePhase.REVEALING || phase == ScenePhase.SHOWN)) {
                    val (tier, index) = cardPair
                    val arrive = revealAnim.value
                    val arriveAlpha = if (phase == ScenePhase.REVEALING) (arrive * 2.2f).coerceIn(0f, 1f) else 1f
                    val arriveScale = if (phase == ScenePhase.REVEALING) (0.86f + 0.14f * arrive) else 1f
                    val arriveFlipY = if (phase == ScenePhase.REVEALING) (-58f * (1f - arrive)) else 0f
                    val arriveShiftY = if (phase == ScenePhase.REVEALING) (40f * (1f - arrive)) else 0f
                    val density = LocalDensity.current.density

                    var cardBoxWidth by remember { mutableFloatStateOf(1f) }
                    var cardBoxHeight by remember { mutableFloatStateOf(1f) }

                    RevealedCatCard(
                        tier = tier,
                        index = index,
                        wenKai = wenKai,
                        tiltX = tiltX,
                        tiltY = tiltY,
                        glintProgress = if (phase == ScenePhase.REVEALING) arrive else -1f,
                        modifier = Modifier
                            .fillMaxSize()
                            .onSizeChanged {
                                cardBoxWidth = it.width.toFloat().coerceAtLeast(1f)
                                cardBoxHeight = it.height.toFloat().coerceAtLeast(1f)
                            }
                            .graphicsLayer {
                                alpha = arriveAlpha
                                scaleX = arriveScale
                                scaleY = arriveScale
                                translationY = arriveShiftY
                                rotationX = -tiltY * 10f
                                rotationY = arriveFlipY + tiltX * 14f
                                cameraDistance = 12f * density
                            }
                            .pointerInput(phase) {
                                if (phase != ScenePhase.SHOWN) return@pointerInput
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        fingerActive = true
                                        fingerX = ((2f * offset.x / cardBoxWidth) - 1f).coerceIn(-1f, 1f)
                                        fingerY = ((2f * offset.y / cardBoxHeight) - 1f).coerceIn(-1f, 1f)
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        fingerX = ((2f * change.position.x / cardBoxWidth) - 1f).coerceIn(-1f, 1f)
                                        fingerY = ((2f * change.position.y / cardBoxHeight) - 1f).coerceIn(-1f, 1f)
                                    },
                                    onDragEnd = {
                                        fingerActive = false
                                        fingerX = 0f
                                        fingerY = 0f
                                    },
                                    onDragCancel = {
                                        fingerActive = false
                                        fingerX = 0f
                                        fingerY = 0f
                                    }
                                )
                            }
                    )
                }
            }

            // Bottom Hint
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 28.dp, start = 20.dp, end = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (statusError.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = statusError,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                }

                Text(
                    text = when (phase) {
                        ScenePhase.SEALED -> "向右滑动封口，或轻点卡包拆开"
                        ScenePhase.DRAWING -> "正在拆开今日猫猫卡包…"
                        ScenePhase.REVEALING -> "正在揭晓…"
                        ScenePhase.SHOWN -> {
                            val label = CardItem.tierLabel(drawnCard?.first ?: "green")
                            if (motionEnabled) "$label · 轻触拖动或转动手机查看光泽" else label
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = hintColor
                )
            }
        }
    }
}

@Composable
private fun SealedCatPack(
    tearProgress: Float,
    tearDirection: Float,
    isRevealing: Boolean,
    isDrawing: Boolean,
    modifier: Modifier = Modifier
) {
    val packTilt = -3f + tearDirection * tearProgress * 3f + (if (isRevealing) 5f * tearProgress else 0f)
    val sealShiftX = tearDirection * tearProgress * 120f
    val sealShiftY = if (isRevealing) -180f * tearProgress else 0f
    val sealRotate = -tearDirection * tearProgress * 14f
    val sealAlpha = if (isRevealing) (1f - tearProgress * 1.4f).coerceIn(0f, 1f) else 1f

    Box(
        modifier = modifier.rotate(packTilt)
    ) {
        // Wrapper body (bottom 89.5%)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 34.dp)
                .shadow(16.dp, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp, bottomStart = 10.dp, bottomEnd = 10.dp))
                .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp, bottomStart = 10.dp, bottomEnd = 10.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF527E65),
                            Color(0xFFBBD7AE),
                            Color(0xFF8DB894),
                            Color(0xFFD5E8BD),
                            Color(0xFF93B598),
                            Color(0xFF66967C),
                            Color(0xFFC1DBB1)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    color = Color(0xFFD8EAD0),
                    shape = RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp, bottomStart = 10.dp, bottomEnd = 10.dp)
                )
        ) {
            // Center medallion circle
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.76f)
                    .aspectRatio(1f)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color(0xABEFF5CE), Color(0x7DA9CAA5))
                        )
                    )
                    .border(1.5.dp, Color(0xBFE5F4CE), CircleShape)
            )

            Text(
                text = "Cat-ch",
                color = Color(0xFF183F30),
                fontFamily = FontFamily.Serif,
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.Bold,
                fontSize = 36.sp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 22.dp, top = 16.dp)
            )

            Image(
                painter = rememberAsyncImagePainter("file:///android_asset/pack_cat.webp"),
                contentDescription = "猫猫卡包",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.76f)
                    .aspectRatio(1f)
            )

            if (isDrawing) {
                CircularProgressIndicator(
                    color = Color(0xFF183F30),
                    strokeWidth = 2.5.dp,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(34.dp)
                )
            }

            Text(
                text = "猫猫卡包",
                color = Color(0xFF183F30),
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                letterSpacing = 3.sp,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 22.dp, bottom = 26.dp)
            )

            // Bottom crimp edge
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(14.dp)
                    .background(Color(0xFF9EC09C))
            )
        }

        // Top Tear Seal Strip
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset { IntOffset(sealShiftX.roundToInt(), sealShiftY.roundToInt()) }
                .rotate(sealRotate)
                .alpha(sealAlpha)
                .fillMaxWidth()
                .height(38.dp)
                .clip(RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp, bottomStart = 2.dp, bottomEnd = 2.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFFD9E8CB), Color(0xFF8CB294), Color(0xFFBDD3B8))
                    )
                )
                .border(
                    1.dp,
                    Color(0xFFD8EAD0),
                    RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp, bottomStart = 2.dp, bottomEnd = 2.dp)
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "撕开",
                    color = Color(0xFF173F2B),
                    fontSize = 12.sp,
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.width(10.dp))
                Canvas(
                    modifier = Modifier
                        .weight(1f)
                        .height(2.dp)
                ) {
                    drawLine(
                        color = Color(0xFF44674E),
                        start = Offset(0f, size.height / 2f),
                        end = Offset(size.width, size.height / 2f),
                        strokeWidth = 2f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "⟶",
                    color = Color(0xFF173F2B),
                    fontSize = 18.sp
                )
            }
        }
    }
}

@Composable
private fun RevealedCatCard(
    tier: String,
    index: Int,
    wenKai: FontFamily,
    tiltX: Float,
    tiltY: Float,
    glintProgress: Float,
    modifier: Modifier = Modifier
) {
    val palette = remember(tier) { tierPalette(tier) }
    val label = CardItem.tierLabel(tier)
    val edition = remember(tier, index) {
        "${(index + 1).toString().padStart(2, '0')} / ${CardItem.tierCount(tier).toString().padStart(2, '0')}"
    }

    Box(
        modifier = modifier
            .shadow(20.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFFFFCF0),
                        palette.foil,
                        palette.frame,
                        palette.metal,
                        palette.foil
                    )
                )
            )
            .border(1.dp, Color(0xB8FFFFEE), RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        // Inner decorative hairline border
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(
                    width = 1.dp,
                    color = palette.letter.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(14.dp)
                )
        )

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            AsyncImage(
                model = "file:///android_asset/cards/${tier}_${index}.webp",
                contentDescription = "$label $edition",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(palette.frame)
            )

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = label,
                        fontFamily = wenKai,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.letter
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = edition,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = palette.letter.copy(alpha = 0.8f),
                        modifier = Modifier.padding(bottom = 3.dp)
                    )
                }

                Text(
                    text = "Cat-ch",
                    fontFamily = FontFamily.Serif,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = palette.letter
                )
            }
        }

        // Dynamic metallic foil shimmer layer responding to tilt & reveal glint
        val sweepOffset = if (glintProgress in 0f..1f) {
            (glintProgress * 2f - 1f) * 420f
        } else {
            tiltX * 160f + tiltY * 60f
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = sweepOffset
                }
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0x14FFFFFF),
                            Color(0x5CEEE8D0),
                            Color(0x20FFFFFF),
                            Color.Transparent
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(520f, 700f)
                    )
                )
        )
    }
}
