package ru.bl3xand.pancake.data.repository

import android.content.Context
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import ru.bl3xand.pancake.BuildConfig
import ru.bl3xand.pancake.utils.logs.Logger
import java.io.File
import java.security.MessageDigest

class GitHubImageSyncRepository(
    private val context: Context,
) {
    companion object {
        private const val TAG = "GitHubImageSyncRepo"
        private val httpClient = OkHttpClient()
        private val JSON = "application/json; charset=utf-8".toMediaType()

        fun repoPathFromUrl(url: String): String? {
            val prefix = "https://raw.githubusercontent.com/${
                BuildConfig.GITHUB_OWNER.trim().substringAfterLast("/")
            }/${
                BuildConfig.GITHUB_REPO.trim().removeSuffix(".git").substringAfterLast("/")
            }/${BuildConfig.GITHUB_BRANCH.trim().ifBlank { "main" }}/"
            if (!url.startsWith(prefix)) return null
            return url
                .removePrefix(prefix)
                .substringBefore('?')
                .substringBefore('#')
                .takeIf { it.isNotBlank() }
        }
    }

    private val owner =
        BuildConfig.GITHUB_OWNER.trim().removePrefix("https://github.com/").substringBefore("/")
    private val repo = BuildConfig.GITHUB_REPO.trim().removeSuffix(".git").substringAfterLast("/")
    private val branch = BuildConfig.GITHUB_BRANCH.trim().ifBlank { "main" }

    suspend fun uploadLocalUri(noteId: String, rawPath: String, index: Int): String? =
        withContext(Dispatchers.IO) {
            try {
                if (!isConfigured()) {
                    Logger.logError(
                        TAG,
                        "GitHub config is empty. owner='$owner', repo='$repo', branch='$branch'"
                    )
                    return@withContext null
                }

                val bytes = readBytes(rawPath) ?: run {
                    Logger.logError(TAG, "Cannot read bytes for $rawPath")
                    return@withContext null
                }
                val fileName = buildDeterministicFileName(bytes, index)
                val targetPath = "notes/$noteId/$fileName"
                val base64Content = Base64.encodeToString(bytes, Base64.NO_WRAP)

                val url = "https://api.github.com/repos/$owner/$repo/contents/$targetPath"
                val existingSha = fetchFileSha(url)

                val jsonBody = JSONObject().apply {
                    put("message", "add note image $fileName")
                    put("content", base64Content)
                    put("branch", branch)
                    if (existingSha != null) {
                        put("sha", existingSha)
                    }
                }.toString()

                val request = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer ${BuildConfig.GITHUB_TOKEN.trim()}")
                    .addHeader("Accept", "application/vnd.github+json")
                    .addHeader("X-GitHub-Api-Version", "2022-11-28")
                    .put(jsonBody.toRequestBody(JSON))
                    .build()

                val response = httpClient.newCall(request).execute()
                val body = response.body?.string().orEmpty()

                if (response.code == 404) {
                    Logger.logError(TAG, "GitHub returned 404 for $owner/$repo.")
                    if (initializeRepo()) return@withContext uploadLocalUri(noteId, rawPath, index)
                    return@withContext null
                }

                if (!response.isSuccessful) {
                    Logger.logError(TAG, "GitHub API error ${response.code}: $body")
                    return@withContext null
                }

                val downloadUrl = runCatching {
                    JSONObject(body).getJSONObject("content").getString("download_url")
                }.getOrNull()
                    ?: "https://raw.githubusercontent.com/$owner/$repo/$branch/$targetPath"

                Logger.logDebug(TAG, "Upload success → $downloadUrl")
                downloadUrl
            } catch (t: Throwable) {
                Logger.logError(TAG, "Upload failed: ${t.message}")
                null
            }
        }

    suspend fun deleteImages(imageUrls: List<String>): List<String> = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext imageUrls.mapNotNull { repoPathFromUrl(it) }
        val failedRepoPaths = mutableListOf<String>()
        imageUrls.forEach { url ->
            val path = repoPathFromUrl(url) ?: return@forEach
            if (!deleteFileByPath(path)) failedRepoPaths += path
        }
        failedRepoPaths
    }

    suspend fun deleteNoteFolder(noteId: String): List<String> = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext emptyList()
        val folderPath = "notes/$noteId"
        val files = listFolderFiles(folderPath)
        val failed = mutableListOf<String>()
        files.forEach { path ->
            if (!deleteFileByPath(path)) failed += path
        }
        Logger.logDebug(TAG, "deleteNoteFolder: removed ${files.size} files for noteId=$noteId")
        failed
    }

    private fun listFolderFiles(folderPath: String): List<String> {
        return try {
            val url = "https://api.github.com/repos/$owner/$repo/contents/$folderPath?ref=$branch"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer ${BuildConfig.GITHUB_TOKEN.trim()}")
                .addHeader("Accept", "application/vnd.github+json")
                .addHeader("X-GitHub-Api-Version", "2022-11-28")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return emptyList()

            val body = response.body?.string().orEmpty()
            val arr = JSONArray(body)
            (0 until arr.length()).mapNotNull { i ->
                arr.getJSONObject(i).optString("path").takeIf { it.isNotBlank() }
            }
        } catch (t: Throwable) {
            Logger.logError(TAG, "listFolderFiles failed for $folderPath: ${t.message}")
            emptyList()
        }
    }

    suspend fun deleteByRepoPath(repoPath: String): Boolean = withContext(Dispatchers.IO) {
        deleteFileByPath(repoPath)
    }

    private fun deleteFileByPath(repoPath: String): Boolean {
        try {
            val metaUrl = "https://api.github.com/repos/$owner/$repo/contents/$repoPath?ref=$branch"
            val metaReq = Request.Builder()
                .url(metaUrl)
                .addHeader("Authorization", "Bearer ${BuildConfig.GITHUB_TOKEN.trim()}")
                .addHeader("Accept", "application/vnd.github+json")
                .addHeader("X-GitHub-Api-Version", "2022-11-28")
                .get()
                .build()

            val metaResp = httpClient.newCall(metaReq).execute()
            if (!metaResp.isSuccessful) {
                Logger.logDebug(TAG, "deleteFileByPath: file not found on GitHub: $repoPath")
                return metaResp.code == 404
            }
            val sha = JSONObject(metaResp.body?.string().orEmpty()).optString("sha")
            if (sha.isBlank()) return false

            val deleteUrl = "https://api.github.com/repos/$owner/$repo/contents/$repoPath"
            val deleteBody = JSONObject().apply {
                put("message", "remove note image: $repoPath")
                put("sha", sha)
                put("branch", branch)
            }.toString()

            val deleteReq = Request.Builder()
                .url(deleteUrl)
                .addHeader("Authorization", "Bearer ${BuildConfig.GITHUB_TOKEN.trim()}")
                .addHeader("Accept", "application/vnd.github+json")
                .addHeader("X-GitHub-Api-Version", "2022-11-28")
                .delete(deleteBody.toRequestBody(JSON))
                .build()

            val deleteResp = httpClient.newCall(deleteReq).execute()
            if (deleteResp.isSuccessful) {
                Logger.logDebug(TAG, "Deleted from GitHub: $repoPath")
                return true
            } else {
                Logger.logError(TAG, "Delete failed ${deleteResp.code} for $repoPath")
                return deleteResp.code == 404
            }
        } catch (t: Throwable) {
            Logger.logError(TAG, "deleteFileByPath failed for $repoPath: ${t.message}")
            return false
        }
    }

    private fun initializeRepo(): Boolean {
        return try {
            val url = "https://api.github.com/repos/$owner/$repo/contents/README.md"
            val body = JSONObject().apply {
                put("message", "init: create repository")
                put(
                    "content",
                    Base64.encodeToString(
                        "# Pancake Sync\nNote images storage.".toByteArray(),
                        Base64.NO_WRAP
                    )
                )
                put("branch", branch)
            }.toString()
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer ${BuildConfig.GITHUB_TOKEN.trim()}")
                .addHeader("Accept", "application/vnd.github+json")
                .addHeader("X-GitHub-Api-Version", "2022-11-28")
                .put(body.toRequestBody(JSON))
                .build()
            val response = httpClient.newCall(request).execute()
            Logger.logDebug(TAG, "initializeRepo: ${response.code}")
            response.isSuccessful
        } catch (t: Throwable) {
            Logger.logError(TAG, "initializeRepo failed: ${t.message}")
            false
        }
    }

    private fun isConfigured(): Boolean =
        BuildConfig.GITHUB_TOKEN.isNotBlank() && owner.isNotBlank() && repo.isNotBlank()

    private fun readBytes(rawPath: String): ByteArray? {
        val uri = Uri.parse(rawPath)
        val contentBytes = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull()
        if (contentBytes != null) return contentBytes

        val filePath = if (rawPath.startsWith("file://")) uri.path else rawPath
        if (filePath.isNullOrBlank()) return null
        val file = File(filePath)
        return if (file.exists()) runCatching { file.readBytes() }.getOrNull() else null
    }

    private fun buildDeterministicFileName(bytes: ByteArray, index: Int): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val hash = digest.joinToString(separator = "") { b -> "%02x".format(b) }
        return "${index}_${hash.take(16)}.jpg"
    }

    private fun fetchFileSha(contentsUrl: String): String? {
        return try {
            val request = Request.Builder()
                .url("$contentsUrl?ref=$branch")
                .addHeader("Authorization", "Bearer ${BuildConfig.GITHUB_TOKEN.trim()}")
                .addHeader("Accept", "application/vnd.github+json")
                .addHeader("X-GitHub-Api-Version", "2022-11-28")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return null

            val body = response.body?.string().orEmpty()
            JSONObject(body).optString("sha").takeIf { it.isNotBlank() }
        } catch (t: Throwable) {
            Logger.logError(TAG, "fetchFileSha failed: ${t.message}")
            null
        }
    }
}