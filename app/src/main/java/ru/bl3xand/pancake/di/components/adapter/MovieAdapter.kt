package ru.bl3xand.pancake.di.components.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import ru.bl3xand.pancake.R
import ru.bl3xand.pancake.data.model.MovieItem
import ru.bl3xand.pancake.data.model.list.MovieListItem
import ru.bl3xand.pancake.di.components.viewholder.DateHeaderViewHolder
import ru.bl3xand.pancake.di.components.viewholder.MovieViewHolder
import ru.bl3xand.pancake.utils.MovieTypeHelper
import ru.bl3xand.pancake.utils.logs.Logger

class MovieAdapter(
    private val context: Context,
    private var items: MutableList<MovieListItem>,
    private val onItemClick: (MovieItem) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_ITEM = 0
        private const val VIEW_TYPE_HEADER = 1
        private const val TAG = "MovieAdapter"
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateItems(newItems: List<MovieItem>) {
        Logger.logDebug(TAG, "updateItems: input=${newItems.size}")
        items.clear()

        val movies = newItems.filter { it.type == MovieTypeHelper.TYPE_MOVIE }
        val series = newItems.filter { it.type == MovieTypeHelper.TYPE_SERIES }

        // Add movies section
        if (movies.isNotEmpty()) {
            items.add(MovieListItem.Header(context.getString(R.string.movies), isFirst = true))
            movies.forEach { items.add(MovieListItem.Item(it)) }
        }

        // Add series section
        if (series.isNotEmpty()) {
            items.add(MovieListItem.Header(context.getString(R.string.series), isFirst = false))
            series.forEach { items.add(MovieListItem.Item(it)) }
        }

        Logger.logDebug(TAG, "updateItems: rendered=${items.size}")
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is MovieListItem.Item -> VIEW_TYPE_ITEM
            is MovieListItem.Header -> VIEW_TYPE_HEADER
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_ITEM -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.movie_item, parent, false)
                MovieViewHolder(view)
            }

            VIEW_TYPE_HEADER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.date_header_item, parent, false)
                DateHeaderViewHolder(view)
            }

            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is MovieListItem.Item -> {
                (holder as MovieViewHolder).bind(
                    item = item.movieItem,
                    onClick = { onItemClick(item.movieItem) }
                )
            }

            is MovieListItem.Header -> {
                (holder as DateHeaderViewHolder).bind(System.currentTimeMillis(), item.title)
            }
        }
    }

    override fun getItemCount(): Int = items.size

    fun isHeader(position: Int): Boolean = items.getOrNull(position) is MovieListItem.Header
}