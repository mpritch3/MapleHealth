package com.example.medtrackv1.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.medtrackv1.data.model.Medication

@Composable
fun MedicationGroupDialog(
    medications: List<Medication>,
    groupName: String?,
    existingGroupNames: Set<String>,
    onDismiss: () -> Unit,
    onSave: (String, Map<Long, Int>) -> Unit
) {
    var name by remember(groupName) { mutableStateOf(groupName.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    val selectedIds = remember(groupName, medications) {
        mutableStateListOf<Long>().apply {
            addAll(medications.filter { it.groupName == groupName && groupName != null }.map { it.id })
        }
    }
    val quantities = remember(groupName, medications) {
        mutableStateMapOf<Long, String>().apply {
            medications.forEach { put(it.id, it.groupDoseMultiplier.coerceAtLeast(1).toString()) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (groupName == null) "Create medication group" else "Edit $groupName") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = { Text("Group name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Text("Select medications and set quantity per group dose", style = MaterialTheme.typography.titleSmall)
                medications.forEach { medication ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = medication.id in selectedIds,
                                onCheckedChange = { checked ->
                                    if (checked) selectedIds.add(medication.id) else selectedIds.remove(medication.id)
                                    error = null
                                }
                            )
                            Text(
                                "${medication.name} · ${medication.doseAmount} ${medication.unit}",
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (medication.id in selectedIds) {
                            OutlinedTextField(
                                value = quantities[medication.id].orEmpty(),
                                onValueChange = { input ->
                                    if (input.all(Char::isDigit)) quantities[medication.id] = input
                                    error = null
                                },
                                label = { Text("Quantity when taking group") },
                                supportingText = { Text("1 logs one dose; 2 logs twice the dose") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.width(240.dp).align(Alignment.End)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val trimmed = name.trim()
                when {
                    trimmed.isEmpty() -> error = "Enter a group name"
                    selectedIds.isEmpty() -> error = "Select at least one medication"
                    selectedIds.any { (quantities[it]?.toIntOrNull() ?: 0) < 1 } ->
                        error = "Enter a quantity of at least 1 for each selected medication"
                    existingGroupNames.any { it.equals(trimmed, ignoreCase = true) && !it.equals(groupName, ignoreCase = true) } ->
                        error = "A group with this name already exists"
                    else -> onSave(trimmed, selectedIds.associateWith { quantities.getValue(it).toInt() })
                }
            }) { Text("Save group") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
