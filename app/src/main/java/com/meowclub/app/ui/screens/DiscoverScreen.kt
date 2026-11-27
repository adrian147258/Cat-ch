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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.meowclub.app.data.CAT_BREEDS
import com.meowclub.app.data.Post
import com.meowclub.app.ui.components.CompactPostCard
import com.meowclub.app.ui.components.GalleryEmptyState
import com.meowclub.app.ui.components.GalleryTextField
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    posts: List<Post>,
    hiddenIds: Set<String>,
    query: String,
    selectedBreed: String,
    bottomInsetPadding: PaddingValues,
    onQueryChange: (String) -> Unit,
    onSelectBreed: (String) -> Unit,
    onOpenPost: (Post) -> Unit,
    onToggleLike: (Post) -> Unit,
    onToggleSave: (Post) -> Unit,
    onHidePost: (Post) -> Unit
) {
    val breeds = remember { listOf("全部") + CAT_BREEDS }
    var actionTarget by remember { mutableStateOf<Post?>(null) }

    val filtered = remember(posts, hiddenIds, query, selectedBreed) {
        val needle = query.trim().lowercase(Locale.ROOT)
        posts.filter { post ->
            !hiddenIds.contains(post.id) &&
                (selectedBreed == "全部" || selectedBreed == post.breed) &&
                (needle.isEmpty() ||
                    post.text.lowercase(Locale.ROOT).contains(needle) ||
                    post.name.lowercase(Locale.ROOT).contains(needle) ||
                    post.breed.lowercase(Locale.ROOT).contains(needle))
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "发现",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 10.dp)
        )

        GalleryTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = "搜索猫咪、昵称或故事",
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = if (query.isNotEmpty()) {
                {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Rounded.Close, contentDescription = "清除搜索")
                    }
                }
            } else null,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(Modifier.height(10.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(breeds) { breed ->
                val selected = breed == selectedBreed
                FilterChip(
                    selected = selected,
                    onClick = { onSelectBreed(breed) },
                    label = { Text(breed) },
                    border = null,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        if (filtered.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize()) {
                GalleryEmptyState(
                    icon = Icons.Outlined.Search,
                    title = "没有找到符合的小猫",
                    subtitle = "换个关键词或切换品种试试看吧",
                    actionLabel = if (query.isNotEmpty() || selectedBreed != "全部") "重置筛选" else null,
                    onAction = {
                        onQueryChange("")
                        onSelectBreed("全部")
                    }
                )
            }
        } else {
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 6.dp,
                    bottom = bottomInsetPadding.calculateBottomPadding() + 16.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalItemSpacing = 12.dp
            ) {
                items(filtered, key = { it.id }) { post ->
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
    }

    actionTarget?.let { target ->
        ModalBottomSheet(onDismissRequest = { actionTarget = null }) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                ListItem(
                    headlineContent = { Text("不感兴趣") },
                    supportingContent = { Text("从广场、发现和偶遇中隐藏，可在设置中恢复") },
                    leadingContent = { Icon(Icons.Outlined.VisibilityOff, contentDescription = null) }
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { actionTarget = null }) { Text("取消") }
                    Spacer(Modifier.width(8.dp))
                    FilledTonalButton(onClick = { onHidePost(target); actionTarget = null }) {
                        Text("确认隐藏")
                    }
                }
            }
        }
    }
}
