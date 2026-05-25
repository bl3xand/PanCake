package ru.bl3xand.pancake.ui.view

import android.R.attr.editTextStyle
import android.content.Context
import android.util.AttributeSet
import com.google.android.material.textfield.TextInputEditText

class PasteAwareEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = editTextStyle
) : TextInputEditText(context, attrs, defStyleAttr) {

    var onPasteListener: (() -> Boolean)? = null

    override fun onTextContextMenuItem(id: Int): Boolean {
        if (id == android.R.id.paste || id == android.R.id.pasteAsPlainText) {
            if (onPasteListener?.invoke() == true) {
                return true
            }
        }
        return super.onTextContextMenuItem(id)
    }
}