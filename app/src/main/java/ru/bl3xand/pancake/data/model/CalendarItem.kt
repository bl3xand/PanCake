package ru.bl3xand.pancake.data.model

data class CalendarItem(
    val id: String = "",
    val taskName: String = "",
    val importanceType: Int = 1,
    val deadline: Long = 0L,
    val createdBy: String = "",
    val timestamp: Long = 0L,
    val recurrence: String = "",
    var isStrikedThrough: Boolean = false
)