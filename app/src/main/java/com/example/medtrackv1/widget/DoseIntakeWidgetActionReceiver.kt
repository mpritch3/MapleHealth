package com.example.medtrackv1.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.medtrackv1.MedTrackApplication
import com.example.medtrackv1.data.model.DoseLog
import com.example.medtrackv1.notification.MedicationReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class DoseIntakeWidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DoseIntakeWidgetProvider.ACTION_LOG_DOSE) return
        val targetKey = intent.getStringExtra(DoseIntakeWidgetProvider.EXTRA_TARGET_KEY) ?: return
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID ||
            !DoseIntakeWidgetProvider.tryLockWidget(context, widgetId)
        ) return
        val pendingResult = goAsync()
        DoseIntakeWidgetProvider.updateWidget(context, widgetId)

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as MedTrackApplication
                val medications = app.medicationRepository.allMedications.first()
                val target = DoseWidgetTargets.resolve(targetKey, medications) ?: return@launch
                val selected = medications.filter { it.id in target.medicationIds }
                if (selected.isEmpty()) return@launch

                val timestamp = System.currentTimeMillis()
                selected.forEach { medication ->
                    app.doseLogRepository.insertDoseLog(
                        DoseLog(
                            medicationId = medication.id,
                            timestamp = timestamp,
                            doseAmount = medication.doseAmount * if (target.key.startsWith("group:")) medication.groupDoseMultiplier.coerceAtLeast(1) else 1
                        )
                    )
                    val logs = app.doseLogRepository.getDoseLogsForMedicationSync(medication.id)
                    MedicationReminderScheduler.schedule(context, medication, logs)
                }
            } finally {
                DoseIntakeWidgetProvider.unlockWidget(context, widgetId)
                DoseIntakeWidgetProvider.updateWidget(context, widgetId)
                pendingResult.finish()
            }
        }
    }
}
