package ru.bl3xand.pancake.ui.viewmodel

import android.app.Application
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import ru.bl3xand.pancake.config.AppConfig
import ru.bl3xand.pancake.utils.logs.Logger
import ru.bl3xand.pancake.utils.preferences.getAppPreferences
import ru.bl3xand.pancake.utils.user.UserNameNormalizer

class ChooseCharacterViewModel(private val application: Application) :
    AndroidViewModel(application) {

    companion object {
        private const val TAG = "ChooseCharacterViewModel"
    }

    private val sharedPreferences by lazy {
        getAppPreferences(application)
    }

    fun saveUserName(userName: String) {
        val normalizedName = UserNameNormalizer.normalize(userName)
        sharedPreferences.edit { putString(AppConfig.Preferences.CHARACTER_KEY, normalizedName) }
        Logger.logDebug(
            tag = TAG,
            msg = "User name saved: $normalizedName"
        )
    }

    fun saveSpaceId(spaceId: String) {
        sharedPreferences.edit { putString(AppConfig.Preferences.SPACE_ID_KEY, spaceId) }
        Logger.logDebug(tag = TAG, msg = "Space saved: $spaceId")
    }
}