package com.meowclub.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.meowclub.app.data.CAT_BREEDS
import com.meowclub.app.data.PublishUiState
import com.meowclub.app.ui.components.GalleryTextField
import com.meowclub.app.ui.theme.AppFonts
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PublishScreen(
    state: PublishUiState,
    onBack: () -> Unit,
    onImportPhotos: (List<Uri>) -> Unit,
    onReorderPhoto: (index: Int, action: String) -> Unit,
    onSelectBreed: (String) -> Unit,
    onTextChange: (String) -> Unit,
    onClearDraft: () -> Unit,
    onSubmit: (confirmedUncertain: Boolean) -> Unit,
    onOpenMyPosts: () -> Unit
) {
    val context = LocalContext.current
    val wenKai = remember(context) { AppFonts.wenKai(context) }

    var editingPhotoIndex by remember { mutableIntStateOf(-1) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showUncertainConfirm by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            onImportPhotos(uris)
        }
    }

    val busy = state.publishing || state.preparingPhotos

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        TopAppBar(
            title = { Text("分享小猫", style = MaterialTheme.typography.titleLarge) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                }
            },
            actions = {
                if (!busy && (state.photos.isNotEmpty() || state.text.isNotEmpty() || state.breed.isNotEmpty())) {
                    TextButton(onClick = { showClearConfirm = true }) {
                        Text("清空草稿")
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            if (state.photos.isEmpty()) {
                Surface(
                    onClick = { if (!busy) picker.launch(arrayOf("image/*")) },
                    enabled = !busy,
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(176.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AddPhotoAlternate,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "选择小猫照片",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "最多 9 张 · 原图最大 30MB 自动压缩",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "已选择 ${state.photos.size} 张照片（点击图片排序或移除）",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(
                            onClick = { if (!busy) picker.launch(arrayOf("image/*")) },
                            enabled = !busy
                        ) {
                            Text("重选")
                        }
                    }

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(vertical = 2.dp)
                    ) {
                        itemsIndexed(state.photos) { index, path ->
                            Box(
                                modifier = Modifier
                                    .size(116.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable(enabled = !busy) { editingPhotoIndex = index }
                            ) {
                                Image(
                                    painter = rememberAsyncImagePainter(File(path)),
                                    contentDescription = "第 ${index + 1} 张照片",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Surface(
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(6.dp),
                                    shape = RoundedCornerShape(999.dp),
                                    color = if (index == 0) MaterialTheme.colorScheme.primary else Color(0x99161412)
                                ) {
                                    Text(
                                        text = if (index == 0) "封面" else "${index + 1}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "猫咪种类（必选）",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CAT_BREEDS.forEach { breed ->
                        val selected = state.breed == breed
                        FilterChip(
                            selected = selected,
                            enabled = !busy,
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
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "猫咪故事（可选）",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${state.text.length}/200",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                GalleryTextField(
                    value = state.text,
                    onValueChange = onTextChange,
                    placeholder = "写下今天遇见它的心情或趣事…",
                    singleLine = false,
                    minLines = 4,
                    maxLines = 7,
                    enabled = !busy,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = wenKai,
                        fontSize = 16.sp,
                        lineHeight = 24.sp
                    )
                )
            }

            if (busy) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = when {
                                state.preparingPhotos -> state.prepareStatus
                                state.uploadPercent >= 100 -> "照片已上传，正在确认发布…"
                                else -> "正在上传照片 ${state.uploadPercent}% · 草稿已保存"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(10.dp))
                        if (state.preparingPhotos || state.uploadPercent >= 100) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        } else {
                            LinearProgressIndicator(
                                progress = { state.uploadPercent / 100f },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            } else {
                val statusText = when {
                    state.publishError.isNotEmpty() -> state.publishError
                    state.draftSaved -> "草稿已自动保存 · 发布后所有用户可见"
                    else -> "草稿未能保存，请检查手机剩余空间"
                }
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (state.publishError.isNotEmpty()) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (state.uploadUncertain) {
                OutlinedButton(
                    onClick = onOpenMyPosts,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("先查看我的发布")
                }
            }

            Spacer(Modifier.height(8.dp))
        }

        Surface(
            color = MaterialTheme.colorScheme.background,
            tonalElevation = 1.dp
        ) {
            Button(
                onClick = {
                    if (state.uploadUncertain) showUncertainConfirm = true
                    else onSubmit(false)
                },
                enabled = !busy,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .height(52.dp)
            ) {
                Text(
                    text = when {
                        state.publishing -> "正在发布…"
                        state.publishError.isNotEmpty() -> "重试发布"
                        else -> "发布猫咪"
                    },
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }

    if (editingPhotoIndex in state.photos.indices) {
        val index = editingPhotoIndex
        ModalBottomSheet(onDismissRequest = { editingPhotoIndex = -1 }) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                Text(
                    text = "第 ${index + 1} 张照片",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
                if (index > 0) {
                    ListItem(
                        headlineContent = { Text("设为封面") },
                        leadingContent = { Icon(Icons.Outlined.StarOutline, contentDescription = null) },
                        modifier = Modifier.clickable {
                            onReorderPhoto(index, "cover")
                            editingPhotoIndex = -1
                        }
                    )
                    ListItem(
                        headlineContent = { Text("向前移动") },
                        leadingContent = { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null) },
                        modifier = Modifier.clickable {
                            onReorderPhoto(index, "prev")
                            editingPhotoIndex = -1
                        }
                    )
                }
                if (index < state.photos.lastIndex) {
                    ListItem(
                        headlineContent = { Text("向后移动") },
                        leadingContent = { Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null) },
                        modifier = Modifier.clickable {
                            onReorderPhoto(index, "next")
                            editingPhotoIndex = -1
                        }
                    )
                }
                ListItem(
                    headlineContent = { Text("移除照片", color = MaterialTheme.colorScheme.error) },
                    leadingContent = {
                        Icon(
                            Icons.Outlined.DeleteOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    modifier = Modifier.clickable {
                        onReorderPhoto(index, "remove")
                        editingPhotoIndex = -1
                    }
                )
            }
        }
    }

    if (showClearConfirm) {
        ModalBottomSheet(onDismissRequest = { showClearConfirm = false }) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text("清空文字和已选照片？", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { showClearConfirm = false }) { Text("保留") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            showClearConfirm = false
                            onClearDraft()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Text("清空")
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (showUncertainConfirm) {
        ModalBottomSheet(onDismissRequest = { showUncertainConfirm = false }) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text("确认后再重试", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    "上次发布可能已经成功。建议先查看「我的发布」，确认没有该帖子后再重试。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = {
                        showUncertainConfirm = false
                        onOpenMyPosts()
                    }) {
                        Text("先去查看")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        showUncertainConfirm = false
                        onSubmit(true)
                    }) {
                        Text("确认未发布，重试")
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
