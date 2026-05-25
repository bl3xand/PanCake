package ru.bl3xand.pancake.di.components.viewholder

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.text.method.LinkMovementMethod
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import io.noties.markwon.Markwon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.bl3xand.pancake.BuildConfig
import ru.bl3xand.pancake.R
import ru.bl3xand.pancake.data.cache.ImageCacheManager
import ru.bl3xand.pancake.data.model.NoteItem
import ru.bl3xand.pancake.data.model.notes.NoteMetaMode
import ru.bl3xand.pancake.utils.image.ImageUrlHelper
import ru.bl3xand.pancake.utils.noteeditor.NoteQuoteStyleHelper
import ru.bl3xand.pancake.utils.noteeditor.NoteColorResolver
import ru.bl3xand.pancake.utils.ui.performAppHapticTap
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotesViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    private val mediaDividerPx = itemView.context.resources.getDimensionPixelSize(R.dimen.note_media_divider)

    private val cardView = itemView as MaterialCardView
    private val mediaPreviewCard: MaterialCardView = itemView.findViewById(R.id.mediaPreviewCard)
    private val mediaSlot1: FrameLayout = itemView.findViewById(R.id.mediaSlot1)
    private val mediaSlot2: FrameLayout = itemView.findViewById(R.id.mediaSlot2)
    private val mediaSlot3: FrameLayout = itemView.findViewById(R.id.mediaSlot3)
    private val mediaSlot4: FrameLayout = itemView.findViewById(R.id.mediaSlot4)
    private val media1: ImageView = itemView.findViewById(R.id.media1)
    private val media2: ImageView = itemView.findViewById(R.id.media2)
    private val media3: ImageView = itemView.findViewById(R.id.media3)
    private val media4: ImageView = itemView.findViewById(R.id.media4)
    private val mediaMoreCircle: View = itemView.findViewById(R.id.mediaMoreCircle)
    private val mediaMoreCount: TextView = itemView.findViewById(R.id.mediaMoreCount)
    private val noteTitle: TextView = itemView.findViewById(R.id.tvTitle)
    private val noteText: TextView = itemView.findViewById(R.id.tvDesc)
    private val webLink: TextView = itemView.findViewById(R.id.tvWebLink)
    private val itemCreatedByAndTime: TextView = itemView.findViewById(R.id.itemCreatedByAndTime)
    private val markwon by lazy {
        Markwon.builder(itemView.context).build()
    }

    private val dateTimeFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

    fun bind(
        item: NoteItem,
        metaMode: NoteMetaMode,
        isSelected: Boolean,
        isSelectionMode: Boolean,
        lifecycleScope: LifecycleCoroutineScope,
        imageCacheManager: ImageCacheManager,
        onClick: (NoteItem) -> Unit,
        onLongClick: (NoteItem) -> Unit
    ) {
        val backgroundColor = parseNoteColor(item.color)
        applyContentColors(backgroundColor)

        val titleText = item.title.trim()
        noteTitle.text = titleText
        noteTitle.isVisible = titleText.isNotBlank()

        val composedText = item.noteText.toPreviewMarkdown()

        webLink.text = item.webLink
        webLink.movementMethod = LinkMovementMethod.getInstance()
        webLink.isVisible = item.webLink.isNotBlank()

        val safeImages = item.imagePaths.filter { it.isNotBlank() }
            .ifEmpty { listOf(item.imgPath).filter { it.isNotBlank() } }

        cardView.minimumHeight = CARD_MIN_HEIGHT_PX

        noteTitle.maxLines = 2
        noteText.maxLines = NOTE_TEXT_MAX_LINES
        noteText.ellipsize = TextUtils.TruncateAt.END
        noteText.isVisible = composedText.isNotBlank()
        if (noteText.isVisible) {
            markwon.setMarkdown(noteText, composedText)
            NoteQuoteStyleHelper.apply(noteText, item.noteText)
            noteText.movementMethod = null
            noteText.linksClickable = false
            noteText.isClickable = false
            noteText.isLongClickable = false
        }

        val (userText, timeMillis) = when (metaMode) {
            NoteMetaMode.CREATED -> item.createdBy to item.timestamp
            NoteMetaMode.UPDATED -> item.updatedBy.ifBlank { item.createdBy } to maxOf(item.updatedAt, item.timestamp)
        }
        val safeUserText = userText.ifBlank {
            itemView.context.getString(R.string.unknown_value)
        }
        val timestampText = dateTimeFormat.format(Date(timeMillis))
        itemCreatedByAndTime.text = itemView.context.getString(
            R.string.created_by_time_format,
            safeUserText,
            timestampText
        )
        itemCreatedByAndTime.isVisible = true

        bindMediaPreview(safeImages, backgroundColor, lifecycleScope, imageCacheManager)

        cardView.setCardBackgroundColor(backgroundColor)

        // Обводка карточки:
        // - В обычном состоянии — 2dp, colorSurfaceContainerHigh (как кнопка "Изменить" на экране дел)
        // - При выделении — 4dp, colorPrimaryContainer (как кнопка "Календарь" на экране дел)
        val strokeColor = if (isSelected) {
            MaterialColors.getColor(itemView, com.google.android.material.R.attr.colorPrimaryContainer)
        } else {
            MaterialColors.getColor(itemView, com.google.android.material.R.attr.colorSecondaryContainer)
        }
        cardView.strokeWidth = if (isSelected) 10 else 2
        cardView.strokeColor = strokeColor

        itemView.setOnClickListener {
            itemView.performAppHapticTap()
            onClick(item)
        }

        itemView.setOnLongClickListener {
            itemView.performAppHapticTap()
            onLongClick(item)
            true
        }

        itemView.alpha = if (isSelectionMode && !isSelected) 0.9f else 1f
    }

    private fun bindMediaPreview(
        images: List<String>,
        noteColor: Int,
        lifecycleScope: LifecycleCoroutineScope,
        imageCacheManager: ImageCacheManager
    ) {
        if (images.isEmpty()) {
            mediaPreviewCard.isVisible = false
            return
        }

        mediaPreviewCard.isVisible = true
        (mediaPreviewCard.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
            params.marginStart = MEDIA_SIDE_INSET_PX
            params.marginEnd = MEDIA_SIDE_INSET_PX
            params.topMargin = MEDIA_TOP_INSET_PX
            mediaPreviewCard.layoutParams = params
        }

        // mediaPreviewCard.strokeWidth = itemView.context.resources.getDimensionPixelSize(R.dimen.media_card_stroke_width)

        val shown = images.take(4)
        val hiddenCount = (images.size - shown.size).coerceAtLeast(0)

        resetMediaGridLayout()

        val slots = listOf(mediaSlot1, mediaSlot2, mediaSlot3, mediaSlot4)
        val views = listOf(media1, media2, media3, media4)
        slots.forEach { it.isVisible = false }
        mediaMoreCount.isVisible = false
        mediaMoreCircle.isVisible = false

        when (shown.size) {
            1 -> {
                mediaSlot1.isVisible = true
                setGridSpec(mediaSlot1, row = 0, col = 0, rowSpan = 2, colSpan = 2)
                loadImageWithCache(views[0], shown[0], lifecycleScope, imageCacheManager)
            }

            2 -> {
                mediaSlot1.isVisible = true
                mediaSlot2.isVisible = true
                setGridSpec(mediaSlot1, row = 0, col = 0, rowSpan = 2, colSpan = 1, right = mediaDividerPx)
                setGridSpec(mediaSlot2, row = 0, col = 1, rowSpan = 2, colSpan = 1, left = mediaDividerPx)
                loadImageWithCache(views[0], shown[0], lifecycleScope, imageCacheManager)
                loadImageWithCache(views[1], shown[1], lifecycleScope, imageCacheManager)
            }

            3 -> {
                mediaSlot1.isVisible = true
                mediaSlot2.isVisible = true
                mediaSlot3.isVisible = true
                setGridSpec(mediaSlot1, row = 0, col = 0, rowSpan = 2, colSpan = 1, right = mediaDividerPx)
                setGridSpec(mediaSlot2, row = 0, col = 1, rowSpan = 1, colSpan = 1, left = mediaDividerPx, bottom = mediaDividerPx)
                setGridSpec(mediaSlot3, row = 1, col = 1, rowSpan = 1, colSpan = 1, left = mediaDividerPx, top = mediaDividerPx)
                loadImageWithCache(views[0], shown[0], lifecycleScope, imageCacheManager)
                loadImageWithCache(views[1], shown[1], lifecycleScope, imageCacheManager)
                loadImageWithCache(views[2], shown[2], lifecycleScope, imageCacheManager)
            }

            else -> {
                mediaSlot1.isVisible = true
                mediaSlot2.isVisible = true
                mediaSlot3.isVisible = true
                mediaSlot4.isVisible = true
                setGridSpec(mediaSlot1, row = 0, col = 0, rowSpan = 1, colSpan = 1, right = mediaDividerPx, bottom = mediaDividerPx)
                setGridSpec(mediaSlot2, row = 0, col = 1, rowSpan = 1, colSpan = 1, left = mediaDividerPx, bottom = mediaDividerPx)
                setGridSpec(mediaSlot3, row = 1, col = 0, rowSpan = 1, colSpan = 1, right = mediaDividerPx, top = mediaDividerPx)
                setGridSpec(mediaSlot4, row = 1, col = 1, rowSpan = 1, colSpan = 1, left = mediaDividerPx, top = mediaDividerPx)
                loadImageWithCache(views[0], shown[0], lifecycleScope, imageCacheManager)
                loadImageWithCache(views[1], shown[1], lifecycleScope, imageCacheManager)
                loadImageWithCache(views[2], shown[2], lifecycleScope, imageCacheManager)
                loadImageWithCache(views[3], shown[3], lifecycleScope, imageCacheManager)
                if (hiddenCount > 0) {
                    // Круг под текстом "+N": цвет заметки с 50% альфой
                    val circleColor = ColorUtils.setAlphaComponent(noteColor, 0x80)
                    val circleDrawable = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(circleColor)
                    }
                    mediaMoreCircle.background = circleDrawable
                    mediaMoreCircle.isVisible = true
                    mediaMoreCount.isVisible = true
                    mediaMoreCount.text = "+$hiddenCount"
                    mediaMoreCount.setBackgroundColor(Color.TRANSPARENT)
                }
            }
        }
    }

    private fun resetMediaGridLayout() {
        setGridSpec(mediaSlot1, row = 0, col = 0, rowSpan = 1, colSpan = 1)
        setGridSpec(mediaSlot2, row = 0, col = 1, rowSpan = 1, colSpan = 1)
        setGridSpec(mediaSlot3, row = 1, col = 0, rowSpan = 1, colSpan = 1)
        setGridSpec(mediaSlot4, row = 1, col = 1, rowSpan = 1, colSpan = 1)
    }

    private fun setGridSpec(
        view: View,
        row: Int,
        col: Int,
        rowSpan: Int,
        colSpan: Int,
        left: Int = 0,
        top: Int = 0,
        right: Int = 0,
        bottom: Int = 0,
    ) {
        val params = (view.layoutParams as? GridLayout.LayoutParams) ?: GridLayout.LayoutParams(
            GridLayout.spec(row, rowSpan, 1f),
            GridLayout.spec(col, colSpan, 1f)
        )
        params.rowSpec = GridLayout.spec(row, rowSpan, 1f)
        params.columnSpec = GridLayout.spec(col, colSpan, 1f)
        params.width = 0
        params.height = 0
        params.setMargins(left, top, right, bottom)
        view.layoutParams = params
    }

    private fun loadImageWithCache(
        target: ImageView,
        uri: String,
        lifecycleScope: LifecycleCoroutineScope,
        imageCacheManager: ImageCacheManager
    ) {
        if (ImageUrlHelper.isGitHubImage(uri)) {
            lifecycleScope.launch {
                val resolvedPath = withContext(Dispatchers.IO) {
                    val imageUrl = if (ImageUrlHelper.isGitHubScheme(uri)) {
                        ImageUrlHelper.toGitHubUrl(uri)
                    } else {
                        uri
                    }
                    val repoPath = if (ImageUrlHelper.isGitHubScheme(uri)) {
                        ImageUrlHelper.extractRepoPath(uri)
                    } else {
                        ImageUrlHelper.extractRepoPathFromUrl(imageUrl)
                    }
                    imageCacheManager.getOrDownloadImage(
                        imageUrl,
                        repoPath,
                        BuildConfig.GITHUB_TOKEN.trim()
                    )
                }
                if (target.isAttachedToWindow) {
                    Glide.with(target.context)
                        .load(resolvedPath)
                        .centerCrop()
                        .into(target)
                }
            }
        } else {
            Glide.with(target.context)
                .load(uri)
                .centerCrop()
                .into(target)
        }
    }

    private fun parseNoteColor(value: String): Int =
        NoteColorResolver.resolve(itemView.context, value)

    private fun applyContentColors(backgroundColor: Int) {
        val primaryTextColor = resolvePrimaryTextColor(backgroundColor)
        val secondaryTextColor = ColorUtils.setAlphaComponent(primaryTextColor, 0xCC)

        noteTitle.setTextColor(primaryTextColor)
        noteText.setTextColor(primaryTextColor)
        webLink.setTextColor(secondaryTextColor)
        itemCreatedByAndTime.setTextColor(secondaryTextColor)
    }

    private fun resolvePrimaryTextColor(backgroundColor: Int): Int {
        val onSurface = MaterialColors.getColor(itemView, com.google.android.material.R.attr.colorOnSurface)
        val onPrimaryContainer = MaterialColors.getColor(itemView, com.google.android.material.R.attr.colorOnPrimaryContainer)

        val contrastOnSurface = ColorUtils.calculateContrast(onSurface, backgroundColor)
        val contrastOnPrimaryContainer = ColorUtils.calculateContrast(onPrimaryContainer, backgroundColor)

        return if (contrastOnSurface >= contrastOnPrimaryContainer) onSurface else onPrimaryContainer
    }

    private fun String.toPreviewMarkdown(): String {
        return this
            .replace(Regex("!\\[[^\\]]*]\\([^)]+\\)"), "")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }

    private companion object {
        const val CARD_MIN_HEIGHT_PX = 96
        const val NOTE_TEXT_MAX_LINES = 16
        const val MEDIA_SIDE_INSET_PX = 0
        const val MEDIA_TOP_INSET_PX = 0
    }
}
