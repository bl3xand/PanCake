package ru.bl3xand.pancake.ui.fragment

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.ImageButton
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toDrawable
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.image.glide.GlideImagesPlugin
import io.noties.markwon.linkify.LinkifyPlugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import ru.bl3xand.pancake.R
import ru.bl3xand.pancake.config.AppConfig
import ru.bl3xand.pancake.data.cache.ImageCacheManager
import ru.bl3xand.pancake.data.model.NoteItem
import ru.bl3xand.pancake.data.model.notes.NoteColors
import ru.bl3xand.pancake.data.repository.GitHubImageSyncRepository
import ru.bl3xand.pancake.data.repository.SyncQueueRepository
import ru.bl3xand.pancake.databinding.FragmentNoteEditorBinding
import ru.bl3xand.pancake.ui.activity.MainActivity
import ru.bl3xand.pancake.ui.dialogs.Dialogs
import ru.bl3xand.pancake.ui.viewmodel.NotesFragmentViewModel
import ru.bl3xand.pancake.ui.viewmodelfactory.NotesFragmentViewModelFactory
import ru.bl3xand.pancake.utils.image.ImageUrlHelper
import ru.bl3xand.pancake.utils.media.MediaCopyUtil
import ru.bl3xand.pancake.utils.noteeditor.NoteColorResolver
import ru.bl3xand.pancake.utils.noteeditor.NoteEditorAutoFormatHelper
import ru.bl3xand.pancake.utils.noteeditor.NoteEditorDateHelper
import ru.bl3xand.pancake.utils.noteeditor.NoteEditorImageUploadHelper
import ru.bl3xand.pancake.utils.noteeditor.NoteEditorMarkdownHelper
import ru.bl3xand.pancake.utils.noteeditor.NoteEditorMediaHelper
import ru.bl3xand.pancake.utils.noteeditor.NoteQuoteStyleHelper
import ru.bl3xand.pancake.utils.preferences.getAppPreferences
import ru.bl3xand.pancake.utils.ui.performAppHapticTap
import ru.bl3xand.pancake.utils.user.UserNameNormalizer
import java.io.File
import java.util.Locale

class NoteEditorFragment : Fragment() {

    private var _binding: FragmentNoteEditorBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: NotesFragmentViewModel
    private val githubSync by lazy { GitHubImageSyncRepository(requireContext().applicationContext) }
    private val syncQueue by lazy { SyncQueueRepository(requireContext().applicationContext) }
    private val prefs by lazy { getAppPreferences(requireContext().applicationContext) }
    private val imageCacheManager by lazy { ImageCacheManager(requireContext().applicationContext) }
    private val imageUploadHelper by lazy {
        NoteEditorImageUploadHelper(requireContext().applicationContext, githubSync, syncQueue)
    }
    private val mediaHelper by lazy {
        NoteEditorMediaHelper(requireContext().applicationContext, imageCacheManager)
    }

    private var noteId: String? = null
    private var selectedColor: String = ""
    private val selectedImagePaths = mutableListOf<String>()
    private var webLink: String = ""
    private var currentDateTime: String = ""
    private var editingNote: NoteItem? = null

    // Состояния загрузки.
    private var isPreparingLocalImage = false
    private var isUploadingAttachments = false
    private var saveJob: Job? = null
    private var backPressedCallback: OnBackPressedCallback? = null
    private var uploadInProgressDialog: AlertDialog? = null
    private var uploadErrorDialog: AlertDialog? = null
    private var isMarkdownPreviewMode = false
    private var isInternalMarkdownEdit = false
    private var shouldHandleListContinuation = false
    private var pendingCameraUri: Uri? = null
    private var pendingPreviewRender = false
    private val dateHelper by lazy { NoteEditorDateHelper(Locale.getDefault()) }

