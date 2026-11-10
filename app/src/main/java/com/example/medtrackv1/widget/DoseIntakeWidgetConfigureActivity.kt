package com.example.medtrackv1.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.example.medtrackv1.MedTrackApplication
import com.example.medtrackv1.ui.theme.Medtrackv1Theme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class DoseIntakeWidgetConfigureActivity : ComponentActivity() {
    private var widgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)
        widgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        lifecycleScope.launch {
            val app = application as MedTrackApplication
            val medications = app.medicationRepository.allMedications.first()
            val targets = DoseWidgetTargets.fromMedications(medications)
            setContent {
                Medtrackv1Theme {
                    DoseWidgetConfigureScreen(
                        targets = targets,
                        onCancel = { finish() },
                        onSave = { selectedKeys ->
                            getSharedPreferences("dose_intake_widget_preferences", MODE_PRIVATE)
                                .edit()
                                .putStringSet(
                                    DoseIntakeWidgetProvider.preferencesKey(widgetId),
                                    selectedKeys.toSet()
                                )
                                .apply()
                            val result = Intent().putExtra(
                                AppWidgetManager.EXTRA_APPWIDGET_ID,
                                widgetId
                            )
                            setResult(Activity.RESULT_OK, result)
                            DoseIntakeWidgetProvider.updateWidget(this@DoseIntakeWidgetConfigureActivity, widgetId)
                            finish()
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DoseWidgetConfigureScreen(
    targets: List<DoseWidgetTarget>,
    onCancel: () -> Unit,
    onSave: (Set<String>) -> Unit
) {
    val selectedKeys = remember { mutableStateListOf<String>() }
    val maxButtons = 12

    Scaffold(
        topBar = { TopAppBar(title = { Text("Dose button widget") }) },
        bottomBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(onClick = onCancel, modifier = Modifier.weight(1f)) {
                    Text("Cancel")
                }
                Button(
                    onClick = { onSave(selectedKeys.toSet()) },
                    enabled = selectedKeys.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Add widget")
                }
            }
        }
    ) { insets ->
        Column(
            modifier = Modifier.fillMaxSize().padding(insets).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Choose up to $maxButtons medication or group buttons. Tap a widget button to log the standard dose for that medication or all medications in its group.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 12.dp)
            )
            if (targets.isEmpty()) {
                Text("Add medications or groups before configuring a dose widget.")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(targets, key = { it.key }) { target ->
                        val checked = target.key in selectedKeys
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                enabled = checked || selectedKeys.size < maxButtons,
                                onCheckedChange = { isChecked ->
                                    if (isChecked) selectedKeys.add(target.key)
                                    else selectedKeys.remove(target.key)
                                }
                            )
                            Text(target.label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }
}
