package ru.bl3xand.pancake.data.model

/**
 * Универсальная обертка для хранения зашифрованных записей в Firebase.
 */
data class EncryptedNode(
    val payload: String = "",
    val iv: String = ""
)

