package ru.bl3xand.pancake.ui.fragment

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import androidx.activity.OnBackPressedCallback
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import ru.bl3xand.pancake.R
import ru.bl3xand.pancake.data.cache.ImageCacheManager
import ru.bl3xand.pancake.data.model.NoteItem
import ru.bl3xand.pancake.data.model.notes.NOTE_COLOR_ORDER_RES
import ru.bl3xand.pancake.data.model.notes.NOTE_COMPARATOR
import ru.bl3xand.pancake.data.model.notes.NoteColors
import ru.bl3xand.pancake.data.model.notes.NoteMetaMode
import ru.bl3xand.pancake.data.sync.GitHubDeleteQueueSyncEngine
import ru.bl3xand.pancake.databinding.FragmentNotesBinding
import ru.bl3xand.pancake.di.components.adapter.NotesAdapter
import ru.bl3xand.pancake.ui.viewmodel.NotesFragmentViewModel
import ru.bl3xand.pancake.ui.viewmodelfactory.NotesFragmentViewModelFactory
import ru.bl3xand.pancake.utils.noteeditor.NoteColorPaletteHelper
import ru.bl3xand.pancake.utils.noteeditor.NoteSortHelper
import ru.bl3xand.pancake.utils.ui.UnifiedItemDecoration
import ru.bl3xand.pancake.utils.ui.ViewAnimationUtils
import ru.bl3xand.pancake.utils.ui.applyTertiaryContainerTint
import ru.bl3xand.pancake.utils.ui.colorResToHex
import ru.bl3xand.pancake.utils.ui.performAppHapticTap

class NotesFragment : Fragment() {

    private var _binding: FragmentNotesBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: NotesFragmentViewModel
    private lateinit var adapter: NotesAdapter

    /** Актуальный список заметок (с учётом оптимистичных pin-изменений) */
    private var currentNotes: List<NoteItem> = emptyList()

    /** Выбранные ID в режиме выделения */
    private val selectedIds = linkedSetOf<String>()
    private var isSelectionMode = false
    private var isPaletteVisible = false

    private enum class SortMode { CUSTOM, UPDATED_AT, CREATED_AT, COLOR }

    private var sortMode = SortMode.CUSTOM

    private val expectedPinState = mutableMapOf<String, Boolean>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotesBinding.inflate(inflater, container, false)
        viewModel = ViewModelProvider(
            requireActivity(),
            NotesFragmentViewModelFactory(requireActivity().application)
        )[NotesFragmentViewModel::class.java]

