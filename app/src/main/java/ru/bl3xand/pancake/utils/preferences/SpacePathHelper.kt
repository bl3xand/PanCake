package ru.bl3xand.pancake.utils.preferences

import android.content.Context
import ru.bl3xand.pancake.config.AppConfig

/**
 * Формирует namespaced-пути Firebase для текущего пространства.
 * Если пространство еще не выбрано, возвращает legacy-узел для совместимости.
 */
object SpacePathHelper {

    fun currentSpaceId(context: Context): String? {
        return getAppPreferences(context)
            .getString(AppConfig.Preferences.SPACE_ID_KEY, null)
            ?.takeIf { it.isNotBlank() }
    }

    fun node(context: Context, baseNode: String): String {
        val spaceId = currentSpaceId(context)
        return "spaces/${spaceId ?: "_no_space_"}/$baseNode"
    }
}

