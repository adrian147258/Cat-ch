package com.meowclub.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meowclub.app.data.Post
import com.meowclub.app.ui.components.CompactPostCard
import com.meowclub.app.ui.components.CuteKittyBadge
import com.meowclub.app.ui.components.FeedShimmerCard
import com.meowclub.app.ui.components.GalleryEmptyState
import com.meowclub.app.ui.components.GalleryPostCard
import com.meowclub.app.ui.theme.AppFonts

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    posts: List<Post>,
    hiddenIds: Set<String>,
    loading: Boolean,
    error: String,
    compactMode: Boolean,
    bottomInsetPadding: PaddingValues,
    onRefresh: () -> Unit,
    onToggleCompact: () -> Unit,
    onEncounter: () -> Unit,
    onOpenPost: (Post) -> Unit,
    onOpenAuthor: (Post) -> Unit,
    onToggleLike: (Post) -> Unit,
    onToggleSave: (Post) -> Unit,
    onHidePost: (Post) -> Unit
) {
    val context = LocalContext.current
    val wenKai = remember(context) { AppFonts.wenKai(context) }
    val visiblePosts = remember(posts, hiddenIds) {
        posts.filterNot { hiddenIds.contains(it.id) }
    }
    var actionTarget by remember { mutableStateOf<Post?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CuteKittyBadge(modifier = Modifier.size(26.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Cat-ch",
                        fontFamily = wenKai,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                Text(
                    text = "今日遇见的小猫们",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            FilledTonalButton(
                onClick = onEncounter,
                shape = RoundedCornerShape(999.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Casino,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("偶遇", style = MaterialTheme.typography.labelLarge)
            }

            Spacer(Modifier.width(8.dp))

            FilledTonalIconButton(
                onClick = onToggleCompact,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Icon(
                    imageVector = if (compactMode) Icons.Outlined.ViewAgenda else Icons.Outlined.GridView,
                    contentDescription = if (compactMode) "切换为大图浏览" else "切换为双列网格"
                )
            }
        }

        if (error.isNotEmpty()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                onClick = onRefresh
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onRefresh) {
                        Text("重试")
                    }
                }
            }
        }

        PullToRefreshBox(
            isRefreshing = loading && visiblePosts.isNotEmpty(),
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize()
        ) {
            when {
                loading && visiblePosts.isEmpty() -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 8.dp,
                            bottom = bottomInsetPadding.calculateBottomPadding() + 16.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(2) { FeedShimmerCard() }
                    }
                }
                visiblePosts.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        GalleryEmptyState(
                            icon = Icons.Outlined.Pets,
                            title = "这里还没有小猫哦",
                            subtitle = "下拉刷新看看大家新拍的小猫，或点击底部 + 分享你的第一只猫",
                            actionLabel = "重新加载",
                            onAction = onRefresh
                        )
                    }
                }
                compactMode -> {
                    LazyVerticalStaggeredGrid(
                        columns = StaggeredGridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 8.dp,
                            bottom = bottomInsetPadding.calculateBottomPadding() + 16.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalItemSpacing = 12.dp
                    ) {
                        items(visiblePosts, key = { it.id }) { post ->
                            CompactPostCard(
                                post = post,
                                onOpenDetail = { onOpenPost(post) },
                                onToggleLike = { onToggleLike(post) },
                                onToggleSave = { onToggleSave(post) },
                                onLongClick = if (!post.mine) ({ actionTarget = post }) else null
                            )
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 8.dp,
                            bottom = bottomInsetPadding.calculateBottomPadding() + 16.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        items(visiblePosts, key = { it.id }) { post ->
                            GalleryPostCard(
                                post = post,
                                onOpenDetail = { onOpenPost(post) },
                                onOpenAuthor = { onOpenAuthor(post) },
                                onToggleLike = { onToggleLike(post) },
                                onToggleSave = { onToggleSave(post) },
                                onMore = if (!post.mine) ({ actionTarget = post }) else null
                            )
                        }
                    }
                }
            }
        }
    }

    actionTarget?.let { target ->
        ModalBottomSheet(onDismissRequest = { actionTarget = null }) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                Text(
                    text = "这篇帖子",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
                ListItem(
                    headlineContent = { Text("不感兴趣") },
                    supportingContent = { Text("从广场、发现和偶遇中隐藏，可在设置中恢复") },
                    leadingContent = {
                        Icon(Icons.Outlined.VisibilityOff, contentDescription = null)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .let { mod ->
                            mod.padding(horizontal = 4.dp)
                        },
                    trailingContent = null,
                    colors = androidx.compose.material3.ListItemDefaults.colors(
                        containerColor = androidx.compose.ui.graphics.Color.Transparent
                    )
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { actionTarget = null }) {
                        Text("取消")
                    }
                    Spacer(Modifier.width(8.dp))
                    FilledTonalButton(
                        onClick = {
                            onHidePost(target)
                            actionTarget = null
                        }
                    ) {
                        Text("确认隐藏")
                    }
                }
            }
        }
    }
}
