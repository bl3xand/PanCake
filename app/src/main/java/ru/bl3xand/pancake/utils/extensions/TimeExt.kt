package ru.bl3xand.pancake.utils.extensions

/**
 * Форматировать длительность времени в читаемый формат.
 *
 * @param millis время в миллисекундах
 * @return отформатированная строка (e.g. "2 дня 3 часа")
 */
fun Long.formatDuration(): String {
    val totalSeconds = this / 1000
    val days = totalSeconds / (24 * 3600)
    val hours = (totalSeconds % (24 * 3600)) / 3600
    val minutes = (totalSeconds % 3600) / 60

    return when {
        days > 0 -> "$days дней"
        hours > 0 -> "$hours часов"
        minutes > 0 -> "$minutes минут"
        else -> "менее минуты"
    }
}