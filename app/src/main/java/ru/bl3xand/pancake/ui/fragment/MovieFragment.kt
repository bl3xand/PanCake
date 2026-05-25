package ru.bl3xand.pancake.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import ru.bl3xand.pancake.R
import ru.bl3xand.pancake.data.model.MovieItem
import ru.bl3xand.pancake.databinding.FragmentMovieBinding
import ru.bl3xand.pancake.di.components.adapter.MovieAdapter
import ru.bl3xand.pancake.ui.dialogs.Dialogs
import ru.bl3xand.pancake.ui.viewmodel.MovieFragmentViewModel
import ru.bl3xand.pancake.ui.viewmodelfactory.MovieFragmentViewModelFactory
import ru.bl3xand.pancake.utils.ui.performAppHapticTap
import ru.bl3xand.pancake.utils.ui.applyTertiaryContainerTint
import ru.bl3xand.pancake.utils.ui.UnifiedItemDecoration

class MovieFragment : Fragment() {

    private var _binding: FragmentMovieBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: MovieFragmentViewModel
    private lateinit var adapter: MovieAdapter

    private var currentStatus: String = ""
    private var currentMovies: List<MovieItem> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMovieBinding.inflate(inflater, container, false)
        viewModel = ViewModelProvider(
            this,
            MovieFragmentViewModelFactory(requireActivity().application)
        )[MovieFragmentViewModel::class.java]

        setupRecyclerView()
        setupBottomNavigation()
        setupFab()
        setupWatchedSearch()
        observeViewModel()

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ViewCompat.setOnApplyWindowInsetsListener(binding.navigationView) { v, insets ->
            // Не даём navigation bar раздувать этот BottomNavigationView
            v.setPadding(0, 0, 0, 0)
            insets
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupRecyclerView() {
        adapter = MovieAdapter(
            context = requireContext(),
            items = mutableListOf(),
            onItemClick = { movie -> showMovieActions(movie) }
        )
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@MovieFragment.adapter
            // Единый механизм отступов для всех экранов
            addItemDecoration(UnifiedItemDecoration { position ->
                this@MovieFragment.adapter.isHeader(position)
            })
        }
    }

    private fun showMovieActions(movie: MovieItem) {
        if (currentStatus == getString(R.string.movie_status_watched)) {
            Dialogs.showWatchedMovieOptionsDialog(
                context = requireContext(),
                movie = movie,
                onMovieToWatching = { updateMovieAndKeepCurrentTab(movie.copy(status = getString(R.string.movie_status_watching))) },
                onMovieToPlan = { updateMovieAndKeepCurrentTab(movie.copy(status = getString(R.string.movie_status_planned))) },
                onDelete = { viewModel.deleteMovie(movie.id) }
            )
            return
        }

        Dialogs.showMovieDetailsDialog(
            context = requireContext(),
            movie = movie,
            currentStatus = currentStatus,
            onMovieToWatching = { updateMovieAndKeepCurrentTab(movie.copy(status = getString(R.string.movie_status_watching))) },
            onMovieToPlan = { updateMovieAndKeepCurrentTab(movie.copy(status = getString(R.string.movie_status_planned))) },
            onUpdateEpisode = { season, episode ->
                // Обновляем прогресс сериала без смены статуса.
                updateMovieAndKeepCurrentTab(movie.copy(season = season, episode = episode))
            },
            onMovieToWatched = {
                updateMovieAndKeepCurrentTab(movie.copy(status = getString(R.string.movie_status_watched)))
            },
            onDelete = { viewModel.deleteMovie(movie.id) }
        )
    }

    private fun updateMovieAndKeepCurrentTab(updatedMovie: MovieItem) {
        viewModel.updateMovie(updatedMovie)
        // Явно перезагружаем текущую вкладку, чтобы UI не перескакивал на другой статус.
        viewModel.loadMoviesByStatus(currentStatus)
    }

    private fun setupBottomNavigation() {
        binding.navigationView.setOnItemSelectedListener { item ->
            binding.navigationView.performAppHapticTap()
            when (item.itemId) {
                R.id.nav_watching -> updateStatus(getString(R.string.movie_status_watching), showFab = true)
                R.id.nav_to_watch -> updateStatus(getString(R.string.movie_status_planned), showFab = true)
                R.id.nav_watched -> updateStatus(getString(R.string.movie_status_watched), showFab = false)
            }
            true
        }
        binding.navigationView.selectedItemId = R.id.nav_watching
    }

    private fun setupFab() {
        binding.fabAddItem.applyTertiaryContainerTint()
        binding.fabAddItem.setOnClickListener {
            binding.fabAddItem.performAppHapticTap()
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, SearchFragment())
                .addToBackStack(null)
                .commit()
        }
    }

    private fun setupWatchedSearch() {
        binding.watchedSearchInput.addTextChangedListener {
            applyMoviesFilter()
        }
    }

    private fun updateStatus(status: String, showFab: Boolean) {
        currentStatus = status
        if (status != getString(R.string.movie_status_watched)) {
            binding.watchedSearchInput.text?.clear()
        }
        viewModel.loadMoviesByStatus(currentStatus)
        if (showFab) {
            binding.fabAddItem.show()
            binding.watchedSearchLayout.visibility = View.GONE
        } else {
            binding.fabAddItem.hide()
            binding.watchedSearchLayout.visibility = View.VISIBLE
        }
    }

    private fun applyMoviesFilter() {
        if (currentStatus != getString(R.string.movie_status_watched)) {
            adapter.updateItems(currentMovies)
            return
        }

        val query = binding.watchedSearchInput.text?.toString().orEmpty().trim()
        if (query.isBlank()) {
            adapter.updateItems(currentMovies)
            return
        }

        val filteredMovies = currentMovies.filter { movie ->
            movie.title.contains(query, ignoreCase = true)
        }
        adapter.updateItems(filteredMovies)
    }

    private fun observeViewModel() {
        viewModel.movies.observe(viewLifecycleOwner) { movies ->
            currentMovies = movies
            applyMoviesFilter()
        }
    }


    fun showMovieWithStatus(status: String) {
        // Переключаемся на нужный статус
        when (status) {
            getString(R.string.movie_status_watching) -> {
                binding.navigationView.selectedItemId = R.id.nav_watching
            }
            getString(R.string.movie_status_planned) -> {
                binding.navigationView.selectedItemId = R.id.nav_to_watch
            }
            getString(R.string.movie_status_watched) -> {
                binding.navigationView.selectedItemId = R.id.nav_watched
            }
        }
    }
}
