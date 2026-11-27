package com.meowclub.app.data

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import coil.Coil
import coil.memory.MemoryCache
import coil.request.ImageRequest
import com.meowclub.app.DraftStore
import com.meowclub.app.ImageFiles
import com.meowclub.app.UploadJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

data class AuthUiState(
    val busy: Boolean = false,
    val message: String = "",
    val isError: Boolean = false,
    val codeSentSuccess: Boolean = false,
    val resetDone: Boolean = false
)

data class PublishUiState(
    val draftId: String = UUID.randomUUID().toString(),
    val text: String = "",
    val breed: String = "",
    val photos: List<String> = emptyList(),
    val preparingPhotos: Boolean = false,
    val prepareStatus: String = "",
    val publishing: Boolean = false,
    val uploadPercent: Int = 0,
    val uploadUncertain: Boolean = false,
    val publishError: String = "",
    val draftSaved: Boolean = true
)

data class ProfileUiState(
    val userId: String = "",
    val name: String = "猫猫爱好者",
    val signature: String = "",
    val coverSource: String = "",
    val signatureUnsynced: Boolean = false,
    val coverUnsynced: Boolean = false
)

data class CardsUiState(
    val cards: List<CardItem> = emptyList(),
    val drawnToday: Boolean = false,
    val loading: Boolean = false,
    val drawing: Boolean = false,
    val error: String = ""
)

data class CommentsState(
    val loading: Boolean = false,
    val posting: Boolean = false,
    val items: List<CommentItem> = emptyList(),
    val error: String = ""
)

data class PublicProfileState(
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val error: String = "",
    val profile: PublicProfileData? = null
)

data class AppUiState(
    val isAuthenticated: Boolean = false,
    val appearance: AppearanceMode = AppearanceMode.SYSTEM,
    val currentTab: MainTab = MainTab.FEED,
    val backStack: List<OverlayRoute> = emptyList(),
    val compactFeed: Boolean = false,
    val posts: List<Post> = emptyList(),
    val feedLoading: Boolean = false,
    val feedError: String = "",
    val hiddenPostIds: Set<String> = emptySet(),
    val searchQuery: String = "",
    val selectedBreed: String = "全部",
    val auth: AuthUiState = AuthUiState(),
    val publish: PublishUiState = PublishUiState(),
    val profile: ProfileUiState = ProfileUiState(),
    val cards: CardsUiState = CardsUiState(),
    val commentsByPost: Map<String, CommentsState> = emptyMap(),
    val publicProfiles: Map<String, PublicProfileState> = emptyMap(),
    val activePhotoViewer: Pair<List<String>, Int>? = null,
    val activeCardScene: CardSceneRequest? = null,
    val activeAnnouncement: AnnouncementData? = null,
    val availableUpdate: UpdateInfo? = null
)

class CatViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = LocalPrefs(application)
    private val draftStore = DraftStore(application)
    private val pendingReactions = HashSet<String>()
    private var draftAccount = ""
    private var feedRequestSerial = 0
    private var lastEncounterId = ""
    private var draftSaveJob: Job? = null
    private var updateChecked = false
    private var announcementShowing = false

    private val api = CatApi(onUnauthorized = { usedToken ->
        handleUnauthorized(usedToken)
    })

    private val _state = MutableStateFlow(
        AppUiState(
            isAuthenticated = prefs.token.isNotEmpty(),
            appearance = prefs.appearance,
            compactFeed = prefs.compactFeedLayout
        )
    )
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val uploadListener = UploadJob.Listener { job ->
        onUploadJobChanged(job)
    }

    init {
        if (prefs.token.isNotEmpty()) {
            enterApp()
        }
    }

    override fun onCleared() {
        UploadJob.current?.detach(uploadListener)
        super.onCleared()
    }

    fun showMessage(msg: String) {
        if (msg.isNotBlank()) _messages.tryEmit(msg)
    }

    // ---- Navigation ----

    fun selectTab(tab: MainTab) {
        _state.update { it.copy(currentTab = tab, backStack = emptyList()) }
    }

    fun openRoute(route: OverlayRoute) {
        _state.update { state ->
            if (state.backStack.lastOrNull() == route) state
            else state.copy(backStack = state.backStack + route)
        }
        when (route) {
            is OverlayRoute.PostDetail -> loadComments(route.postId)
            is OverlayRoute.PublicProfile -> loadPublicProfile(route.userId, route.displayName)
            OverlayRoute.Publish -> persistDraftNow()
            else -> {}
        }
    }

    fun popBack(): Boolean {
        val current = _state.value
        if (current.activePhotoViewer != null) {
            closePhotoViewer()
            return true
        }
        if (current.activeCardScene != null) {
            closeCardScene()
            return true
        }
        if (current.backStack.isNotEmpty()) {
            if (current.backStack.last() == OverlayRoute.Publish) {
                persistDraftNow()
            }
            _state.update { it.copy(backStack = it.backStack.dropLast(1)) }
            return true
        }
        if (current.currentTab != MainTab.FEED) {
            _state.update { it.copy(currentTab = MainTab.FEED) }
            return true
        }
        return false
    }

    fun openPhotoViewer(urls: List<String>, initialIndex: Int) {
        if (urls.isNotEmpty()) {
            _state.update { it.copy(activePhotoViewer = urls to initialIndex.coerceIn(0, urls.lastIndex)) }
        }
    }

    fun closePhotoViewer() {
        _state.update { it.copy(activePhotoViewer = null) }
    }

    fun openUnopenedPack() {
        if (_state.value.cards.drawing) return
        if (_state.value.cards.drawnToday) {
            showMessage("今天已经抽过啦，请明日再来")
            return
        }
        _state.update { it.copy(activeCardScene = CardSceneRequest.UnopenedPack) }
    }

    fun openCardScene(tier: String, index: Int) {
        if (_state.value.cards.drawing) {
            showMessage("正在打开今日卡包")
            return
        }
        if (!CardItem.valid(tier, index)) {
            showMessage("卡片资料暂时无法读取")
            return
        }
        _state.update { it.copy(activeCardScene = CardSceneRequest.PreviewCard(tier, index)) }
    }

    fun closeCardScene(didDraw: Boolean = false) {
        _state.update { it.copy(activeCardScene = null) }
        if (didDraw) refreshCards()
    }

    // ---- Auth ----

    fun clearAuthFeedback() {
        _state.update { it.copy(auth = AuthUiState()) }
    }

    fun sendSignUpCode(name: String, email: String, password: String) {
        runAuthTask(login = false) { api.signUpEmail(name, email, password) }
    }

    fun sendSignInCode(email: String) {
        runAuthTask(login = false) { api.sendSignInOtp(email) }
    }

    fun registerWithCode(email: String, code: String) {
        runAuthTask(login = true) { api.verifyEmail(email, code) }
    }

    fun loginWithPassword(email: String, password: String) {
        runAuthTask(login = true) { api.signInPassword(email, password) }
    }

    fun loginWithCode(email: String, code: String) {
        runAuthTask(login = true) { api.signInOtp(email, code) }
    }

    fun sendResetCode(email: String) {
        viewModelScope.launch {
            _state.update { it.copy(auth = AuthUiState(busy = true)) }
            try {
                api.requestPasswordReset(email)
                _state.update {
                    it.copy(
                        auth = AuthUiState(
                            busy = false,
                            message = "如邮箱已注册，验证码将发送至邮箱",
                            isError = false,
                            codeSentSuccess = true
                        )
                    )
                }
            } catch (_: Exception) {
                _state.update {
                    it.copy(auth = AuthUiState(busy = false, message = "发送失败，请稍后重试", isError = true))
                }
            }
        }
    }

    fun resetPassword(email: String, code: String, newPassword: String) {
        viewModelScope.launch {
            _state.update { it.copy(auth = AuthUiState(busy = true)) }
            try {
                api.resetPassword(email, code, newPassword)
                _state.update {
                    it.copy(
                        auth = AuthUiState(
                            busy = false,
                            message = "密码已重置，请使用新密码登录",
                            isError = false,
                            resetDone = true
                        )
                    )
                }
            } catch (_: Exception) {
                _state.update {
                    it.copy(auth = AuthUiState(busy = false, message = "重置失败，请检查验证码或稍后重试", isError = true))
                }
            }
        }
    }

    private fun runAuthTask(login: Boolean, block: suspend () -> JSONObject) {
        viewModelScope.launch {
            _state.update { it.copy(auth = AuthUiState(busy = true)) }
            try {
                val result = block()
                if (login) {
                    val token = result.optString("token")
                    if (token.isEmpty()) throw ApiException("请先验证邮箱，再登录")
                    val loginUser = result.optJSONObject("user")
                    val userId = loginUser?.optString("id", "") ?: ""
                    val userName = loginUser?.optString("name", "猫猫爱好者") ?: "猫猫爱好者"
                    prefs.saveLoginSession(token, userName, userId)
                    _state.update { it.copy(auth = AuthUiState()) }
                    enterApp()
                } else {
                    _state.update {
                        it.copy(
                            auth = AuthUiState(
                                busy = false,
                                message = "验证码已发送，请查看邮箱",
                                isError = false,
                                codeSentSuccess = true
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                val msg = if (e is ApiException) (e.message ?: "连接失败，请稍后重试") else "连接失败，请稍后重试"
                _state.update {
                    it.copy(auth = AuthUiState(busy = false, message = msg, isError = true))
                }
            }
        }
    }

    fun signOut() {
        val pub = _state.value.publish
        if (pub.publishing || pub.preparingPhotos) {
            showMessage("请等待照片处理完成后退出登录")
            return
        }
        persistDraftNow()
        val token = prefs.token
        viewModelScope.launch {
            if (token.isNotEmpty()) {
                try { api.signOut(token) } catch (_: Exception) {}
            }
            prefs.signOutSession()
            pendingReactions.clear()
            draftAccount = ""
            announcementShowing = false
            _state.update {
                AppUiState(
                    isAuthenticated = false,
                    appearance = prefs.appearance,
                    compactFeed = prefs.compactFeedLayout
                )
            }
        }
    }

    @Synchronized
    private fun handleUnauthorized(usedToken: String) {
        if (usedToken.isEmpty() || usedToken != prefs.token) return
        prefs.clearTokenOnly()
        viewModelScope.launch(Dispatchers.Main) {
            if (!_state.value.isAuthenticated) return@launch
            pendingReactions.clear()
            announcementShowing = false
            _state.update {
                AppUiState(
                    isAuthenticated = false,
                    appearance = prefs.appearance,
                    compactFeed = prefs.compactFeedLayout,
                    auth = AuthUiState(message = "登录已过期，请重新登录", isError = true)
                )
            }
        }
    }

    // ---- App Entry & Sync ----

    private fun enterApp() {
        pendingReactions.clear()
        val hidden = prefs.readHiddenPosts()
        val cachedPosts = restoreInitialPosts()
        _state.update {
            it.copy(
                isAuthenticated = true,
                currentTab = MainTab.FEED,
                backStack = emptyList(),
                hiddenPostIds = hidden,
                posts = cachedPosts,
                profile = buildProfileUiState()
            )
        }
        restoreDraft()
        restoreCardsCache()
        preheatPostPhotos(cachedPosts)
        refreshFeed(notify = false) {
            loadOwnProfile()
            loadAnnouncement(manual = false)
        }
        refreshCards()
        migrateLegacyIfNeeded()
        if (!updateChecked) {
            updateChecked = true
            viewModelScope.launch {
                delay(2500)
                if (_state.value.isAuthenticated) {
                    val version = try {
                        getApplication<Application>().packageManager
                            .getPackageInfo(getApplication<Application>().packageName, 0).versionName ?: "0.9.0"
                    } catch (_: Exception) { "0.9.0" }
                    val update = api.checkForUpdate(version)
                    if (update != null && _state.value.isAuthenticated) {
                        _state.update { it.copy(availableUpdate = update) }
                    }
                }
            }
        }
    }

    private fun restoreInitialPosts(): List<Post> {
        val localPosts = prefs.readLegacyLocalPosts()
        val cloud = try {
            val raw = JSONObject(prefs.cloudCache)
            val (parsed, updates) = api.parseCloudPosts(
                data = raw,
                pendingReactions = emptySet(),
                existingLikes = { prefs.isLiked(it) },
                existingSaves = { prefs.isSaved(it) },
                existingLikeCount = { 0 }
            )
            prefs.applyReactionBatch(updates)
            parsed
        } catch (_: Exception) {
            emptyList()
        }
        return cloud + localPosts
    }

    private fun migrateLegacyIfNeeded() {
        val legacy = prefs.legacyCloudToken
        val token = prefs.token
        if (legacy.isEmpty() || token.isEmpty()) return
        viewModelScope.launch {
            try {
                api.migrateAnonymous(token, legacy)
                prefs.legacyCloudToken = ""
                refreshFeed(notify = false)
            } catch (_: Exception) {
                showMessage("旧发布暂未同步，稍后登录会重试")
            }
        }
    }

    // ---- Feed, Search & Encounter ----

    fun toggleFeedLayout() {
        val next = !_state.value.compactFeed
        prefs.compactFeedLayout = next
        _state.update { it.copy(compactFeed = next) }
    }

    fun refreshFeed(notify: Boolean = true, onDone: (() -> Unit)? = null) {
        val requestToken = prefs.token
        if (requestToken.isEmpty()) {
            onDone?.invoke()
            return
        }
        val requestSerial = ++feedRequestSerial
        _state.update { it.copy(feedLoading = true, feedError = "") }
        viewModelScope.launch {
            try {
                val raw = api.fetchPostsRaw(requestToken)
                if (requestToken != prefs.token || requestSerial != feedRequestSerial) {
                    onDone?.invoke()
                    return@launch
                }
                val existingById = _state.value.posts.associateBy { it.id }
                val (cloudPosts, reactionUpdates) = api.parseCloudPosts(
                    data = raw,
                    pendingReactions = pendingReactions,
                    existingLikes = { id -> existingById[id]?.liked ?: prefs.isLiked(id) },
                    existingSaves = { id -> existingById[id]?.saved ?: prefs.isSaved(id) },
                    existingLikeCount = { id -> existingById[id]?.likeCount ?: 0 }
                )
                prefs.applyReactionBatch(reactionUpdates)
                prefs.cloudCache = raw.toString()
                val localLegacy = _state.value.posts.filter { !it.id.startsWith("cloud_") }
                val merged = cloudPosts + localLegacy
                _state.update {
                    it.copy(
                        posts = merged,
                        feedLoading = false,
                        feedError = ""
                    )
                }
                preheatPostPhotos(merged)
                if (notify) showMessage("已刷新")
            } catch (_: Exception) {
                if (requestToken != prefs.token || requestSerial != feedRequestSerial) {
                    onDone?.invoke()
                    return@launch
                }
                val err = if (_state.value.posts.isEmpty()) "联网失败，请检查网络" else "刷新失败，当前显示已保存的内容"
                _state.update { it.copy(feedLoading = false, feedError = err) }
                if (notify) showMessage(err)
            } finally {
                onDone?.invoke()
            }
        }
    }

    fun randomCatEncounter() {
        val current = _state.value
        val candidates = current.posts.filter {
            !current.hiddenPostIds.contains(it.id) && it.photoUrl.isNotEmpty()
        }
        if (candidates.isEmpty()) {
            showMessage(if (current.feedLoading) "猫猫还在赶来，稍等一下吧" else "还没有可偶遇的小猫，下拉刷新试试")
            return
        }
        val pool = if (candidates.size > 1) candidates.filter { it.id != lastEncounterId } else candidates
        val selected = pool.random()
        lastEncounterId = selected.id
        openRoute(OverlayRoute.PostDetail(selected.id))
    }

    fun updateSearchQuery(query: String) {
        _state.update { it.copy(searchQuery = query) }
    }

    fun selectBreedFilter(breed: String) {
        _state.update { it.copy(selectedBreed = breed) }
    }

    fun hidePost(postId: String) {
        val updated = _state.value.hiddenPostIds + postId
        prefs.writeHiddenPosts(updated)
        _state.update { it.copy(hiddenPostIds = updated) }
        showMessage("已隐藏，可在设置中恢复")
    }

    fun restoreAllHiddenPosts() {
        prefs.writeHiddenPosts(emptySet())
        _state.update { it.copy(hiddenPostIds = emptySet()) }
        showMessage("已恢复全部帖子")
    }

    // ---- Reactions ----

    fun toggleReaction(post: Post, kind: String) {
        val key = "$kind:${post.id}"
        if (pendingReactions.contains(key)) return
        val before = if (kind == "like") post.liked else post.saved
        val after = !before
        prefs.setReaction(kind, post.id, after)
        val optimisticCount = if (kind == "like") (post.likeCount + if (after) 1 else -1).coerceAtLeast(0) else post.likeCount

        updatePostAcrossState(post.id) { p ->
            if (kind == "like") p.copy(liked = after, likeCount = optimisticCount)
            else p.copy(saved = after)
        }

        if (!post.id.startsWith("cloud_")) return
        val token = prefs.token
        if (token.isEmpty()) return
        pendingReactions.add(key)
        viewModelScope.launch {
            try {
                val serverLikes = api.reactPost(token, post.id, kind, after)
                pendingReactions.remove(key)
                if (token != prefs.token) return@launch
                if (kind == "like" && serverLikes >= 0) {
                    updatePostAcrossState(post.id) { it.copy(likeCount = serverLikes) }
                }
            } catch (_: Exception) {
                pendingReactions.remove(key)
                if (token != prefs.token) return@launch
                prefs.setReaction(kind, post.id, before)
                updatePostAcrossState(post.id) { p ->
                    if (kind == "like") p.copy(liked = before, likeCount = (p.likeCount + if (before) 1 else -1).coerceAtLeast(0))
                    else p.copy(saved = before)
                }
                showMessage("操作没成功，请检查网络")
            }
        }
    }

    private fun updatePostAcrossState(postId: String, transform: (Post) -> Post) {
        _state.update { state ->
            val updatedPosts = state.posts.map { if (it.id == postId) transform(it) else it }
            val updatedProfiles = state.publicProfiles.mapValues { (_, profileState) ->
                val p = profileState.profile ?: return@mapValues profileState
                if (p.posts.none { it.id == postId }) profileState
                else profileState.copy(profile = p.copy(posts = p.posts.map { if (it.id == postId) transform(it) else it }))
            }
            state.copy(posts = updatedPosts, publicProfiles = updatedProfiles)
        }
    }

    // ---- Detail, Delete & Comments ----

    fun deletePost(post: Post) {
        if (!post.id.startsWith("cloud_")) {
            val remaining = _state.value.posts.filterNot { it.id == post.id }
            prefs.saveLegacyLocalPosts(remaining)
            _state.update { it.copy(posts = remaining) }
            popBack()
            showMessage("已删除")
            return
        }
        val token = prefs.token
        if (token.isEmpty()) return
        viewModelScope.launch {
            try {
                api.deletePost(token, post.id)
                _state.update { state ->
                    state.copy(posts = state.posts.filterNot { it.id == post.id })
                }
                if (_state.value.backStack.lastOrNull() == OverlayRoute.PostDetail(post.id)) {
                    popBack()
                }
                showMessage("已删除")
                refreshFeed(notify = false)
            } catch (_: Exception) {
                showMessage("删除失败，请检查网络")
            }
        }
    }

    fun loadComments(postId: String) {
        val token = prefs.token
        if (token.isEmpty() || !postId.startsWith("cloud_")) return
        _state.update { state ->
            val prev = state.commentsByPost[postId] ?: CommentsState()
            state.copy(commentsByPost = state.commentsByPost + (postId to prev.copy(loading = true, error = "")))
        }
        viewModelScope.launch {
            try {
                val list = api.fetchComments(token, postId)
                _state.update { state ->
                    val prev = state.commentsByPost[postId] ?: CommentsState()
                    state.copy(commentsByPost = state.commentsByPost + (postId to prev.copy(loading = false, items = list, error = "")))
                }
            } catch (_: Exception) {
                _state.update { state ->
                    val prev = state.commentsByPost[postId] ?: CommentsState()
                    state.copy(commentsByPost = state.commentsByPost + (postId to prev.copy(loading = false, error = "评论暂时加载失败，请稍后重试")))
                }
            }
        }
    }

    fun sendComment(postId: String, text: String, parentId: String?, onSuccess: () -> Unit) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            showMessage("请先输入内容")
            return
        }
        val token = prefs.token
        if (token.isEmpty()) return
        _state.update { state ->
            val prev = state.commentsByPost[postId] ?: CommentsState()
            state.copy(commentsByPost = state.commentsByPost + (postId to prev.copy(posting = true)))
        }
        viewModelScope.launch {
            try {
                api.postComment(token, postId, trimmed, parentId)
                _state.update { state ->
                    val prev = state.commentsByPost[postId] ?: CommentsState()
                    state.copy(commentsByPost = state.commentsByPost + (postId to prev.copy(posting = false)))
                }
                onSuccess()
                loadComments(postId)
            } catch (e: Exception) {
                _state.update { state ->
                    val prev = state.commentsByPost[postId] ?: CommentsState()
                    state.copy(commentsByPost = state.commentsByPost + (postId to prev.copy(posting = false)))
                }
                showMessage(if (e is ApiException) (e.message ?: "发送失败，请稍后重试") else "发送失败，请稍后重试")
            }
        }
    }

    // ---- Profile & Public Profile ----

    private fun buildProfileUiState(): ProfileUiState {
        val localCover = prefs.localCoverPath()
        val publicCover = prefs.publicCoverPath()
        val userId = prefs.userId
        val coverSource = when {
            localCover.isNotEmpty() -> localCover
            isProfileCoverPath(userId, publicCover) -> apiOrigin() + publicCover
            else -> ""
        }
        return ProfileUiState(
            userId = userId,
            name = prefs.userName,
            signature = prefs.profileSignature(),
            coverSource = coverSource,
            signatureUnsynced = prefs.profileSignatureUnsynced(),
            coverUnsynced = localCover.isNotEmpty() && prefs.profileCoverUnsynced()
        )
    }

    private fun refreshProfileUi() {
        _state.update { it.copy(profile = buildProfileUiState()) }
    }

    fun loadOwnProfile() {
        val token = prefs.token
        if (token.isEmpty()) return
        viewModelScope.launch {
            try {
                val (id, sig, rawCover) = api.fetchOwnProfile(token)
                if (token != prefs.token || !Regex("[A-Za-z0-9_-]{1,128}").matches(id)) return@launch
                val cover = if (rawCover.isNotEmpty() && isProfileCoverPath(id, rawCover)) rawCover else ""
                prefs.syncOwnProfileFromServer(id, sig, cover)
                if (draftAccount.isEmpty()) {
                    val pub = _state.value.publish
                    if (!pub.publishing && !pub.preparingPhotos && pub.photos.isEmpty() && pub.text.isEmpty() && pub.breed.isEmpty()) {
                        restoreDraft()
                    } else {
                        draftAccount = id
                    }
                }
                refreshProfileUi()
            } catch (_: Exception) {
            }
        }
    }

    fun updateNickname(newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        prefs.userName = trimmed
        refreshProfileUi()
        showMessage("昵称已保存")
    }

    fun updateSignature(value: String) {
        val trimmed = value.trim()
        prefs.saveLocalSignature(trimmed)
        refreshProfileUi()
        val token = prefs.token
        if (token.isEmpty()) {
            showMessage("签名已保存在本机，请登录后公开")
            return
        }
        showMessage("正在同步公开签名…")
        viewModelScope.launch {
            try {
                api.updateSignature(token, trimmed)
                if (token != prefs.token) return@launch
                prefs.markSignatureSynced(trimmed)
                refreshProfileUi()
                showMessage("签名已公开")
            } catch (_: Exception) {
                if (token == prefs.token) showMessage("签名已保存在本机，公开同步失败，请重试")
            }
        }
    }

    fun saveAndUploadProfileCover(uri: Uri) {
        showMessage("正在保存封面…")
        viewModelScope.launch {
            val savedPath = withContext(Dispatchers.IO) {
                var bitmap: Bitmap? = null
                var original: File? = null
                var target: File? = null
                try {
                    val app = getApplication<Application>()
                    original = File.createTempFile("cover-orig-", ".tmp", app.cacheDir)
                    app.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(original).use { output ->
                            ImageFiles.copy(input, output, ImageFiles.MAX_ORIGINAL)
                        }
                    } ?: throw IOException("Invalid image")
                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(original.path, opts)
                    if (opts.outWidth <= 0 || opts.outHeight <= 0) throw IOException("Invalid image")
                    opts.inSampleSize = ImageFiles.sampleSize(opts.outWidth, opts.outHeight, 1600)
                    opts.inJustDecodeBounds = false
                    bitmap = BitmapFactory.decodeFile(original.path, opts) ?: throw IOException("Invalid image")
                    bitmap = applyExifOrientation(bitmap, original.path)
                    target = File(app.filesDir, "cover_${System.nanoTime()}.jpg")
                    for (quality in intArrayOf(90, 80, 70, 60, 50)) {
                        FileOutputStream(target).use { out ->
                            if (!bitmap!!.compress(Bitmap.CompressFormat.JPEG, quality, out)) {
                                throw IOException("Unable to save image")
                            }
                        }
                        if (target.length() <= 2_000_000) break
                    }
                    if (target.length() > 2_000_000) throw IOException("Image too large")
                    target.absolutePath
                } catch (_: Exception) {
                    target?.delete()
                    null
                } finally {
                    original?.delete()
                    bitmap?.recycle()
                }
            }
            if (savedPath == null) {
                showMessage("图片未能读取，请换一张试试")
                return@launch
            }
            prefs.saveLocalCover(savedPath)
            refreshProfileUi()
            syncProfileCover(savedPath)
        }
    }

    private fun syncProfileCover(filePath: String) {
        val token = prefs.token
        if (token.isEmpty()) {
            showMessage("封面已保存在本机，请登录后公开")
            return
        }
        showMessage("正在同步公开封面…")
        viewModelScope.launch {
            try {
                if (token != prefs.token || filePath != prefs.localCoverPath()) return@launch
                val bytes = withContext(Dispatchers.IO) {
                    FileInputStream(filePath).use { it.readBytes() }
                }
                val (respId, cover) = api.uploadCover(token, bytes)
                if (token != prefs.token) return@launch
                val userId = respId.ifEmpty { prefs.userId }
                if (!isProfileCoverPath(userId, cover)) throw IOException("Invalid cover response")
                prefs.markCoverSynced(userId, filePath, cover)
                refreshProfileUi()
                showMessage("封面已公开")
            } catch (_: Exception) {
                if (token == prefs.token) showMessage("封面已保存在本机，公开同步失败，请重试")
            }
        }
    }

    fun loadPublicProfile(userId: String, displayName: String) {
        val token = prefs.token
        if (token.isEmpty()) return
        if (!Regex("[A-Za-z0-9_-]{1,128}").matches(userId)) {
            _state.update {
                it.copy(publicProfiles = it.publicProfiles + (userId to PublicProfileState(loading = false, error = "这位猫友的主页暂不可用")))
            }
            return
        }
        _state.update {
            it.copy(publicProfiles = it.publicProfiles + (userId to PublicProfileState(loading = true, error = "")))
        }
        viewModelScope.launch {
            try {
                val (profileData, reactionUpdates) = api.fetchPublicProfile(
                    token = token,
                    ownerId = userId,
                    ownerNameFallback = displayName,
                    isLiked = { prefs.isLiked(it) },
                    isSaved = { prefs.isSaved(it) }
                )
                prefs.applyReactionBatch(reactionUpdates.filter { (kind, id, _) -> !pendingReactions.contains("$kind:$id") })
                _state.update {
                    it.copy(publicProfiles = it.publicProfiles + (userId to PublicProfileState(loading = false, profile = profileData)))
                }
            } catch (_: Exception) {
                _state.update {
                    it.copy(publicProfiles = it.publicProfiles + (userId to PublicProfileState(loading = false, error = "主页加载失败，点击重试")))
                }
            }
        }
    }

    fun loadMorePublicWorks(userId: String) {
        val currentState = _state.value.publicProfiles[userId] ?: return
        val currentProfile = currentState.profile ?: return
        val before = currentProfile.nextBefore ?: return
        val beforeId = currentProfile.nextBeforeId ?: return
        if (currentState.loadingMore) return
        val token = prefs.token
        if (token.isEmpty()) return

        _state.update {
            it.copy(publicProfiles = it.publicProfiles + (userId to currentState.copy(loadingMore = true)))
        }
        viewModelScope.launch {
            try {
                val (nextPage, reactionUpdates) = api.fetchPublicProfile(
                    token = token,
                    ownerId = userId,
                    ownerNameFallback = currentProfile.name,
                    before = before,
                    beforeId = beforeId,
                    isLiked = { prefs.isLiked(it) },
                    isSaved = { prefs.isSaved(it) }
                )
                prefs.applyReactionBatch(reactionUpdates.filter { (kind, id, _) -> !pendingReactions.contains("$kind:$id") })
                val existingIds = currentProfile.posts.map { it.id }.toHashSet()
                val mergedPosts = currentProfile.posts + nextPage.posts.filter { existingIds.add(it.id) }
                val updatedProfile = currentProfile.copy(
                    posts = mergedPosts,
                    nextBefore = nextPage.nextBefore,
                    nextBeforeId = nextPage.nextBeforeId
                )
                _state.update {
                    it.copy(publicProfiles = it.publicProfiles + (userId to PublicProfileState(loading = false, loadingMore = false, profile = updatedProfile)))
                }
            } catch (_: Exception) {
                _state.update {
                    it.copy(publicProfiles = it.publicProfiles + (userId to currentState.copy(loadingMore = false)))
                }
                showMessage("更多作品加载失败，请重试")
            }
        }
    }

    // ---- Publish & Draft ----

    private fun restoreDraft() {
        draftAccount = prefs.userId
        val saved = draftStore.read(draftAccount)
        _state.update {
            it.copy(
                publish = PublishUiState(
                    draftId = saved.id,
                    text = saved.text,
                    breed = saved.breed,
                    photos = saved.photos.toList(),
                    uploadUncertain = saved.pending,
                    publishError = if (saved.pending) "上次上传结果未确认，请先查看我的发布再重试" else ""
                )
            )
        }
        val activeJob = UploadJob.current
        if (activeJob != null && activeJob.account == draftAccount) {
            activeJob.attach(uploadListener)
        }
    }

    private fun toDraftSnapshot(pub: PublishUiState): DraftStore.Draft = DraftStore.Draft().apply {
        id = pub.draftId
        text = pub.text
        breed = pub.breed
        pending = pub.publishing || pub.uploadUncertain
        photos.addAll(pub.photos)
    }

    private fun persistDraftNow(): Boolean {
        draftSaveJob?.cancel()
        if (draftAccount.isEmpty()) draftAccount = prefs.userId
        val pub = _state.value.publish
        if (draftAccount.isEmpty() || pub.draftId.isEmpty()) return false
        if (pub.publishing) return true
        val ok = draftStore.save(draftAccount, toDraftSnapshot(pub))
        _state.update { it.copy(publish = it.publish.copy(draftSaved = ok)) }
        return ok
    }

    fun updateDraftText(text: String) {
        val clipped = text.take(200)
        _state.update { it.copy(publish = it.publish.copy(text = clipped)) }
        draftSaveJob?.cancel()
        draftSaveJob = viewModelScope.launch {
            delay(400)
            persistDraftNow()
        }
    }

    fun updateDraftBreed(breed: String) {
        _state.update { it.copy(publish = it.publish.copy(breed = breed)) }
        persistDraftNow()
    }

    fun reorderDraftPhoto(index: Int, action: String) {
        val pub = _state.value.publish
        if (pub.publishing || pub.preparingPhotos || index !in pub.photos.indices) return
        val list = pub.photos.toMutableList()
        var removedPath: String? = null
        when (action) {
            "cover" -> {
                val path = list.removeAt(index)
                list.add(0, path)
            }
            "prev" -> if (index > 0) {
                val tmp = list[index - 1]
                list[index - 1] = list[index]
                list[index] = tmp
            }
            "next" -> if (index < list.lastIndex) {
                val tmp = list[index + 1]
                list[index + 1] = list[index]
                list[index] = tmp
            }
            "remove" -> removedPath = list.removeAt(index)
        }
        _state.update { it.copy(publish = it.publish.copy(photos = list)) }
        if (persistDraftNow() && removedPath != null) {
            draftStore.deletePhotos(listOf(removedPath))
        }
    }

    fun clearDraft() {
        val pub = _state.value.publish
        if (pub.publishing || pub.preparingPhotos) return
        if (draftAccount.isEmpty()) draftAccount = prefs.userId
        if (!draftStore.clear(draftAccount, pub.draftId)) {
            showMessage("草稿未能清空，请重试")
            return
        }
        _state.update {
            it.copy(publish = PublishUiState(draftId = UUID.randomUUID().toString()))
        }
    }

    fun importDraftPhotos(uris: List<Uri>) {
        val pub = _state.value.publish
        if (pub.preparingPhotos || pub.publishing || uris.isEmpty()) return
        if (uris.size > 9) {
            showMessage("最多选择 9 张照片，请重新选择")
            return
        }
        if (draftAccount.isEmpty()) draftAccount = prefs.userId
        val account = draftAccount
        _state.update {
            it.copy(publish = it.publish.copy(preparingPhotos = true, prepareStatus = "正在压缩照片…", publishError = ""))
        }

        viewModelScope.launch {
            val prepared = ArrayList<String>()
            var errorMsg: String? = null
            withContext(Dispatchers.IO) {
                val app = getApplication<Application>()
                for (i in uris.indices) {
                    val number = i + 1
                    _state.update {
                        it.copy(publish = it.publish.copy(prepareStatus = "正在处理第 $number / ${uris.size} 张照片…"))
                    }
                    var bitmap: Bitmap? = null
                    var original: File? = null
                    var file: File? = null
                    try {
                        original = File.createTempFile("original-", ".tmp", app.cacheDir)
                        app.contentResolver.openInputStream(uris[i])?.use { input ->
                            FileOutputStream(original).use { output ->
                                ImageFiles.copy(input, output, ImageFiles.MAX_ORIGINAL)
                            }
                        } ?: throw IOException("无法读取图片")
                        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(original.path, options)
                        if (options.outWidth <= 0 || options.outHeight <= 0) throw IOException("图片格式无法读取，请换一张")
                        options.inSampleSize = ImageFiles.sampleSize(options.outWidth, options.outHeight, 1920)
                        options.inJustDecodeBounds = false
                        bitmap = BitmapFactory.decodeFile(original.path, options) ?: throw IOException("无法读取图片")
                        bitmap = applyExifOrientation(bitmap, original.path)
                        file = File.createTempFile("photo-", ".jpg", draftStore.directory)
                        for (quality in intArrayOf(88, 78, 68, 58, 48)) {
                            FileOutputStream(file).use { out ->
                                if (!bitmap!!.compress(Bitmap.CompressFormat.JPEG, quality, out)) {
                                    throw IOException("图片保存失败")
                                }
                            }
                            if (file.length() <= ImageFiles.MAX_UPLOAD) break
                        }
                        if (file.length() <= 0 || file.length() > ImageFiles.MAX_UPLOAD) {
                            throw IOException("图片压缩后仍超过 5MB，请换一张")
                        }
                        prepared.add(file.absolutePath)
                    } catch (e: Exception) {
                        file?.delete()
                        errorMsg = e.message ?: "有图片未能读取，请重新选择"
                        break
                    } finally {
                        original?.delete()
                        bitmap?.recycle()
                    }
                }
            }

            if (account != draftAccount || errorMsg != null) {
                draftStore.deletePhotos(prepared)
                _state.update {
                    it.copy(
                        publish = it.publish.copy(
                            preparingPhotos = false,
                            prepareStatus = "",
                            publishError = errorMsg ?: it.publish.publishError
                        )
                    )
                }
                return@launch
            }

            val previousPhotos = _state.value.publish.photos
            _state.update {
                it.copy(publish = it.publish.copy(preparingPhotos = false, prepareStatus = "", photos = prepared))
            }
            if (persistDraftNow()) {
                draftStore.deletePhotos(previousPhotos)
                _state.update { it.copy(publish = it.publish.copy(publishError = "")) }
            } else {
                _state.update {
                    it.copy(publish = it.publish.copy(photos = previousPhotos, publishError = "草稿保存失败，请检查手机剩余空间"))
                }
                draftStore.deletePhotos(prepared)
            }
        }
    }

    fun submitPublish(confirmedAfterUncertain: Boolean = false) {
        val pub = _state.value.publish
        if (pub.publishing || pub.preparingPhotos) return
        if (pub.photos.isEmpty()) {
            showMessage("先选一张小猫照片吧")
            return
        }
        if (pub.breed.isEmpty()) {
            showMessage("请选择小猫种类")
            return
        }
        if (pub.uploadUncertain && !confirmedAfterUncertain) return
        if (UploadJob.current != null && !UploadJob.current!!.finished) {
            showMessage("已有照片正在上传，请稍等")
            return
        }
        val trimmedText = pub.text.trim()
        _state.update { it.copy(publish = it.publish.copy(text = trimmedText, uploadUncertain = false)) }
        if (!persistDraftNow()) {
            _state.update { it.copy(publish = it.publish.copy(publishError = "草稿保存失败，请检查手机剩余空间")) }
            return
        }
        val token = prefs.token
        if (token.isEmpty()) {
            showMessage("请先登录，草稿已保留")
            return
        }
        val snapshot = toDraftSnapshot(_state.value.publish).apply { pending = true }
        if (!draftStore.save(draftAccount, snapshot)) {
            showMessage("草稿保存失败，请重试")
            return
        }
        _state.update {
            it.copy(publish = it.publish.copy(publishing = true, publishError = "", uploadPercent = 0))
        }
        val job = UploadJob(getApplication(), draftAccount, snapshot)
        UploadJob.current = job
        job.attach(uploadListener)
        job.start(apiOrigin(), token, prefs.userName)
    }

    private fun onUploadJobChanged(job: UploadJob) {
        if (job.account != draftAccount || job.account != prefs.userId) return
        _state.update {
            it.copy(publish = it.publish.copy(publishing = !job.finished, uploadPercent = job.percent))
        }
        if (!job.finished) return
        job.detach(uploadListener)
        if (UploadJob.current === job) UploadJob.current = null
        if (job.succeeded) {
            draftSaveJob?.cancel()
            _state.update { state ->
                val trimmedStack = if (state.backStack.lastOrNull() == OverlayRoute.Publish) {
                    state.backStack.dropLast(1)
                } else state.backStack
                state.copy(
                    currentTab = MainTab.PROFILE,
                    backStack = trimmedStack,
                    publish = PublishUiState(draftId = UUID.randomUUID().toString())
                )
            }
            showMessage("发布成功！")
            refreshFeed(notify = false)
        } else {
            _state.update {
                it.copy(
                    publish = it.publish.copy(
                        publishing = false,
                        uploadUncertain = job.uncertain,
                        publishError = job.error
                    )
                )
            }
            showMessage("发布未完成，草稿已保留")
        }
    }

    private fun applyExifOrientation(bitmap: Bitmap, path: String): Bitmap {
        var orientation = ExifInterface.ORIENTATION_NORMAL
        try {
            orientation = ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } catch (_: IOException) {
        }
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(-90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
        }
        if (matrix.isIdentity) return bitmap
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    // ---- Cards ----

    private fun parseCardArray(array: JSONArray): List<CardItem> {
        val list = ArrayList<CardItem>(array.length())
        for (i in 0 until array.length()) {
            val rec = array.optJSONObject(i) ?: continue
            val tier = rec.optString("tier")
            val index = rec.optInt("card_index", -1)
            if (CardItem.valid(tier, index)) {
                list.add(
                    CardItem(
                        tier = tier,
                        index = index,
                        day = rec.optString("day", ""),
                        createdAt = rec.optLong("created_at", 0L)
                    )
                )
            }
        }
        return list
    }

    private fun restoreCardsCache() {
        val account = prefs.userId
        val saved = prefs.cardsCache(account)
        if (saved.isNotEmpty()) {
            try {
                val parsed = parseCardArray(JSONArray(saved))
                _state.update { it.copy(cards = it.cards.copy(cards = parsed)) }
            } catch (_: Exception) {
            }
        }
    }

    fun refreshCards(onDone: (() -> Unit)? = null) {
        val token = prefs.token
        val account = prefs.userId
        if (token.isEmpty()) {
            onDone?.invoke()
            return
        }
        _state.update { it.copy(cards = it.cards.copy(loading = true, error = "")) }
        viewModelScope.launch {
            try {
                val (album, drawnToday) = api.fetchCards(token)
                if (token != prefs.token) {
                    onDone?.invoke()
                    return@launch
                }
                val serialized = album.toString()
                prefs.saveCardsCache(account, serialized)
                val parsed = parseCardArray(album)
                _state.update {
                    it.copy(cards = it.cards.copy(cards = parsed, drawnToday = drawnToday, loading = false, error = ""))
                }
            } catch (_: Exception) {
                if (token != prefs.token) {
                    onDone?.invoke()
                    return@launch
                }
                _state.update {
                    it.copy(
                        cards = it.cards.copy(
                            loading = false,
                            error = if (it.cards.cards.isEmpty()) "卡册加载失败，点击重试" else ""
                        )
                    )
                }
            } finally {
                onDone?.invoke()
            }
        }
    }

    private fun preheatPostPhotos(posts: List<Post>) {
        val app = getApplication<Application>()
        val loader = Coil.imageLoader(app)
        posts.asSequence()
            .filter { it.photoUrl.startsWith("https://") }
            .take(6)
            .forEach { post ->
                loader.enqueue(
                    ImageRequest.Builder(app)
                        .data(post.photoUrl)
                        .memoryCacheKey(post.photoUrl)
                        .diskCacheKey(post.photoUrl)
                        .build()
                )
            }
    }

    fun commitDailyCardDraw(onResult: (Result<Pair<String, Int>>) -> Unit) {
        if (_state.value.cards.drawing) return
        val token = prefs.token
        if (token.isEmpty()) {
            onResult(Result.failure(ApiException("请先登录")))
            return
        }
        _state.update { it.copy(cards = it.cards.copy(drawing = true)) }
        viewModelScope.launch {
            try {
                val (tier, index) = api.drawCard(token)
                if (token != prefs.token) return@launch
                if (!CardItem.valid(tier, index)) throw ApiException("卡片资料暂时无法读取")
                _state.update { it.copy(cards = it.cards.copy(drawing = false, drawnToday = true)) }
                onResult(Result.success(tier to index))
            } catch (e: Exception) {
                if (token != prefs.token) return@launch
                _state.update { it.copy(cards = it.cards.copy(drawing = false)) }
                val msg = if (e is ApiException) (e.message ?: "连接失败，请稍后再试") else "连接失败，请稍后再试"
                showMessage(msg)
                onResult(Result.failure(ApiException(msg)))
            }
        }
    }

    // ---- Settings, Announcement & Update ----

    fun setAppearance(mode: AppearanceMode) {
        prefs.appearance = mode
        _state.update { it.copy(appearance = mode) }
    }

    fun loadAnnouncement(manual: Boolean) {
        val token = prefs.token
        if (token.isEmpty()) return
        viewModelScope.launch {
            try {
                val item = api.fetchAnnouncement(token)
                if (token != prefs.token) return@launch
                if (item == null) {
                    if (manual) showMessage("暂时没有公告")
                    return@launch
                }
                if (announcementShowing || (!manual && item.revision == prefs.announcementSeen)) return@launch
                announcementShowing = true
                _state.update { it.copy(activeAnnouncement = item) }
            } catch (_: Exception) {
                if (manual) showMessage("公告加载失败，请稍后重试")
            }
        }
    }

    fun dismissAnnouncement() {
        val current = _state.value.activeAnnouncement
        if (current != null) {
            prefs.announcementSeen = current.revision
        }
        announcementShowing = false
        _state.update { it.copy(activeAnnouncement = null) }
    }

    fun publishAnnouncement(content: String, adminKey: String, onResult: (Boolean, String?) -> Unit) {
        val token = prefs.token
        if (token.isEmpty()) {
            onResult(false, "请先登录")
            return
        }
        viewModelScope.launch {
            try {
                val res = api.publishAnnouncement(token, content.trim(), adminKey.trim())
                prefs.announcementSeen = res.revision
                showMessage("公告已发布")
                onResult(true, null)
            } catch (e: Exception) {
                onResult(false, if (e is ApiException) (e.message ?: "发布失败，请稍后重试") else "发布失败，请稍后重试")
            }
        }
    }

    fun dismissUpdate() {
        _state.update { it.copy(availableUpdate = null) }
    }
}
