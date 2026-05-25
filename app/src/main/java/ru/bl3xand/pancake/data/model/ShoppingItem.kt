package ru.bl3xand.pancake.data.model

data class ShoppingItem(
    val id: String = "",
    val name: String = "",
    val count: String = "",
    val type: String = "",
    val createdBy: String = "",
    val timestamp: Long = 0L,
    var isStrikedThrough: Boolean = false,
    var order: Int = 0
)