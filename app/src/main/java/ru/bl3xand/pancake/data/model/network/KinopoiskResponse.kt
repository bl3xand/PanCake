package ru.bl3xand.pancake.data.model.network

import ru.bl3xand.pancake.data.model.MovieItem


data class KinopoiskResponse(
    val docs: List<ApiMovieItem> = listOf()
)