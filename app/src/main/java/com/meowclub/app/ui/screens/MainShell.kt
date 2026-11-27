package com.meowclub.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meowclub.app.data.CatViewModel
import com.meowclub.app.data.MainTab
import com.meowclub.app.data.OverlayRoute
import com.meowclub.app.data.PublicProfileState
import com.meowclub.app.ui.components.CardSceneDialogOverlay
import com.meowclub.app.ui.components.FloatingCatsBackground
import com.meowclub.app.ui.components.NavCatSprites
import com.meowclub.app.ui.components.PhotoViewerDialog
import com.meowclub.app.ui.theme.AppFonts
import com.meowclub.app.ui.theme.CatchTheme
import kotlinx.coroutines.flow.collectLatest

@Composable
fun MainAppRoot(viewModel: CatViewModel) {
    val uiState by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.messages.collectLatest { msg ->
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(msg)
        }
    }

    CatchTheme(appearance = uiState.appearance) {
        if (!uiState.isAuthenticated) {
            AuthScreen(
                appearance = uiState.appearance,
                authState = uiState.auth,
                onClearFeedback = viewModel::clearAuthFeedback,
                onSendSignUpCode = viewModel::sendSignUpCode,
                onSendSignInCode = viewModel::sendSignInCode,
                onRegister = viewModel::registerWithCode,
                onLoginPassword = viewModel::loginWithPassword,
                onLoginCode = viewModel::loginWithCode,
                onSendResetCode = viewModel::sendResetCode,
                onResetPassword = viewModel::resetPassword
            )
            return@CatchTheme
        }

        BackHandler(
            enabled = uiState.backStack.isNotEmpty() ||
                uiState.currentTab != MainTab.FEED ||
                uiState.activePhotoViewer != null ||
                uiState.activeCardScene != null
        ) {
            viewModel.popBack()
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            FloatingCatsBackground(
                appearance = uiState.appearance,
                modifier = Modifier.fillMaxSize()
            )
            Scaffold(
                containerColor = Color.Transparent,
                snackbarHost = {
                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                },
                bottomBar = {
                    if (uiState.backStack.isEmpty()) {
                        GalleryBottomBar(
                            currentTab = uiState.currentTab,
                            onSelectTab = viewModel::selectTab,
                            onOpenPublish = { viewModel.openRoute(OverlayRoute.Publish) }
                        )
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                ) {
                    when (uiState.currentTab) {
                        MainTab.FEED -> FeedScreen(
                            posts = uiState.posts,
                            hiddenIds = uiState.hiddenPostIds,
                            loading = uiState.feedLoading,
                            error = uiState.feedError,
                            compactMode = uiState.compactFeed,
                            bottomInsetPadding = innerPadding,
                            onRefresh = { viewModel.refreshFeed(notify = true) },
                            onToggleCompact = viewModel::toggleFeedLayout,
                            onEncounter = viewModel::randomCatEncounter,
                            onOpenPost = { post -> viewModel.openRoute(OverlayRoute.PostDetail(post.id)) },
                            onOpenAuthor = { post ->
                                if (!post.mine && post.authorId.isNotEmpty()) {
                                    viewModel.openRoute(OverlayRoute.PublicProfile(post.authorId, post.name))
                                }
                            },
                            onToggleLike = { post -> viewModel.toggleReaction(post, "like") },
                            onToggleSave = { post -> viewModel.toggleReaction(post, "save") },
                            onHidePost = { post -> viewModel.hidePost(post.id) }
                        )

                        MainTab.DISCOVER -> DiscoverScreen(
                            posts = uiState.posts,
                            hiddenIds = uiState.hiddenPostIds,
                            query = uiState.searchQuery,
                            selectedBreed = uiState.selectedBreed,
                            bottomInsetPadding = innerPadding,
                            onQueryChange = viewModel::updateSearchQuery,
                            onSelectBreed = viewModel::selectBreedFilter,
                            onOpenPost = { post -> viewModel.openRoute(OverlayRoute.PostDetail(post.id)) },
                            onToggleLike = { post -> viewModel.toggleReaction(post, "like") },
                            onToggleSave = { post -> viewModel.toggleReaction(post, "save") },
                            onHidePost = { post -> viewModel.hidePost(post.id) }
                        )

                        MainTab.CARDS -> CardsScreen(
                            state = uiState.cards,
                            bottomInsetPadding = innerPadding,
                            onRefresh = { viewModel.refreshCards() },
                            onDrawToday = viewModel::openUnopenedPack,
                            onOpenCardPreview = { card ->
                                viewModel.openCardScene(card.tier, card.index)
                            }
                        )

                        MainTab.PROFILE -> ProfileScreen(
                            profile = uiState.profile,
                            posts = uiState.posts,
                            bottomInsetPadding = innerPadding,
                            onOpenSettings = { viewModel.openRoute(OverlayRoute.Settings) },
                            onOpenAnnouncement = { viewModel.loadAnnouncement(manual = true) },
                            onUploadCover = viewModel::saveAndUploadProfileCover,
                            onUpdateSignature = viewModel::updateSignature,
                            onOpenPost = { post -> viewModel.openRoute(OverlayRoute.PostDetail(post.id)) },
                            onToggleLike = { post -> viewModel.toggleReaction(post, "like") },
                            onToggleSave = { post -> viewModel.toggleReaction(post, "save") }
                        )
                    }
                }
            }

            val topRoute = uiState.backStack.lastOrNull()
            AnimatedContent(
                targetState = topRoute,
                transitionSpec = {
                    if (targetState != null) {
                        (slideInHorizontally { it / 3 } + fadeIn()) togetherWith fadeOut()
                    } else {
                        fadeIn() togetherWith (slideOutHorizontally { it / 3 } + fadeOut())
                    }
                },
                label = "overlayRoute"
            ) { route ->
                when (route) {
                    is OverlayRoute.PostDetail -> {
                        val post = uiState.posts.firstOrNull { it.id == route.postId }
                            ?: uiState.publicProfiles.values.asSequence()
                                .mapNotNull { it.profile?.posts?.firstOrNull { p -> p.id == route.postId } }
                                .firstOrNull()
                        val commentsState = uiState.commentsByPost[route.postId] ?: com.meowclub.app.data.CommentsState()
                        PostDetailScreen(
                            post = post,
                            commentsState = commentsState,
                            onBack = { viewModel.popBack() },
                            onOpenPhotos = viewModel::openPhotoViewer,
                            onOpenAuthor = { target ->
                                if (!target.mine && target.authorId.isNotEmpty()) {
                                    viewModel.openRoute(OverlayRoute.PublicProfile(target.authorId, target.name))
                                }
                            },
                            onToggleLike = { target -> viewModel.toggleReaction(target, "like") },
                            onToggleSave = { target -> viewModel.toggleReaction(target, "save") },
                            onDeletePost = viewModel::deletePost,
                            onRetryComments = { viewModel.loadComments(route.postId) },
                            onSendComment = { text, parentId, onSuccess ->
                                viewModel.sendComment(route.postId, text, parentId, onSuccess)
                            }
                        )
                    }

                    is OverlayRoute.PublicProfile -> {
                        val profileState = uiState.publicProfiles[route.userId] ?: PublicProfileState()
                        PublicProfileScreen(
                            displayName = route.displayName,
                            state = profileState,
                            onBack = { viewModel.popBack() },
                            onRetry = { viewModel.loadPublicProfile(route.userId, route.displayName) },
                            onLoadMore = { viewModel.loadMorePublicWorks(route.userId) },
                            onOpenPost = { post -> viewModel.openRoute(OverlayRoute.PostDetail(post.id)) },
                            onToggleLike = { post -> viewModel.toggleReaction(post, "like") },
                            onToggleSave = { post -> viewModel.toggleReaction(post, "save") }
                        )
                    }

                    OverlayRoute.Publish -> PublishScreen(
                        state = uiState.publish,
                        onBack = { viewModel.popBack() },
                        onImportPhotos = viewModel::importDraftPhotos,
                        onReorderPhoto = viewModel::reorderDraftPhoto,
                        onSelectBreed = viewModel::updateDraftBreed,
                        onTextChange = viewModel::updateDraftText,
                        onClearDraft = viewModel::clearDraft,
                        onSubmit = viewModel::submitPublish,
                        onOpenMyPosts = { viewModel.selectTab(MainTab.PROFILE) }
                    )

                    OverlayRoute.Settings -> SettingsScreen(
                        appearance = uiState.appearance,
                        currentName = uiState.profile.name,
                        hiddenCount = uiState.hiddenPostIds.size,
                        onBack = { viewModel.popBack() },
                        onChangeAppearance = viewModel::setAppearance,
                        onUpdateNickname = viewModel::updateNickname,
                        onRestoreHidden = viewModel::restoreAllHiddenPosts,
                        onPublishAnnouncement = viewModel::publishAnnouncement,
                        onOpenHelp = { viewModel.openRoute(OverlayRoute.Help) },
                        onOpenAbout = { viewModel.openRoute(OverlayRoute.About) },
                        onSignOut = viewModel::signOut,
                        onShowMessage = viewModel::showMessage
                    )

                    OverlayRoute.Help -> InfoScreen(
                        title = "使用帮助",
                        bodyText = "如何使用猫猫社\n\n在广场浏览小猫，点爱心点赞，点书签收藏。\n点底部加号选择照片，发布到自己的猫猫窝。\n列表滑到顶部后继续下拉，松手获取最新内容。\n在广场或发现长按他人帖子可隐藏，在设置中随时恢复。",
                        onBack = { viewModel.popBack() }
                    )

                    OverlayRoute.About -> InfoScreen(
                        title = "关于 Cat-ch",
                        bodyText = "Cat-ch 0.9.1\n\n分享每一只可爱的小猫。\n支持邮箱密码或邮箱验证码登录、多图故事、评论回复与每日抽卡。照片和帖子发布后对其他使用者可见。",
                        onBack = { viewModel.popBack() }
                    )

                    null -> {}
                }
            }
        }

        uiState.activePhotoViewer?.let { (urls, initial) ->
            PhotoViewerDialog(
                sources = urls,
                initialIndex = initial,
                onDismiss = viewModel::closePhotoViewer
            )
        }

        uiState.activeCardScene?.let { req ->
            CardSceneDialogOverlay(
                request = req,
                appearance = uiState.appearance,
                onCommitDraw = viewModel::commitDailyCardDraw,
                onDismiss = { didDraw -> viewModel.closeCardScene(didDraw) }
            )
        }

        uiState.activeAnnouncement?.let { announcement ->
            val wenKai = remember(context) { AppFonts.wenKai(context) }
            AlertDialog(
                onDismissRequest = viewModel::dismissAnnouncement,
                title = { Text("软件公告") },
                text = {
                    Text(
                        text = announcement.content,
                        fontFamily = wenKai,
                        fontSize = 16.sp,
                        lineHeight = 24.sp
                    )
                },
                confirmButton = {
                    Button(onClick = viewModel::dismissAnnouncement) { Text("知道了") }
                }
            )
        }

        uiState.availableUpdate?.let { update ->
            AlertDialog(
                onDismissRequest = viewModel::dismissUpdate,
                title = { Text("发现新版本 ${update.tag}") },
                text = { Text("有新的 Cat-ch 版本，可以前往下载并安装。已安装的数据通常会保留。") },
                dismissButton = {
                    TextButton(onClick = viewModel::dismissUpdate) { Text("稍后") }
                },
                confirmButton = {
                    Button(onClick = {
                        viewModel.dismissUpdate()
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(update.apkUrl)))
                    }) {
                        Text("前往下载")
                    }
                }
            )
        }
    }
}

