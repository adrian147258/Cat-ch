package com.meowclub.app.data

import android.content.Context
import android.content.SharedPreferences
import com.meowclub.app.ImageFiles
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Preserves 100% compatibility with the existing "meow" SharedPreferences schema
 * while isolating per-account local state cleanly on sign-out or account switch.
 */
class LocalPrefs(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("meow", Context.MODE_PRIVATE)

    var token: String
        get() = prefs.getString("account_token", "") ?: ""
        set(value) { prefs.edit().putString("account_token", value).apply() }

    var userId: String
        get() = prefs.getString("account_user_id", "") ?: ""
        set(value) { prefs.edit().putString("account_user_id", value).apply() }

    var userName: String
        get() = (prefs.getString("name", "猫猫爱好者") ?: "猫猫爱好者").ifBlank { "猫猫爱好者" }
        set(value) { prefs.edit().putString("name", value).apply() }

    var appearance: AppearanceMode
        get() = AppearanceMode.fromKey(prefs.getString("appearance", "system"))
        set(value) { prefs.edit().putString("appearance", value.key).apply() }

    var cloudCache: String
        get() = prefs.getString("cloud_cache", "{\"posts\":[]}") ?: "{\"posts\":[]}"
        set(value) { prefs.edit().putString("cloud_cache", value).apply() }

    var announcementSeen: String
        get() = prefs.getString("announcement_seen", "") ?: ""
        set(value) { prefs.edit().putString("announcement_seen", value).apply() }

    var legacyCloudToken: String
        get() = prefs.getString("cloud_token", "") ?: ""
        set(value) {
            if (value.isEmpty()) prefs.edit().remove("cloud_token").commit()
            else prefs.edit().putString("cloud_token", value).apply()
        }

    var compactFeedLayout: Boolean
        get() = prefs.getBoolean("feed_compact", false)
        set(value) { prefs.edit().putBoolean("feed_compact", value).apply() }

    fun isLiked(postId: String): Boolean = prefs.getBoolean("like$postId", false)
    fun isSaved(postId: String): Boolean = prefs.getBoolean("save$postId", false)

    fun setReaction(kind: String, postId: String, active: Boolean) {
        prefs.edit().putBoolean("$kind$postId", active).apply()
    }

    fun applyReactionBatch(updates: List<Triple<String, String, Boolean>>) {
        val editor = prefs.edit()
        for ((kind, postId, active) in updates) {
            editor.putBoolean("$kind$postId", active)
        }
        editor.apply()
    }

    fun profileSignature(): String =
        if (prefs.contains("profile_signature")) prefs.getString("profile_signature", "") ?: ""
        else prefs.getString("profile_public_signature", "") ?: ""

    fun profileSignatureUnsynced(): Boolean =
        prefs.contains("profile_signature") && !prefs.getBoolean("profile_signature_public", false)

    fun saveLocalSignature(value: String) {
        prefs.edit()
            .putString("profile_signature", value)
            .putBoolean("profile_signature_public", false)
            .apply()
    }

    fun markSignatureSynced(value: String) {
        val editor = prefs.edit().putString("profile_public_signature", value)
        if (value == (prefs.getString("profile_signature", "") ?: "")) {
            editor.putBoolean("profile_signature_public", true)
        }
        editor.apply()
    }

    fun localCoverPath(): String {
        val path = prefs.getString("profile_cover", "") ?: ""
        return if (path.isNotEmpty() && File(path).isFile) path else ""
    }

    fun publicCoverPath(): String = prefs.getString("profile_public_cover", "") ?: ""

    fun profileCoverUnsynced(): Boolean =
        localCoverPath().isNotEmpty() && !prefs.getBoolean("profile_cover_public", false)

    fun saveLocalCover(path: String): String {
        val previous = prefs.getString("profile_cover", "") ?: ""
        prefs.edit()
            .putString("profile_cover", path)
            .putBoolean("profile_cover_public", false)
            .apply()
        if (previous.isNotEmpty() && previous != path) {
            File(previous).delete()
        }
        return previous
    }

    fun markCoverSynced(userId: String, localPath: String, remoteCover: String) {
        val editor = prefs.edit()
            .putString("account_user_id", userId)
            .putString("profile_public_cover", remoteCover)
        if (localPath == (prefs.getString("profile_cover", "") ?: "")) {
            editor.putString("profile_synced_local_cover", localPath)
                .putString("profile_synced_cover", remoteCover)
                .putBoolean("profile_cover_public", true)
        }
        editor.apply()
    }

    fun syncOwnProfileFromServer(id: String, signature: String, cover: String) {
        val signaturePublic = prefs.contains("profile_signature") &&
            signature == (prefs.getString("profile_signature", "") ?: "")
        val localCover = prefs.getString("profile_cover", "") ?: ""
        val coverPublic = localCover.isNotEmpty() &&
            localCover == (prefs.getString("profile_synced_local_cover", "") ?: "") &&
            cover == (prefs.getString("profile_synced_cover", "") ?: "")
        prefs.edit()
            .putString("account_user_id", id)
            .putString("profile_public_signature", signature)
            .putString("profile_public_cover", cover)
            .putBoolean("profile_signature_public", signaturePublic)
            .putBoolean("profile_cover_public", coverPublic)
            .apply()
    }

    fun cardsCache(accountId: String): String =
        if (accountId.isEmpty()) "" else prefs.getString("cards_cache_$accountId", "") ?: ""

    fun saveCardsCache(accountId: String, serialized: String) {
        if (accountId.isNotEmpty()) {
            prefs.edit().putString("cards_cache_$accountId", serialized).apply()
        }
    }

    private fun hiddenKey(): String {
        val account = userId.ifEmpty { "guest" }
        return "hidden_posts_" + ImageFiles.key(account)
    }

    fun readHiddenPosts(): Set<String> =
        prefs.getStringSet(hiddenKey(), emptySet())?.toSet() ?: emptySet()

    fun writeHiddenPosts(ids: Set<String>) {
        if (ids.isEmpty()) prefs.edit().remove(hiddenKey()).apply()
        else prefs.edit().putStringSet(hiddenKey(), HashSet(ids)).apply()
    }

    fun readLegacyLocalPosts(): List<Post> {
        val list = ArrayList<Post>()
        try {
            val array = JSONArray(prefs.getString("posts", "[]"))
            for (i in array.length() - 1 downTo 0) {
                val o = array.getJSONObject(i)
                val id = o.getString("id")
                list.add(
                    0,
                    Post(
                        id = id,
                        name = "我",
                        text = o.getString("text"),
                        photoUrl = o.getString("photo"),
                        breed = o.optString("breed", ""),
                        mine = true,
                        authorId = "",
                        imageCount = 1,
                        likeCount = 0,
                        liked = isLiked(id),
                        saved = isSaved(id)
                    )
                )
            }
        } catch (_: Exception) {
        }
        return list
    }

    fun saveLegacyLocalPosts(posts: List<Post>) {
        val array = JSONArray()
        for (p in posts) {
            if (p.mine && p.id.startsWith("local")) {
                try {
                    array.put(
                        JSONObject()
                            .put("id", p.id)
                            .put("text", p.text)
                            .put("photo", p.photoUrl)
                            .put("breed", p.breed)
                    )
                } catch (_: Exception) {
                }
            }
        }
        prefs.edit().putString("posts", array.toString()).apply()
    }

    fun saveLoginSession(token: String, name: String, newUserId: String) {
        val previousUserId = prefs.getString("account_user_id", "") ?: ""
        val editor = prefs.edit()
            .putString("account_token", token)
            .putString("name", name.ifBlank { "猫猫爱好者" })
            .putString("account_user_id", newUserId)
        if (newUserId != previousUserId) {
            clearAccountSession(editor, removeLocalProfile = previousUserId.isNotEmpty())
        }
        editor.commit()
    }

    fun signOutSession() {
        val editor = prefs.edit()
            .remove("account_token")
            .remove("account_user_id")
        clearAccountSession(editor, removeLocalProfile = true)
        editor.commit()
    }

    fun clearTokenOnly() {
        prefs.edit().remove("account_token").commit()
    }

    private fun clearAccountSession(editor: SharedPreferences.Editor, removeLocalProfile: Boolean) {
        editor.remove("cloud_cache")
            .remove("profile_public_signature")
            .remove("profile_public_cover")
            .remove("announcement_seen")
            .putBoolean("profile_signature_public", false)
            .putBoolean("profile_cover_public", false)
        if (removeLocalProfile) {
            val localCover = prefs.getString("profile_cover", "") ?: ""
            if (localCover.isNotEmpty()) File(localCover).delete()
            editor.remove("profile_signature")
                .remove("profile_cover")
                .remove("profile_synced_local_cover")
                .remove("profile_synced_cover")
        }
        for (key in prefs.all.keys.toList()) {
            if (key.startsWith("likecloud_") || key.startsWith("savecloud_")) {
                editor.remove(key)
            }
        }
    }
}
