package ru.bl3xand.pancake.data.model

import com.google.firebase.database.PropertyName

data class NoteItem(
    val id: String = "",
    val title: String = "",
    val subTitle: String = "",
    val noteText: String = "",
    val color: String = "",
    val imgPath: String = "",
    val imagePaths: List<String> = emptyList(),
    val webLink: String = "",
    val dateTime: String = "",
    val createdBy: String = "",
    val updatedBy: String = "",
    val timestamp: Long = 0L,
    val updatedAt: Long = 0L,
    val sortOrder: Long = 0L,
    // @PropertyName обязателен: Firebase некорректно сериализует Kotlin Boolean с is-префиксом
    @get:PropertyName("isPinned")
    @set:PropertyName("isPinned")
    var isPinned: Boolean = false
)
