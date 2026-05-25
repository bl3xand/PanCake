package ru.bl3xand.pancake.utils.noteeditor

import android.text.Editable

/**
 * Хелпер для автоматического форматирования текста в редакторе заметок.
 * Обеспечивает продолжение упорядоченных и маркированных списков при нажатии Enter.
 */
object NoteEditorAutoFormatHelper {

    private val ORDERED_LIST_REGEX = Regex("^(\\s*)(\\d+)\\.\\s+.+$")
    private val BULLET_LIST_REGEX = Regex("^(\\s*)([-*+])\\s+.+$")

    /**
     * Вставляет продолжение текущего элемента списка (нумерованного или маркированного)
     * после нажатия Enter.
     *
     * @param editable текущий текст редактора
     * @param cursor позиция курсора (сразу после символа новой строки)
     * @return строку продолжения для вставки или null, если не в списке
     */
    fun resolveListContinuation(editable: Editable, cursor: Int): String? {
        if (cursor <= 0 || editable.getOrNull(cursor - 1) != '\n') return null

        val previousLine = editable.substring(0, cursor - 1).substringAfterLast('\n')
        val orderedMatch = ORDERED_LIST_REGEX.matchEntire(previousLine)
        val bulletMatch = BULLET_LIST_REGEX.matchEntire(previousLine)

        return when {
            orderedMatch != null -> {
                val indent = orderedMatch.groupValues[1]
                val nextNumber = orderedMatch.groupValues[2].toIntOrNull()?.plus(1) ?: return null
                "$indent$nextNumber. "
            }
            bulletMatch != null -> {
                val indent = bulletMatch.groupValues[1]
                val bullet = bulletMatch.groupValues[2]
                "$indent$bullet "
            }
            else -> null
        }
    }
}

