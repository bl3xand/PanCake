package ru.bl3xand.pancake.utils.extensions

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan

/**
 * Сделать часть строки жирной.
 *
 * @param fullText полная строка
 * @param boldPart часть строки которая должна быть жирной
 * @return SpannableStringBuilder с жирным текстом
 */
fun boldPrefix(fullText: String, boldPart: String): SpannableStringBuilder {
    val spannable = SpannableStringBuilder(fullText)
    val startIndex = fullText.indexOf(boldPart)
    if (startIndex >= 0) {
        spannable.setSpan(
            StyleSpan(Typeface.BOLD),
            startIndex,
            startIndex + boldPart.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
    }
    return spannable
}