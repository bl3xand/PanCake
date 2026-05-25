package ru.bl3xand.pancake.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import ru.bl3xand.pancake.config.AppConfig
import ru.bl3xand.pancake.data.model.MovieItem
import ru.bl3xand.pancake.utils.logs.Logger
import ru.bl3xand.pancake.utils.preferences.SpacePathHelper
import ru.bl3xand.pancake.utils.preferences.getAppPreferences
import ru.bl3xand.pancake.utils.security.SpaceCrypto
import ru.bl3xand.pancake.utils.user.UserNameNormalizer

class MovieFragmentViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "MovieFragmentViewModel"
    }

    private val database: DatabaseReference =
        FirebaseDatabase.getInstance().reference.child(
            SpacePathHelper.node(application, AppConfig.Firebase.MOVIES)
        )

    private val _movies = MutableLiveData<List<MovieItem>>()
    val movies: LiveData<List<MovieItem>> get() = _movies

    private val sharedPreferences by lazy { getAppPreferences(application) }
    private val spaceId: String by lazy {
        SpacePathHelper.currentSpaceId(application) ?: error("Space is not selected")
    }
    private var moviesListener: ValueEventListener? = null

    override fun onCleared() {
        super.onCleared()
        clearMoviesListener()
    }

    fun loadMoviesByStatus(status: String) {
        Logger.logDebug(TAG, "loadMoviesByStatus: status=$status")
        // Держим только один активный listener для текущего статуса.
        clearMoviesListener()

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val items = snapshot.children
                    .mapNotNull { SpaceCrypto.decodeSnapshot<MovieItem>(spaceId, it) }
                    .map { item -> item.copy(createdBy = UserNameNormalizer.normalize(item.createdBy)) }
                    .filter { it.status == status }
                    .sortedByDescending { it.timestamp }
                Logger.logDebug(TAG, "loadMoviesByStatus resultCount=${items.size} status=$status")
                _movies.value = items
            }

            override fun onCancelled(error: DatabaseError) {
                Logger.logError(TAG, "loadMoviesByStatus error: $error")
            }
        }

        database.addValueEventListener(listener)
        moviesListener = listener
    }

    fun addMovie(movie: MovieItem, status: String) {
        Logger.logDebug(TAG, "addMovie: title=${movie.title} type=${movie.type} status=$status")
        val movieId = database.push().key ?: return
        val currentItems = _movies.value.orEmpty()
        val maxOrder = currentItems.maxOfOrNull { it.order } ?: -1
        val userName = getCurrentUserName()

        val newMovie = movie.copy(
            id = movieId,
            status = status,
            createdBy = userName,
            timestamp = System.currentTimeMillis(),
            order = maxOrder + 1
        )
        Logger.logDebug(TAG, "addMovie: saving movieId=$movieId")
        database.child(movieId).setValue(SpaceCrypto.encryptModel(spaceId, newMovie))
    }

    fun deleteMovie(movieId: String) {
        Logger.logDebug(TAG, "deleteMovie: movieId=$movieId")
        database.child(movieId).removeValue()
    }

    fun updateMovie(movie: MovieItem) {
        Logger.logDebug(TAG, "updateMovie: id=${movie.id} status=${movie.status}")
        database.child(movie.id).setValue(SpaceCrypto.encryptModel(spaceId, movie))
    }

    fun searchMovieByTitle(title: String, status: String, callback: (MovieItem?) -> Unit) {
        val normalizedTitle = normalizeTitle(title)
        database.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val movie = snapshot.children
                        .mapNotNull { SpaceCrypto.decodeSnapshot<MovieItem>(spaceId, it) }
                        .filter { it.status == status }
                        .firstOrNull { normalizeTitle(it.title) == normalizedTitle }
                    callback(movie)
                }

                override fun onCancelled(error: DatabaseError) {
                    Logger.logError(TAG, "searchMovieByTitle error: $error")
                    callback(null)
                }
            })
    }

    fun searchMovieInStatuses(
        title: String,
        statuses: List<String>,
        callback: (movie: MovieItem?, status: String?) -> Unit
    ) {
        val normalizedTitle = normalizeTitle(title)
        database.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val matched = snapshot.children
                    .mapNotNull { SpaceCrypto.decodeSnapshot<MovieItem>(spaceId, it) }
                    .firstOrNull { movie ->
                        statuses.contains(movie.status) && normalizeTitle(movie.title) == normalizedTitle
                    }
                callback(matched, matched?.status)
            }

            override fun onCancelled(error: DatabaseError) {
                Logger.logError(TAG, "searchMovieInStatuses error: $error")
                callback(null, null)
            }
        })
    }

    private fun clearMoviesListener() {
        val listener = moviesListener
        if (listener != null) {
            database.removeEventListener(listener)
        }
        moviesListener = null
    }

    private fun getCurrentUserName(): String =
        UserNameNormalizer.normalize(
            sharedPreferences.getString(AppConfig.Preferences.CHARACTER_KEY, AppConfig.Characters.DEFAULT)
        )

    private fun normalizeTitle(value: String): String = value.trim().lowercase()
}

