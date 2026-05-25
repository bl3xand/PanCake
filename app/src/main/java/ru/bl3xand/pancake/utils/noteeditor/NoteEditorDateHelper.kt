package ru.bl3xand.pancake.utils.noteeditor

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Форматтеры даты/времени для экрана редактора заметки.
 */
class NoteEditorDateHelper(
    locale: Locale = Locale.getDefault()
) {
    private val editorDateTimeFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", locale)
    private val editorLegacyDateFormats = listOf(
        SimpleDateFormat("dd/M/yyyy HH:mm:ss", locale),
        SimpleDateFormat("dd/MM/yyyy HH:mm:ss", locale),
        SimpleDateFormat("dd.MM.yyyy HH:mm", locale)
    )

    fun nowFormatted(): String = editorDateTimeFormat.format(Date())

    fun formatEpochMillis(value: Long): String = editorDateTimeFormat.format(Date(value))

    fun normalize(value: String): String {
        if (value.isBlank()) return ""
        val parsed = editorLegacyDateFormats.firstNotNullOfOrNull { format ->
            runCatching { format.parse(value) }.getOrNull()
        }
        return parsed?.let { editorDateTimeFormat.format(it) } ?: value
    }
}

