package ru.bl3xand.pancake.di.components.viewholder

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import ru.bl3xand.pancake.R
import ru.bl3xand.pancake.data.model.MovieItem
import ru.bl3xand.pancake.utils.MovieTypeHelper
import ru.bl3xand.pancake.utils.ui.performAppHapticTap
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.graphics.drawable.Drawable

class MovieViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    private val cardView = itemView as MaterialCardView
    private val poster: ImageView = itemView.findViewById(R.id.posterImageView)
    private val posterPlaceholder: ImageView = itemView.findViewById(R.id.posterPlaceholder)
    private val title: TextView = itemView.findViewById(R.id.titleTextView)
    private val seasonView: TextView = itemView.findViewById(R.id.seasonTextView)
    private val episodeView: TextView = itemView.findViewById(R.id.episodeTextView)
    private val createdByAndTime: TextView = itemView.findViewById(R.id.itemCreatedByAndTime)

    private val dateTimeFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

    fun bind(item: MovieItem, onClick: () -> Unit) {
        bindContent(item)
        bindColors(item)
        bindInteractions(onClick)
    }

    private fun boldLabel(label: String, value: String): SpannableStringBuilder {
        val ssb = SpannableStringBuilder()
        val start = 0
        ssb.append(label)
        ssb.setSpan(StyleSpan(Typeface.BOLD), start, ssb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        ssb.append(" $value")
        return ssb
    }

    private fun bindContent(item: MovieItem) {
        val context = itemView.context
        title.text = item.title

        if (item.type == MovieTypeHelper.TYPE_SERIES) {
            seasonView.isVisible = true
            episodeView.isVisible = true
            if (item.season > 0) {
                seasonView.text = boldLabel(context.getString(R.string.season_label), item.season.toString())
            } else {
                seasonView.text = boldLabel(context.getString(R.string.season_label), context.getString(R.string.not_started))
            }
            if (item.episode > 0) {
                episodeView.text = boldLabel(context.getString(R.string.episode_label), item.episode.toString())
            } else {
                episodeView.text = boldLabel(context.getString(R.string.episode_label), context.getString(R.string.not_started))
            }
        } else {
            seasonView.isVisible = false
            episodeView.isVisible = false
        }

        val timestamp = dateTimeFormat.format(Date(item.timestamp))
        createdByAndTime.text = context.getString(R.string.created_by_time_format, item.createdBy, timestamp)

        // Постер с заглушкой
        if (item.posterUrl.isNotBlank()) {
            // Сбрасываем состояние recycled ViewHolder перед новым запросом.
            poster.isVisible = true
            posterPlaceholder.isVisible = false
            Glide.with(itemView.context)
                .load(item.posterUrl)
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .skipMemoryCache(false)
                .dontAnimate()
                .listener(object : RequestListener<Drawable> {
                    override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>, isFirstResource: Boolean): Boolean {
                        posterPlaceholder.isVisible = true
                        poster.isVisible = false
                        return false
                    }
                    override fun onResourceReady(resource: Drawable, model: Any, target: Target<Drawable>?, dataSource: DataSource, isFirstResource: Boolean): Boolean {
                        posterPlaceholder.isVisible = false
                        poster.isVisible = true
                        return false
                    }
                })
                .into(poster)
        } else {
            poster.isVisible = false
            posterPlaceholder.isVisible = true
        }
    }

    private fun bindColors(item: MovieItem) {
        val important = getMaterialColor(com.google.android.material.R.attr.colorSecondaryContainer)
        val onImportant = getMaterialColor(com.google.android.material.R.attr.colorOnSecondaryContainer)
        val veryImportant = getMaterialColor(com.google.android.material.R.attr.colorPrimaryContainer)
        val onVeryImportant = getMaterialColor(com.google.android.material.R.attr.colorOnPrimaryContainer)

        // Цвет зависит только от типа контента, а не от статуса вкладки.
        val isSeries = item.type == MovieTypeHelper.TYPE_SERIES
        val cardColor = if (isSeries) veryImportant else important
        val onCardColor = if (isSeries) onVeryImportant else onImportant
        val secondaryTextColor = ColorUtils.setAlphaComponent(onCardColor, 245)

        cardView.setCardBackgroundColor(cardColor)
        cardView.strokeWidth = 2
        cardView.strokeColor = ColorUtils.setAlphaComponent(onCardColor, if (isSeries) 96 else 56)

        title.setTextColor(onCardColor)
        seasonView.setTextColor(secondaryTextColor)
        episodeView.setTextColor(secondaryTextColor)
        createdByAndTime.setTextColor(secondaryTextColor)
    }

    private fun getMaterialColor(attrResId: Int): Int =
        MaterialColors.getColor(itemView, attrResId)


    private fun bindInteractions(onClick: () -> Unit) {
        itemView.setOnClickListener {
            itemView.performAppHapticTap()
            onClick()
        }
    }
}

