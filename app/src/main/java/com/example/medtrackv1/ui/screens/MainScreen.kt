package com.example.medtrackv1.ui.screens

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.example.medtrackv1.notification.MedicationReminderScheduler
import com.example.medtrackv1.widget.DoseIntakeWidgetProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.example.medtrackv1.ui.viewmodel.MedicationViewModel

enum class NavTab(val title: String) {
    MEDICATIONS("Medications"),
    INSIGHTS("Concentration"),
    TRACKERS("Trackers"),
    HISTORY("History"),
    BACKUP("Backup")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MedicationViewModel
) {
    var selectedTabItem by remember { mutableIntStateOf(0) }
    var menuExpanded by remember { mutableStateOf(false) }
    var requestedMedicationAction by remember { mutableStateOf<MedicationListAction?>(null) }
    var requestedAddTracker by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val userMessage by viewModel.userMessage.collectAsState()
    val context = LocalContext.current
    val medications by viewModel.medications.collectAsState()
    val doseLogs by viewModel.doseLogs.collectAsState()

    LaunchedEffect(medications, doseLogs) {
        DoseIntakeWidgetProvider.updateAll(context)
        medications.forEach { medication ->
            MedicationReminderScheduler.schedule(context, medication, doseLogs.filter { it.medicationId == medication.id })
        }
    }

    LaunchedEffect(userMessage) {
        userMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearUserMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (selectedTabItem) {
                            0 -> NavTab.MEDICATIONS.title
                            1 -> NavTab.INSIGHTS.title
                            2 -> NavTab.TRACKERS.title
                            3 -> NavTab.HISTORY.title
                            else -> NavTab.BACKUP.title
                        },
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.Menu, contentDescription = "Open menu")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Add medication") },
                            onClick = {
                                menuExpanded = false
                                selectedTabItem = 0
                                requestedMedicationAction = MedicationListAction.ADD_MEDICATION
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Add tracker") },
                            onClick = {
                                menuExpanded = false
                                selectedTabItem = 2
                                requestedAddTracker = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Create medication group") },
                            enabled = medications.isNotEmpty(),
                            onClick = {
                                menuExpanded = false
                                selectedTabItem = 0
                                requestedMedicationAction = MedicationListAction.CREATE_GROUP
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Add dose widget") },
                            onClick = {
                                menuExpanded = false
                                val manager = AppWidgetManager.getInstance(context)
                                if (manager.isRequestPinAppWidgetSupported) {
                                    manager.requestPinAppWidget(
                                        ComponentName(context, DoseIntakeWidgetProvider::class.java),
                                        null,
                                        null
                                    )
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Add the dose widget from your home screen's widget picker.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Backup") },
                            onClick = {
                                menuExpanded = false
                                selectedTabItem = 4
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTabItem == 0,
                    onClick = { selectedTabItem = 0 },
                    icon = { Icon(Icons.Default.Medication, contentDescription = "Medications") },
                    label = { Text("Medications") }
                )
                NavigationBarItem(
                    selected = selectedTabItem == 1,
                    onClick = { selectedTabItem = 1 },
                    icon = { Icon(Icons.Default.Insights, contentDescription = "Concentration") },
                    label = { Text("Levels") }
                )
                NavigationBarItem(
                    selected = selectedTabItem == 2,
                    onClick = { selectedTabItem = 2 },
                    icon = { Icon(Icons.Default.AccessTime, contentDescription = "Trackers") },
                    label = { Text("Trackers") }
                )
                NavigationBarItem(
                    selected = selectedTabItem == 3,
                    onClick = { selectedTabItem = 3 },
                    icon = { Icon(Icons.Default.History, contentDescription = "History") },
                    label = { Text("History") }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        val modifier = Modifier.padding(innerPadding)
        when (selectedTabItem) {
            0 -> MedicationListScreen(
                viewModel = viewModel,
                modifier = modifier,
                requestedAction = requestedMedicationAction,
                onActionHandled = { requestedMedicationAction = null }
            )
            1 -> ConcentrationScreen(viewModel = viewModel, modifier = modifier)
            2 -> CountersScreen(
                viewModel = viewModel,
                modifier = modifier,
                requestedAddCounter = requestedAddTracker,
                onTrackerActionHandled = { requestedAddTracker = false }
            )
            3 -> DoseHistoryScreen(viewModel = viewModel, modifier = modifier)
            4 -> SettingsBackupScreen(viewModel = viewModel, modifier = modifier)
        }
    }
}
