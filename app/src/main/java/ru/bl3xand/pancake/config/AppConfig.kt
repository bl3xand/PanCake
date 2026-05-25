package ru.bl3xand.pancake.config

/**
 * Централизованная конфигурация приложения.
 * Содержит все константы, настройки и параметры.
 */
object AppConfig {

    // ==================== Preferences ключи ====================
    object Preferences {
        const val CHARACTER_KEY = "character"
        const val SPACE_ID_KEY = "space_id"
        const val LOCALE_INITIALIZED_KEY = "locale_initialized"
    }

    // ==================== Пользователи ====================
    object Characters {
        const val DEFAULT = "Unknown"
    }

    // ==================== Firebase Realtime Database узлы ====================
    object Firebase {
        const val SHOPPING = "shopping_items"
        const val CALENDAR = "calendar_items"
        const val MOVIES = "movies"
        const val NOTES = "notes"
    }
}