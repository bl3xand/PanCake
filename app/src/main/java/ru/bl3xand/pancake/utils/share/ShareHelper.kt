package ru.bl3xand.pancake.utils.share

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/**
 * Помощник для обмена информацией о пространстве (QR + текст) через Android share sheet.
 */
object ShareHelper {

    /**
     * Делитесь текстом с пробельным кодом и дополнительным растровым изображением QR с помощью приложения Android share sheet.
     *
     * Если предоставлена qrBitmap, она будет сохранена в CacheDir /share_images и прикреплена в виде потока.
     *
     * @param activity Действие, используемое для запуска программы выбора и разрешения доступа к файлам.
     * @param spaceId - код пробела, который необходимо включить в текстовый и QR-контент для общего доступа.
     * @param qrBitmap - Необязательное растровое изображение QR-кода, которое можно прикрепить в виде изображения.
     * @param spaceCodeLabel - Локализованная метка для космического кода (например, "Код страны").
     * @параметр chooserTitle задает заголовок для диалогового окна выбора (например, "Выбрать").
     * @возвращает значение true, если программа выбора успешно запущена, и значение false в противном случае.
     */
    fun shareSpace(
        activity: Activity,
        spaceId: String,
        qrBitmap: Bitmap?,
        spaceCodeLabel: String,
        chooserTitle: String
    ): Boolean {
        return try {
            val shareText = "$spaceCodeLabel: $spaceId"
            val uri: Uri? = qrBitmap?.let { saveQrToCacheAndGetUri(activity, it, spaceId) }

            val intent = Intent(Intent.ACTION_SEND).apply {
                if (uri != null) {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    clipData = ClipData.newUri(activity.contentResolver, "space_qr", uri)
                } else {
                    type = "text/plain"
                }
                putExtra(Intent.EXTRA_TEXT, shareText)
            }

            activity.startActivity(Intent.createChooser(intent, chooserTitle))
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun saveQrToCacheAndGetUri(
        activity: Activity,
        bitmap: Bitmap,
        spaceId: String
    ): Uri? {
        return try {
            val dir = File(activity.cacheDir, "share_images").apply { mkdirs() }
            val file = File(dir, "space_qr_${spaceId}.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            FileProvider.getUriForFile(
                activity,
                "${activity.packageName}.fileprovider",
                file
            )
        } catch (_: Exception) {
            null
        }
    }
}