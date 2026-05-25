package ru.bl3xand.pancake.data.model.list

import ru.bl3xand.pancake.data.model.CalendarItem

/**
 * Запечатанный класс для представления элементов в списке задач календаря.
 * Содержит либо задачу, либо заголовок с датой.
 */
sealed class CalendarListItem {
    /**
     * Элемент задачи в списке.
     *
     * @property calendarItem данные задачи
     */
    data class TaskItem(val calendarItem: CalendarItem) : CalendarListItem()

    /**
     * Заголовок с датой для группировки задач.
     *
     * @property dateMillis время в миллисекундах для форматирования
     * @property title пользовательский текст (e.g. "Нет времени", "Выполнено")
     */
    data class DateHeader(val dateMillis: Long, val title: String = "") : CalendarListItem()
}