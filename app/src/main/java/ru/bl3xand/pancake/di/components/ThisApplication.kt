package ru.bl3xand.pancake.di.components

import android.app.Application
import com.google.android.material.color.DynamicColors
import com.google.firebase.database.FirebaseDatabase
import ru.bl3xand.pancake.data.sync.GitHubDeleteQueueSyncEngine

class ThisApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        DynamicColors.applyToActivitiesIfAvailable(this)

        // Установите поддержку офлайн-режима для Firebase Realtime Database
        FirebaseDatabase.getInstance().setPersistenceEnabled(true)

        // Runtime-синк очереди удаления медиа (без фонового Worker).
        GitHubDeleteQueueSyncEngine.start(this)
    }
}