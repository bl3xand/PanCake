package ru.bl3xand.pancake.utils.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager

/**
 * Получить стандартные SharedPreferences приложения.
 * Используется для сохранения простых настроек и пользовательских данных.
 *
 * @param context Context приложения
 * @return SharedPreferences объект
 */
fun getAppPreferences(context: Context): SharedPreferences {
    return PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
}