package ru.bl3xand.pancake.ui.viewmodelfactory

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import ru.bl3xand.pancake.ui.viewmodel.ShoppingFragmentViewModel

class ShoppingFragmentViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ShoppingFragmentViewModel(application) as T
    }
}