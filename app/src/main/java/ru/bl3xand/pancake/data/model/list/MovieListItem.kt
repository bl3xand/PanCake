package ru.bl3xand.pancake.data.model.list

import ru.bl3xand.pancake.data.model.MovieItem

/**
 * Запечатанный класс для представления элементов в списке фильмов.
 * Содержит либо фильм/сериал, либо заголовок секции.
 */
sealed class MovieListItem {
    /**
     * Элемент фильма/сериала в списке.
     *
     * @property movieItem данные фильма
     */
    data class Item(val movieItem: MovieItem) : MovieListItem()

    /**
     * Заголовок секции (Фильмы / Сериалы).
     *
     * @property title текст заголовка
     * @property isFirst является ли это первой секцией
     */
    data class Header(val title: String, val isFirst: Boolean = false) : MovieListItem()
}