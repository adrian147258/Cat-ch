package com.meowclub.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.meowclub.app.data.Post
import com.meowclub.app.data.PublicProfileState
import com.meowclub.app.ui.components.AuthorAvatar
import com.meowclub.app.ui.components.CompactPostCard
import com.meowclub.app.ui.components.GalleryEmptyState
import com.meowclub.app.ui.theme.AppFonts

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublicProfileScreen(
    displayName: String,
    state: PublicProfileState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenPost: (Post) -> Unit,
    onToggleLike: (Post) -> Unit,
    onToggleSave: (Post) -> Unit
) {
    val context = LocalContext.current
    val wenKai = remember(context) { AppFonts.wenKai(context) }
    val profile = state.profile

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        TopAppBar(
            title = { Text("${displayName.ifBlank { profile?.name ?: "猫友" }}的猫猫窝", style = MaterialTheme.typography.titleLarge) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        when {
            state.loading && profile == null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.error.isNotEmpty() && profile == null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    GalleryEmptyState(
                        icon = Icons.Outlined.Pets,
                        title = "主页暂时打不开",
                        subtitle = state.error,
                        actionLabel = "点击重试",
                        onAction = onRetry
                    )
                }
            }
            profile != null -> {
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalItemSpacing = 12.dp
                ) {
                    item(span = StaggeredGridItemSpan.FullLine) {
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = MaterialTheme.colorScheme.surface,
                            shadowElevation = 1.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                if (!profile.coverUrl.isNullOrEmpty()) {
                                    AsyncImage(
                                        model = profile.coverUrl,
                                        contentDescription = "${profile.name}的主页封面",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(176.dp)
                                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                    )
                                }
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        AuthorAvatar(name = profile.name, size = 46.dp)
                                        Spacer(Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = profile.name,
                                                style = MaterialTheme.typography.titleLarge,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "共 ${profile.postCount} 篇作品",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(12.dp))
                                    Text(
                                        text = profile.signature.ifBlank { "这位猫友还没有填写签名" },
                                        fontFamily = wenKai,
                                        fontSize = 15.sp,
                                        lineHeight = 22.sp,
                                        color = if (profile.signature.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    if (profile.posts.isEmpty()) {
                        item(span = StaggeredGridItemSpan.FullLine) {
                            GalleryEmptyState(
                                icon = Icons.Outlined.Pets,
                                title = "还没有公开作品",
                                subtitle = "等这位猫友分享第一张小猫照片吧"
                            )
                        }
                    } else {
                        items(profile.posts, key = { it.id }) { post ->
                            CompactPostCard(
                                post = post,
                                onOpenDetail = { onOpenPost(post) },
                                onToggleLike = { onToggleLike(post) },
                                onToggleSave = { onToggleSave(post) }
                            )
                        }
                    }

                    if (profile.nextBefore != null && profile.nextBeforeId != null) {
                        item(span = StaggeredGridItemSpan.FullLine) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                FilledTonalButton(
                                    onClick = onLoadMore,
                                    enabled = !state.loadingMore,
                                    shape = RoundedCornerShape(999.dp)
                                ) {
                                    if (state.loadingMore) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                        Spacer(Modifier.width(8.dp))
                                        Text("正在加载…")
                                    } else {
                                        Text("查看更多作品")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