        setupRecyclerView()
        setupFab()
        setupSearch()
        setupSelectionActions()
        observeViewModel()

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupBackPressedHandler()
        GitHubDeleteQueueSyncEngine.trigger(
            requireContext().applicationContext,
            forceRetryNow = true
        )
    }

    override fun onResume() {
        super.onResume()
        GitHubDeleteQueueSyncEngine.trigger(
            requireContext().applicationContext,
            forceRetryNow = true
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupBackPressedHandler() {
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    // Закрыть клавиатуру при нажатии back в режиме ввода поиска
                    val isFocused = binding.searchInput.isFocused
                    if (isFocused) {
                        binding.searchInput.clearFocus()
                        return
                    }

                    if (isSelectionMode) {
                        clearSelection()
                        return
                    }
                    isEnabled = false
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
            }
        )
    }

    // --- Инициализация RecyclerView ---

    private fun setupRecyclerView() {
        val imageCacheManager = ImageCacheManager(requireContext().applicationContext)
        adapter = NotesAdapter(
            onClick = ::onCardClick,
            onLongClick = ::onCardLongClick,
            lifecycleScope = viewLifecycleOwner.lifecycleScope,
            imageCacheManager = imageCacheManager
        )
        adapter.setMetaMode(NoteMetaMode.UPDATED)

        binding.recyclerView.apply {
            layoutManager =
                StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL).apply {
                    gapStrategy = StaggeredGridLayoutManager.GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS
                }
            adapter = this@NotesFragment.adapter
            itemAnimator = DefaultItemAnimator().apply {
                supportsChangeAnimations = false
            }
            // Единый механизм отступов для всех экранов (включая header-элементы)
            addItemDecoration(UnifiedItemDecoration(spanCount = 2) { position ->
                this@NotesFragment.adapter.isHeader(position)
            })
        }

        setupDragAndDrop()
    }

    private fun setupDragAndDrop() {
        val callback = NotesDragCallback(
            // Перетаскивание доступно только в режиме "Свой вариант"
            isSelectionActive = { isSelectionMode || sortMode != SortMode.CUSTOM },
            isHeader = adapter::isHeader,
            onMove = { from, to -> adapter.moveItem(from, to) },
            onDropped = { moved, startPos ->
                if (_binding == null) return@NotesDragCallback
                if (moved) {
                    viewModel.reorderNotes(adapter.currentIds())
                    realignStaggeredGrid()
                } else if (startPos != RecyclerView.NO_POSITION) {
                    adapter.getItem(startPos)?.let { startSelectionWith(it.id) }
                }
            }
        )
        ItemTouchHelper(callback).attachToRecyclerView(binding.recyclerView)
    }

    // --- FAB ---

    private fun setupFab() {
        binding.fabBtnCreateNote.applyTertiaryContainerTint()
        binding.fabBtnCreateNote.setOnClickListener {
            it.performAppHapticTap()
            openEditor()
        }
    }

    // --- Панель выбора (selection toolbar) ---

    private fun setupSelectionActions() {
        val palette = NoteColorPaletteHelper.palette(requireContext())
        val colorViews = listOf(
            binding.colorChoice1, binding.colorChoice2, binding.colorChoice3,
            binding.colorChoice4, binding.colorChoice5, binding.colorChoice6,
            binding.colorChoice7, binding.colorChoice8, binding.colorChoice9,
            binding.colorChoice10
        )

        colorViews.forEachIndexed { index, view ->
            view.setOnClickListener {
                it.performAppHapticTap()
                applySelectionColor(palette[index].hex)
            }
        }

        with(binding) {
            actionCloseSelection.setOnClickListener {
                it.performAppHapticTap()
                clearSelection()
            }
            actionDelete.setOnClickListener {
                it.performAppHapticTap()
                viewModel.deleteNotes(selectedIds.toSet())
                clearSelection()
            }
            actionDuplicate.setOnClickListener {
                it.performAppHapticTap()
                viewModel.duplicateNotes(selectedIds.toSet())
                clearSelection()
            }
            actionColor.setOnClickListener {
                it.performAppHapticTap()
                isPaletteVisible = !isPaletteVisible
                syncSelectionUi()
            }
            actionPin.setOnClickListener {
                it.performAppHapticTap()
                onPinClicked()
            }
        }
    }

    private fun onPinClicked() {
        val idsSnapshot = selectedIds.toSet()
        val shouldPin = currentNotes.filter { it.id in idsSnapshot }.any { !it.isPinned }

        // Оптимистичное обновление: сразу меняем UI, не ждём Firebase
        idsSnapshot.forEach { expectedPinState[it] = shouldPin }
        currentNotes = currentNotes
            .map { if (it.id in idsSnapshot) it.copy(isPinned = shouldPin) else it }
            .sortedWith(NOTE_COMPARATOR)

        clearSelection()
        applyFilter(binding.searchInput.text?.toString().orEmpty())
        viewModel.togglePinned(idsSnapshot, shouldPin)
    }

    // --- Поиск ---
    private fun setupSearch() {
        binding.searchInput.doOnTextChanged { text, _, _, _ ->
            applyFilter(text?.toString().orEmpty())
        }
        binding.btnSort.setOnClickListener { view ->
            view.performAppHapticTap()
            showSortOptionsDropdown()
        }
    }

    private fun showSortOptionsDropdown() {
        val menuView = layoutInflater.inflate(R.layout.dialog_note_sort_options, null)
        val popup = PopupWindow(
            menuView,
            binding.searchLayout.width,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )
        popup.elevation = 10f
        popup.isOutsideTouchable = true

        val optionCustom = menuView.findViewById<MaterialButton>(R.id.sortByCustom)
        val optionUpdated = menuView.findViewById<MaterialButton>(R.id.sortByUpdated)
        val optionCreated = menuView.findViewById<MaterialButton>(R.id.sortByCreated)
        val optionColor = menuView.findViewById<MaterialButton>(R.id.sortByColor)

        val selectedBg = MaterialColors.getColor(
            menuView,
            com.google.android.material.R.attr.colorPrimaryContainer
        )
        val selectedFg = MaterialColors.getColor(
            menuView,
            com.google.android.material.R.attr.colorOnPrimaryContainer
        )
        val normalBg = MaterialColors.getColor(
            menuView,
            com.google.android.material.R.attr.colorSurfaceContainer
        )
        val normalFg =
            MaterialColors.getColor(menuView, com.google.android.material.R.attr.colorOnSurface)
        val normalStroke = MaterialColors.getColor(
            menuView,
            com.google.android.material.R.attr.colorOutlineVariant
        )

        fun styleOption(button: MaterialButton, selected: Boolean) {
            // Убираем иконку/сдвиг, оставляем только цветовую подсветку.
            button.icon = null
            button.backgroundTintList =
                ColorStateList.valueOf(if (selected) selectedBg else normalBg)
            button.setTextColor(if (selected) selectedFg else normalFg)
            button.strokeWidth = if (selected) 0 else 1
            button.strokeColor = ColorStateList.valueOf(normalStroke)
        }

        val allOptions = listOf(optionCustom, optionUpdated, optionCreated, optionColor)
        allOptions.forEach { styleOption(it, selected = false) }

        when (sortMode) {
            SortMode.CUSTOM -> styleOption(optionCustom, selected = true)
            SortMode.UPDATED_AT -> styleOption(optionUpdated, selected = true)
            SortMode.CREATED_AT -> styleOption(optionCreated, selected = true)
            SortMode.COLOR -> styleOption(optionColor, selected = true)
        }

        fun applySort(mode: SortMode, sourceView: View) {
            sourceView.performAppHapticTap()
            sortMode = mode
            adapter.setMetaMode(
                when (mode) {
                    SortMode.CUSTOM -> NoteMetaMode.UPDATED
                    SortMode.CREATED_AT -> NoteMetaMode.CREATED
                    SortMode.UPDATED_AT, SortMode.COLOR -> NoteMetaMode.UPDATED
                }
            )
            applyFilter(binding.searchInput.text?.toString().orEmpty())
            popup.dismiss()
        }

        optionCustom.setOnClickListener { applySort(SortMode.CUSTOM, it) }
        optionUpdated.setOnClickListener { applySort(SortMode.UPDATED_AT, it) }
        optionCreated.setOnClickListener { applySort(SortMode.CREATED_AT, it) }
        optionColor.setOnClickListener { applySort(SortMode.COLOR, it) }

        popup.showAsDropDown(binding.searchLayout, 0, 8)
    }

    // --- Наблюдение за данными ---

    private fun observeViewModel() {
        viewModel.notes.observe(viewLifecycleOwner) { notes ->
            currentNotes = mergePendingPinState(notes)
            applyFilter(binding.searchInput.text?.toString().orEmpty())
        }
    }

    /**
     * Накладывает ожидаемые pin-изменения поверх данных из Firebase.
     * Когда Firebase подтверждает изменение — удаляет запись из pending-карты.
     * При этом сохраняет sortOrder из currentNotes, чтобы не потерять порядок после перемещения.
     */
    private fun mergePendingPinState(notes: List<NoteItem>): List<NoteItem> {
        if (expectedPinState.isEmpty()) return notes

        // Создаём map старых sortOrder для сохранения порядка
        val sortOrderMap = currentNotes.associateBy({ it.id }, { it.sortOrder })

        return notes
            .map { note ->
                val expected = expectedPinState[note.id] ?: return@map note
                if (note.isPinned == expected) expectedPinState.remove(note.id)
                // Восстанавливаем sortOrder из currentNotes, если он был там
                val preservedSortOrder = sortOrderMap[note.id] ?: note.sortOrder
                note.copy(isPinned = expected, sortOrder = preservedSortOrder)
            }
            .sortedWith(NOTE_COMPARATOR)
    }

    // --- Фильтрация ---

    private fun applyFilter(queryText: String) {
        val query = queryText.trim()
        val filtered = if (query.isBlank()) {
            currentNotes
        } else {
            currentNotes.filter { note ->
                note.title.contains(query, ignoreCase = true) ||
                        note.noteText.contains(query, ignoreCase = true) ||
                        note.webLink.contains(query, ignoreCase = true)
            }
        }

        val sortHelperMode = when (sortMode) {
            SortMode.CUSTOM -> NoteSortHelper.Mode.CUSTOM
            SortMode.UPDATED_AT -> NoteSortHelper.Mode.UPDATED_AT
            SortMode.CREATED_AT -> NoteSortHelper.Mode.CREATED_AT
            SortMode.COLOR -> NoteSortHelper.Mode.COLOR
        }

        val colorOrderHex = if (sortMode == SortMode.COLOR) {
            NOTE_COLOR_ORDER_RES.mapIndexed { index, colorRes ->
                if (index == 0) NoteColors.DEFAULT_MARKER
                else requireContext().colorResToHex(colorRes)
            }
        } else {
            emptyList()
        }

        val sorted = NoteSortHelper.sort(filtered, sortHelperMode, colorOrderHex)
        adapter.updateItems(sorted)
        syncSelectionUi()
    }

    private fun realignStaggeredGrid() {
        val recyclerView = _binding?.recyclerView ?: return
        recyclerView.post {
            val layoutManager =
                recyclerView.layoutManager as? StaggeredGridLayoutManager ?: return@post
            layoutManager.invalidateSpanAssignments()
            recyclerView.requestLayout()
        }
    }

    // --- Клики по карточкам ---

    private fun onCardClick(note: NoteItem) {
        if (isSelectionMode) toggleSelection(note.id) else openEditor(note.id)
    }

    private fun onCardLongClick(note: NoteItem) {
        if (isSelectionMode) {
            toggleSelection(note.id)
        } else {
            startSelectionWith(note.id)
        }
    }

    // --- Управление выделением ---

    private fun startSelectionWith(noteId: String) {
        isSelectionMode = true
        selectedIds.add(noteId)
        syncSelectionUi()
    }

    private fun toggleSelection(noteId: String) {
        if (!selectedIds.add(noteId)) selectedIds.remove(noteId)
        if (selectedIds.isEmpty()) clearSelection() else syncSelectionUi()
    }

    private fun clearSelection() {
        selectedIds.clear()
        isSelectionMode = false
        isPaletteVisible = false
        syncSelectionUi()
    }

    private fun syncSelectionUi() {
        with(binding) {
            animateSelectionBar(isSelectionMode)
            topColorPalette.visibility =
                if (isSelectionMode && isPaletteVisible) View.VISIBLE else View.GONE
            selectionCount.text = getString(R.string.selected_count_format, selectedIds.size)

            val allPinned = areAllSelectedPinned()
            actionPin.setImageResource(if (allPinned) R.drawable.ic_push_pin_filled_24 else R.drawable.ic_push_pin_24)
            actionPin.contentDescription =
                getString(if (allPinned) R.string.unpin_note else R.string.pin_note)

            val hasSelection = isSelectionMode && selectedIds.isNotEmpty()
            actionPin.isEnabled = hasSelection
            actionColor.isEnabled = hasSelection
            actionPin.alpha = if (hasSelection) 1f else 0.5f
            actionColor.alpha = if (hasSelection) 1f else 0.5f
        }
        adapter.setSelection(selectedIds, isSelectionMode)
    }

    private var isSelectionBarVisible = false

    private fun animateSelectionBar(show: Boolean) {
        if (show == isSelectionBarVisible) return
        isSelectionBarVisible = show

        if (show) {
            ViewAnimationUtils.crossfadeReplace(
                show = binding.selectionActions,
                hide = binding.searchLayout
            )
        } else {
            ViewAnimationUtils.crossfadeRestore(
                show = binding.searchLayout,
                hide = binding.selectionActions
            )
        }
    }

    private fun areAllSelectedPinned(): Boolean {
        if (selectedIds.isEmpty()) return false
        return currentNotes.filter { it.id in selectedIds }.all { it.isPinned }
    }

    private fun applySelectionColor(color: String) {
        viewModel.setColor(selectedIds.toSet(), color)
        clearSelection()
    }

    // --- Навигация ---

    private fun openEditor(noteId: String? = null) {
        parentFragmentManager.beginTransaction()
            .setTransition(androidx.fragment.app.FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
            .replace(R.id.fragment_container, NoteEditorFragment.newInstance(noteId))
            .addToBackStack(NoteEditorFragment::class.java.simpleName)
            .commit()
    }
}

