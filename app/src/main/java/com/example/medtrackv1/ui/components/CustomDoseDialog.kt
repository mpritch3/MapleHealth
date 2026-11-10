package com.example.medtrackv1.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.medtrackv1.data.model.Medication
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomDoseDialog(
    medications: List<Medication>,
    preselectedMedication: Medication? = null,
    onDismiss: () -> Unit,
    onLogDose: (medicationId: Long, doseAmount: Double, timestamp: Long) -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US) }

    var selectedMed by remember { mutableStateOf(preselectedMedication ?: medications.firstOrNull()) }
    var doseAmountStr by remember { mutableStateOf(selectedMed?.doseAmount?.toString() ?: "500") }
    var dateTimeStr by remember { mutableStateOf(dateFormat.format(Date())) }

    var dropdownExpanded by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Log Custom Dose") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                // Medication selector
                ExposedDropdownMenuBox(
                    expanded = dropdownExpanded,
                    onExpandedChange = { dropdownExpanded = !dropdownExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedMed?.let { "${it.name} (${it.doseAmount} ${it.unit})" } ?: "Select Medication",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Medication") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                        modifier = Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )

                    ExposedDropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false }
                    ) {
                        medications.forEach { med ->
                            DropdownMenuItem(
                                text = { Text("${med.name} (${med.doseAmount} ${med.unit})") },
                                onClick = {
                                    selectedMed = med
                                    doseAmountStr = med.doseAmount.toString()
                                    dropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = doseAmountStr,
                    onValueChange = { doseAmountStr = it },
                    label = { Text("Dose Amount (${selectedMed?.unit ?: "mg"})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = dateTimeStr,
                    onValueChange = { dateTimeStr = it },
                    label = { Text("Date & Time (yyyy-MM-dd HH:mm)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val med = selectedMed
                    if (med == null) {
                        errorMessage = "Please select a medication"
                        return@TextButton
                    }
                    val doseVal = doseAmountStr.toDoubleOrNull()
                    if (doseVal == null || doseVal <= 0.0) {
                        errorMessage = "Dose amount must be positive"
                        return@TextButton
                    }

                    val dateParsed = try {
                        dateFormat.parse(dateTimeStr.trim())
                    } catch (_: Exception) {
                        null
                    }

                    if (dateParsed == null) {
                        errorMessage = "Invalid date format. Use yyyy-MM-dd HH:mm"
                        return@TextButton
                    }

                    onLogDose(med.id, doseVal, dateParsed.time)
                }
            ) {
                Text("Log Dose")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
