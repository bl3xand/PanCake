package ru.bl3xand.pancake.data.model


data class MovieItem(
    val id: String = "",
    val title: String = "",
    val season: Int = 0,
    val episode: Int = 0,
    val status: String = "",
    val posterUrl: String = "",
    val createdBy: String = "",
    val timestamp: Long = 0L,
    val type: String = "", // "movie" или "series"
    val order: Int = 0
)