package ru.bl3xand.pancake.di.components.viewholder

import android.content.res.ColorStateList
import android.graphics.Paint
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import ru.bl3xand.pancake.R
import ru.bl3xand.pancake.data.model.CalendarItem
import ru.bl3xand.pancake.utils.extensions.boldPrefix
import ru.bl3xand.pancake.utils.extensions.formatDuration
import ru.bl3xand.pancake.utils.ui.performAppHapticTap
import java.text.SimpleDateFormat
import java.util.Locale

class CalendarViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    private val cardView = itemView as MaterialCardView
    private val taskName: TextView = itemView.findViewById(R.id.taskName)
    private val importanceType: TextView = itemView.findViewById(R.id.importanceType)
    private val slaType: TextView = itemView.findViewById(R.id.slaType)
    private val deadlineType: TextView = itemView.findViewById(R.id.deadlineType)
    private val itemCreatedByAndTime: TextView = itemView.findViewById(R.id.itemCreatedByAndTime)
    private val recurrenceType: TextView = itemView.findViewById(R.id.recurrenceType)
    private val deleteButton: ImageButton = itemView.findViewById(R.id.deleteButton)

    private val dateTimeFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

    fun bind(
        item: CalendarItem,
        isEditMode: Boolean,
        onDeleteItem: (String) -> Unit,
        onClick: () -> Unit
    ) {
        bindContent(item)
        bindColors(item)
        bindInteractions(item, isEditMode, onDeleteItem, onClick)
    }

    // ─── Content ────────────────────────────────────────────────────────────────

    private fun bindContent(item: CalendarItem) {
        val ctx = itemView.context
        taskName.text = item.taskName

        val importanceLabel = ctx.getString(R.string.importance_label)
        val importanceValue = when (item.importanceType) {
            1 -> ctx.getString(R.string.low_importance)
            2 -> ctx.getString(R.string.mid_importance)
            3 -> ctx.getString(R.string.high_importance)
            else -> "?"
        }
        importanceType.text = boldPrefix("$importanceLabel $importanceValue", importanceLabel)

        slaType.text = buildSlaText(item)
        deadlineType.text = buildDeadlineText(item)
        recurrenceType.text = buildRecurrenceText(item)

        itemCreatedByAndTime.text = itemView.context.getString(
            R.string.created_by_time_format,
            item.createdBy,
            dateTimeFormat.format(item.timestamp)
        )

        taskName.paintFlags = if (item.isStrikedThrough) {
            taskName.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        } else {
            taskName.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
        }
    }

    private fun buildSlaText(item: CalendarItem): CharSequence {
        val ctx = itemView.context
        val remainingLabel = ctx.getString(R.string.remaining_label)
        return when {
            item.isStrikedThrough ->
                boldPrefix("$remainingLabel ${ctx.getString(R.string.task_completed)}", remainingLabel)
            item.deadline <= 0L ->
                boldPrefix("$remainingLabel ${ctx.getString(R.string.no_time)}", remainingLabel)
            else -> buildTimeLeftText(item.deadline)
        }
    }

    private fun buildTimeLeftText(deadline: Long): CharSequence {
        val timeLeft = deadline - System.currentTimeMillis()
        return if (timeLeft < 0) {
            val label = itemView.context.getString(R.string.overdue)
            boldPrefix("$label: на ${(-timeLeft).formatDuration()}", label)
        } else {
            val label = itemView.context.getString(R.string.remaining_label)
            boldPrefix("$label ${timeLeft.formatDuration()}", label)
        }
    }

    private fun buildDeadlineText(item: CalendarItem): CharSequence {
        val ctx = itemView.context
        val label = ctx.getString(R.string.deadline_label)
        val value = if (item.deadline <= 0L) {
            ctx.getString(R.string.no_time)
        } else {
            dateTimeFormat.format(item.deadline)
        }
        return boldPrefix("$label $value", label)
    }

    private fun buildRecurrenceText(item: CalendarItem): CharSequence {
        val label = itemView.context.getString(R.string.recurrence_label)
        return boldPrefix("$label ${item.recurrence}", label)
    }

    // ─── Colors ─────────────────────────────────────────────────────────────────

    private data class CardColors(val container: Int, val onContainer: Int, val text: Int)

    private fun resolveCardColors(item: CalendarItem): CardColors {
        val primary = getMaterialColor(com.google.android.material.R.attr.colorPrimaryContainer)
        val onPrimary = getMaterialColor(com.google.android.material.R.attr.colorOnPrimaryContainer)
        val secondary = getMaterialColor(com.google.android.material.R.attr.colorSecondaryContainer)
        val onSecondary = getMaterialColor(com.google.android.material.R.attr.colorOnSecondaryContainer)
        val tertiary = getMaterialColor(com.google.android.material.R.attr.colorTertiaryContainer)
        val onTertiary = getMaterialColor(com.google.android.material.R.attr.colorOnTertiaryContainer)
        val neutral = getMaterialColor(com.google.android.material.R.attr.colorSurfaceContainerHigh)
        val onNeutral = getMaterialColor(com.google.android.material.R.attr.colorOnSurface)
        val error = getMaterialColor(com.google.android.material.R.attr.colorErrorContainer)
        val onError = getMaterialColor(com.google.android.material.R.attr.colorOnErrorContainer)
        val black = itemView.context.getColor(R.color.black)
        val white = itemView.context.getColor(R.color.white)

        return when {
            item.isStrikedThrough -> CardColors(
                container = ColorUtils.blendARGB(tertiary, black, 0.70f),
                onContainer = onTertiary,
                text = white
            )
            item.deadline > 0L && (item.deadline - System.currentTimeMillis()) < 0 -> CardColors(error, onError, onError)
            item.importanceType == 1 -> CardColors(neutral, onNeutral, onNeutral)
            item.importanceType == 2 -> CardColors(secondary, onSecondary, onSecondary)
            item.importanceType == 3 -> CardColors(primary, onPrimary, onPrimary)
            else -> CardColors(secondary, onSecondary, onSecondary)
        }
    }

    private fun bindColors(item: CalendarItem) {
        val colors = resolveCardColors(item)
        val surfaceContainer = getMaterialColor(com.google.android.material.R.attr.colorSurfaceContainer)
        val itemAlpha = if (item.isStrikedThrough) 0.2f else 1.0f
        val backgroundColor = if (item.isStrikedThrough) {
            ColorUtils.blendARGB(colors.container, surfaceContainer, 0.50f)
        } else {
            colors.container
        }

        cardView.setCardBackgroundColor(backgroundColor)
        cardView.strokeWidth = 2
        cardView.strokeColor = ColorUtils.setAlphaComponent(colors.onContainer, 56)

        val primaryTextColor = colors.text
        val secondaryTextColor = ColorUtils.setAlphaComponent(
            primaryTextColor,
            if (item.isStrikedThrough) 230 else 245
        )

        taskName.setTextColor(primaryTextColor)
        importanceType.setTextColor(secondaryTextColor)
        slaType.setTextColor(secondaryTextColor)
        deadlineType.setTextColor(secondaryTextColor)
        itemCreatedByAndTime.setTextColor(secondaryTextColor)
        recurrenceType.setTextColor(secondaryTextColor)
        deleteButton.imageTintList = ColorStateList.valueOf(primaryTextColor)

        cardView.alpha = 1.0f
        taskName.alpha = itemAlpha
        importanceType.alpha = itemAlpha
        slaType.alpha = itemAlpha
        deadlineType.alpha = itemAlpha
        recurrenceType.alpha = itemAlpha
        itemCreatedByAndTime.alpha = itemAlpha
        deleteButton.alpha = 1.0f
    }

    private fun getMaterialColor(attrResId: Int): Int =
        MaterialColors.getColor(itemView, attrResId)

    // ─── Interactions ────────────────────────────────────────────────────────────

    private fun bindInteractions(
        item: CalendarItem,
        isEditMode: Boolean,
        onDeleteItem: (String) -> Unit,
        onClick: () -> Unit
    ) {
        deleteButton.visibility = if (isEditMode) View.VISIBLE else View.GONE
        deleteButton.setOnClickListener {
            deleteButton.performAppHapticTap()
            onDeleteItem(item.id)
        }
        itemView.setOnClickListener {
            if (!isEditMode) {
                itemView.performAppHapticTap()
                onClick()
            }
        }
    }
}