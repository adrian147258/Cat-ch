package com.meowclub.app.data

import com.meowclub.app.BuildConfig

val CAT_BREEDS = listOf("橘猫", "布偶", "狸花", "英短", "美短", "三花", "其它")

data class Post(
    val id: String,
    val name: String,
    val text: String,
    val photoUrl: String,
    val breed: String,
    val mine: Boolean,
    val authorId: String,
    val imageCount: Int,
    val likeCount: Int,
    val liked: Boolean,
    val saved: Boolean,
    val createdAt: Long = 0L
) {
    fun photoUrls(): List<String> = List(imageCount.coerceIn(1, 9)) { index ->
        if (index == 0) photoUrl else "$photoUrl/$index"
    }
}

data class CommentItem(
    val id: String,
    val parentId: String?,
    val name: String,
    val text: String,
    val createdAt: Long
)

data class CardItem(
    val tier: String,
    val index: Int,
    val day: String,
    val createdAt: Long
) {
    val label: String
        get() = tierLabel(tier)

    val edition: String
        get() = "${(index + 1).toString().padStart(2, '0')} / ${tierCount(tier).toString().padStart(2, '0')}"

    val assetUri: String
        get() = "file:///android_asset/cards/${tier}_${index}.webp"

    companion object {
        const val TOTAL_CARDS = 43

        fun tierCount(tier: String): Int = when (tier) {
            "green" -> 20
            "blue" -> 10
            "purple" -> 8
            "gold" -> 5
            else -> 0
        }

        fun tierLabel(tier: String): String = when (tier) {
            "gold" -> "金卡"
            "purple" -> "紫卡"
            "blue" -> "蓝卡"
            else -> "绿卡"
        }

        fun valid(tier: String, index: Int): Boolean {
            val count = tierCount(tier)
            return count > 0 && index in 0 until count
        }
    }
}

sealed interface CardSceneRequest {
    data object UnopenedPack : CardSceneRequest
    data class PreviewCard(val tier: String, val index: Int) : CardSceneRequest
}

data class PublicProfileData(
    val id: String,
    val name: String,
    val signature: String,
    val coverUrl: String?,
    val postCount: Int,
    val posts: List<Post>,
    val nextBefore: Long?,
    val nextBeforeId: String?
)

data class AnnouncementData(
    val revision: String,
    val content: String
)

data class UpdateInfo(
    val tag: String,
    val apkUrl: String
)

enum class AppearanceMode(val key: String, val label: String) {
    SYSTEM("system", "跟随系统"),
    LIGHT("light", "浅色"),
    DARK("dark", "深色");

    companion object {
        fun fromKey(key: String?): AppearanceMode =
            entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

enum class MainTab(val label: String) {
    FEED("广场"),
    DISCOVER("发现"),
    CARDS("抽卡"),
    PROFILE("我的")
}

sealed interface OverlayRoute {
    data class PostDetail(val postId: String) : OverlayRoute
    data class PublicProfile(val userId: String, val displayName: String) : OverlayRoute
    data object Publish : OverlayRoute
    data object Settings : OverlayRoute
    data object Help : OverlayRoute
    data object About : OverlayRoute
}

fun isProfileCoverPath(userId: String, path: String): Boolean {
    if (!Regex("[A-Za-z0-9_-]{1,128}").matches(userId)) return false
    return Regex("/profile-covers/" + Regex.escape(userId) + "\\?v=[0-9a-f-]{36}").matches(path)
}

fun apiOrigin(): String = BuildConfig.API_BASE_URL
