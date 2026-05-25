package ru.bl3xand.pancake.utils.ui

import android.view.View

object ViewAnimationUtils {

    fun crossfadeReplace(
        show: View,
        hide: View,
        duration: Long = 200L
    ) {
        show.visibility = View.VISIBLE
        show.scaleX = 0.8f
        show.scaleY = 0.8f
        show.alpha = 0f
        show.animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(duration)
            .start()

        hide.animate()
            .alpha(0f)
            .setDuration(duration - 50)
            .withEndAction { hide.visibility = View.INVISIBLE }
            .start()
    }

    fun crossfadeRestore(
        show: View,
        hide: View,
        duration: Long = 200L
    ) {
        hide.animate()
            .scaleX(0.8f)
            .scaleY(0.8f)
            .alpha(0f)
            .setDuration(duration)
            .withEndAction { hide.visibility = View.GONE }
            .start()

        show.visibility = View.VISIBLE
        show.animate()
            .alpha(1f)
            .setDuration(duration)
            .start()
    }
}