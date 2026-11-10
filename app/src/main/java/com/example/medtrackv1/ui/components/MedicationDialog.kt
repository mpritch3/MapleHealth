package com.example.medtrackv1.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.medtrackv1.data.model.Medication
import com.example.medtrackv1.data.model.NotificationMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationDialog(
    medication: Medication? = null,
    onDismiss: () -> Unit,
    onSave: (Medication) -> Unit
) {
    var name by remember { mutableStateOf(medication?.name ?: "") }
    var unit by remember { mutableStateOf(medication?.unit ?: "mg") }
    var doseAmount by remember { mutableStateOf(medication?.doseAmount?.toString() ?: "500") }
    var halfLifeMin by remember { mutableStateOf((medication?.halfLifeMinHours ?: medication?.halfLifeHours ?: 4.0).toString()) }
    var halfLifeMax by remember { mutableStateOf((medication?.halfLifeMaxHours ?: medication?.halfLifeHours ?: 4.0).toString()) }
    var tmaxMin by remember { mutableStateOf((medication?.tmaxMinHours ?: medication?.absorptionTimeHours ?: 0.5).toString()) }
    var tmaxMax by remember { mutableStateOf((medication?.tmaxMaxHours ?: medication?.absorptionTimeHours ?: 0.5).toString()) }
    var notificationMode by remember { mutableStateOf(medication?.notificationMode ?: NotificationMode.TIME_BASED) }

    val scheduledTimes = remember(medication?.id) {
        mutableStateListOf<String>().apply {
            val existing = medication?.scheduledTimes
                ?.split(',')
                ?.map(String::trim)
                ?.filter(String::isNotEmpty)
            if (!existing.isNullOrEmpty()) addAll(existing)
            else add(medication?.scheduledTime ?: "08:00")
        }
    }
    var targetLowConc by remember { mutableStateOf(medication?.targetLowConcentration?.toString() ?: "5.0") }
    var targetAvgConc by remember { mutableStateOf(medication?.targetAvgConcentration?.toString() ?: "15.0") }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var dropdownExpanded by remember { mutableStateOf(false) }
    var infoDefinition by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = if (medication == null) "Add Medication" else "Edit Medication") },
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

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Medication Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = doseAmount,
                        onValueChange = { doseAmount = it },
                        label = { Text("Dose Amount") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it },
                        label = { Text("Unit (mg, mcg)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                ParameterRangeHeader(
                    subscript = "½",
                    definition = "Elimination half-life (t½) is the time it takes for the amount of medication in the body to decrease by half.",
                    onInfoClick = { infoDefinition = it }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = halfLifeMin,
                        onValueChange = { halfLifeMin = it },
                        label = { Text("Min (hrs)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = halfLifeMax,
                        onValueChange = { halfLifeMax = it },
                        label = { Text("Max (hrs)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                ParameterRangeHeader(
                    subscript = "max",
                    definition = "Tmax is the time after a dose when the medication reaches its maximum concentration in plasma.",
                    onInfoClick = { infoDefinition = it }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = tmaxMin,
                        onValueChange = { tmaxMin = it },
                        label = { Text("Min (hrs)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = tmaxMax,
                        onValueChange = { tmaxMax = it },
                        label = { Text("Max (hrs)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Text(
                    "PK calculations use the midpoint of each range.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                ExposedDropdownMenuBox(
                    expanded = dropdownExpanded,
                    onExpandedChange = { dropdownExpanded = !dropdownExpanded }
                ) {
                    OutlinedTextField(
                        value = when (notificationMode) {
                            NotificationMode.TIME_BASED -> "Time-based Notification"
                            NotificationMode.LOW_CONCENTRATION -> "Low Concentration Alert"
                            NotificationMode.AVERAGE_CONCENTRATION -> "Target Avg Concentration"
                        },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Notification Mode") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                        modifier = Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )

                    ExposedDropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Time-based Notification") },
                            onClick = {
                                notificationMode = NotificationMode.TIME_BASED
                                dropdownExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Low Concentration Alert") },
                            onClick = {
                                notificationMode = NotificationMode.LOW_CONCENTRATION
                                dropdownExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Target Avg Concentration") },
                            onClick = {
                                notificationMode = NotificationMode.AVERAGE_CONCENTRATION
                                dropdownExpanded = false
                            }
                        )
                    }
                }

                when (notificationMode) {
                    NotificationMode.TIME_BASED -> {
                        Text("Daily reminder times (24-hour HH:mm)", style = MaterialTheme.typography.labelLarge)
                        scheduledTimes.forEachIndexed { index, time ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = time,
                                    onValueChange = { scheduledTimes[index] = it },
                                    label = { Text("Reminder " + (index + 1)) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                if (scheduledTimes.size > 1) {
                                    TextButton(onClick = { scheduledTimes.removeAt(index) }) {
                                        Text("Remove")
                                    }
                                }
                            }
                        }
                        TextButton(
                            onClick = { scheduledTimes.add("20:00") },
                            enabled = scheduledTimes.size < 10
                        ) {
                            Text("Add another reminder")
                        }
                    }
                    NotificationMode.LOW_CONCENTRATION -> {
                        OutlinedTextField(
                            value = targetLowConc,
                            onValueChange = { targetLowConc = it },
                            label = { Text("Low Concentration Threshold") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    NotificationMode.AVERAGE_CONCENTRATION -> {
                        OutlinedTextField(
                            value = targetAvgConc,
                            onValueChange = { targetAvgConc = it },
                            label = { Text("Target Avg Concentration") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val nameTrimmed = name.trim()
                    if (nameTrimmed.isEmpty()) {
                        errorMessage = "Please enter medication name"
                        return@TextButton
                    }
                    val doseVal = doseAmount.toDoubleOrNull()
                    if (doseVal == null || doseVal <= 0.0) {
                        errorMessage = "Dose amount must be a positive number"
                        return@TextButton
                    }
                    val hlMinVal = halfLifeMin.toDoubleOrNull()
                    val hlMaxVal = halfLifeMax.toDoubleOrNull()
                    if (hlMinVal == null || hlMaxVal == null || !hlMinVal.isFinite() || !hlMaxVal.isFinite() || hlMinVal <= 0.0 || hlMaxVal <= 0.0 || hlMinVal > hlMaxVal) {
                        errorMessage = "Enter a valid half-life range; minimum must be positive and no greater than maximum"
                        return@TextButton
                    }
                    val tmaxMinVal = tmaxMin.toDoubleOrNull()
                    val tmaxMaxVal = tmaxMax.toDoubleOrNull()
                    if (tmaxMinVal == null || tmaxMaxVal == null || !tmaxMinVal.isFinite() || !tmaxMaxVal.isFinite() || tmaxMinVal < 0.0 || tmaxMaxVal < 0.0 || tmaxMinVal > tmaxMaxVal) {
                        errorMessage = "Enter a valid Tmax range; minimum must be 0 or greater and no greater than maximum"
                        return@TextButton
                    }
                    val hlVal = hlMinVal + (hlMaxVal - hlMinVal) / 2.0
                    val tmaxVal = tmaxMinVal + (tmaxMaxVal - tmaxMinVal) / 2.0

                    var schedTime: String? = null
                    var schedTimes: String? = null
                    var lowConc: Double? = null
                    var avgConc: Double? = null

                    when (notificationMode) {
                        NotificationMode.TIME_BASED -> {
                            val normalizedTimes = scheduledTimes.map { value ->
                                runCatching { java.time.LocalTime.parse(value.trim()).toString() }.getOrNull()
                            }
                            if (normalizedTimes.any { it == null }) {
                                errorMessage = "Enter each reminder as a valid 24-hour time, such as 08:00"
                                return@TextButton
                            }
                            if (normalizedTimes.filterNotNull().distinct().size != normalizedTimes.size) {
                                errorMessage = "Each daily reminder time must be unique"
                                return@TextButton
                            }
                            val sortedTimes = normalizedTimes.filterNotNull().sortedBy(java.time.LocalTime::parse)
                            schedTimes = sortedTimes.joinToString(",")
                            schedTime = sortedTimes.first()
                        }
                        NotificationMode.LOW_CONCENTRATION -> {
                            lowConc = targetLowConc.toDoubleOrNull() ?: 5.0
                        }
                        NotificationMode.AVERAGE_CONCENTRATION -> {
                            avgConc = targetAvgConc.toDoubleOrNull() ?: 15.0
                        }
                    }

                    val updatedMedication = (medication ?: Medication(
                        name = nameTrimmed,
                        unit = unit.trim().ifEmpty { "mg" },
                        doseAmount = doseVal,
                        halfLifeHours = hlVal,
                        absorptionTimeHours = tmaxVal,
                        halfLifeMinHours = hlMinVal,
                        halfLifeMaxHours = hlMaxVal,
                        tmaxMinHours = tmaxMinVal,
                        tmaxMaxHours = tmaxMaxVal,
                        notificationMode = notificationMode,
                        scheduledTime = schedTime,
                        scheduledTimes = schedTimes,
                        targetLowConcentration = lowConc,
                        targetAvgConcentration = avgConc
                    )).copy(
                        name = nameTrimmed,
                        unit = unit.trim().ifEmpty { "mg" },
                        doseAmount = doseVal,
                        halfLifeHours = hlVal,
                        absorptionTimeHours = tmaxVal,
                        halfLifeMinHours = hlMinVal,
                        halfLifeMaxHours = hlMaxVal,
                        tmaxMinHours = tmaxMinVal,
                        tmaxMaxHours = tmaxMaxVal,
                        notificationMode = notificationMode,
                        scheduledTime = schedTime,
                        scheduledTimes = schedTimes,
                        targetLowConcentration = lowConc,
                        targetAvgConcentration = avgConc
                    )

                    onSave(updatedMedication)
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )

    infoDefinition?.let { definition ->
        AlertDialog(
            onDismissRequest = { infoDefinition = null },
            title = { Text("About this value") },
            text = { Text(definition) },
            confirmButton = { TextButton(onClick = { infoDefinition = null }) { Text("OK") } }
        )
    }
}

@Composable
private fun ParameterRangeHeader(
    subscript: String,
    definition: String,
    onInfoClick: (String) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = buildAnnotatedString {
                append("t")
                withStyle(SpanStyle(baselineShift = BaselineShift.Subscript)) {
                    append(subscript)
                }
            },
            style = MaterialTheme.typography.titleSmall
        )
        TextButton(onClick = { onInfoClick(definition) }) {
            Text("(i)", style = MaterialTheme.typography.labelSmall)
        }
    }
}
