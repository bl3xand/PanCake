package ru.bl3xand.pancake.utils.noteeditor

import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.widget.TextView
import com.google.android.material.color.MaterialColors

/**
 * Единый стиль цитат для preview в редакторе и на карточках заметок.
 */
object NoteQuoteStyleHelper {

    fun apply(textView: TextView, rawMarkdown: String) {
        val rendered = textView.text ?: return
        val spannable = SpannableStringBuilder(rendered)
        val quoteContents = extractQuoteContents(rawMarkdown)
        if (quoteContents.isEmpty()) {
            textView.text = spannable
            return
        }

        val quoteColor = MaterialColors.getColor(
            textView,
            com.google.android.material.R.attr.colorOnSurfaceVariant
        )

        quoteContents.forEach { quote ->
            if (quote.isBlank()) return@forEach
            var startSearch = 0
            while (startSearch < spannable.length) {
                val start = spannable.indexOf(quote, startSearch)
                if (start < 0) break
                val end = start + quote.length

                spannable.setSpan(
                    TypefaceSpan("serif"),
                    start,
                    end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                spannable.setSpan(
                    StyleSpan(Typeface.NORMAL),
                    start,
                    end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                spannable.setSpan(
                    RelativeSizeSpan(0.95f),
                    start,
                    end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                spannable.setSpan(
                    ForegroundColorSpan(quoteColor),
                    start,
                    end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )

                startSearch = end
            }
        }

        textView.text = spannable
    }

    private fun extractQuoteContents(rawMarkdown: String): List<String> {
        val markdownQuotes = Regex("(?m)(^>+\\s?.*(?:\\n>+\\s?.*)*)")
            .findAll(rawMarkdown)
            .map { match ->
                match.value
                    .lineSequence()
                    .map { line -> line.replaceFirst(Regex("^>+\\s?"), "") }
                    .joinToString("\n")
                    .trim()
            }
            .filter { it.isNotBlank() }
            .toList()

        val blockQuotes = Regex("```([\\s\\S]*?)```")
            .findAll(rawMarkdown)
            .map { it.groupValues[1].trim() }
            .filter { it.isNotBlank() }
            .toList()

        val inlineQuotes = Regex("`([^`]+)`")
            .findAll(rawMarkdown)
            .map { it.groupValues[1].trim() }
            .filter { it.isNotBlank() }
            .toList()

        return markdownQuotes + blockQuotes + inlineQuotes
    }
}