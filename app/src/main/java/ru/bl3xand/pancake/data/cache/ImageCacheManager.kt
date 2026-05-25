package ru.bl3xand.pancake.data.cache

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest

/**
 * Управляет локальным кэшем изображений из GitHub.
 * Обеспечивает автоматическое обновление кэша при протухании токена.
 */
class ImageCacheManager(private val context: Context) {

    private val cacheDir: File
        get() = File(context.cacheDir, CACHE_DIRNAME).also { it.mkdirs() }

    private val httpClient = OkHttpClient()

    /**
     * Получает путь локального кэша для изображения.
     * Если локального кэша нет, загружает изображение с GitHub.
     *
     * @param imageUrl URL изображения (может быть локальный путь или GitHub URL)
     * @param repoPath путь в репозитории для переиспользования при загрузке
     * @param token GitHub токен для авторизации
     * @return локальный путь к кэшированному изображению или исходный URL если загрузка не удалась
     */
    suspend fun getOrDownloadImage(
        imageUrl: String,
        repoPath: String?,
        token: String
    ): String = withContext(Dispatchers.IO) {
        // Если это локальный путь и файл существует - возвращаем его
        if (!imageUrl.isRemoteUrl()) {
            val file = File(imageUrl)
            if (file.exists()) return@withContext imageUrl
        }

        val cacheKey = generateCacheKey(repoPath ?: imageUrl)
        val cachedFile = File(cacheDir, cacheKey)

        // Если кэш существует - возвращаем его
        if (cachedFile.exists() && cachedFile.length() > 0) {
            Log.d(TAG, "Using cached image: ${cachedFile.absolutePath}")
            return@withContext cachedFile.absolutePath
        }

        // Пытаемся загрузить изображение с GitHub
        downloadAndCacheImage(imageUrl, repoPath, token, cachedFile)
    }

    /**
     * Загружает изображение с GitHub и сохраняет в кэш.
     * Используется для обновления протухшего кэша.
     *
     * @return локальный путь к загруженному файлу или исходный URL если загрузка не удалась
     */
    private fun downloadAndCacheImage(
        imageUrl: String,
        repoPath: String?,
        token: String,
        cacheFile: File
    ): String {
        return try {
            // Приоритет: используем API GitHub если есть repoPath
            val downloadUrl = if (!repoPath.isNullOrBlank()) {
                getGitHubApiDownloadUrl(repoPath, token)
            } else if (imageUrl.isRemoteUrl()) {
                imageUrl
            } else {
                return imageUrl // не можем загрузить локальный путь
            }

            if (downloadUrl == null) {
                Log.w(TAG, "Could not resolve download URL for: $imageUrl")
                return imageUrl
            }

            val request = Request.Builder()
                .url(downloadUrl)
                .addHeader("User-Agent", "Pancake/1.0")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "Failed to download image: ${response.code}")
                return imageUrl
            }

            response.body?.bytes()?.let { bytes ->
                cacheFile.writeBytes(bytes)
                Log.d(TAG, "Cached image to: ${cacheFile.absolutePath}")
                cacheFile.absolutePath
            } ?: imageUrl

        } catch (e: Exception) {
            Log.e(TAG, "Error downloading image: ${e.message}", e)
            imageUrl
        }
    }

    /**
     * Получает URL для загрузки из GitHub API вместо использования raw.githubusercontent.com
     * Это обходит проблему с протухшими токенами в URL.
     *
     * @param repoPath путь в репозитории (например: "notes/note-123/0_abc123.jpg")
     * @param token GitHub токен
     * @return URL для скачивания или null если ошибка
     */
    private fun getGitHubApiDownloadUrl(repoPath: String, token: String): String? {
        return try {
            val owner = extractOwnerFromConfig()
            val repo = extractRepoFromConfig()
            val branch = extractBranchFromConfig()

            if (owner.isBlank() || repo.isBlank()) {
                Log.w(TAG, "GitHub config is not set")
                return null
            }

            val apiUrl = "https://api.github.com/repos/$owner/$repo/contents/$repoPath?ref=$branch"
            val request = Request.Builder()
                .url(apiUrl)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/vnd.github+json")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "GitHub API error ${response.code} for $repoPath")
                return null
            }

            val body = response.body?.string() ?: return null
            val downloadUrl = runCatching {
                org.json.JSONObject(body).getString("download_url")
            }.getOrNull()

            Log.d(TAG, "Got GitHub API download URL for: $repoPath")
            downloadUrl

        } catch (e: Exception) {
            Log.e(TAG, "Error getting GitHub API download URL: ${e.message}", e)
            null
        }
    }

    /**
     * Очищает кэш изображений.
     */
    suspend fun clearCache(): Unit = withContext(Dispatchers.IO) {
        try {
            cacheDir.deleteRecursively()
            cacheDir.mkdirs()
            Log.d(TAG, "Image cache cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing cache: ${e.message}", e)
        }
    }

    /**
     * Удаляет конкретное изображение из кэша.
     */
    suspend fun removeCachedImage(imageUrl: String, repoPath: String?): Unit = withContext(Dispatchers.IO) {
        val cacheKey = generateCacheKey(repoPath ?: imageUrl)
        val file = File(cacheDir, cacheKey)
        if (file.exists()) {
            file.delete()
            Log.d(TAG, "Removed cached image: $cacheKey")
        }
    }

    /**
     * Генерирует детерминированный ключ кэша на основе пути в репозитории или URL.
     */
    private fun generateCacheKey(identifier: String): String {
        // Используем SHA-256 для создания уникального, но короткого имени файла
        val digest = MessageDigest.getInstance("SHA-256").digest(identifier.toByteArray())
        val hash = digest.joinToString(separator = "") { b -> "%02x".format(b) }
        return "img_${hash.take(16)}.cache"
    }

    companion object {
        private const val TAG = "ImageCacheManager"
        private const val CACHE_DIRNAME = "image_cache"

        private fun String.isRemoteUrl(): Boolean =
            startsWith("http://") || startsWith("https://")

        // Эти функции должны совпадать с GitHubImageSyncRepository
        private fun extractOwnerFromConfig(): String {
            return try {
                val githubOwner = ru.bl3xand.pancake.BuildConfig.GITHUB_OWNER.trim()
                    .removePrefix("https://github.com/")
                    .substringBefore("/")
                githubOwner
            } catch (e: Exception) {
                ""
            }
        }

        private fun extractRepoFromConfig(): String {
            return try {
                ru.bl3xand.pancake.BuildConfig.GITHUB_REPO.trim()
                    .removeSuffix(".git")
                    .substringAfterLast("/")
            } catch (e: Exception) {
                ""
            }
        }

        private fun extractBranchFromConfig(): String {
            return try {
                ru.bl3xand.pancake.BuildConfig.GITHUB_BRANCH.trim()
                    .ifBlank { "main" }
            } catch (e: Exception) {
                "main"
            }
        }
    }
}