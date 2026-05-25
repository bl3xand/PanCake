package ru.bl3xand.pancake.utils.noteeditor

import android.content.Context
import android.graphics.Color
import androidx.core.graphics.toColorInt
import com.google.android.material.R
import com.google.android.material.color.MaterialColors
import ru.bl3xand.pancake.data.model.notes.NoteColors

object NoteColorResolver {

    /**
     * Разрешает цвет заметки в реальный int-цвет.
     * Если значение = маркер дефолта или пустое — берёт цвет из динамической темы.
     */
    fun resolve(context: Context, colorValue: String): Int {
        return if (isDefault(colorValue)) {
            defaultColor(context)
        } else {
            runCatching { colorValue.toColorInt() }
                .getOrElse { defaultColor(context) }
        }
    }

    /**
     * Проверяет, является ли значение дефолтным цветом.
     */
    fun isDefault(colorValue: String): Boolean {
        return colorValue.isBlank() || colorValue == NoteColors.DEFAULT_MARKER
    }

    private fun defaultColor(context: Context): Int {
        return MaterialColors.getColor(
            context,
            R.attr.colorSurfaceContainerHigh,
            Color.GRAY
        )
    }
}