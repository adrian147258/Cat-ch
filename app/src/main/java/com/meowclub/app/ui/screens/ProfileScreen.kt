package com.meowclub.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.meowclub.app.data.Post
import com.meowclub.app.data.ProfileUiState
import com.meowclub.app.ui.components.AuthorAvatar
import com.meowclub.app.ui.components.CatPawBadge
import com.meowclub.app.ui.components.CompactPostCard
import com.meowclub.app.ui.components.GalleryEmptyState
import com.meowclub.app.ui.components.GalleryTextField
import com.meowclub.app.ui.theme.AppFonts
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    profile: ProfileUiState,
    posts: List<Post>,
    bottomInsetPadding: PaddingValues,
    onOpenSettings: () -> Unit,
    onOpenAnnouncement: () -> Unit,
    onUploadCover: (Uri) -> Unit,
    onUpdateSignature: (String) -> Unit,
    onOpenPost: (Post) -> Unit,
    onToggleLike: (Post) -> Unit,
    onToggleSave: (Post) -> Unit
) {
    val context = LocalContext.current
    val wenKai = remember(context) { AppFonts.wenKai(context) }

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var editingSignature by remember { mutableStateOf(false) }
    var signatureDraft by remember { mutableStateOf("") }

    val coverPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onUploadCover(uri)
    }

    val myPosts = remember(posts) { posts.filter { it.mine } }
    val likedPosts = remember(posts) { posts.filter { it.liked } }
    val savedPosts = remember(posts) { posts.filter { it.saved } }

    val activeList = when (selectedTab) {
        0 -> myPosts
        1 -> likedPosts
        else -> savedPosts
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CatPawBadge(
                modifier = Modifier.size(22.dp),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "我的猫猫窝",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onOpenAnnouncement) {
                Icon(Icons.Outlined.Campaign, contentDescription = "软件公告")
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = "设置")
            }
        }

        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 6.dp,
                bottom = bottomInsetPadding.calculateBottomPadding() + 20.dp
            ),
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
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(168.dp)
                                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                .clickable { coverPicker.launch(arrayOf("image/*")) }
                        ) {
                            if (profile.coverSource.isNotEmpty()) {
                                val model: Any = if (profile.coverSource.startsWith("https://")) {
                                    profile.coverSource
                                } else {
                                    File(profile.coverSource)
                                }
                                AsyncImage(
                                    model = model,
                                    contentDescription = "猫猫窝封面",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.AddPhotoAlternate,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(30.dp)
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = "添加猫猫窝封面",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            FilledTonalIconButton(
                                onClick = { coverPicker.launch(arrayOf("image/*")) },
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(10.dp),
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = Color(0x88161412),
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.AddPhotoAlternate,
                                    contentDescription = "更换封面",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AuthorAvatar(name = profile.name, size = 46.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = profile.name,
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (profile.signatureUnsynced || profile.coverUnsynced) {
                                            "本机签名或封面尚未公开，重新保存后同步"
                                        } else {
                                            "签名、封面和作品对其他猫友可见"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(Modifier.height(14.dp))

                            Surface(
                                onClick = {
                                    signatureDraft = profile.signature
                                    editingSignature = true
                                },
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = profile.signature.ifBlank { "还没有填写公开个性签名，点此写一句吧" },
                                        fontFamily = wenKai,
                                        fontSize = 15.sp,
                                        lineHeight = 22.sp,
                                        color = if (profile.signature.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                                        else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Icon(
                                        imageVector = Icons.Outlined.Edit,
                                        contentDescription = "编辑签名",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item(span = StaggeredGridItemSpan.FullLine) {
                val tabs = listOf(
                    "发布 ${myPosts.size}",
                    "点赞 ${likedPosts.size}",
                    "收藏 ${savedPosts.size}"
                )
                SecondaryTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.clip(RoundedCornerShape(16.dp)),
                    divider = {}
                ) {
                    tabs.forEachIndexed { idx, title ->
                        Tab(
                            selected = selectedTab == idx,
                            onClick = { selectedTab = idx },
                            text = { Text(title, style = MaterialTheme.typography.labelLarge) }
                        )
                    }
                }
            }

            if (activeList.isEmpty()) {
                item(span = StaggeredGridItemSpan.FullLine) {
                    GalleryEmptyState(
                        icon = Icons.Outlined.Pets,
                        title = when (selectedTab) {
                            0 -> "还没有发布过小猫"
                            1 -> "还没有点赞过小猫"
                            else -> "还没有收藏过小猫"
                        },
                        subtitle = when (selectedTab) {
                            0 -> "点击底部 + 分享你的第一张猫咪照片吧"
                            1 -> "在广场遇到喜欢的小猫时，点颗爱心吧"
                            else -> "看到心动的小猫，点亮书签收藏起来"
                        }
                    )
                }
            } else {
                items(activeList, key = { it.id }) { post ->
                    CompactPostCard(
                        post = post,
                        onOpenDetail = { onOpenPost(post) },
                        onToggleLike = { onToggleLike(post) },
                        onToggleSave = { onToggleSave(post) }
                    )
                }
            }
        }
    }

    if (editingSignature) {
        ModalBottomSheet(onDismissRequest = { editingSignature = false }) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .imePadding()
            ) {
                Text("编辑公开个性签名", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                GalleryTextField(
                    value = signatureDraft,
                    onValueChange = { if (it.length <= 120) signatureDraft = it },
                    placeholder = "写下你的个性签名（最多 120 字）",
                    singleLine = false,
                    minLines = 3,
                    maxLines = 5,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = wenKai)
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { editingSignature = false }) { Text("取消") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        editingSignature = false
                        onUpdateSignature(signatureDraft)
                    }) {
                        Text("保存并公开")
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}
