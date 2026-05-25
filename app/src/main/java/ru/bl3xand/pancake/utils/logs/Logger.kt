package ru.bl3xand.pancake.utils.logs

import android.util.Log

object Logger {
    fun logInfo(tag: String, msg: String) {
        Log.i(tag, msg)
    }

    fun logError(tag: String, msg: String) {
        Log.e(tag, msg)
    }

    fun logDebug(tag: String, msg: String) {
        Log.d(tag, msg)
    }
}