package ru.bl3xand.pancake.utils.media

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import ru.bl3xand.pancake.utils.logs.Logger
import java.io.File

/**
 * Копирует выбранное изображение во внутреннее хранилище приложения.
 * Сжатие и конвертация в JPEG осуществляется в ImageCompressionUtil.
 */
object MediaCopyUtil {
    private const val TAG = "MediaCopyUtil"
    private const val DIR = "note_images"

    /**
     * Копирует content:// URI и сжимает её с помощью ImageCompressionUtil.
     * Всегда сохраняет как JPEG для экономии места.
     */
    fun copyToAppStorage(context: Context, uri: Uri): String {
        return try {
            val dir = File(context.filesDir, DIR).also { it.mkdirs() }
            val destFile = File(dir, "${System.currentTimeMillis()}.jpg")

            if (ImageCompressionUtil.compressAndSave(context, uri, destFile)) {
                Logger.logDebug(TAG, "Copied to ${destFile.absolutePath} (${destFile.length() / 1024}KB)")
                Uri.fromFile(destFile).toString()
            } else {
                Logger.logError(TAG, "Compression failed for $uri")
                ""
            }
        } catch (t: Throwable) {
            Logger.logError(TAG, "copyToAppStorage failed: ${t.message}")
            ""
        }
    }

    /** Сохраняет bitmap камеры в JPEG во внутреннее хранилище приложения. */
    fun saveBitmapToAppStorage(context: Context, bitmap: Bitmap): String {
        return try {
            val dir = File(context.filesDir, DIR).also { it.mkdirs() }
            val destFile = File(dir, "${System.currentTimeMillis()}.jpg")
            destFile.outputStream().use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
            }
            Logger.logDebug(TAG, "Saved bitmap to ${destFile.absolutePath} (${destFile.length() / 1024}KB)")
            Uri.fromFile(destFile).toString()
        } catch (t: Throwable) {
            Logger.logError(TAG, "saveBitmapToAppStorage failed: ${t.message}")
            ""
        }
    }
}