    private fun noteColor1Hex(): String = NoteColors.DEFAULT_MARKER
    private val markwon by lazy {
        Markwon.builder(requireContext())
            .usePlugin(GlideImagesPlugin.create(Glide.with(this)))
            .usePlugin(LinkifyPlugin.create())
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureConfiguration(builder: io.noties.markwon.MarkwonConfiguration.Builder) {
                    builder.linkResolver { _, link ->
                        if (isImageLink(link)) {
                            _binding?.root?.performAppHapticTap()
                            openImageFullscreen(link)
                        } else {
                            val intent = Intent(Intent.ACTION_VIEW, link.toUri())
                            runCatching { startActivity(intent) }
                        }
                    }
                }
            })
            .build()
    }

    private val openImagePicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                importMediaFromUri(uri)
            }
        }

    private val takePicture =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            val uri = pendingCameraUri
            if (success && uri != null) {
                importMediaFromUri(uri)
            }
            pendingCameraUri = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        noteId = arguments?.getString(ARG_NOTE_ID)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNoteEditorBinding.inflate(inflater, container, false)
        viewModel = ViewModelProvider(
            requireActivity(),
            NotesFragmentViewModelFactory(requireActivity().application)
        )[NotesFragmentViewModel::class.java]
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Применяем insets ДО первого layout
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            binding.bottomActionsContent.updatePadding(bottom = navBars.bottom)
            insets
        }
        // Принудительно запрашиваем insets сразу
        binding.root.requestApplyInsets()

        // Скрываем bottomNav сразу, до анимации
        (activity as? MainActivity)?.setBottomNavigationVisible(false)

        currentDateTime = dateHelper.nowFormatted()
        if (selectedColor.isBlank()) {
            selectedColor = noteColor1Hex()
        }
        updateEditorMeta()
        updateColorView()

        setupMarkdownEditor()
        setupPasteInterceptor()
        setupListeners()
        setupQuoteCopyOnPreview()
        registerBottomSheetResult()
        bindExistingNote()
        setupBackPressedHandler()
        isMarkdownPreviewMode = true
        updateMarkdownModeUi()

        if (noteId == null && editingNote == null) {
            binding.root.post {
                focusEditorAndShowKeyboard()
            }
        }
    }

    override fun onDestroyView() {
        (activity as? MainActivity)?.setBottomNavigationVisible(true)
        uploadInProgressDialog?.dismiss()
        uploadInProgressDialog = null
        uploadErrorDialog?.dismiss()
        uploadErrorDialog = null
        super.onDestroyView()
        _binding = null
    }

    override fun onResume() {
        super.onResume()
        (activity as? MainActivity)?.setBottomNavigationVisible(false)
    }

    private fun setupListeners() {
        binding.btnMediaAction.setOnClickListener {
            it.performAppHapticTap()
            showMediaSourceDialog()
        }

        binding.btnMarkdownMode.setOnClickListener {
            it.performAppHapticTap()
            toggleMarkdownMode()
        }

        binding.btnMoreActions.setOnClickListener {
            it.performAppHapticTap()
            NoteOptionsBottomSheetFragment
                .newInstance(
                    isEditMode = noteId != null || editingNote != null,
                    selectedColor = selectedColor
                )
                .show(parentFragmentManager, "NoteOptionsBottomSheet")
        }

        binding.btnOk.setOnClickListener {
            if (binding.etWebLink.text?.toString().orEmpty().isBlank()) {
                snackbarShow(R.string.error_web_link_required)
            } else {
                checkWebUrl()
            }
        }

        binding.btnCancel.setOnClickListener {
            binding.layoutWebUrl.isVisible = false
            if (webLink.isNotBlank()) {
                binding.tvWebLink.isVisible = true
            }
        }

        binding.imgUrlDelete.setOnClickListener {
            webLink = ""
            binding.tvWebLink.isVisible = false
            binding.imgUrlDelete.isVisible = false
            binding.layoutWebUrl.isVisible = false
        }

        binding.tvWebLink.setOnClickListener {
            val parsed = webLink.toUri()
            val intent = Intent(Intent.ACTION_VIEW, parsed)
            runCatching { startActivity(intent) }
        }
    }

    private fun setupPasteInterceptor() {
        binding.etNoteDesc.onPasteListener = {
            val clipboard =
                requireContext().getSystemService(android.content.ClipboardManager::class.java)
            val clip = clipboard?.primaryClip
            val hasImage = clip != null &&
                    clip.description?.let { desc ->
                        (0 until desc.mimeTypeCount).any {
                            desc.getMimeType(it).startsWith("image/")
                        }
                    } == true

            if (hasImage && clip.itemCount > 0) {
                val uri = clip.getItemAt(0)?.uri
                if (uri != null) {
                    val tempFile = copyClipboardImageToTemp(uri)
                    if (tempFile != null) {
                        importMediaFromUri(Uri.fromFile(tempFile))
                        true
                    } else {
                        // Не удалось прочитать — вставляем как обычный текст
                        false
                    }
                } else {
                    false
                }
            } else {
                false
            }
        }
    }

    private fun copyClipboardImageToTemp(uri: Uri): File? {
        return try {
            val bytes =
                requireContext().contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes != null && bytes.isNotEmpty()) {
                val tempDir = File(requireContext().cacheDir, "clipboard_temp").also { it.mkdirs() }
                val tempFile = File(tempDir, "paste_${System.currentTimeMillis()}.jpg")
                tempFile.outputStream().use { it.write(bytes) }
                tempFile
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Clipboard image not accessible: ${e.message}")
            null
        }
    }

    private fun registerBottomSheetResult() {
        parentFragmentManager.setFragmentResultListener(
            NoteOptionsBottomSheetFragment.REQUEST_KEY,
            viewLifecycleOwner
        ) { _, bundle ->
            when (bundle.getString(NoteOptionsBottomSheetFragment.KEY_ACTION)) {
                NoteOptionsBottomSheetFragment.ACTION_COLOR -> {
                    selectedColor =
                        bundle.getString(NoteOptionsBottomSheetFragment.KEY_SELECTED_COLOR)
                            .orEmpty()
                            .ifBlank { noteColor1Hex() }
                    updateColorView()
                }

                NoteOptionsBottomSheetFragment.ACTION_DELETE_NOTE -> {
                    noteId?.let {
                        viewModel.deleteNote(it)
                        parentFragmentManager.popBackStack()
                    }
                }
            }
        }
    }

    private fun bindExistingNote() {
        val id = noteId ?: return
        viewModel.notes.observe(viewLifecycleOwner) { notes ->
            val note = notes.firstOrNull { it.id == id } ?: return@observe
            editingNote = note
            selectedColor =
                note.color.ifBlank { noteColor1Hex() }
            val noteImages = note.imagePaths.filter { it.isNotBlank() }
                .ifEmpty { listOf(note.imgPath).filter { it.isNotBlank() } }
            webLink = note.webLink
            currentDateTime = dateHelper.normalize(note.dateTime).ifBlank { currentDateTime }
            val editorText = NoteEditorMarkdownHelper.ensureMarkdownImages(
                text = note.noteText,
                imagePaths = noteImages,
                imageAltText = getString(R.string.note_markdown_image_alt)
            )

            binding.etNoteTitle.setText(note.title)
            binding.etNoteDesc.setText(editorText)
            updateEditorMeta(note)
            binding.etWebLink.setText(webLink)
            binding.tvWebLink.text = webLink
            binding.tvWebLink.isVisible = webLink.isNotBlank()
            binding.imgUrlDelete.isVisible = webLink.isNotBlank()
            updateColorView()
            syncSelectedImagePathsFromText(editorText)
            renderMarkdownPreview()
        }
    }

    private fun attemptSaveAndExit() {
        val draft = collectDraftContent()

        if (draft.isCompletelyEmpty()) {
            handleEmptyNoteExit()
            return
        }

        if (noteId != null && !hasNoteChanges(draft)) {
            backPressedCallback?.isEnabled = false
            if (isAdded) parentFragmentManager.popBackStack()
            return
        }

        if (saveJob?.isActive == true) return

        saveJob = lifecycleScope.launch {
            var saveCompletedSuccessfully = false
            isUploadingAttachments = true
            updateLoadingState()
            imageUploadHelper.currentSessionUploadedRepoUrls.clear()

            try {
                withTimeout(SAVE_TIMEOUT_MS) {
                    val targetNoteId = noteId ?: viewModel.createNoteId()
                    if (targetNoteId.isNullOrBlank()) {
                        snackbarShow(R.string.error_unknown)
                        return@withTimeout
                    }

                    val uploadResult =
                        imageUploadHelper.uploadImagesToGitHub(targetNoteId, draft.desc)
                    if (uploadResult == null) {
                        showUploadErrorDialog()
                        return@withTimeout
                    }

                    persistNoteWithPaths(
                        targetNoteId = targetNoteId,
                        title = draft.title,
                        desc = uploadResult.markdownText,
                        imagePaths = uploadResult.imagePaths
                    )

                    saveCompletedSuccessfully = true
                    parentFragmentManager.popBackStack()
                }
            } catch (_: TimeoutCancellationException) {
                showUploadErrorDialog()
            } catch (_: kotlinx.coroutines.CancellationException) {
                imageUploadHelper.cleanupCurrentSessionUploads()
            } finally {
                isUploadingAttachments = false
                updateLoadingState()
                saveJob = null
                dismissUploadInProgressDialog()
                if (saveCompletedSuccessfully) {
                    dismissUploadErrorDialog()
                }
            }
        }
    }

    private fun persistNoteWithPaths(
        targetNoteId: String,
        title: String,
        desc: String,
        imagePaths: List<String>
    ) {
        if (noteId == null) {
            viewModel.addNote(
                noteId = targetNoteId,
                title = title,
                noteText = desc,
                color = selectedColor,
                imagePaths = imagePaths,
                webLink = webLink,
                dateTime = currentDateTime
            )
        } else {
            val existing = editingNote ?: return
            viewModel.updateNote(
                existing.copy(
                    title = title,
                    noteText = desc,
                    color = selectedColor,
                    imgPath = imagePaths.firstOrNull().orEmpty(),
                    imagePaths = imagePaths,
                    webLink = webLink,
                    dateTime = currentDateTime
                )
            )
        }
    }

    private fun exitAndSaveWithoutLocalMedia() {
        val draft = collectDraftContent()
        if (draft.isCompletelyEmpty()) {
            handleEmptyNoteExit()
            return
        }

        val targetNoteId = noteId ?: viewModel.createNoteId()
        if (targetNoteId.isNullOrBlank()) {
            snackbarShow(R.string.error_unknown)
            return
        }

        lifecycleScope.launch {
            saveJob?.cancel()
            imageUploadHelper.cleanupCurrentSessionUploads()

            val remoteOnly = selectedImagePaths.filter { path ->
                NoteEditorMarkdownHelper.isRemotePath(path)
            }
            val markdownWithoutLocalImages =
                NoteEditorMarkdownHelper.removeLocalMarkdownImages(draft.desc)
            persistNoteWithPaths(
                targetNoteId = targetNoteId,
                title = draft.title,
                desc = markdownWithoutLocalImages,
                imagePaths = remoteOnly
            )
            isUploadingAttachments = false
            updateLoadingState()
            dismissActiveDialogs()
            backPressedCallback?.isEnabled = false
            if (isAdded) parentFragmentManager.popBackStack()
        }
    }


    private fun checkWebUrl() {
        val value = binding.etWebLink.text?.toString().orEmpty().trim()
        if (Patterns.WEB_URL.matcher(value).matches()) {
            webLink = value
            binding.layoutWebUrl.isVisible = false
            binding.tvWebLink.isVisible = true
            binding.tvWebLink.text = value
            binding.imgUrlDelete.isVisible = true
        } else {
            snackbarShow(R.string.error_web_link_invalid)
        }
    }

    private fun openImageFullscreen(path: String) {
        val dialog = Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val dialogView = layoutInflater.inflate(R.layout.dialog_note_image_fullscreen, null)
        val imageView =
            dialogView.findViewById<com.github.chrisbanes.photoview.PhotoView>(R.id.fullscreenImage)
        val actionsButton = dialogView.findViewById<ImageButton>(R.id.buttonImageActions)

        val originalPath = findOriginalImagePath(path)

        lifecycleScope.launch {
            val displayPath = mediaHelper.resolveDisplayPath(originalPath)
            Glide.with(this@NoteEditorFragment).load(displayPath).into(imageView)
        }

        actionsButton.setOnClickListener { button ->
            button.performAppHapticTap()
            showImageActionsMenu(button, originalPath)
        }

        dialogView.setOnClickListener { dialog.dismiss() }
        imageView.setOnClickListener { /* не закрываем */ }

        dialog.window?.apply {
            setBackgroundDrawable(Color.BLACK.toDrawable())
            addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        }
        dialog.setContentView(dialogView)
        dialog.show()
    }

    /**
     * По кэш-пути или любому пути находит оригинальный путь из selectedImagePaths.
     * Если не найден — возвращает исходный путь.
     */
    private fun findOriginalImagePath(displayPath: String): String {
        if (selectedImagePaths.contains(displayPath)) return displayPath
        if (displayPath.startsWith("https://raw.githubusercontent.com/")) return displayPath

        // Ищем по имени файла в кэш-пути
        val fileName = displayPath.substringAfterLast("/").substringBefore(".")
        return selectedImagePaths.firstOrNull { it.contains(fileName) } ?: displayPath
    }

    private fun showImageActionsMenu(anchor: View, path: String) {
        Log.d(TAG, "showImageActionsMenu called with path: $path")

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.note_image_actions)
            .setItems(
                arrayOf(
                    getString(R.string.note_share_image_action),
                    getString(R.string.note_save_image_action)
                )
            ) { _, which ->
                performDialogHaptic()
                when (which) {
                    0 -> {
                        Log.d(TAG, "Share image clicked")
                        shareImage(path)
                    }

                    1 -> {
                        Log.d(TAG, "Save image clicked")
                        saveImageToGallery(path)
                    }
                }
            }
            .show()
    }

    private fun saveImageToGallery(path: String) {
        lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) {
                mediaHelper.saveImageToGallery(path)
            }

            if (!success) {
                snackbarShow(R.string.error_unknown)
            }
        }
    }

    private fun shareImage(path: String) {
        lifecycleScope.launch {
            val intent = withContext(Dispatchers.IO) {
                mediaHelper.buildShareIntent(path, getString(R.string.note_share_image_action))
            }
            if (intent != null) {
                startActivity(intent)
            } else {
                snackbarShow(R.string.error_unknown)
            }
        }
    }

    private fun updateColorView() {
        val resolvedColor = NoteColorResolver.resolve(requireContext(), selectedColor)
        binding.colorView.setBackgroundColor(resolvedColor)
    }

    private fun snackbarShow(messageRes: Int) {
        Snackbar.make(binding.root, messageRes, Snackbar.LENGTH_SHORT).show()
    }

    private fun setupBackPressedHandler() {
        backPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isUploadingAttachments) {
                    showUploadInProgressDialog()
                } else {
                    attemptSaveAndExit()
                }
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            backPressedCallback!!
        )
    }

    private fun showUploadInProgressDialog() {
        dismissActiveDialogs()
        uploadInProgressDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.loading_in_progress)
            .setMessage(R.string.upload_in_progress_message)
            .setNegativeButton(R.string.continue_action) { _, _ ->
                performDialogHaptic()
            }
            .setPositiveButton(R.string.exit) { _, _ ->
                performDialogHaptic()
                exitAndSaveWithoutLocalMedia()
            }
            .show()
    }

    private fun showUploadErrorDialog() {
        dismissUploadInProgressDialog()
        uploadErrorDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.upload_failed_title)
            .setMessage(R.string.upload_failed_message)
            .setNegativeButton(R.string.retry_action) { _, _ ->
                performDialogHaptic()
                lifecycleScope.launch {
                    imageUploadHelper.cleanupCurrentSessionUploads()
                    attemptSaveAndExit()
                }
            }
            .setPositiveButton(R.string.exit_without_media_action) { _, _ ->
                performDialogHaptic()
                exitAndSaveWithoutLocalMedia()
            }
            .show()
    }


    private fun updateLoadingState() {
        val blockByUpload = isUploadingAttachments
        val b = _binding ?: return
        b.btnMediaAction.isEnabled = !isPreparingLocalImage && !blockByUpload
        b.btnMarkdownMode.isEnabled = !isPreparingLocalImage && !blockByUpload
        b.btnMoreActions.isEnabled = !isPreparingLocalImage && !blockByUpload
        b.fullScreenLoadingOverlay.isVisible = blockByUpload
    }

    private fun setupMarkdownEditor() {
        binding.tvMarkdownPreview.isVisible = false

        binding.etNoteDesc.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) =
                Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                shouldHandleListContinuation = count > before &&
                        s != null &&
                        start + count <= s.length &&
                        s.subSequence(start, start + count).contains('\n')
            }

            override fun afterTextChanged(s: Editable?) {
                if (s == null || isInternalMarkdownEdit) return
                if (shouldHandleListContinuation) {
                    handleMarkdownAutoFormatting(s)
                }
                shouldHandleListContinuation = false
                syncSelectedImagePathsFromText(s.toString())
                if (isMarkdownPreviewMode) {
                    renderMarkdownPreview()
                }
            }
        })
    }

    private fun toggleMarkdownMode() {
        isMarkdownPreviewMode = !isMarkdownPreviewMode
        updateMarkdownModeUi()
    }

    private fun updateMarkdownModeUi() {
        val isPreview = isMarkdownPreviewMode
        binding.etNoteDesc.isVisible = !isPreview
        binding.tvMarkdownPreview.isVisible = isPreview
        binding.etNoteTitle.isEnabled = !isPreview
        binding.etNoteTitle.isFocusable = !isPreview
        binding.etNoteTitle.isFocusableInTouchMode = !isPreview
        binding.etNoteTitle.isCursorVisible = !isPreview

        binding.btnMediaAction.visibility = if (isPreview) View.INVISIBLE else View.VISIBLE

        binding.btnMarkdownMode.setImageResource(
            if (isPreview) R.drawable.edit_icon else R.drawable.note_ic_preview
        )
        binding.btnMarkdownMode.contentDescription = getString(
            if (isPreview) R.string.note_markdown_edit_action else R.string.note_markdown_preview_action
        )
        if (isPreview) {
            binding.etNoteDesc.clearFocus()
            renderMarkdownPreview()
        }
    }

    private fun renderMarkdownPreview() {
        val previewText = binding.etNoteDesc.text?.toString().orEmpty()
        val contentWidth = binding.tvMarkdownPreview.width -
                binding.tvMarkdownPreview.paddingLeft -
                binding.tvMarkdownPreview.paddingRight
        if (contentWidth <= 0) {
            if (!pendingPreviewRender) {
                pendingPreviewRender = true
                binding.tvMarkdownPreview.post {
                    pendingPreviewRender = false
                    if (isMarkdownPreviewMode && _binding != null) {
                        renderMarkdownPreview()
                    }
                }
            }
            return
        }

        lifecycleScope.launch {
            var prepared = NoteEditorMarkdownHelper.preparePreviewMarkdown(previewText)

            NoteEditorMarkdownHelper.extractMarkdownImagePaths(previewText).forEach { path ->
                if (ImageUrlHelper.isGitHubImage(path)) {
                    val cachedPath = mediaHelper.resolveDisplayPath(path)
                    if (cachedPath != path) {
                        prepared = prepared.replace("($path)", "($cachedPath)")
                    }
                }
            }

            markwon.setMarkdown(binding.tvMarkdownPreview, prepared)
            NoteQuoteStyleHelper.apply(binding.tvMarkdownPreview, previewText)
        }
    }

    private fun showMediaSourceDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_note_add_actions, null)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .create()

        dialogView.findViewById<MaterialButton>(R.id.buttonTakePhoto).setOnClickListener {
            performDialogHaptic()
            dialog.dismiss()
            launchCameraCapture()
        }
        dialogView.findViewById<MaterialButton>(R.id.buttonPickImage).setOnClickListener {
            performDialogHaptic()
            dialog.dismiss()
            openImagePicker.launch(arrayOf("image/*"))
        }
        dialogView.findViewById<MaterialButton>(R.id.buttonAddLink).setOnClickListener {
            performDialogHaptic()
            dialog.dismiss()
            showAddLinkDialog()
        }
        dialogView.findViewById<MaterialButton>(R.id.buttonAddHeading).setOnClickListener {
            performDialogHaptic()
            dialog.dismiss()
            applyMarkdownAction(MarkdownAction.HEADING)
            focusEditorAndShowKeyboard()
        }
        dialogView.findViewById<MaterialButton>(R.id.buttonAddSubheading).setOnClickListener {
            performDialogHaptic()
            dialog.dismiss()
            applyMarkdownAction(MarkdownAction.SUBHEADING)
            focusEditorAndShowKeyboard()
        }
        dialogView.findViewById<MaterialButton>(R.id.buttonAddOrderedList).setOnClickListener {
            performDialogHaptic()
            dialog.dismiss()
            applyMarkdownAction(MarkdownAction.ORDERED_LIST)
            focusEditorAndShowKeyboard()
        }
        dialogView.findViewById<MaterialButton>(R.id.buttonAddBulletList).setOnClickListener {
            performDialogHaptic()
            dialog.dismiss()
            applyMarkdownAction(MarkdownAction.BULLET_LIST)
            focusEditorAndShowKeyboard()
        }
        dialogView.findViewById<MaterialButton>(R.id.buttonAddInlineQuote).setOnClickListener {
            performDialogHaptic()
            dialog.dismiss()
            applyMarkdownAction(MarkdownAction.INLINE_QUOTE)
            focusEditorAndShowKeyboard()
        }
        dialogView.findViewById<MaterialButton>(R.id.buttonAddBlockQuote).setOnClickListener {
            performDialogHaptic()
            dialog.dismiss()
            applyMarkdownAction(MarkdownAction.BLOCK_QUOTE)
            focusEditorAndShowKeyboard()
        }
        dialogView.findViewById<MaterialButton>(R.id.buttonAddCode).setOnClickListener {
            performDialogHaptic()
            dialog.dismiss()
            applyMarkdownAction(MarkdownAction.CODE_BLOCK)
            focusEditorAndShowKeyboard()
        }

        dialog.show()
    }

    private fun showAddLinkDialog() {
        Dialogs.showAddMarkdownLinkDialog(
            context = requireContext(),
            onLinkReady = { markdownLink ->
                insertMarkdownAtCursor(markdownLink)
            }
        )
    }

    private fun launchCameraCapture() {
        val uri = createCameraImageUri() ?: return
        pendingCameraUri = uri
        takePicture.launch(uri)
    }

    private fun createCameraImageUri(): Uri? {
        return runCatching {
            val dir = File(requireContext().filesDir, NOTE_IMAGES_DIR).also { it.mkdirs() }
            val imageFile = File(dir, "camera_${System.currentTimeMillis()}.jpg")
            FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                imageFile
            )
        }.getOrElse {
            snackbarShow(R.string.error_unknown)
            null
        }
    }

    private fun importMediaFromUri(uri: Uri) {
        isPreparingLocalImage = true
        updateLoadingState()

        lifecycleScope.launch(Dispatchers.Default) {
            val localPath = MediaCopyUtil.copyToAppStorage(requireContext(), uri)
            lifecycleScope.launch(Dispatchers.Main) {
                if (_binding == null) return@launch
                if (localPath.isNotBlank()) {
                    insertImageMarkdownAtCursor(localPath)
                }
                isPreparingLocalImage = false
                updateLoadingState()
            }
        }
    }

    private fun insertImageMarkdownAtCursor(imagePath: String) {
        val editText = binding.etNoteDesc
        val cursor = editText.selectionStart.coerceAtLeast(0)
        val prefix = if (cursor > 0 && editText.text?.get(cursor - 1) != '\n') "\n" else ""
        val suffix =
            if (cursor < editText.length() && editText.text?.get(cursor) != '\n') "\n" else ""
        val markdownImage =
            "$prefix![${getString(R.string.note_markdown_image_alt)}]($imagePath)$suffix"

        isInternalMarkdownEdit = true
        editText.text?.insert(cursor, markdownImage)
        isInternalMarkdownEdit = false

        syncSelectedImagePathsFromText(editText.text?.toString().orEmpty())
        if (isMarkdownPreviewMode) {
            renderMarkdownPreview()
        }
    }

    private fun insertMarkdownAtCursor(snippet: String) {
        val editText = binding.etNoteDesc
        val cursor = editText.selectionStart.coerceAtLeast(0)
        val prefix = if (cursor > 0 && editText.text?.get(cursor - 1) != '\n') "\n" else ""
        val suffix =
            if (cursor < editText.length() && editText.text?.get(cursor) != '\n') "\n" else ""
        val payload = "$prefix$snippet$suffix"

        isInternalMarkdownEdit = true
        editText.text?.insert(cursor, payload)
        isInternalMarkdownEdit = false

        val newCursor = (cursor + prefix.length + payload.length - prefix.length - suffix.length)
            .coerceAtMost(editText.text?.length ?: 0)
        editText.setSelection(newCursor)
        if (isMarkdownPreviewMode) {
            renderMarkdownPreview()
        }
    }

    private fun insertMarkdownTemplateAtCursor(template: String, cursorOffset: Int) {
        val editText = binding.etNoteDesc
        val cursor = editText.selectionStart.coerceAtLeast(0)
        val prefix = if (cursor > 0 && editText.text?.get(cursor - 1) != '\n') "\n" else ""
        val suffix =
            if (cursor < editText.length() && editText.text?.get(cursor) != '\n') "\n" else ""
        val payload = "$prefix$template$suffix"

        isInternalMarkdownEdit = true
        editText.text?.insert(cursor, payload)
        isInternalMarkdownEdit = false

        val selection =
            (cursor + prefix.length + cursorOffset).coerceAtMost(editText.text?.length ?: 0)
        editText.setSelection(selection)
        if (isMarkdownPreviewMode) {
            renderMarkdownPreview()
        }
    }

    private fun applyMarkdownAction(action: MarkdownAction) {
        val editText = binding.etNoteDesc
        val text = editText.text ?: return
        val selectionStart = editText.selectionStart.coerceAtLeast(0)
        val selectionEnd = editText.selectionEnd.coerceAtLeast(0)

        if (selectionStart == selectionEnd) {
            when (action) {
                MarkdownAction.HEADING ->
                    insertMarkdownTemplateAtCursor(HEADING_MARKDOWN_SNIPPET, HEADING_CURSOR_OFFSET)

                MarkdownAction.SUBHEADING ->
                    insertMarkdownTemplateAtCursor(
                        SUBHEADING_MARKDOWN_SNIPPET,
                        SUBHEADING_CURSOR_OFFSET
                    )

                MarkdownAction.ORDERED_LIST ->
                    insertMarkdownTemplateAtCursor(
                        ORDERED_LIST_MARKDOWN_SNIPPET,
                        ORDERED_LIST_CURSOR_OFFSET
                    )

                MarkdownAction.BULLET_LIST ->
                    insertMarkdownTemplateAtCursor(
                        BULLET_LIST_MARKDOWN_SNIPPET,
                        BULLET_LIST_CURSOR_OFFSET
                    )

                MarkdownAction.INLINE_QUOTE ->
                    insertMarkdownTemplateAtCursor(
                        INLINE_QUOTE_MARKDOWN_SNIPPET,
                        INLINE_QUOTE_CURSOR_OFFSET
                    )

                MarkdownAction.BLOCK_QUOTE ->
                    insertMarkdownTemplateAtCursor(
                        BLOCK_QUOTE_MARKDOWN_SNIPPET,
                        BLOCK_QUOTE_CURSOR_OFFSET
                    )

                MarkdownAction.CODE_BLOCK ->
                    insertMarkdownTemplateAtCursor(
                        CODE_BLOCK_MARKDOWN_SNIPPET,
                        CODE_BLOCK_CURSOR_OFFSET
                    )
            }
            return
        }

        val start = minOf(selectionStart, selectionEnd)
        val end = maxOf(selectionStart, selectionEnd)
        val selectedText = text.substring(start, end)
        val transformed = transformSelection(action, selectedText)

        isInternalMarkdownEdit = true
        text.replace(start, end, transformed)
        isInternalMarkdownEdit = false

        editText.setSelection(start, (start + transformed.length).coerceAtMost(text.length))
        syncSelectedImagePathsFromText(text.toString())
        if (isMarkdownPreviewMode) {
            renderMarkdownPreview()
        }
    }

    private fun transformSelection(action: MarkdownAction, selectedText: String): String {
        return when (action) {
            MarkdownAction.HEADING -> applyPrefixToSelectedLines(selectedText) { _ -> "# " }
            MarkdownAction.SUBHEADING -> applyPrefixToSelectedLines(selectedText) { _ -> "## " }
            MarkdownAction.ORDERED_LIST -> applyPrefixToSelectedLines(selectedText) { index -> "${index + 1}. " }
            MarkdownAction.BULLET_LIST -> applyPrefixToSelectedLines(selectedText) { _ -> "- " }
            MarkdownAction.INLINE_QUOTE -> applyInlineQuoteToSelectedLines(selectedText)
            MarkdownAction.BLOCK_QUOTE -> applyPrefixToSelectedLines(selectedText) { _ -> "> " }
            MarkdownAction.CODE_BLOCK -> "```\n$selectedText\n```"
        }
    }

    private fun applyPrefixToSelectedLines(
        selectedText: String,
        prefixProvider: (lineIndex: Int) -> String
    ): String {
        var lineIndex = 0
        return selectedText
            .split("\n")
            .joinToString("\n") { line ->
                if (line.isBlank()) {
                    line
                } else {
                    val prefix = prefixProvider(lineIndex)
                    lineIndex += 1
                    "$prefix$line"
                }
            }
    }

    private fun applyInlineQuoteToSelectedLines(selectedText: String): String {
        return selectedText
            .split("\n")
            .joinToString("\n") { line ->
                if (line.isBlank()) line else "`$line`"
            }
    }

    private fun focusEditorAndShowKeyboard() {
        if (isMarkdownPreviewMode) {
            isMarkdownPreviewMode = false
            updateMarkdownModeUi()
        }

        binding.etNoteDesc.requestFocus()
        binding.etNoteDesc.post {
            val imm = requireContext().getSystemService(InputMethodManager::class.java)
            imm?.showSoftInput(binding.etNoteDesc, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    /**
     * Устанавливает обработчик долгого нажатия на tvMarkdownPreview:
     * если пользователь коснулся inline-цитаты (`…`) или блочной цитаты (```…```),
     * содержимое копируется в буфер обмена.
     */
    private fun setupQuoteCopyOnPreview() {
        binding.tvMarkdownPreview.setOnLongClickListener { view ->
            val rawText = binding.etNoteDesc.text?.toString() ?: return@setOnLongClickListener false

            // Пытаемся найти цитату в позиции касания
            val copied = extractQuoteAtPosition(rawText)
            if (copied != null) {
                val clipboard =
                    requireContext().getSystemService(android.content.ClipboardManager::class.java)
                clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("quote", copied))
                Snackbar.make(view, R.string.note_quote_copied, Snackbar.LENGTH_SHORT)
                    .show()
                view.performAppHapticTap()
                true
            } else {
                false
            }
        }
    }

    /**
     * Находит первую inline- или block-цитату в тексте и возвращает её содержимое.
     * Используется при долгом нажатии в preview-режиме.
     */
    private fun extractQuoteAtPosition(rawText: String): String? {
        val markdownQuoteBlock = Regex("(?m)(^>+\\s?.*(?:\\n>+\\s?.*)*)")
            .find(rawText)
            ?.value
            ?.lineSequence()
            ?.map { line -> line.replaceFirst(Regex("^>+\\s?"), "") }
            ?.joinToString("\n")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        if (markdownQuoteBlock != null) return markdownQuoteBlock

        // Ищем блочную цитату (```…```) — приоритет выше
        val blockMatch = Regex("```([\\s\\S]*?)```").find(rawText)
        if (blockMatch != null) return blockMatch.groupValues[1].trim()

        // Ищем inline-цитату (`…`)
        val inlineMatch = Regex("`([^`]+)`").find(rawText)
        if (inlineMatch != null) return inlineMatch.groupValues[1].trim()

        return null
    }

    private fun handleMarkdownAutoFormatting(editable: Editable) {
        val cursor = binding.etNoteDesc.selectionStart
        val continuation =
            NoteEditorAutoFormatHelper.resolveListContinuation(editable, cursor) ?: return

        isInternalMarkdownEdit = true
        editable.insert(cursor, continuation)
        binding.etNoteDesc.setSelection(cursor + continuation.length)
        isInternalMarkdownEdit = false
    }

    private fun syncSelectedImagePathsFromText(markdownText: String) {
        selectedImagePaths.clear()
        selectedImagePaths.addAll(
            NoteEditorMarkdownHelper.extractMarkdownImagePaths(markdownText).distinct()
        )
    }

    private fun performDialogHaptic() {
        _binding?.root?.performAppHapticTap()
    }

    private fun dismissActiveDialogs() {
        dismissUploadInProgressDialog()
        dismissUploadErrorDialog()
    }

    private fun dismissUploadInProgressDialog() {
        uploadInProgressDialog?.dismiss()
        uploadInProgressDialog = null
    }

    private fun dismissUploadErrorDialog() {
        uploadErrorDialog?.dismiss()
        uploadErrorDialog = null
    }

    private fun collectDraftContent(): DraftContent {
        val title = _binding?.etNoteTitle?.text?.toString().orEmpty().trim()
        val desc = _binding?.etNoteDesc?.text?.toString().orEmpty().trim()
        val hasAnyMedia = NoteEditorMarkdownHelper.extractMarkdownImagePaths(desc).isNotEmpty()
        val hasWebLink = webLink.isNotBlank()
        return DraftContent(title, desc, hasAnyMedia, hasWebLink)
    }

    private fun hasNoteChanges(draft: DraftContent): Boolean {
        val note = editingNote ?: return true
        val currentImages = note.imagePaths.filter { it.isNotBlank() }
            .ifEmpty { listOf(note.imgPath).filter { it.isNotBlank() } }
        val originalEditorText = NoteEditorMarkdownHelper.ensureMarkdownImages(
            text = note.noteText,
            imagePaths = currentImages,
            imageAltText = getString(R.string.note_markdown_image_alt)
        )

        return note.title != draft.title ||
                originalEditorText != draft.desc ||
                note.color != selectedColor ||
                note.webLink != webLink
    }

    private fun handleEmptyNoteExit() {
        lifecycleScope.launch {
            if (!noteId.isNullOrBlank()) {
                viewModel.deleteNote(noteId.orEmpty())
            }
            dismissActiveDialogs()
            backPressedCallback?.isEnabled = false
            if (isAdded) parentFragmentManager.popBackStack()
        }
    }

    companion object {
        private const val TAG = "NoteEditorFragment"
        private const val ARG_NOTE_ID = "arg_note_id"
        private const val SAVE_TIMEOUT_MS = 30_000L
        private const val NOTE_IMAGES_DIR = "note_images"
        private const val HEADING_MARKDOWN_SNIPPET = "# "
        private const val SUBHEADING_MARKDOWN_SNIPPET = "## "
        private const val ORDERED_LIST_MARKDOWN_SNIPPET = "1. "
        private const val BULLET_LIST_MARKDOWN_SNIPPET = "- "
        private const val HEADING_CURSOR_OFFSET = 2
        private const val SUBHEADING_CURSOR_OFFSET = 4
        private const val ORDERED_LIST_CURSOR_OFFSET = 3
        private const val BULLET_LIST_CURSOR_OFFSET = 2
        private const val INLINE_QUOTE_MARKDOWN_SNIPPET = "``"
        private const val INLINE_QUOTE_CURSOR_OFFSET = 1
        private const val BLOCK_QUOTE_MARKDOWN_SNIPPET = "> "
        private const val BLOCK_QUOTE_CURSOR_OFFSET = 2
        private const val CODE_BLOCK_MARKDOWN_SNIPPET = "```\n\n```"
        private const val CODE_BLOCK_CURSOR_OFFSET = 4

        fun newInstance(noteId: String? = null) = NoteEditorFragment().apply {
            arguments = Bundle().apply { putString(ARG_NOTE_ID, noteId) }
        }
    }

    private enum class MarkdownAction {
        HEADING,
        SUBHEADING,
        ORDERED_LIST,
        BULLET_LIST,
        INLINE_QUOTE,
        BLOCK_QUOTE,
        CODE_BLOCK
    }


    private fun String.isLikelyImagePath(): Boolean {
        val value = lowercase(Locale.ROOT)
        return value.startsWith("content://") ||
                value.endsWith(".png") ||
                value.endsWith(".jpg") ||
                value.endsWith(".jpeg") ||
                value.endsWith(".webp")
    }

    private data class DraftContent(
        val title: String,
        val desc: String,
        val hasAnyMedia: Boolean,
        val hasWebLink: Boolean
    ) {
        fun isCompletelyEmpty(): Boolean {
            return title.isBlank() && desc.isBlank() && !hasAnyMedia && !hasWebLink
        }
    }

    private fun isImageLink(link: String): Boolean {
        if (selectedImagePaths.contains(link)) return true
        if (link.contains("/image_cache/") || link.contains("/note_images/")) return true
        if (link.isLikelyImagePath()) return true
        if (link.startsWith("https://raw.githubusercontent.com/")) return true
        return false
    }

    private fun updateEditorMeta(note: NoteItem? = null) {
        val updatedBy = UserNameNormalizer.normalize(
            value = note?.updatedBy
                ?.takeIf { it.isNotBlank() }
                ?: prefs.getString(
                    AppConfig.Preferences.CHARACTER_KEY,
                    getString(R.string.unknown_value)
                ),
            fallback = getString(R.string.unknown_value)
        )
        val displayedDateTime = note
            ?.updatedAt
            ?.takeIf { it > 0L }
            ?.let { dateHelper.formatEpochMillis(it) }
            ?: currentDateTime

        binding.tvDataTime.text = getString(
            R.string.editor_updated_by_time_format,
            updatedBy,
            displayedDateTime
        )
    }
}
