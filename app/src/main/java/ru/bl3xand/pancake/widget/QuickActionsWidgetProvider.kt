package ru.bl3xand.pancake.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.util.TypedValue
import android.widget.RemoteViews
import ru.bl3xand.pancake.R
import ru.bl3xand.pancake.ui.activity.MainActivity

class QuickActionsWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (widgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_quick_actions)

            val iconTint = resolveThemeColor(context, com.google.android.material.R.attr.colorOnPrimaryContainer)

            for ((viewId, action) in BUTTON_ACTIONS) {
                views.setOnClickPendingIntent(
                    viewId,
                    createPendingIntent(context, widgetId, action)
                )
                views.setInt(viewId, "setColorFilter", iconTint)
            }

            views.setOnClickPendingIntent(
                android.R.id.background,
                createOpenAppPendingIntent(context, widgetId)
            )

            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        val action = intent.action ?: return
        if (action in ACTIONS) {
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_WIDGET_ACTION, action)
            }
            context.startActivity(launchIntent)
        }
    }

    private fun resolveThemeColor(context: Context, attr: Int): Int {
        val typedValue = TypedValue()
        val theme = context.resources.newTheme().apply {
            applyStyle(com.google.android.material.R.style.Theme_Material3_DynamicColors_DayNight, true)
        }
        theme.resolveAttribute(attr, typedValue, true)
        return context.getColor(typedValue.resourceId)
    }

    private fun createPendingIntent(
        context: Context,
        widgetId: Int,
        action: String
    ): PendingIntent {
        val intent = Intent(context, QuickActionsWidgetProvider::class.java).apply {
            this.action = action
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        }
        return PendingIntent.getBroadcast(
            context,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createOpenAppPendingIntent(context: Context, widgetId: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            widgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val EXTRA_WIDGET_ACTION = "widget_action"
        const val ACTION_SHOPPING = "ru.bl3xand.pancake.WIDGET_SHOPPING"
        const val ACTION_CALENDAR = "ru.bl3xand.pancake.WIDGET_CALENDAR"
        const val ACTION_MOVIE = "ru.bl3xand.pancake.WIDGET_MOVIE"
        const val ACTION_NOTE = "ru.bl3xand.pancake.WIDGET_NOTE"

        val ACTIONS = setOf(ACTION_SHOPPING, ACTION_CALENDAR, ACTION_MOVIE, ACTION_NOTE)

        private val BUTTON_ACTIONS = listOf(
            R.id.btn_shopping to ACTION_SHOPPING,
            R.id.btn_calendar to ACTION_CALENDAR,
            R.id.btn_movie to ACTION_MOVIE,
            R.id.btn_note to ACTION_NOTE
        )
    }
}