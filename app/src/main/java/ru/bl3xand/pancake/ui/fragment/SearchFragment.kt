package ru.bl3xand.pancake.ui.fragment

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import ru.bl3xand.pancake.R
import ru.bl3xand.pancake.config.AppConfig
import ru.bl3xand.pancake.data.model.MovieItem
import ru.bl3xand.pancake.data.model.network.ApiMovieItem
import ru.bl3xand.pancake.databinding.FragmentSearchBinding
import ru.bl3xand.pancake.di.components.adapter.MovieSearchAdapter
import ru.bl3xand.pancake.ui.dialogs.Dialogs
import ru.bl3xand.pancake.ui.viewmodel.MovieFragmentViewModel
import ru.bl3xand.pancake.ui.viewmodel.SearchViewModel
import ru.bl3xand.pancake.ui.viewmodelfactory.MovieFragmentViewModelFactory
import ru.bl3xand.pancake.utils.MovieTypeHelper
import ru.bl3xand.pancake.utils.preferences.getAppPreferences
import ru.bl3xand.pancake.utils.ui.UnifiedItemDecoration
import ru.bl3xand.pancake.utils.user.UserNameNormalizer

class SearchFragment : Fragment() {

    companion object {
        private const val SEARCH_DEBOUNCE_MS = 2_000L
    }

    private data class MovieDraft(
        val title: String,
        val type: String,
        val posterUrl: String = ""
    )

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: SearchViewModel
    private lateinit var movieViewModel: MovieFragmentViewModel
    private lateinit var searchAdapter: MovieSearchAdapter
    private val searchHandler = Handler(Looper.getMainLooper())
    private var pendingSearchRunnable: Runnable? = null
    private var lastSubmittedQuery: String? = null

    private val movieStatuses: List<String>
        get() = listOf(
            getString(R.string.movie_status_watching),
            getString(R.string.movie_status_planned),
            getString(R.string.movie_status_watched)
        )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        viewModel = ViewModelProvider(this)[SearchViewModel::class.java]
        movieViewModel = ViewModelProvider(
            this,
            MovieFragmentViewModelFactory(requireActivity().application)
        )[MovieFragmentViewModel::class.java]

        setupUI()
        observeViewModel()

        return binding.root
    }

    override fun onDestroyView() {
        pendingSearchRunnable?.let(searchHandler::removeCallbacks)
        pendingSearchRunnable = null
        super.onDestroyView()
        _binding = null
    }

    private fun setupUI() {
        binding.recyclerViewResults.layoutManager = LinearLayoutManager(context)
        binding.recyclerViewResults.addItemDecoration(UnifiedItemDecoration())

        searchAdapter = MovieSearchAdapter(
            onMovieSelected = { apiMovie ->
                handleMovieSelected(apiMovie)
            },
            onAddCustomMovie = {
                showAddCustomMovieDialog()
            }
        )
        binding.recyclerViewResults.adapter = searchAdapter

        binding.editTextSearch.addTextChangedListener {
            scheduleSearch(binding.editTextSearch.text?.toString().orEmpty())
        }

        binding.editTextSearch.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN) {
                submitSearchNow(binding.editTextSearch.text?.toString().orEmpty())
                return@setOnKeyListener true
            }
            false
        }
    }

    private fun scheduleSearch(query: String) {
        pendingSearchRunnable?.let(searchHandler::removeCallbacks)
        if (query.isBlank()) return

        // Делаем запрос только после паузы в наборе.
        pendingSearchRunnable = Runnable {
            submitSearchNow(query)
        }.also { runnable ->
            searchHandler.postDelayed(runnable, SEARCH_DEBOUNCE_MS)
        }
    }

    private fun submitSearchNow(query: String) {
        pendingSearchRunnable?.let(searchHandler::removeCallbacks)
        pendingSearchRunnable = null
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) return
        // Не отправляем подряд один и тот же запрос.
        if (normalizedQuery == lastSubmittedQuery) return

        lastSubmittedQuery = normalizedQuery
        viewModel.searchMovies(normalizedQuery)
    }

    private fun handleMovieSelected(apiMovie: ApiMovieItem) {
        val draft = MovieDraft(
            title = apiMovie.name.orEmpty(),
            type = MovieTypeHelper.determineTypeFromApi(apiMovie.type),
            posterUrl = apiMovie.poster?.url.orEmpty()
        )
        checkForDuplicateAndAdd(draft)
    }

    private fun checkForDuplicateAndAdd(draft: MovieDraft) {
        movieViewModel.searchMovieInStatuses(draft.title, movieStatuses) { existing, status ->
            if (!isAdded || context == null) return@searchMovieInStatuses
            if (existing != null && !status.isNullOrBlank()) {
                showDuplicateFoundDialog(draft.title, status)
            } else {
                showMovieStatusDialog(draft)
            }
        }
    }

    private fun showDuplicateFoundDialog(movieTitle: String, status: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.movie_already_added_title)
            .setMessage(getString(R.string.movie_already_added_message, movieTitle, status))
            .setPositiveButton(R.string.show_action) { _, _ ->
                openMovieTab(status)
            }
            .setNegativeButton(R.string.dialog_cancel_action, null)
            .show()
    }

    private fun showMovieStatusDialog(draft: MovieDraft) {
        val safeContext = context ?: return
        Dialogs.showMovieStatusDialog(
            context = safeContext,
            title = draft.title.ifBlank { getString(R.string.unknown_value) },
            onStatusSelected = { status ->
                addMovieToDatabase(draft, status)
            }
        )
    }

    private fun addMovieToDatabase(draft: MovieDraft, status: String) {
        // Собираем локальную модель и пишем в БД через VM.
        val movieItem = MovieItem(
            title = draft.title,
            posterUrl = draft.posterUrl,
            createdBy = getCurrentUserName(),
            timestamp = System.currentTimeMillis(),
            type = draft.type,
            status = status
        )

        movieViewModel.addMovie(movieItem, status)
        openMovieTab(status)
    }

    private fun showAddCustomMovieDialog() {
        val safeContext = context ?: return
        Dialogs.showAddCustomMovieDialog(
            context = safeContext,
            onAddMovie = { title, type ->
                Dialogs.showMovieStatusDialog(
                    context = safeContext,
                    title = title,
                    onStatusSelected = { status ->
                        addMovieToDatabase(
                            draft = MovieDraft(title = title, type = type),
                            status = status
                        )
                    }
                )
            }
        )
    }

    /**
     * Получить имя текущего пользователя из настроек.
     *
     * @return имя пользователя или "Unknown"
     */
    private fun getCurrentUserName(): String =
        UserNameNormalizer.normalize(
            getAppPreferences(requireContext())
                .getString(AppConfig.Preferences.CHARACTER_KEY, AppConfig.Characters.DEFAULT)
        )

    private fun openMovieTab(status: String) {
        // Используем activity manager, чтобы не падать при detach фрагмента.
        val fragmentManager = activity?.supportFragmentManager ?: return
        fragmentManager.popBackStack()
        fragmentManager.executePendingTransactions()
        (fragmentManager.findFragmentById(R.id.fragment_container) as? MovieFragment)
            ?.showMovieWithStatus(status)
    }

    private fun observeViewModel() {
        viewModel.searchResults.observe(viewLifecycleOwner) { results ->
            searchAdapter.updateResults(results)
        }
    }
}

