package ru.bl3xand.pancake.di.components.adapter

import android.annotation.SuppressLint
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import ru.bl3xand.pancake.R
import ru.bl3xand.pancake.data.cache.ImageCacheManager
import ru.bl3xand.pancake.data.model.NoteItem
import ru.bl3xand.pancake.data.model.notes.NoteMetaMode
import ru.bl3xand.pancake.di.components.viewholder.NotesViewHolder

class NotesAdapter(
    private val onClick: (NoteItem) -> Unit,
    private val onLongClick: (NoteItem) -> Unit,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val imageCacheManager: ImageCacheManager
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val noteItems = mutableListOf<NoteItem>()
    private val displayItems = mutableListOf<DisplayItem>()
    private val selectedIds = linkedSetOf<String>()
    private var isSelectionMode: Boolean = false
    private var metaMode: NoteMetaMode = NoteMetaMode.UPDATED

    init {
        setHasStableIds(true)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateItems(newItems: List<NoteItem>) {
        val oldDisplayItems = displayItems.toList()
        noteItems.clear()
        noteItems.addAll(newItems)
        val newDisplayItems = buildDisplayItems()
        applyDisplayDiff(oldDisplayItems, newDisplayItems)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun setSelection(selected: Set<String>, selectionMode: Boolean) {
        selectedIds.clear()
        selectedIds.addAll(selected)
        isSelectionMode = selectionMode
        notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun setMetaMode(mode: NoteMetaMode) {
        metaMode = mode
        notifyDataSetChanged()
    }

    fun moveItem(from: Int, to: Int): Boolean {
        val fromItem = displayItems.getOrNull(from) as? DisplayItem.Note ?: return false
        val toItem = displayItems.getOrNull(to) as? DisplayItem.Note ?: return false
        if (fromItem.value.isPinned != toItem.value.isPinned) return false

        val fromIndex = noteItems.indexOfFirst { it.id == fromItem.value.id }
        val toIndex = noteItems.indexOfFirst { it.id == toItem.value.id }
        if (fromIndex < 0 || toIndex < 0) return false

        val moved = noteItems.removeAt(fromIndex)
        noteItems.add(toIndex, moved)
        val oldDisplayItems = displayItems.toList()
        val newDisplayItems = buildDisplayItems()
        applyDisplayDiff(oldDisplayItems, newDisplayItems)
        return true
    }

    fun getItem(position: Int): NoteItem? =
        (displayItems.getOrNull(position) as? DisplayItem.Note)?.value

    fun currentIds(): List<String> = noteItems.map { it.id }

    fun isHeader(position: Int): Boolean = displayItems.getOrNull(position) is DisplayItem.Header

    fun getSpanSize(position: Int): Int = if (isHeader(position)) 2 else 1

    override fun getItemId(position: Int): Long {
        return displayItems[position].stableId
    }

    override fun getItemViewType(position: Int): Int {
        return when (displayItems[position]) {
            is DisplayItem.Header -> VIEW_TYPE_HEADER
            is DisplayItem.Note -> VIEW_TYPE_NOTE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_HEADER) {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.note_section_header_item, parent, false)
            HeaderViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.note_item, parent, false)
            NotesViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = displayItems[position]) {
            is DisplayItem.Header -> (holder as HeaderViewHolder).bind(item.titleRes)
            is DisplayItem.Note -> (holder as NotesViewHolder).bind(
                item.value,
                metaMode,
                selectedIds.contains(item.value.id),
                isSelectionMode,
                lifecycleScope,
                imageCacheManager,
                onClick,
                onLongClick
            )
        }
    }

    override fun onViewAttachedToWindow(holder: RecyclerView.ViewHolder) {
        super.onViewAttachedToWindow(holder)
        val pos = holder.bindingAdapterPosition
        if (pos != RecyclerView.NO_POSITION && getItemViewType(pos) == VIEW_TYPE_HEADER) {
            val params = holder.itemView.layoutParams as? StaggeredGridLayoutManager.LayoutParams
            params?.isFullSpan = true
        }
    }

    override fun getItemCount(): Int = displayItems.size

    private fun buildDisplayItems(): List<DisplayItem> {
        val result = mutableListOf<DisplayItem>()
        if (noteItems.isEmpty()) return result

        val pinned = noteItems.filter { it.isPinned }
        val other = noteItems.filterNot { it.isPinned }

        // Заголовок и заметки "Закреплённые" — только если есть хотя бы одна
        if (pinned.isNotEmpty()) {
            result.add(DisplayItem.Header(R.string.notes_section_pinned))
            result.addAll(buildSectionNotes(pinned))
        }

        // Заголовок "Другие" — только если есть незакреплённые И есть закреплённые выше
        // Если закреплённых нет — просто список без заголовка
        if (other.isNotEmpty()) {
            if (pinned.isNotEmpty()) {
                result.add(DisplayItem.Header(R.string.notes_section_other))
                result.addAll(buildSectionNotes(other))
            } else {
                result.addAll(buildSectionNotes(other))
            }
        }

        return result
    }

    private fun buildSectionNotes(notes: List<NoteItem>): List<DisplayItem.Note> {
        return notes.map { note ->
            DisplayItem.Note(value = note)
        }
    }

    private fun applyDisplayDiff(oldItems: List<DisplayItem>, newItems: List<DisplayItem>) {
        val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = oldItems.size

            override fun getNewListSize(): Int = newItems.size

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return oldItems[oldItemPosition].stableId == newItems[newItemPosition].stableId
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return oldItems[oldItemPosition] == newItems[newItemPosition]
            }
        })
        displayItems.clear()
        displayItems.addAll(newItems)
        diffResult.dispatchUpdatesTo(this)
    }

    private sealed interface DisplayItem {
        val stableId: Long

        data class Header(val titleRes: Int) : DisplayItem {
            override val stableId: Long = -titleRes.toLong()
        }

        data class Note(
            val value: NoteItem,
        ) : DisplayItem {
            override val stableId: Long = value.id.hashCode().toLong()
        }
    }

    private class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.tvSectionTitle)

        fun bind(valueRes: Int) {
            val text = itemView.context.getString(valueRes)
            // Жирный текст как у разделителей на экране Кино
            val spannable = SpannableString(text)
            spannable.setSpan(StyleSpan(Typeface.BOLD), 0, text.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            title.text = spannable
        }
    }

    private companion object {
        const val VIEW_TYPE_HEADER = 0
        const val VIEW_TYPE_NOTE = 1
    }
}

