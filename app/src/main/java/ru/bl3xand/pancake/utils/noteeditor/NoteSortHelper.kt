package ru.bl3xand.pancake.utils.noteeditor

import ru.bl3xand.pancake.data.model.NoteItem
import ru.bl3xand.pancake.data.model.notes.NOTE_COMPARATOR
import ru.bl3xand.pancake.data.model.notes.NOTE_CREATED_AT_COMPARATOR
import ru.bl3xand.pancake.data.model.notes.NOTE_UPDATED_AT_COMPARATOR
import ru.bl3xand.pancake.data.model.notes.noteColorComparator

object NoteSortHelper {

    enum class Mode { CUSTOM, UPDATED_AT, CREATED_AT, COLOR }

    fun sort(
        notes: List<NoteItem>,
        mode: Mode,
        colorOrderHex: List<String> = emptyList()
    ): List<NoteItem> = when (mode) {
        Mode.CUSTOM -> notes.sortedWith(NOTE_COMPARATOR)
        Mode.UPDATED_AT -> notes.sortedWith(NOTE_UPDATED_AT_COMPARATOR)
        Mode.CREATED_AT -> notes.sortedWith(NOTE_CREATED_AT_COMPARATOR)
        Mode.COLOR -> notes.sortedWith(noteColorComparator(colorOrderHex))
    }
}