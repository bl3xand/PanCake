package ru.bl3xand.pancake.ui.viewmodelfactory

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import ru.bl3xand.pancake.ui.viewmodel.NotesFragmentViewModel

class NotesFragmentViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return NotesFragmentViewModel(application) as T
    }
}

