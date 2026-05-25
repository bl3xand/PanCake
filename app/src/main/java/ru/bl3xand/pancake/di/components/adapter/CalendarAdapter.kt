package ru.bl3xand.pancake.di.components.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import ru.bl3xand.pancake.R
import ru.bl3xand.pancake.data.model.CalendarItem
import ru.bl3xand.pancake.data.model.list.CalendarListItem
import ru.bl3xand.pancake.di.components.viewholder.CalendarViewHolder
import ru.bl3xand.pancake.di.components.viewholder.DateHeaderViewHolder
import java.text.SimpleDateFormat
import java.util.*

class CalendarAdapter(
    private val context: Context,
    private var items: MutableList<CalendarListItem>,
    private val onDeleteItem: (String) -> Unit,
    private val onItemClick: (Int) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val VIEW_TYPE_TASK = 0
        const val VIEW_TYPE_DATE_HEADER = 1
    }

    private var isEditMode = false

    @SuppressLint("NotifyDataSetChanged")
    fun setEditMode(editMode: Boolean) {
        isEditMode = editMode
        notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateItems(calendarItems: List<CalendarItem>) {
        items.clear()
        var lastDate = ""
        val activeItems = calendarItems.filter { !it.isStrikedThrough }

        val noTimeItems = activeItems.filter { it.deadline <= 0L }.sortedByDescending { it.importanceType }
        if (noTimeItems.isNotEmpty()) {
            items.add(CalendarListItem.DateHeader(0L, context.getString(R.string.no_time_tasks)))
            noTimeItems.forEach { items.add(CalendarListItem.TaskItem(it)) }
        }

        // Сортировка активных задач с дедлайном по дате, внутри дня — по важности убывающе
        activeItems.filter { it.deadline > 0L }
            .sortedWith(compareBy({ it.deadline.let { d ->
                val cal = Calendar.getInstance().apply { timeInMillis = d }
                SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(cal.time)
            } }, { -it.importanceType }))
            .forEach { calendarItem ->
                val calendar = Calendar.getInstance().apply { timeInMillis = calendarItem.deadline }
                val date = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(calendar.time)
                if (date != lastDate) {
                    items.add(CalendarListItem.DateHeader(calendarItem.deadline))
                    lastDate = date
                }
                items.add(CalendarListItem.TaskItem(calendarItem))
            }

        // Добавление выполненных задач в конец списка
        val completedItems = calendarItems.filter { it.isStrikedThrough }
        if (completedItems.isNotEmpty()) {
            items.add(
                CalendarListItem.DateHeader(
                    System.currentTimeMillis(),
                    context.getString(R.string.completed_tasks)
                )
            )
            completedItems.forEach { completedItem ->
                items.add(CalendarListItem.TaskItem(completedItem))
            }
        }

        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is CalendarListItem.TaskItem -> VIEW_TYPE_TASK
            is CalendarListItem.DateHeader -> VIEW_TYPE_DATE_HEADER
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_TASK -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.calendar_item, parent, false)
                CalendarViewHolder(view)
            }
            VIEW_TYPE_DATE_HEADER -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.date_header_item, parent, false)
                DateHeaderViewHolder(view)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is CalendarListItem.TaskItem -> {
                (holder as CalendarViewHolder).bind(
                    item = item.calendarItem,
                    isEditMode = isEditMode,
                    onDeleteItem = { onDeleteItem(item.calendarItem.id) },
                    onClick = { onItemClick(position) }
                )
            }
            is CalendarListItem.DateHeader -> {
                (holder as DateHeaderViewHolder).bind(item.dateMillis, item.title)
            }
        }
    }

    fun getItem(position: Int): CalendarListItem {
        return items[position]
    }

    override fun getItemCount(): Int = items.size

    fun isHeader(position: Int): Boolean = items.getOrNull(position) is CalendarListItem.DateHeader
}