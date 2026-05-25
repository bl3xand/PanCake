package ru.bl3xand.pancake.di.components.viewholder

import android.content.res.ColorStateList
import android.graphics.Paint
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import ru.bl3xand.pancake.R
import ru.bl3xand.pancake.data.model.ShoppingItem
import ru.bl3xand.pancake.utils.ui.performAppHapticTap
import java.text.SimpleDateFormat
import java.util.Locale

class ShoppingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    private val cardView = itemView as MaterialCardView
    private val itemName: TextView = itemView.findViewById(R.id.itemName)
    private val itemCount: TextView = itemView.findViewById(R.id.itemCount)
    private val nameAndCountLayout: View = itemView.findViewById(R.id.nameAndCountLayout)
    private val itemType: TextView = itemView.findViewById(R.id.itemType)
    private val itemCreatedByAndTime: TextView = itemView.findViewById(R.id.itemCreatedByAndTime)
    private val deleteItemButton: ImageButton = itemView.findViewById(R.id.deleteButton)

    private val dateTimeFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

    fun bind(
        item: ShoppingItem,
        isEditMode: Boolean,
        onDeleteItem: (String) -> Unit,
        onClick: () -> Unit
    ) {
        bindContent(item)
        bindColors(item)
        bindInteractions(item, isEditMode, onDeleteItem, onClick)
    }

    // ─── Content ────────────────────────────────────────────────────────────────

    private fun bindContent(item: ShoppingItem) {
        itemName.text = item.name
        itemCount.text = item.count
        itemType.text = item.type
        itemCreatedByAndTime.text = itemView.context.getString(
            R.string.created_by_time_format,
            item.createdBy,
            dateTimeFormat.format(item.timestamp)
        )

        itemName.paintFlags = if (item.isStrikedThrough) {
            itemName.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        } else {
            itemName.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
        }
    }

    // ─── Colors ─────────────────────────────────────────────────────────────────

    private data class CardColors(val container: Int, val onContainer: Int, val text: Int)

    private fun resolveCardColors(item: ShoppingItem): CardColors {
        val ctx = itemView.context
        val primary = getMaterialColor(com.google.android.material.R.attr.colorPrimaryContainer)
        val onPrimary = getMaterialColor(com.google.android.material.R.attr.colorOnPrimaryContainer)
        val secondary = getMaterialColor(com.google.android.material.R.attr.colorSecondaryContainer)
        val onSecondary = getMaterialColor(com.google.android.material.R.attr.colorOnSecondaryContainer)
        val tertiary = getMaterialColor(com.google.android.material.R.attr.colorTertiaryContainer)
        val onTertiary = getMaterialColor(com.google.android.material.R.attr.colorOnTertiaryContainer)
        val neutral = getMaterialColor(com.google.android.material.R.attr.colorSurfaceContainerHigh)
        val onNeutral = getMaterialColor(com.google.android.material.R.attr.colorOnSurface)
        val black = ctx.getColor(R.color.black)
        val white = ctx.getColor(R.color.white)

        return when {
            item.isStrikedThrough -> CardColors(
                container = ColorUtils.blendARGB(tertiary, black, 0.70f),
                onContainer = onTertiary,
                text = white
            )
            item.type == ctx.getString(R.string.food) ->
                CardColors(primary, onPrimary, onPrimary)
            item.type == ctx.getString(R.string.household_goods) ->
                CardColors(neutral, onNeutral, onNeutral)
            item.type == ctx.getString(R.string.clothes) ->
                CardColors(secondary, onSecondary, onSecondary)
            item.type == ctx.getString(R.string.home_goods) ->
                CardColors(primary, onPrimary, onPrimary)
            item.type == ctx.getString(R.string.tech_goods) ->
                CardColors(neutral, onNeutral, onNeutral)
            else ->
                CardColors(secondary, onSecondary, onSecondary)
        }
    }

    private fun bindColors(item: ShoppingItem) {
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
        cardView.strokeColor = ColorUtils.setAlphaComponent(
            colors.onContainer,
            when {
                item.isStrikedThrough -> 90
                else -> 56
            }
        )

        val primaryTextColor = colors.text
        val secondaryTextColor = ColorUtils.setAlphaComponent(
            primaryTextColor,
            if (item.isStrikedThrough) 230 else 245
        )

        itemName.setTextColor(primaryTextColor)
        itemCount.setTextColor(primaryTextColor)
        itemType.setTextColor(secondaryTextColor)
        itemCreatedByAndTime.setTextColor(secondaryTextColor)
        deleteItemButton.imageTintList = ColorStateList.valueOf(primaryTextColor)

        val contentAlpha = if (item.isStrikedThrough) 0.30f else 1.0f
        cardView.alpha = 1.0f
        itemName.alpha = contentAlpha
        itemCount.alpha = contentAlpha
        itemType.alpha = contentAlpha
        itemCreatedByAndTime.alpha = contentAlpha
        deleteItemButton.alpha = 1.0f
    }

    private fun getMaterialColor(attrResId: Int): Int =
        MaterialColors.getColor(itemView, attrResId)

    // ─── Interactions ────────────────────────────────────────────────────────────

    private fun bindInteractions(
        item: ShoppingItem,
        isEditMode: Boolean,
        onDeleteItem: (String) -> Unit,
        onClick: () -> Unit
    ) {
        deleteItemButton.visibility = if (isEditMode) View.VISIBLE else View.GONE

        val spacingPx = (8 * itemView.resources.displayMetrics.density).toInt()
        val layoutParams = nameAndCountLayout.layoutParams as? ViewGroup.MarginLayoutParams
        layoutParams?.marginEnd = if (isEditMode) spacingPx else 0
        nameAndCountLayout.layoutParams = layoutParams

        deleteItemButton.setOnClickListener {
            deleteItemButton.performAppHapticTap()
            onDeleteItem(item.id)
        }
        itemView.setOnClickListener {
            itemView.performAppHapticTap()
            onClick()
        }
    }
}