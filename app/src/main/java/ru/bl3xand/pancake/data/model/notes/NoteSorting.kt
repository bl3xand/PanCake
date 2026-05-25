package ru.bl3xand.pancake.data.model.notes

import ru.bl3xand.pancake.R
import ru.bl3xand.pancake.data.model.NoteItem

/** Сортировка заметок: закреплённые → порядок → время обновления */
val NOTE_COMPARATOR: Comparator<NoteItem> =
    compareByDescending<NoteItem> { it.isPinned }
        .thenBy { it.sortOrder }
        .thenByDescending { maxOf(it.updatedAt, it.timestamp) }

/** Порядок палитры заметок в ресурсах colors.xml */
val NOTE_COLOR_ORDER_RES = listOf(
    0, // заглушка для дефолтного цвета, подменяется в NotesFragment
    R.color.note_color_rose,
    R.color.note_color_orange,
    R.color.note_color_yellow,
    R.color.note_color_lime,
    R.color.note_color_green,
    R.color.note_color_sky,
    R.color.note_color_blue,
    R.color.note_color_purple,
    R.color.note_color_violet
)

/** Стандарт: по дате изменения, затем базовая сортировка. */
val NOTE_UPDATED_AT_COMPARATOR: Comparator<NoteItem> =
    compareByDescending<NoteItem> { it.isPinned }
        .thenByDescending { maxOf(it.updatedAt, it.timestamp) }
        .thenBy { it.sortOrder }

/** По дате создания, затем базовая сортировка. */
val NOTE_CREATED_AT_COMPARATOR: Comparator<NoteItem> =
    compareByDescending<NoteItem> { it.isPinned }
        .thenByDescending { it.timestamp }
        .thenBy { it.sortOrder }

private fun NoteItem.colorRank(colorOrderHex: List<String>): Int {
    val index = colorOrderHex.indexOfFirst { it.equals(color, ignoreCase = true) }
    return if (index >= 0) index else Int.MAX_VALUE
}

/** Сортировка по цвету (по палитре), затем базовая сортировка. */
fun noteColorComparator(colorOrderHex: List<String>): Comparator<NoteItem> =
    compareBy<NoteItem> { it.colorRank(colorOrderHex) }
        .then(NOTE_COMPARATOR)

