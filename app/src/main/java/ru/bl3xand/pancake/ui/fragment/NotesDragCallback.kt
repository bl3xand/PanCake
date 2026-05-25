package ru.bl3xand.pancake.ui.fragment

import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView

/**
 * Callback для перетаскивания заметок в GridLayoutManager.
 * Поддерживает все 4 направления; запрещает drag в режиме выделения и для заголовков секций.
 */
class NotesDragCallback(
    private val isSelectionActive: () -> Boolean,
    private val isHeader: (Int) -> Boolean,
    private val onMove: (from: Int, to: Int) -> Boolean,
    private val onDropped: (moved: Boolean, startPosition: Int) -> Unit
) : ItemTouchHelper.SimpleCallback(
    ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT,
    0
) {
    private var moved = false
    private var startPosition = RecyclerView.NO_POSITION

    override fun isLongPressDragEnabled(): Boolean = true

    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
        val position = viewHolder.bindingAdapterPosition
        // Запрещаем drag в режиме выделения и для заголовков
        if (isSelectionActive() || position == RecyclerView.NO_POSITION || isHeader(position)) {
            return 0
        }
        return makeMovementFlags(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT,
            0
        )
    }

    override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        super.onSelectedChanged(viewHolder, actionState)
        if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
            startPosition = viewHolder?.bindingAdapterPosition ?: RecyclerView.NO_POSITION
            moved = false
        }
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        val from = viewHolder.bindingAdapterPosition
        val to = target.bindingAdapterPosition
        if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
        moved = onMove(from, to)
        return moved
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)
        val savedMoved = moved
        val savedStart = startPosition
        moved = false
        startPosition = RecyclerView.NO_POSITION
        onDropped(savedMoved, savedStart)
    }
}

