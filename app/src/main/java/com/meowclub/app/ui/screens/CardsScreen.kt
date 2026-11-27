package com.meowclub.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import com.meowclub.app.data.CardItem
import com.meowclub.app.data.CardsUiState
import com.meowclub.app.ui.components.CuteKittyBadge
import com.meowclub.app.ui.components.GalleryEmptyState
import com.meowclub.app.ui.theme.AppFonts

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardsScreen(
    state: CardsUiState,
    bottomInsetPadding: PaddingValues,
    onRefresh: () -> Unit,
    onDrawToday: () -> Unit,
    onOpenCardPreview: (CardItem) -> Unit
) {
    val context = LocalContext.current
    val wenKai = remember(context) { AppFonts.wenKai(context) }
    val uniqueCount = remember(state.cards) {
        state.cards.map { "${it.tier}_${it.index}" }.distinct().size
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CuteKittyBadge(modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "猫猫卡册",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                Text(
                    text = "已收集 $uniqueCount / ${CardItem.TOTAL_CARDS} 款",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        PullToRefreshBox(
            isRefreshing = state.loading,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize()
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 6.dp,
                    bottom = bottomInsetPadding.calculateBottomPadding() + 20.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(span = { GridItemSpan(2) }) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                painter = rememberAsyncImagePainter("file:///android_asset/pack_cat.webp"),
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.size(84.dp)
                            )
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "每日猫猫卡包",
                                    fontFamily = wenKai,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = if (state.drawnToday) "今日卡包已开启，明日零点再来" else "每日一次拆包，滑动撕开收获惊喜",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                                Spacer(Modifier.height(12.dp))
                                Button(
                                    onClick = onDrawToday,
                                    enabled = !state.drawing && !state.drawnToday,
                                    shape = RoundedCornerShape(999.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.AutoAwesome,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        when {
                                            state.drawing -> "正在打开卡包…"
                                            state.drawnToday -> "今天已经抽过啦"
                                            else -> "拆开今日卡包"
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                item(span = { GridItemSpan(2) }) {
                    Text(
                        text = "我的猫猫卡 (${state.cards.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 6.dp, start = 4.dp)
                    )
                }

                if (state.error.isNotEmpty() && state.cards.isEmpty()) {
                    item(span = { GridItemSpan(2) }) {
                        GalleryEmptyState(
                            icon = Icons.Outlined.Style,
                            title = "卡册暂时无法读取",
                            subtitle = state.error,
                            actionLabel = "点此重试",
                            onAction = onRefresh
                        )
                    }
                } else if (!state.loading && state.cards.isEmpty()) {
                    item(span = { GridItemSpan(2) }) {
                        GalleryEmptyState(
                            icon = Icons.Outlined.Style,
                            title = "还没有猫猫卡",
                            subtitle = "点击上方拆开你的第一包每日猫猫卡吧"
                        )
                    }
                } else {
                    items(state.cards) { card ->
                        CardGridItem(card = card, onClick = { onOpenCardPreview(card) })
                    }
                }
            }
        }
    }
}

@Composable
private fun CardGridItem(
    card: CardItem,
    onClick: () -> Unit
) {
    val tierColor = when (card.tier) {
        "gold" -> Color(0xFFC89B3C)
        "purple" -> Color(0xFF8B63B8)
        "blue" -> Color(0xFF4A8BB8)
        else -> Color(0xFF538E72)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            AsyncImage(
                model = card.assetUri,
                contentDescription = "${card.label} ${card.day}",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.78f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(tierColor)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = card.label,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = card.edition,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (card.day.isNotEmpty()) {
                Text(
                    text = card.day,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 18.dp, top = 2.dp, bottom = 2.dp)
                )
            }
        }
    }
}
