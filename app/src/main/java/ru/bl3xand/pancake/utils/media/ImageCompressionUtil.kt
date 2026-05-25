package ru.bl3xand.pancake.utils.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import ru.bl3xand.pancake.utils.logs.Logger
import java.io.File

/**
 * Оптимизированное сжатие изображений:
 * - Auto-detect формат (JPG, PNG, WebP, HEIF)
 * - Масштабирование до макс 1024px
 * - Конвертация в JPEG 75% (лучший ratio качество/размер)
 * - Целевой размер файла: < 200KB
 */
object ImageCompressionUtil {
    private const val TAG = "ImageCompressionUtil"
    private const val MAX_SIDE = 1024         // макс размер px
    private const val JPEG_QUALITY = 75       // 75% - оптимальный баланс
    private const val TARGET_SIZE_KB = 200    // целевой размер < 200KB

    fun compressAndSave(context: Context, uri: Uri, outputFile: File): Boolean {
        return try {
            // 1. Декодируем с оптимальным масштабом
            val bitmap = decodeBitmapWithScale(context, uri) ?: return false
            val normalizedBitmap = rotateBitmapIfNeeded(bitmap, readExifRotation(context, uri))

            // 2. Сохраняем как JPEG с качеством 75%
            var quality = JPEG_QUALITY
            var lastSize: Long

            // Итеративно снижаем качество если файл слишком большой
            while (quality > 50) {
                outputFile.outputStream().use { out ->
                    normalizedBitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                }
                lastSize = outputFile.length()

                if (lastSize <= TARGET_SIZE_KB * 1024) {
                    if (normalizedBitmap !== bitmap) {
                        bitmap.recycle()
                    }
                    normalizedBitmap.recycle()
                    Logger.logDebug(TAG, "Compressed ${uri.lastPathSegment}: quality=$quality, size=${lastSize / 1024}KB")
                    return true
                }
                quality -= 5
            }

            if (normalizedBitmap !== bitmap) {
                bitmap.recycle()
            }
            normalizedBitmap.recycle()
            Logger.logDebug(TAG, "Compressed to min quality=${quality + 5}KB: ${outputFile.length() / 1024}KB")
            true
        } catch (t: Throwable) {
            Logger.logError(TAG, "compressAndSave failed: ${t.message}")
            false
        }
    }

    /** Декодирует bitmap с правильным масштабом чтобы не превышать maxSide */
    private fun decodeBitmapWithScale(context: Context, uri: Uri): Bitmap? {
        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            }

            val sample = calcSampleSize(opts.outWidth, opts.outHeight, MAX_SIDE)

            val opts2 = BitmapFactory.Options().apply { inSampleSize = sample }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts2)
            }
        } catch (t: Throwable) {
            Logger.logError(TAG, "decodeBitmapWithScale failed: ${t.message}")
            null
        }
    }

    private fun calcSampleSize(width: Int, height: Int, maxSide: Int): Int {
        val longest = maxOf(width, height)
        var sample = 1
        while (longest / (sample * 2) >= maxSide) sample *= 2
        return sample
    }

    private fun readExifRotation(context: Context, uri: Uri): Int {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val exif = ExifInterface(input)
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        } catch (_: Throwable) {
            0
        }
    }

    private fun rotateBitmapIfNeeded(source: Bitmap, rotation: Int): Bitmap {
        if (rotation == 0) return source
        return try {
            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        } catch (_: Throwable) {
            source
        }
    }
}

