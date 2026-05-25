package ru.bl3xand.pancake.utils.noteeditor

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.runBlocking
import ru.bl3xand.pancake.BuildConfig
import ru.bl3xand.pancake.data.cache.ImageCacheManager
import ru.bl3xand.pancake.utils.image.ImageUrlHelper
import java.io.File
import java.util.Locale

class NoteEditorMediaHelper(
    private val context: Context,
    private val imageCacheManager: ImageCacheManager
) {

    companion object {
        private const val TAG = "NoteEditorMediaHelper"
    }

    fun saveImageToGallery(path: String): Boolean {
        val bytes = readImageBytes(path) ?: return false
        val mimeType = resolveMimeType(path)
        val extension = mimeType.substringAfter('/').ifBlank { "jpg" }

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "pancake_note_${System.currentTimeMillis()}.$extension")
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Pancake")
        }

        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return false

        context.contentResolver.openOutputStream(uri)?.use { output ->
            output.write(bytes)
        } ?: return false

        return true
    }

    fun buildShareIntent(path: String, shareTitle: String): Intent? {
        return try {
            val bytes = readImageBytes(path) ?: return null

            val extension = resolveMimeType(path).substringAfter('/').ifBlank { "jpg" }
            val cacheDir = File(context.cacheDir, "share_images").also { it.mkdirs() }
            val file = File(cacheDir, "note_share_${System.currentTimeMillis()}.$extension")
            file.outputStream().use { it.write(bytes) }

            val shareUri = FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file
            )

            Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, shareUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }.let { Intent.createChooser(it, shareTitle) }
        } catch (e: Exception) {
            Log.e(TAG, "Error building share intent: ${e.message}", e)
            null
        }
    }

    fun readImageBytes(path: String): ByteArray? {
        return runCatching {
            when {
                ImageUrlHelper.isGitHubImage(path) -> {
                    val repoPath = ImageUrlHelper.extractRepoPathFromUrl(path)
                    val cachedPath = runBlocking {
                        imageCacheManager.getOrDownloadImage(
                            path, repoPath, BuildConfig.GITHUB_TOKEN.trim()
                        )
                    }
                    File(cachedPath).takeIf { it.exists() }?.readBytes()
                }
                path.startsWith("content://") ->
                    context.contentResolver.openInputStream(Uri.parse(path))?.use { it.readBytes() }
                else ->
                    File(path).takeIf { it.exists() }?.readBytes()
            }
        }.onFailure { e ->
            Log.e(TAG, "Failed to read image bytes from $path: ${e.message}", e)
        }.getOrNull()
    }

    fun resolveMimeType(path: String): String {
        val value = path.lowercase(Locale.ROOT)
        return when {
            value.endsWith(".png") -> "image/png"
            value.endsWith(".webp") -> "image/webp"
            else -> "image/jpeg"
        }
    }

    suspend fun resolveDisplayPath(path: String): String {
        return if (ImageUrlHelper.isGitHubImage(path)) {
            val repoPath = ImageUrlHelper.extractRepoPathFromUrl(path)
            imageCacheManager.getOrDownloadImage(
                path, repoPath, BuildConfig.GITHUB_TOKEN.trim()
            )
        } else {
            path
        }
    }
}