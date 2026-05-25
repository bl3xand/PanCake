package ru.bl3xand.pancake.utils.user

import ru.bl3xand.pancake.config.AppConfig

object UserNameNormalizer {
    fun normalize(value: String?, fallback: String = AppConfig.Characters.DEFAULT): String {
        return value
            ?.trim()
            ?.split(Regex("\\s+"))
            ?.firstOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: fallback
    }
}