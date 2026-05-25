package ru.bl3xand.pancake.utils.ui

import android.content.res.ColorStateList
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import com.google.android.material.color.MaterialColors
import com.google.android.material.floatingactionbutton.FloatingActionButton
import ru.bl3xand.pancake.R
import java.util.Locale

/**
 * Расширения для унифицированной работы с UI элементами.
 * Содержит вспомогательные функции для цветов, haptic feedback и стилизации.
 */

// ======================== Haptic Feedback ========================

/**
 * Выполнить haptic feedback при нажатии.
 * Использует единый профиль интенсивности для всего приложения.
 *
 * @return true если haptic feedback был выполнен успешно
 */
fun View.performAppHapticTap(): Boolean {
    return performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
}

// ======================== Цветовые ресурсы ========================

/**
 * Преобразовать color resource в HEX-строку для сохранения.
 * Используется для сохранения цветов в Firebase.
 *
 * @param colorRes ID цветового ресурса
 * @return HEX-строка формата "#RRGGBB"
 */
fun android.content.Context.colorResToHex(@ColorRes colorRes: Int): String {
    val colorInt = ContextCompat.getColor(this, colorRes)
    return String.format(Locale.US, "#%06X", 0xFFFFFF and colorInt)
}

// ======================== FAB Стилизация ========================

/**
 * Применить tertiary container оттенок к FloatingActionButton.
 * Используется для стандартной стилизации FAB'ов в приложении.
 */
fun FloatingActionButton.applyTertiaryContainerTint() {
    backgroundTintList = ColorStateList.valueOf(
        MaterialColors.getColor(this, com.google.android.material.R.attr.colorTertiaryContainer)
    )
}