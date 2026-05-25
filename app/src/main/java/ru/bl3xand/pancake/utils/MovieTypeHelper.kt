package ru.bl3xand.pancake.utils

import ru.bl3xand.pancake.utils.logs.Logger

object MovieTypeHelper {

    private const val TAG = "MovieTypeHelper"
    const val TYPE_MOVIE = "movie"
    const val TYPE_SERIES = "series"

    /**
     * Определяет тип контента на основе API type
     */
    fun determineTypeFromApi(apiType: String?): String {
        val result = when (apiType?.lowercase()) {
            // Сериалы
            "tv-series", "tv_series", "series" -> TYPE_SERIES
            "animated-series", "animated_series" -> TYPE_SERIES
            "anime" -> TYPE_SERIES

            // Фильмы
            "film", "movie" -> TYPE_MOVIE
            "cartoon", "animation" -> TYPE_MOVIE

            // По умолчанию - фильм
            else -> TYPE_MOVIE
        }

        Logger.logDebug(TAG, "determineTypeFromApi: input='$apiType' output='$result'")
        return result
    }

    /**
     * Проверяет может ли тип быть определен автоматически
     */
    fun canBeAutoDetected(apiType: String?): Boolean {
        return apiType != null && apiType.isNotBlank()
    }
}

