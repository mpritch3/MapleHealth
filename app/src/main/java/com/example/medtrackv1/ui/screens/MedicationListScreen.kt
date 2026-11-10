package com.example.medtrackv1.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.medtrackv1.data.model.Medication
import com.example.medtrackv1.ui.components.CustomDoseDialog
import com.example.medtrackv1.ui.components.MedicationCard
import com.example.medtrackv1.ui.components.MedicationDialog
import com.example.medtrackv1.ui.components.MedicationGroupDialog
import com.example.medtrackv1.ui.viewmodel.MedicationViewModel

enum class MedicationListAction {
    ADD_MEDICATION,
    CREATE_GROUP
}

@Composable
fun MedicationListScreen(
    viewModel: MedicationViewModel,
    modifier: Modifier = Modifier,
    requestedAction: MedicationListAction? = null,
    onActionHandled: () -> Unit = {}
) {
    val medications by viewModel.medications.collectAsState()
    val groupedMedications = medications.filter { !it.groupName.isNullOrBlank() }.groupBy { it.groupName!! }

    var showAddEditDialog by remember { mutableStateOf(false) }
    var selectedMedicationForEdit by remember { mutableStateOf<Medication?>(null) }

    var showCustomDoseDialog by remember { mutableStateOf(false) }
    var selectedMedicationForDose by remember { mutableStateOf<Medication?>(null) }

    var medicationToDelete by remember { mutableStateOf<Medication?>(null) }
    var showGroupDialog by remember { mutableStateOf(false) }
    var groupToEdit by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(requestedAction) {
        when (requestedAction) {
            MedicationListAction.ADD_MEDICATION -> {
                selectedMedicationForEdit = null
                showAddEditDialog = true
                onActionHandled()
            }
            MedicationListAction.CREATE_GROUP -> {
                groupToEdit = null
                showGroupDialog = true
                onActionHandled()
            }
            null -> Unit
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (medications.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Medication,
                    contentDescription = null,
                    modifier = Modifier.padding(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "No medications added yet",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Open the menu to add your first medication",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp, top = 16.dp, start = 16.dp, end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(groupedMedications.entries.toList(), key = { "group-${it.key}" }) { entry ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(entry.key, style = MaterialTheme.typography.titleMedium)
                            Text(entry.value.joinToString { "${it.name} ×${it.groupDoseMultiplier}" }, style = MaterialTheme.typography.bodyMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { viewModel.takeGroupDose(entry.value) }) { Text("Take all") }
                                OutlinedButton(onClick = { groupToEdit = entry.key; showGroupDialog = true }) { Text("Edit group") }
                            }
                        }
                    }
                }
                itemsIndexed(items = medications, key = { _, medication -> medication.id }) { index, medication ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        MedicationCard(
                            medication = medication,
                            onTakeDoseNow = { viewModel.takeDoseNow(medication) },
                            onLogCustomDose = {
                                selectedMedicationForDose = medication
                                showCustomDoseDialog = true
                            },
                            onEdit = {
                                selectedMedicationForEdit = medication
                                showAddEditDialog = true
                            },
                            onDelete = { medicationToDelete = medication },
                            canMoveUp = index > 0,
                            canMoveDown = index < medications.lastIndex,
                            onMoveUp = { viewModel.moveMedication(medication.id, -1) },
                            onMoveDown = { viewModel.moveMedication(medication.id, 1) }
                        )
                    }
                }
            }
        }

    }

    if (showAddEditDialog) {
        MedicationDialog(
            medication = selectedMedicationForEdit,
            onDismiss = {
                showAddEditDialog = false
                selectedMedicationForEdit = null
            },
            onSave = { medication ->
                if (selectedMedicationForEdit == null) {
                    viewModel.addMedication(medication)
                } else {
                    viewModel.updateMedication(medication)
                }
                showAddEditDialog = false
                selectedMedicationForEdit = null
            }
        )
    }

    if (showGroupDialog) {
        MedicationGroupDialog(
            medications = medications,
            groupName = groupToEdit,
            existingGroupNames = groupedMedications.keys,
            onDismiss = { showGroupDialog = false; groupToEdit = null },
            onSave = { name, groupQuantities ->
                medications.forEach { medication ->
                    val belongsToEditedGroup = medication.groupName == groupToEdit
                    val selectedForGroup = medication.id in groupQuantities
                    val newGroupName = when {
                        selectedForGroup -> name
                        belongsToEditedGroup -> null
                        else -> medication.groupName
                    }
                    val newQuantity = when {
                        selectedForGroup -> groupQuantities.getValue(medication.id)
                        belongsToEditedGroup -> 1
                        else -> medication.groupDoseMultiplier
                    }
                    if (newGroupName != medication.groupName || newQuantity != medication.groupDoseMultiplier) {
                        viewModel.updateMedication(
                            medication.copy(groupName = newGroupName, groupDoseMultiplier = newQuantity)
                        )
                    }
                }
                showGroupDialog = false
                groupToEdit = null
            }
        )
    }

    if (showCustomDoseDialog) {
        CustomDoseDialog(
            medications = medications,
            preselectedMedication = selectedMedicationForDose,
            onDismiss = {
                showCustomDoseDialog = false
                selectedMedicationForDose = null
            },
            onLogDose = { medId, doseAmount, timestamp ->
                viewModel.logCustomDose(medId, doseAmount, timestamp)
                showCustomDoseDialog = false
                selectedMedicationForDose = null
            }
        )
    }

    if (medicationToDelete != null) {
        AlertDialog(
            onDismissRequest = { medicationToDelete = null },
            title = { Text("Delete Medication") },
            text = { Text("Are you sure you want to delete '${medicationToDelete?.name}'? All dose history for this medication will also be deleted.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        medicationToDelete?.let { viewModel.deleteMedication(it) }
                        medicationToDelete = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { medicationToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
