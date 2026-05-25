package ru.bl3xand.pancake.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import ru.bl3xand.pancake.data.model.network.ApiMovieItem
import ru.bl3xand.pancake.data.model.network.KinopoiskResponse
import ru.bl3xand.pancake.data.network.KinopoiskApiService
import ru.bl3xand.pancake.utils.logs.Logger

class SearchViewModel : ViewModel() {

    companion object {
        private const val TAG = "SearchViewModel"
    }

    private val _searchResults = MutableLiveData<List<ApiMovieItem>>()
    val searchResults: LiveData<List<ApiMovieItem>> get() = _searchResults
    private var activeSearchCall: Call<KinopoiskResponse>? = null
    private var lastSubmittedQuery: String? = null

    fun searchMovies(query: String) {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) return
        if (normalizedQuery == lastSubmittedQuery) {
            Logger.logDebug(TAG, "searchMovies skipped: duplicate query=$normalizedQuery")
            return
        }

        lastSubmittedQuery = normalizedQuery
        activeSearchCall?.cancel()
        Logger.logDebug(TAG, "searchMovies: query=$normalizedQuery")

        val call = KinopoiskApiService.create().searchMovies(normalizedQuery)
        activeSearchCall = call
        call
            .enqueue(object : Callback<KinopoiskResponse> {
                override fun onResponse(
                    call: Call<KinopoiskResponse>,
                    response: Response<KinopoiskResponse>
                ) {
                    if (call.isCanceled) return
                    if (response.isSuccessful) {
                        val docs = response.body()?.docs ?: emptyList()
                        Logger.logDebug(TAG, "searchMovies: received ${docs.size} items")
                        _searchResults.value = docs
                    } else {
                        Logger.logError(TAG, "searchMovies failed: code=${response.code()}")
                        _searchResults.value = emptyList()
                    }
                }

                override fun onFailure(call: Call<KinopoiskResponse>, t: Throwable) {
                    if (call.isCanceled) {
                        Logger.logDebug(TAG, "searchMovies canceled")
                        return
                    }
                    Logger.logError(TAG, "searchMovies request failed: ${t.message}")
                    _searchResults.value = emptyList()
                }
            })
    }

    override fun onCleared() {
        activeSearchCall?.cancel()
        activeSearchCall = null
        super.onCleared()
    }
}