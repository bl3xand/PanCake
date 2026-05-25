package ru.bl3xand.pancake.di.components.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import ru.bl3xand.pancake.R
import ru.bl3xand.pancake.data.model.network.ApiMovieItem
import ru.bl3xand.pancake.utils.ui.performAppHapticTap

class MovieSearchAdapter(
    private val onMovieSelected: (ApiMovieItem) -> Unit,
    private val onAddCustomMovie: (() -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_MOVIE = 0
        private const val TYPE_ADD_CUSTOM = 1
    }

    private var movies: List<ApiMovieItem> = listOf()

    fun updateResults(newMovies: List<ApiMovieItem>) {
        movies = newMovies
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return if (position < movies.size) TYPE_MOVIE else TYPE_ADD_CUSTOM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_MOVIE -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.movie_item, parent, false)
                MovieViewHolder(view)
            }
            TYPE_ADD_CUSTOM -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.search_add_custom_movie_item, parent, false)
                AddCustomViewHolder(view)
            }
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is MovieViewHolder -> holder.bind(movies[position])
            is AddCustomViewHolder -> holder.bind(onAddCustomMovie)
        }
    }

    override fun getItemCount(): Int = movies.size + 1 // +1 для карточки "Добавить свой"

    inner class MovieViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val poster: ImageView = itemView.findViewById(R.id.posterImageView)
        private val title: TextView = itemView.findViewById(R.id.titleTextView)

        fun bind(movie: ApiMovieItem) {
            // Для пустого имени показываем нейтральный fallback.
            title.text = movie.name ?: itemView.context.getString(R.string.unknown_value)
            Glide.with(itemView.context)
                .load(movie.poster?.url)
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .skipMemoryCache(false)
                .dontAnimate()
                .into(poster)
            itemView.setOnClickListener { onMovieSelected(movie) }
        }
    }

    class AddCustomViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(onAddClick: (() -> Unit)? = null) {
            itemView.setOnClickListener {
                itemView.performAppHapticTap()
                onAddClick?.invoke()
            }
        }
    }
}