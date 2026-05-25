package ru.bl3xand.pancake.di.components

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.core.content.edit
import com.google.android.material.color.DynamicColors
import com.google.firebase.database.FirebaseDatabase
import ru.bl3xand.pancake.config.AppConfig
import ru.bl3xand.pancake.data.sync.GitHubDeleteQueueSyncEngine
import ru.bl3xand.pancake.utils.preferences.getAppPreferences
import java.util.Locale

class ThisApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        DynamicColors.applyToActivitiesIfAvailable(this)
        applyLocaleOnFirstLaunch()

        // Установите поддержку офлайн-режима для Firebase Realtime Database
        FirebaseDatabase.getInstance().setPersistenceEnabled(true)

        // Runtime-синк очереди удаления медиа (без фонового Worker).
        GitHubDeleteQueueSyncEngine.start(this)
    }

    private fun applyLocaleOnFirstLaunch() {
        val prefs = getAppPreferences(this)
        if (prefs.getBoolean(AppConfig.Preferences.LOCALE_INITIALIZED_KEY, false)) return

        val languageTag = when (Locale.getDefault().language.lowercase(Locale.ROOT)) {
            "ru" -> "ru"
            "zh" -> "zh-CN"
            else -> "en"
        }

        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageTag))
        prefs.edit { putBoolean(AppConfig.Preferences.LOCALE_INITIALIZED_KEY, true) }
    }
}