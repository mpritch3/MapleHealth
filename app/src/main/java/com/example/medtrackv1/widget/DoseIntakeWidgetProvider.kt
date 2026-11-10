package com.example.medtrackv1.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import com.example.medtrackv1.MedTrackApplication
import com.example.medtrackv1.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DoseIntakeWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { updateWidget(context, appWidgetManager, it) }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle
    ) {
        updateWidget(context, appWidgetManager, appWidgetId)
    }

    companion object {
        const val ACTION_LOG_DOSE = "com.example.medtrackv1.widget.LOG_DOSE"
        const val EXTRA_TARGET_KEY = "dose_target_key"
        private const val PREFERENCES = "dose_intake_widget_preferences"
        private const val BUSY_TIMEOUT_MS = 30_000L
        private val lock = Any()

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val provider = ComponentName(context, DoseIntakeWidgetProvider::class.java)
            manager.getAppWidgetIds(provider).forEach { updateWidget(context, manager, it) }
        }

        fun updateWidget(context: Context, widgetId: Int) {
            updateWidget(context, AppWidgetManager.getInstance(context), widgetId)
        }

        private fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
            CoroutineScope(Dispatchers.IO).launch {
                val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                val selectedKeys = preferences.getStringSet(preferencesKey(widgetId), emptySet())
                    .orEmpty()
                val app = context.applicationContext as MedTrackApplication
                val medications = app.medicationRepository.allMedications.first()
                val targetsByKey = DoseWidgetTargets.fromMedications(medications).associateBy { it.key }
                val targets = selectedKeys.mapNotNull { targetsByKey[it] }.sortedBy { it.label.lowercase() }

                val options = manager.getAppWidgetOptions(widgetId)
                val widthDp = maxOf(
                    options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH),
                    options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
                )
                val heightDp = maxOf(
                    options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT),
                    options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
                )
                val columns = cellCount(widthDp)
                val rows = cellCount(heightDp)
                val busy = isBusy(preferences, widgetId)

                val root = RemoteViews(context.packageName, R.layout.widget_dose_intake)
                root.setViewVisibility(
                    R.id.widget_empty,
                    if (targets.isEmpty()) View.VISIBLE else View.GONE
                )
                root.removeAllViews(R.id.widget_buttons)
                targets.take(rows).forEach { target ->
                    val item = if (columns >= 2) {
                        RemoteViews(context.packageName, R.layout.widget_dose_button_wide).apply {
                            setTextViewText(R.id.widget_button, "Take")
                            setTextViewText(R.id.widget_medication_name, target.label)
                        }
                    } else {
                        RemoteViews(context.packageName, R.layout.widget_dose_button).apply {
                            setTextViewText(R.id.widget_button, target.label)
                        }
                    }
                    val clickIntent = Intent(context, DoseIntakeWidgetActionReceiver::class.java)
                        .setAction(ACTION_LOG_DOSE)
                        .setData(Uri.parse("medtrack-widget://$widgetId/${Uri.encode(target.key)}"))
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                        .putExtra(EXTRA_TARGET_KEY, target.key)
                    val pendingIntent = PendingIntent.getBroadcast(
                        context,
                        (widgetId.toString() + target.key).hashCode(),
                        clickIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    item.setOnClickPendingIntent(R.id.widget_button, pendingIntent)
                    item.setContentDescription(R.id.widget_button, "Log dose for ${target.label}")
                    item.setBoolean(R.id.widget_button, "setEnabled", !busy)
                    root.addView(R.id.widget_buttons, item)
                }
                withContext(Dispatchers.Main) {
                    manager.updateAppWidget(widgetId, root)
                }
            }
        }

        internal fun tryLockWidget(context: Context, widgetId: Int): Boolean {
            val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            synchronized(lock) {
                val key = busyKey(widgetId)
                val lockedUntil = preferences.getLong(key, 0L)
                if (lockedUntil > SystemClock.elapsedRealtime()) return false
                return preferences.edit()
                    .putLong(key, SystemClock.elapsedRealtime() + BUSY_TIMEOUT_MS)
                    .commit()
            }
        }

        internal fun unlockWidget(context: Context, widgetId: Int) {
            context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .remove(busyKey(widgetId))
                .apply()
        }

        private fun isBusy(preferences: android.content.SharedPreferences, widgetId: Int): Boolean =
            preferences.getLong(busyKey(widgetId), 0L) > SystemClock.elapsedRealtime()

        private fun busyKey(widgetId: Int) = "widget_busy_until_$widgetId"

        private fun cellCount(sizeDp: Int): Int =
            ((sizeDp + 30) / 70).coerceAtLeast(1)

        internal fun preferencesKey(widgetId: Int) = "widget_$widgetId"
    }
}