@Composable
private fun GalleryBottomBar(
    currentTab: MainTab,
    onSelectTab: (MainTab) -> Unit,
    onOpenPublish: () -> Unit
) {
    val context = LocalContext.current
    val catSprites = remember(context) { NavCatSprites.sprites(context) }

    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                BottomNavItem(
                    label = MainTab.FEED.label,
                    selected = currentTab == MainTab.FEED,
                    catSprite = catSprites.getOrNull(0),
                    selectedIcon = Icons.Rounded.Home,
                    unselectedIcon = Icons.Outlined.Home,
                    onClick = { onSelectTab(MainTab.FEED) },
                    modifier = Modifier.weight(1f)
                )
                BottomNavItem(
                    label = MainTab.DISCOVER.label,
                    selected = currentTab == MainTab.DISCOVER,
                    catSprite = catSprites.getOrNull(1),
                    selectedIcon = Icons.Rounded.Explore,
                    unselectedIcon = Icons.Outlined.Explore,
                    onClick = { onSelectTab(MainTab.DISCOVER) },
                    modifier = Modifier.weight(1f)
                )

                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    val earColor = MaterialTheme.colorScheme.primary
                    Box(
                        modifier = Modifier.size(width = 52.dp, height = 46.dp),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(12.dp)
                                .align(Alignment.TopCenter)
                        ) {
                            val leftEar = Path().apply {
                                moveTo(size.width * 0.14f, size.height)
                                lineTo(size.width * 0.24f, 0f)
                                lineTo(size.width * 0.40f, size.height)
                                close()
                            }
                            val rightEar = Path().apply {
                                moveTo(size.width * 0.86f, size.height)
                                lineTo(size.width * 0.76f, 0f)
                                lineTo(size.width * 0.60f, size.height)
                                close()
                            }
                            drawPath(leftEar, color = earColor)
                            drawPath(rightEar, color = earColor)
                        }
                        Surface(
                            onClick = onOpenPublish,
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            shadowElevation = 2.dp,
                            modifier = Modifier.size(width = 50.dp, height = 38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = "发布猫猫",
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }

                BottomNavItem(
                    label = MainTab.CARDS.label,
                    selected = currentTab == MainTab.CARDS,
                    catSprite = catSprites.getOrNull(2),
                    selectedIcon = Icons.Rounded.Style,
                    unselectedIcon = Icons.Outlined.Style,
                    onClick = { onSelectTab(MainTab.CARDS) },
                    modifier = Modifier.weight(1f)
                )
                BottomNavItem(
                    label = MainTab.PROFILE.label,
                    selected = currentTab == MainTab.PROFILE,
                    catSprite = catSprites.getOrNull(3),
                    selectedIcon = Icons.Rounded.Person,
                    unselectedIcon = Icons.Outlined.Person,
                    onClick = { onSelectTab(MainTab.PROFILE) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun BottomNavItem(
    label: String,
    selected: Boolean,
    catSprite: ImageBitmap?,
    selectedIcon: ImageVector,
    unselectedIcon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val catScale by animateFloatAsState(
        targetValue = if (selected) 1.12f else 0.96f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "navCatScale"
    )
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                else Color.Transparent
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (catSprite != null) {
            Image(
                bitmap = catSprite,
                contentDescription = label,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(34.dp)
                    .scale(catScale)
            )
        } else {
            Icon(
                imageVector = if (selected) selectedIcon else unselectedIcon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(23.dp)
            )
        }
        Spacer(Modifier.height(1.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = tint
        )
    }
}
