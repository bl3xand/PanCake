package ru.bl3xand.pancake.utils.noteeditor

import android.content.Context
import ru.bl3xand.pancake.R
import ru.bl3xand.pancake.data.model.notes.NoteColors
import ru.bl3xand.pancake.utils.ui.colorResToHex

object NoteColorPaletteHelper {

    data class ColorEntry(
        val colorRes: Int,
        val hex: String
    )

    fun palette(context: Context): List<ColorEntry> = listOf(
        ColorEntry(0, NoteColors.DEFAULT_MARKER),
        ColorEntry(R.color.note_color_rose, context.colorResToHex(R.color.note_color_rose)),
        ColorEntry(R.color.note_color_orange, context.colorResToHex(R.color.note_color_orange)),
        ColorEntry(R.color.note_color_yellow, context.colorResToHex(R.color.note_color_yellow)),
        ColorEntry(R.color.note_color_lime, context.colorResToHex(R.color.note_color_lime)),
        ColorEntry(R.color.note_color_green, context.colorResToHex(R.color.note_color_green)),
        ColorEntry(R.color.note_color_sky, context.colorResToHex(R.color.note_color_sky)),
        ColorEntry(R.color.note_color_blue, context.colorResToHex(R.color.note_color_blue)),
        ColorEntry(R.color.note_color_purple, context.colorResToHex(R.color.note_color_purple)),
        ColorEntry(R.color.note_color_violet, context.colorResToHex(R.color.note_color_violet)),
    )
}