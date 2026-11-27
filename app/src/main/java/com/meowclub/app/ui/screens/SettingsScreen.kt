package com.meowclub.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meowclub.app.data.AppearanceMode
import com.meowclub.app.ui.components.GalleryTextField
import com.meowclub.app.ui.theme.AppFonts

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    appearance: AppearanceMode,
    currentName: String,
    hiddenCount: Int,
    onBack: () -> Unit,
    onChangeAppearance: (AppearanceMode) -> Unit,
    onUpdateNickname: (String) -> Unit,
    onRestoreHidden: () -> Unit,
    onPublishAnnouncement: (content: String, key: String, onResult: (Boolean, String?) -> Unit) -> Unit,
    onOpenHelp: () -> Unit,
    onOpenAbout: () -> Unit,
    onSignOut: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    var showNicknameSheet by remember { mutableStateOf(false) }
    var nicknameDraft by remember { mutableStateOf(currentName) }
    var showRestoreSheet by remember { mutableStateOf(false) }
    var showSignOutSheet by remember { mutableStateOf(false) }
    var showAnnouncementSheet by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        TopAppBar(
            title = { Text("设置", style = MaterialTheme.typography.titleLarge) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Palette,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("外观模式", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.height(12.dp))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        AppearanceMode.entries.forEachIndexed { idx, mode ->
                            SegmentedButton(
                                selected = appearance == mode,
                                onClick = { onChangeAppearance(mode) },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = idx,
                                    count = AppearanceMode.entries.size
                                )
                            ) {
                                Text(mode.label, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    ListItem(
                        headlineContent = { Text("编辑昵称") },
                        supportingContent = { Text(currentName) },
                        leadingContent = { Icon(Icons.Outlined.Badge, contentDescription = null) },
                        trailingContent = { Icon(Icons.Rounded.ChevronRight, contentDescription = null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable {
                            nicknameDraft = currentName
                            showNicknameSheet = true
                        }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                    )
                    ListItem(
                        headlineContent = { Text("恢复不感兴趣的帖子") },
                        supportingContent = { Text("当前已隐藏 $hiddenCount 篇（仅本机当前账号生效）") },
                        leadingContent = { Icon(Icons.Outlined.Visibility, contentDescription = null) },
                        trailingContent = { Icon(Icons.Rounded.ChevronRight, contentDescription = null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable {
                            if (hiddenCount == 0) onShowMessage("暂时没有隐藏的帖子")
                            else showRestoreSheet = true
                        }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                    )
                    ListItem(
                        headlineContent = { Text("管理员发布公告") },
                        supportingContent = { Text("需要登录及管理员口令") },
                        leadingContent = { Icon(Icons.Outlined.Campaign, contentDescription = null) },
                        trailingContent = { Icon(Icons.Rounded.ChevronRight, contentDescription = null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { showAnnouncementSheet = true }
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    ListItem(
                        headlineContent = { Text("使用帮助") },
                        leadingContent = { Icon(Icons.AutoMirrored.Rounded.HelpOutline, contentDescription = null) },
                        trailingContent = { Icon(Icons.Rounded.ChevronRight, contentDescription = null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable(onClick = onOpenHelp)
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                    )
                    ListItem(
                        headlineContent = { Text("关于 Cat-ch") },
                        supportingContent = { Text("版本 0.9.1") },
                        leadingContent = { Icon(Icons.Outlined.Info, contentDescription = null) },
                        trailingContent = { Icon(Icons.Rounded.ChevronRight, contentDescription = null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable(onClick = onOpenAbout)
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { showSignOutSheet = true }
            ) {
                ListItem(
                    headlineContent = {
                        Text("退出登录", color = MaterialTheme.colorScheme.error)
                    },
                    leadingContent = {
                        Icon(
                            Icons.AutoMirrored.Rounded.Logout,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        }
    }

    if (showNicknameSheet) {
        ModalBottomSheet(onDismissRequest = { showNicknameSheet = false }) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .imePadding()
            ) {
                Text("你希望怎么称呼？", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                GalleryTextField(
                    value = nicknameDraft,
                    onValueChange = { if (it.length <= 24) nicknameDraft = it },
                    placeholder = "输入昵称"
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { showNicknameSheet = false }) { Text("取消") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (nicknameDraft.trim().isNotEmpty()) {
                                showNicknameSheet = false
                                onUpdateNickname(nicknameDraft)
                            }
                        }
                    ) {
                        Text("保存")
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }

    if (showRestoreSheet) {
        ModalBottomSheet(onDismissRequest = { showRestoreSheet = false }) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text("恢复隐藏的帖子？", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(6.dp))
                Text(
                    "这些帖子会重新出现在广场、发现和随机看猫中。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { showRestoreSheet = false }) { Text("取消") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        showRestoreSheet = false
                        onRestoreHidden()
                    }) {
                        Text("恢复全部")
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (showSignOutSheet) {
        ModalBottomSheet(onDismissRequest = { showSignOutSheet = false }) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text("确认退出当前账号？", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(6.dp))
                Text(
                    "草稿会按账号保留在本地，重新登录后仍可继续编辑。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { showSignOutSheet = false }) { Text("取消") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            showSignOutSheet = false
                            onSignOut()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Text("退出登录")
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (showAnnouncementSheet) {
        var content by remember { mutableStateOf("") }
        var adminKey by remember { mutableStateOf("") }
        var sending by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf("") }

        ModalBottomSheet(onDismissRequest = { if (!sending) showAnnouncementSheet = false }) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("发布软件公告", style = MaterialTheme.typography.titleLarge)
                GalleryTextField(
                    value = content,
                    onValueChange = { if (it.length <= 500) content = it },
                    placeholder = "输入公告内容（最多 500 字）",
                    singleLine = false,
                    minLines = 3,
                    maxLines = 6,
                    enabled = !sending
                )
                GalleryTextField(
                    value = adminKey,
                    onValueChange = { adminKey = it },
                    placeholder = "管理员口令",
                    enabled = !sending,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Password)
                )
                if (error.isNotEmpty()) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = { showAnnouncementSheet = false },
                        enabled = !sending
                    ) { Text("取消") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        enabled = !sending,
                        onClick = {
                            if (content.trim().isEmpty() || adminKey.trim().isEmpty()) {
                                error = "请输入公告和管理员口令"
                                return@Button
                            }
                            sending = true
                            error = ""
                            onPublishAnnouncement(content, adminKey) { ok, err ->
                                sending = false
                                if (ok) showAnnouncementSheet = false
                                else error = err ?: "发布失败，请稍后重试"
                            }
                        }
                    ) {
                        if (sending) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("发布")
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoScreen(
    title: String,
    bodyText: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val wenKai = remember(context) { AppFonts.wenKai(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        TopAppBar(
            title = { Text(title, style = MaterialTheme.typography.titleLarge) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Text(
                text = bodyText,
                fontFamily = wenKai,
                fontSize = 16.sp,
                lineHeight = 26.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(20.dp)
            )
        }
    }
}
