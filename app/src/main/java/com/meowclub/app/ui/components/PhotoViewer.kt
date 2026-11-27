package com.meowclub.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest

@Composable
fun PhotoViewerDialog(
    sources: List<String>,
    initialIndex: Int,
    onDismiss: () -> Unit
) {
    if (sources.isEmpty()) return
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, sources.lastIndex),
        pageCount = { sources.size }
    )
    var currentScale by remember { mutableFloatStateOf(1f) }
    var resetSignal by remember { mutableIntStateOf(0) }

    LaunchedEffect(pagerState.currentPage) {
        currentScale = 1f
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .systemBarsPadding()
        ) {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = currentScale <= 1.02f,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                ZoomablePhotoPage(
                    source = sources[page],
                    isCurrentPage = page == pagerState.currentPage,
                    resetSignal = resetSignal,
                    onScaleChanged = { scale ->
                        if (page == pagerState.currentPage) {
                            currentScale = scale
                        }
                    }
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                FilledTonalIconButton(
                    onClick = onDismiss,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = Color(0x88242220),
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "关闭")
                }

                if (sources.size > 1) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = Color(0x88242220)
                    ) {
                        Text(
                            text = "${pagerState.currentPage + 1} / ${sources.size}",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                        )
                    }
                } else {
                    Spacer(Modifier.size(40.dp))
                }

                FilledTonalIconButton(
                    onClick = { resetSignal++ },
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = Color(0x88242220),
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Rounded.RestartAlt, contentDescription = "复原")
                }
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 18.dp),
                shape = RoundedCornerShape(999.dp),
                color = Color(0x66242220)
            ) {
                Text(
                    text = if (sources.size > 1) "双指或双击缩放 · 原比例左右滑动切图" else "双指或双击缩放照片",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFE0DDD8),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun ZoomablePhotoPage(
    source: String,
    isCurrentPage: Boolean,
    resetSignal: Int,
    onScaleChanged: (Float) -> Unit
) {
    val context = LocalContext.current
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var retryKey by remember(source) { mutableIntStateOf(0) }
    var loadFailed by remember(source) { mutableStateOf(false) }

    LaunchedEffect(isCurrentPage, resetSignal) {
        if (!isCurrentPage || resetSignal > 0) {
            scale = 1f
            offset = Offset.Zero
            onScaleChanged(1f)
        }
    }

    fun clampOffset(candidate: Offset, targetScale: Float): Offset {
        if (targetScale <= 1f || boxSize.width <= 0 || boxSize.height <= 0) return Offset.Zero
        val maxX = boxSize.width * (targetScale - 1f) / 2f
        val maxY = boxSize.height * (targetScale - 1f) / 2f
        return Offset(
            x = candidate.x.coerceIn(-maxX, maxX),
            y = candidate.y.coerceIn(-maxY, maxY)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { boxSize = it }
            .pointerInput(source) {
                detectTapGestures(
                    onTap = {
                        if (loadFailed) {
                            loadFailed = false
                            retryKey++
                        }
                    },
                    onDoubleTap = { tap ->
                        if (loadFailed) return@detectTapGestures
                        if (scale > 1.02f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            val target = 2.5f
                            val center = Offset(boxSize.width / 2f, boxSize.height / 2f)
                            val shifted = (center - tap) * (target - 1f)
                            scale = target
                            offset = clampOffset(shifted, target)
                        }
                        onScaleChanged(scale)
                    }
                )
            }
            .pointerInput(source) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val multiTouch = event.changes.count { it.pressed } > 1
                        if (multiTouch || scale > 1.02f) {
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            val nextScale = (scale * zoom).coerceIn(1f, 5f)
                            val nextOffset = if (nextScale <= 1.01f) {
                                Offset.Zero
                            } else {
                                clampOffset(offset + pan, nextScale)
                            }
                            if (nextScale != scale || nextOffset != offset) {
                                scale = nextScale
                                offset = nextOffset
                                onScaleChanged(nextScale)
                            }
                            if (multiTouch || scale > 1.02f) {
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center
    ) {
        val request = remember(source, retryKey) {
            val builder = ImageRequest.Builder(context)
                .data(source)
                .memoryCacheKey(source)
                .diskCacheKey(source)
                .placeholderMemoryCacheKey(coil.memory.MemoryCache.Key(source))
                .crossfade(140)
            if (retryKey > 0) builder.setParameter("retry", retryKey)
            builder.build()
        }
        SubcomposeAsyncImage(
            model = request,
            contentDescription = "全屏照片",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
            onError = { loadFailed = true },
            onSuccess = { loadFailed = false },
            error = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(34.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "照片加载失败，点击重试",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        )
    }
}
