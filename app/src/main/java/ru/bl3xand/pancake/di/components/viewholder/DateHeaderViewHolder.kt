package ru.bl3xand.pancake.di.components.viewholder

import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.style.StyleSpan
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import ru.bl3xand.pancake.R
import java.text.SimpleDateFormat
import java.util.*

class DateHeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    private val dateTextView: TextView = itemView.findViewById(R.id.dateTextView)
    private val fullDateFormat = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault())
    private val shortDateFormat = SimpleDateFormat("d MMMM", Locale.getDefault())

    fun bind(dateMillis: Long, title: String = "") {
        val today = Calendar.getInstance()
        val headerDate = Calendar.getInstance().apply { timeInMillis = dateMillis }

        val isToday = today.get(Calendar.YEAR) == headerDate.get(Calendar.YEAR) &&
                today.get(Calendar.DAY_OF_YEAR) == headerDate.get(Calendar.DAY_OF_YEAR)

        val dateText = if (title.isNotEmpty()) {
            title
        } else if (isToday) {
            "Сегодня, ${shortDateFormat.format(Date(dateMillis))}"
        } else {
            fullDateFormat.format(Date(dateMillis)).replaceFirstChar { it.uppercase(Locale.getDefault()) }
        }

        val spannableString = SpannableString(dateText)
        spannableString.setSpan(
            StyleSpan(Typeface.BOLD),
            0,
            dateText.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        dateTextView.text = spannableString
    }
}