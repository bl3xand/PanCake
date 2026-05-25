package ru.bl3xand.pancake.di.components.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import ru.bl3xand.pancake.R
import ru.bl3xand.pancake.data.model.ShoppingItem
import ru.bl3xand.pancake.data.model.list.ShoppingListItem
import ru.bl3xand.pancake.di.components.viewholder.DateHeaderViewHolder
import ru.bl3xand.pancake.di.components.viewholder.ShoppingViewHolder

class ShoppingAdapter(
    private val context: Context,
    private var items: MutableList<ShoppingListItem>,
    private val onDeleteItem: (String) -> Unit,
    private val onItemClick: (ShoppingItem) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    companion object {
        private const val VIEW_TYPE_ITEM = 0
        private const val VIEW_TYPE_HEADER = 1
    }

    private var isEditMode = false

    @SuppressLint("NotifyDataSetChanged")
    fun setEditMode(editMode: Boolean) {
        isEditMode = editMode
        notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateItems(newItems: List<ShoppingItem>) {
        items.clear()

        val activeItems = newItems.filterNot { it.isStrikedThrough }
        val addedItems = newItems
            .filter { it.isStrikedThrough }
            .sortedByDescending { it.timestamp }

        if (activeItems.isNotEmpty()) {
            items.add(ShoppingListItem.Header(context.getString(R.string.to_buy_items)))
            activeItems.forEach { items.add(ShoppingListItem.Item(it)) }
        }

        if (addedItems.isNotEmpty()) {
            items.add(ShoppingListItem.Header(context.getString(R.string.added_items)))
            addedItems.forEach { items.add(ShoppingListItem.Item(it)) }
        }

        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is ShoppingListItem.Item -> VIEW_TYPE_ITEM
            is ShoppingListItem.Header -> VIEW_TYPE_HEADER
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_ITEM -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.shopping_item, parent, false)
                ShoppingViewHolder(view)
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
            is ShoppingListItem.Item -> {
                (holder as ShoppingViewHolder).bind(
                    item = item.shoppingItem,
                    isEditMode = isEditMode,
                    onDeleteItem = onDeleteItem,
                    onClick = {
                        if (!isEditMode) {
                            onItemClick(item.shoppingItem)
                        }
                    }
                )
            }

            is ShoppingListItem.Header -> {
                (holder as DateHeaderViewHolder).bind(System.currentTimeMillis(), item.title)
            }
        }
    }

    override fun getItemCount(): Int = items.size

    fun isHeader(position: Int): Boolean = items.getOrNull(position) is ShoppingListItem.Header
}