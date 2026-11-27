package com.meowclub.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.nio.charset.StandardCharsets
import javax.net.ssl.SSLException

class ApiException(message: String) : IOException(message)

class CatApi(
    private val baseUrl: String = apiOrigin(),
    private val onUnauthorized: (String) -> Unit
) {
    private fun readBounded(stream: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (true) {
            val n = stream.read(buf)
            if (n == -1) break
            if (out.size() + n > 5_000_000) throw IOException("响应过大")
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private suspend fun requestJson(
        method: String,
        path: String,
        body: ByteArray? = null,
        token: String? = null,
        extraHeaders: Map<String, String> = emptyMap()
    ): JSONObject = withContext(Dispatchers.IO) {
        val conn = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            requestMethod = method
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Origin", baseUrl)
            if (!token.isNullOrEmpty()) {
                setRequestProperty("Authorization", "Bearer $token")
            }
            for ((k, v) in extraHeaders) {
                setRequestProperty(k, v)
            }
        }
        try {
            if (body != null) {
                conn.doOutput = true
                conn.setFixedLengthStreamingMode(body.size.toLong())
                conn.outputStream.use { it.write(body) }
            }
            val status = conn.responseCode
            val stream = if (status >= 400) conn.errorStream else conn.inputStream
            val payload = stream?.use { readBounded(it) } ?: ByteArray(0)
            if (status == 401 && !token.isNullOrEmpty()) {
                onUnauthorized(token)
            }
            val json = try {
                JSONObject(String(payload, StandardCharsets.UTF_8))
            } catch (_: Exception) {
                throw ApiException("服务器返回异常（HTTP $status），请检查后端地址和服务状态")
            }
            if (status !in 200..299) {
                val msg = json.optString("message", json.optString("error", "服务器暂时无法连接"))
                throw ApiException(msg)
            }
            json
        } catch (e: ApiException) {
            throw e
        } catch (_: UnknownHostException) {
            throw ApiException("无法解析服务器域名，请检查网络和后端地址")
        } catch (_: SocketTimeoutException) {
            throw ApiException("连接服务器超时，请检查网络或稍后重试")
        } catch (_: SSLException) {
            throw ApiException("无法建立安全连接，请检查手机时间和服务器证书")
        } catch (_: ConnectException) {
            throw ApiException("无法连接服务器，请检查后端服务是否正常运行")
        } finally {
            conn.disconnect()
        }
    }

    private suspend fun authPost(path: String, json: JSONObject): JSONObject =
        requestJson(
            method = "POST",
            path = "/api/auth/$path",
            body = json.toString().toByteArray(StandardCharsets.UTF_8),
            extraHeaders = mapOf("Content-Type" to "application/json")
        )

    suspend fun signUpEmail(name: String, email: String, password: String): JSONObject =
        authPost("sign-up/email", JSONObject().put("name", name).put("email", email).put("password", password))

    suspend fun sendSignInOtp(email: String): JSONObject =
        authPost("email-otp/send-verification-otp", JSONObject().put("email", email).put("type", "sign-in"))

    suspend fun verifyEmail(email: String, otp: String): JSONObject =
        authPost("email-otp/verify-email", JSONObject().put("email", email).put("otp", otp))

    suspend fun signInPassword(email: String, password: String): JSONObject =
        authPost("sign-in/email", JSONObject().put("email", email).put("password", password))

    suspend fun signInOtp(email: String, otp: String): JSONObject =
        authPost("sign-in/email-otp", JSONObject().put("email", email).put("otp", otp))

    suspend fun requestPasswordReset(email: String): JSONObject =
        authPost("email-otp/request-password-reset", JSONObject().put("email", email))

    suspend fun resetPassword(email: String, otp: String, password: String): JSONObject =
        authPost("email-otp/reset-password", JSONObject().put("email", email).put("otp", otp).put("password", password))

    suspend fun signOut(token: String) {
        requestJson(
            method = "POST",
            path = "/api/auth/sign-out",
            body = "{}".toByteArray(StandardCharsets.UTF_8),
            token = token,
            extraHeaders = mapOf("Content-Type" to "application/json")
        )
    }

    suspend fun fetchPostsRaw(token: String): JSONObject =
        requestJson("GET", "/posts", token = token)

    suspend fun reactPost(token: String, postId: String, kind: String, active: Boolean): Int {
        val body = JSONObject().put("active", active).toString().toByteArray(StandardCharsets.UTF_8)
        val res = requestJson(
            method = "PUT",
            path = "/posts/$postId/$kind",
            body = body,
            token = token,
            extraHeaders = mapOf("Content-Type" to "application/json")
        )
        return res.optInt("like_count", -1)
    }

    suspend fun deletePost(token: String, postId: String) {
        requestJson("DELETE", "/posts/$postId", token = token)
    }

    suspend fun fetchComments(token: String, postId: String): List<CommentItem> {
        val res = requestJson("GET", "/posts/$postId/comments", token = token)
        val arr = res.getJSONArray("comments")
        val list = ArrayList<CommentItem>(arr.length())
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val parent = item.optString("parent_id", "").let { if (it == "null" || it.isEmpty()) null else it }
            list.add(
                CommentItem(
                    id = item.optString("id"),
                    parentId = parent,
                    name = item.optString("name", "猫猫爱好者"),
                    text = item.optString("text", ""),
                    createdAt = item.optLong("created_at", 0L)
                )
            )
        }
        return list
    }

    suspend fun postComment(token: String, postId: String, text: String, parentId: String?) {
        val data = JSONObject().put("text", text)
        if (!parentId.isNullOrEmpty()) data.put("parent_id", parentId)
        requestJson(
            method = "POST",
            path = "/posts/$postId/comments",
            body = data.toString().toByteArray(StandardCharsets.UTF_8),
            token = token,
            extraHeaders = mapOf("Content-Type" to "application/json")
        )
    }

    suspend fun fetchOwnProfile(token: String): Triple<String, String, String> {
        val data = requestJson("GET", "/profile", token = token)
        val id = data.optString("id", "")
        val signature = data.optString("signature", "")
        val cover = if (data.isNull("cover")) "" else data.optString("cover", "")
        return Triple(id, signature, cover)
    }

    suspend fun updateSignature(token: String, signature: String) {
        val data = JSONObject().put("signature", signature).toString().toByteArray(StandardCharsets.UTF_8)
        requestJson(
            method = "PUT",
            path = "/profile",
            body = data,
            token = token,
            extraHeaders = mapOf("Content-Type" to "application/json")
        )
    }

    suspend fun uploadCover(token: String, bytes: ByteArray): Pair<String, String> {
        val res = requestJson(
            method = "PUT",
            path = "/profile/cover",
            body = bytes,
            token = token,
            extraHeaders = mapOf("Content-Type" to "image/jpeg")
        )
        return res.optString("id", "") to res.optString("cover", "")
    }

    suspend fun fetchPublicProfile(
        token: String,
        ownerId: String,
        ownerNameFallback: String,
        before: Long? = null,
        beforeId: String? = null,
        isLiked: (String) -> Boolean,
        isSaved: (String) -> Boolean
    ): Pair<PublicProfileData, List<Triple<String, String, Boolean>>> {
        val query = if (before != null && beforeId != null) "?before=$before&before_id=$beforeId" else ""
        val data = requestJson("GET", "/profiles/$ownerId$query", token = token)
        val name = ownerNameFallback.ifBlank { data.optString("name", "猫猫爱好者") }
        val cover = if (data.isNull("cover")) "" else data.optString("cover", "")
        val coverUrl = if (isProfileCoverPath(ownerId, cover)) baseUrl + cover else null
        val signature = data.optString("signature", "").trim()
        val arr = data.getJSONArray("posts")
        val works = ArrayList<Post>(arr.length())
        val reactionUpdates = ArrayList<Triple<String, String, Boolean>>()
        val seen = HashSet<String>()
        for (i in 0 until arr.length()) {
            val item = arr.getJSONObject(i)
            val id = item.getString("id")
            val path = item.getString("image")
            if (!id.startsWith("cloud_") || path != "/images/$id" || !seen.add(id)) continue
            val breed = if (item.isNull("breed")) "" else item.optString("breed", "")
            val liked = item.optBoolean("liked", isLiked(id))
            val saved = item.optBoolean("saved", isSaved(id))
            reactionUpdates.add(Triple("like", id, liked))
            reactionUpdates.add(Triple("save", id, saved))
            works.add(
                Post(
                    id = id,
                    name = item.optString("name", name),
                    text = item.optString("text", ""),
                    photoUrl = baseUrl + path,
                    breed = breed,
                    mine = false,
                    authorId = ownerId,
                    imageCount = item.optInt("image_count", 1).coerceIn(1, 9),
                    likeCount = item.optInt("like_count", 0).coerceAtLeast(0),
                    liked = liked,
                    saved = saved,
                    createdAt = item.optLong("created_at", 0L)
                )
            )
        }
        val nextObj = data.optJSONObject("next")
        val nextBefore = nextObj?.optLong("before", -1L)?.takeIf { it >= 0L }
        val nextBeforeId = nextObj?.optString("before_id", "")?.takeIf { Regex("cloud_[0-9a-f-]{36}").matches(it) }
        return PublicProfileData(
            id = ownerId,
            name = name,
            signature = signature,
            coverUrl = coverUrl,
            postCount = data.optInt("post_count", works.size),
            posts = works,
            nextBefore = if (nextBefore != null && nextBeforeId != null) nextBefore else null,
            nextBeforeId = if (nextBefore != null && nextBeforeId != null) nextBeforeId else null
        ) to reactionUpdates
    }

    suspend fun fetchCards(token: String): Pair<JSONArray, Boolean> {
        val data = requestJson("GET", "/cards", token = token)
        return data.getJSONArray("cards") to data.optBoolean("drawn_today", false)
    }

    suspend fun drawCard(token: String): Pair<String, Int> {
        val data = requestJson("POST", "/cards/draw", body = ByteArray(0), token = token)
        return data.optString("tier") to data.optInt("index", -1)
    }

    suspend fun fetchAnnouncement(token: String): AnnouncementData? {
        val item = requestJson("GET", "/announcement", token = token).optJSONObject("announcement") ?: return null
        val rev = item.optString("revision", "")
        val content = item.optString("content", "")
        if (rev.isEmpty() || content.isEmpty()) return null
        return AnnouncementData(rev, content)
    }

    suspend fun publishAnnouncement(token: String, content: String, adminKey: String): AnnouncementData {
        val data = JSONObject().put("content", content).toString().toByteArray(StandardCharsets.UTF_8)
        val res = requestJson(
            method = "POST",
            path = "/announcement",
            body = data,
            token = token,
            extraHeaders = mapOf(
                "Content-Type" to "application/json",
                "X-Announcement-Key" to adminKey
            )
        )
        return AnnouncementData(res.optString("revision", ""), res.optString("content", content))
    }

    suspend fun migrateAnonymous(token: String, legacyToken: String) {
        requestJson(
            method = "POST",
            path = "/migrate-anonymous",
            body = ByteArray(0),
            token = token,
            extraHeaders = mapOf("X-Legacy-Token" to legacyToken)
        )
    }

    suspend fun checkForUpdate(currentVersion: String): UpdateInfo? = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL("https://api.github.com/repos/adrian147258/Cat-ch/releases?per_page=10").openConnection() as HttpURLConnection).apply {
                connectTimeout = 7000
                readTimeout = 7000
                setRequestProperty("Accept", "application/vnd.github+json")
            }
            if (conn.responseCode != 200) return@withContext null
            val raw = conn.inputStream.use { readBounded(it) }
            val releases = JSONArray(String(raw, StandardCharsets.UTF_8))
            for (i in 0 until releases.length()) {
                val r = releases.optJSONObject(i) ?: continue
                if (r.optBoolean("draft")) continue
                val tag = r.optString("tag_name", "")
                val version = tag.replaceFirst(Regex("^[vV]"), "").split("-")[0]
                if (!Regex("[0-9]+(\\.[0-9]+)*").matches(version) || compareVersions(version, currentVersion) <= 0) continue
                val assets = r.optJSONArray("assets") ?: continue
                var apkUrl = ""
                for (j in 0 until assets.length()) {
                    val asset = assets.optJSONObject(j) ?: continue
                    if (asset.optString("name").endsWith(".apk")) {
                        apkUrl = asset.optString("browser_download_url")
                        break
                    }
                }
                if (apkUrl.startsWith("https://github.com/adrian147258/Cat-ch/releases/download/")) {
                    return@withContext UpdateInfo(tag, apkUrl)
                }
            }
            null
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun compareVersions(a: String, b: String): Int {
        val x = a.split(".")
        val y = b.split(".")
        val len = maxOf(x.size, y.size)
        for (i in 0 until len) {
            val n = x.getOrNull(i)?.toIntOrNull() ?: 0
            val m = y.getOrNull(i)?.toIntOrNull() ?: 0
            if (n != m) return n.compareTo(m)
        }
        return 0
    }

    fun parseCloudPosts(
        data: JSONObject,
        pendingReactions: Set<String>,
        existingLikes: (String) -> Boolean,
        existingSaves: (String) -> Boolean,
        existingLikeCount: (String) -> Int
    ): Pair<List<Post>, List<Triple<String, String, Boolean>>> {
        val arr = data.getJSONArray("posts")
        val list = ArrayList<Post>(arr.length())
        val reactionUpdates = ArrayList<Triple<String, String, Boolean>>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val id = o.getString("id")
            val path = o.getString("image")
            if (!id.startsWith("cloud_") || path != "/images/$id") continue
            val likePending = pendingReactions.contains("like:$id")
            val savePending = pendingReactions.contains("save:$id")
            val liked = if (likePending) existingLikes(id) else o.optBoolean("liked")
            val saved = if (savePending) existingSaves(id) else o.optBoolean("saved")
            val likeCount = if (likePending) existingLikeCount(id) else o.optInt("like_count", 0).coerceAtLeast(0)
            if (!likePending) reactionUpdates.add(Triple("like", id, liked))
            if (!savePending) reactionUpdates.add(Triple("save", id, saved))
            list.add(
                Post(
                    id = id,
                    name = o.optString("name", "猫猫爱好者"),
                    text = o.optString("text", ""),
                    photoUrl = baseUrl + path,
                    breed = if (o.isNull("breed")) "" else o.optString("breed", ""),
                    mine = o.optBoolean("mine"),
                    authorId = if (o.isNull("author_id")) "" else o.optString("author_id", ""),
                    imageCount = o.optInt("image_count", 1).coerceIn(1, 9),
                    likeCount = likeCount,
                    liked = liked,
                    saved = saved,
                    createdAt = o.optLong("created_at", 0L)
                )
            )
        }
        return list to reactionUpdates
    }
}
