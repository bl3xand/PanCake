package ru.bl3xand.pancake.data.model.list

import ru.bl3xand.pancake.data.model.ShoppingItem

/**
 * Запечатанный класс для представления элементов в списке покупок.
 * Содержит либо товар, либо заголовок секции.
 */
sealed class ShoppingListItem {
    /**
     * Элемент товара в списке.
     *
     * @property shoppingItem данные товара
     */
    data class Item(val shoppingItem: ShoppingItem) : ShoppingListItem()

    /**
     * Заголовок секции (К покупке / Куплено).
     *
     * @property title текст заголовка
     */
    data class Header(val title: String) : ShoppingListItem()
}