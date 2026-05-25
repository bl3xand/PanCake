package ru.bl3xand.pancake.data.model.network

data class ApiMovieItem(
    val id: String?,
    val name: String?,
    val year: Int?,
    val poster: Poster?,
    val type: String? = null // "movie" или "series"
)