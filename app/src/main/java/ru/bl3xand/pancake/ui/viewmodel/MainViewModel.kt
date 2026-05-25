package ru.bl3xand.pancake.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import ru.bl3xand.pancake.config.AppConfig
import ru.bl3xand.pancake.utils.preferences.getAppPreferences

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val sharedPreferences by lazy { getAppPreferences(application) }

    private val _characterSelected = MutableLiveData<Boolean>()
    val characterSelected: LiveData<Boolean> get() = _characterSelected

    init {
        checkCharacterSelected()
    }

    private fun checkCharacterSelected() {
        val userName = sharedPreferences.getString(AppConfig.Preferences.CHARACTER_KEY, null)
        val spaceId = sharedPreferences.getString(AppConfig.Preferences.SPACE_ID_KEY, null)
        _characterSelected.value =
            !userName.isNullOrBlank() && !spaceId.isNullOrBlank()
    }
}