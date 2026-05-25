package ru.bl3xand.pancake.config

/**
 * Централизованная конфигурация приложения.
 * Содержит все константы, настройки и параметры.
 * Избегает хардкода по всему приложению.
 */
object AppConfig {

    // ==================== Preferences ключи ====================
    object Preferences {
        const val CHARACTER_KEY = "character"
        const val SPACE_ID_KEY = "space_id"
    }

    // ==================== Персонажи ====================
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