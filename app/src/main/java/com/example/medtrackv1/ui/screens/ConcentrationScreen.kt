package com.example.medtrackv1.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.medtrackv1.data.model.DoseLog
import com.example.medtrackv1.data.model.Medication
import com.example.medtrackv1.data.model.NotificationMode
import com.example.medtrackv1.domain.pk.PkEngine
import com.example.medtrackv1.ui.components.MedicationOrderButtons
import com.example.medtrackv1.ui.viewmodel.MedicationViewModel
import java.text.DateFormat
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.Date
import kotlinx.coroutines.delay

private enum class PlotPeriod {
    AROUND_NOW,
    TODAY,
    THIS_WEEK,
    CUSTOM
}

@Composable
fun ConcentrationScreen(viewModel: MedicationViewModel, modifier: Modifier = Modifier) {
    val medications by viewModel.medications.collectAsState()
    val doseLogs by viewModel.doseLogs.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }


    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            now = System.currentTimeMillis()
        }
    }

    if (medications.isEmpty()) {
        Column(modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
            Text("Add a medication and log a dose to see its concentration curve.")
        }
        return
    }

    Column(modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(medications, key = { _, medication -> medication.id }) { index, medication ->
                ConcentrationCard(
                    medication = medication,
                    logs = doseLogs.filter { it.medicationId == medication.id },
                    now = now,
                    medicationIndex = index,
                    medicationCount = medications.size,
                    onMoveUp = { viewModel.moveMedication(medication.id, -1) },
                    onMoveDown = { viewModel.moveMedication(medication.id, 1) }
                )
            }
        }
    }
}

@Composable
private fun PlotRangeControls(
    period: PlotPeriod,
    centeredWindowHours: Int,
    onWindowSelected: (Int) -> Unit,
    customHoursBack: String,
    customHoursAhead: String,
    onCustomHoursBackChange: (String) -> Unit,
    onCustomHoursAheadChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (period == PlotPeriod.AROUND_NOW) {
            Text("Window centered on now", style = MaterialTheme.typography.labelMedium)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(1, 8, 24).forEach { hours ->
                    FilterChip(
                        selected = centeredWindowHours == hours,
                        onClick = { onWindowSelected(hours) },
                        label = { Text("$hours h") }
                    )
                }
            }
        } else if (period == PlotPeriod.CUSTOM) {
            Text(
                "Set how many hours to show before and after now (0–720).",
                style = MaterialTheme.typography.labelMedium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = customHoursBack,
                    onValueChange = onCustomHoursBackChange,
                    label = { Text("Hours back") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = customHoursAhead,
                    onValueChange = onCustomHoursAheadChange,
                    label = { Text("Hours ahead") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            val back = customHoursBack.toPlotHoursOrNull()
            val ahead = customHoursAhead.toPlotHoursOrNull()
            if (back == null || ahead == null || (back == 0.0 && ahead == 0.0)) {
                Text(
                    "Enter values from 0 to 720; at least one must be greater than 0.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun ConcentrationCard(
    medication: Medication,
    logs: List<DoseLog>,
    now: Long,
    medicationIndex: Int,
    medicationCount: Int,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    val context = LocalContext.current
    val preferences = remember {
        context.getSharedPreferences("graph_timeline_preferences", Context.MODE_PRIVATE)
    }
    var period by remember(medication.id) {
        mutableStateOf(
            runCatching {
                PlotPeriod.valueOf(preferences.getString("period_${medication.id}", null) ?: "AROUND_NOW")
            }.getOrDefault(PlotPeriod.AROUND_NOW)
        )
    }
    var centeredWindowHours by remember(medication.id) {
        mutableIntStateOf(preferences.getInt("centered_hours_${medication.id}", 8))
    }
    var customHoursBack by remember(medication.id) {
        mutableStateOf(preferences.getString("hours_back_${medication.id}", "8") ?: "8")
    }
    var customHoursAhead by remember(medication.id) {
        mutableStateOf(preferences.getString("hours_ahead_${medication.id}", "8") ?: "8")
    }
    var periodMenuExpanded by remember(medication.id) { mutableStateOf(false) }
    val selectPeriod: (PlotPeriod) -> Unit = { selected ->
        period = selected
        preferences.edit().putString("period_${medication.id}", selected.name).apply()
        periodMenuExpanded = false
    }
    val effectiveHoursBack = customHoursBack.toPlotHoursOrDefault()
    val effectiveHoursAhead = customHoursAhead.toPlotHoursOrDefault()
    val (startTime, endTime) = plotBounds(
        period = period,
        now = now,
        centeredWindowHours = centeredWindowHours,
        customHoursBack = effectiveHoursBack,
        customHoursAhead = effectiveHoursAhead
    )
    val pointsCount = when (period) {
        PlotPeriod.AROUND_NOW -> 100
        PlotPeriod.CUSTOM -> ((effectiveHoursBack + effectiveHoursAhead) * 4.0).toInt().coerceIn(100, 500)
        PlotPeriod.TODAY -> 144
        PlotPeriod.THIS_WEEK -> 168
    }
    val curve = PkEngine.calculateConcentrationCurve(
        medication = medication,
        doseLogs = logs,
        startTime = startTime,
        endTime = endTime,
        pointsCount = pointsCount
    )
    val current = PkEngine.calculateConcentrationAt(medication, logs, now)
    val nowFraction = when (period) {
        PlotPeriod.AROUND_NOW -> 0.5
        PlotPeriod.CUSTOM -> {
            val span = effectiveHoursBack + effectiveHoursAhead
            if (span > 0.0) effectiveHoursBack / span else 0.5
        }
        PlotPeriod.TODAY, PlotPeriod.THIS_WEEK -> 1.0
    }
    val referenceLevel = when (medication.notificationMode) {
        NotificationMode.LOW_CONCENTRATION -> medication.targetLowConcentration
        NotificationMode.AVERAGE_CONCENTRATION -> medication.targetAvgConcentration
        NotificationMode.TIME_BASED -> null
    }?.takeIf { it.isFinite() && it > 0.0 }
    val referenceLabel = when (medication.notificationMode) {
        NotificationMode.LOW_CONCENTRATION -> "Low concentration alert"
        NotificationMode.AVERAGE_CONCENTRATION -> "Target average concentration"
        NotificationMode.TIME_BASED -> null
    }
    val curveMaximum = curve.maxOfOrNull { it.concentration } ?: 0.0
    val maxValue = maxOf(curveMaximum, referenceLevel ?: 0.0).coerceAtLeast(0.0001)
    val axisColor = MaterialTheme.colorScheme.outlineVariant
    val lineColor = MaterialTheme.colorScheme.primary
    val referenceColor = MaterialTheme.colorScheme.tertiary
    val nowColor = MaterialTheme.colorScheme.secondary

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text(medication.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                MedicationOrderButtons(
                    canMoveUp = medicationIndex > 0,
                    canMoveDown = medicationIndex < medicationCount - 1,
                    onMoveUp = onMoveUp,
                    onMoveDown = onMoveDown
                )
                androidx.compose.foundation.layout.Box {
                    IconButton(onClick = { periodMenuExpanded = true }) {
                        Icon(Icons.Default.Menu, contentDescription = "Graph period menu for ${medication.name}")
                    }
                    DropdownMenu(
                        expanded = periodMenuExpanded,
                        onDismissRequest = { periodMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(if (period == PlotPeriod.AROUND_NOW) "✓ Around now" else "Around now") },
                            onClick = { selectPeriod(PlotPeriod.AROUND_NOW) }
                        )
                        DropdownMenuItem(
                            text = { Text(if (period == PlotPeriod.TODAY) "✓ Today" else "Today") },
                            onClick = { selectPeriod(PlotPeriod.TODAY) }
                        )
                        DropdownMenuItem(
                            text = { Text(if (period == PlotPeriod.THIS_WEEK) "✓ This week" else "This week") },
                            onClick = { selectPeriod(PlotPeriod.THIS_WEEK) }
                        )
                        DropdownMenuItem(
                            text = { Text(if (period == PlotPeriod.CUSTOM) "✓ Custom" else "Custom") },
                            onClick = { selectPeriod(PlotPeriod.CUSTOM) }
                        )
                    }
                }
            }
            PlotRangeControls(
                period = period,
                centeredWindowHours = centeredWindowHours,
                onWindowSelected = {
                    centeredWindowHours = it
                    preferences.edit().putInt("centered_hours_${medication.id}", it).apply()
                },
                customHoursBack = customHoursBack,
                customHoursAhead = customHoursAhead,
                onCustomHoursBackChange = {
                    customHoursBack = it
                    preferences.edit().putString("hours_back_${medication.id}", it).apply()
                },
                onCustomHoursAheadChange = {
                    customHoursAhead = it
                    preferences.edit().putString("hours_ahead_${medication.id}", it).apply()
                }
            )
            Text(
                "Estimated now: %.2f %s".format(current, medication.unit),
                style = MaterialTheme.typography.bodyLarge
            )
            Canvas(Modifier.fillMaxWidth().height(150.dp)) {
                val left = 12f
                val top = 8f
                val plotWidth = size.width - left - 8f
                val plotHeight = size.height - top - 20f
                drawLine(
                    axisColor,
                    Offset(left, top + plotHeight),
                    Offset(left + plotWidth, top + plotHeight),
                    1.dp.toPx()
                )
                drawLine(
                    axisColor,
                    Offset(left, top),
                    Offset(left, top + plotHeight),
                    1.dp.toPx()
                )
                if (curve.size > 1) {
                    val path = Path()
                    curve.forEachIndexed { index, point ->
                        val x = left + plotWidth * index / curve.lastIndex
                        val y = top + plotHeight * (1.0 - point.concentration / maxValue).toFloat()
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(path, lineColor, style = Stroke(width = 3.dp.toPx()))
                }
                val nowX = left + plotWidth * nowFraction.toFloat()
                drawLine(
                    color = nowColor,
                    start = Offset(nowX, top),
                    end = Offset(nowX, top + plotHeight),
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(5.dp.toPx(), 4.dp.toPx())
                    )
                )
                referenceLevel?.let { level ->
                    val y = top + plotHeight * (1.0 - level / maxValue).toFloat()
                    drawLine(
                        color = referenceColor,
                        start = Offset(left, y),
                        end = Offset(left + plotWidth, y),
                        strokeWidth = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(8.dp.toPx(), 5.dp.toPx())
                        )
                    )
                }
            }
            if (referenceLevel != null && referenceLabel != null) {
                Text(
                    text = "$referenceLabel: %.2f %s".format(referenceLevel, medication.unit),
                    color = referenceColor,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            BoxWithConstraints(Modifier.fillMaxWidth().height(36.dp)) {
                if (period != PlotPeriod.CUSTOM || effectiveHoursBack > 0.0) {
                    Text(
                        formatPlotLabel(startTime, period, effectiveHoursBack + effectiveHoursAhead),
                        modifier = Modifier.align(Alignment.BottomStart),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                if ((period == PlotPeriod.AROUND_NOW) ||
                    (period == PlotPeriod.CUSTOM && effectiveHoursAhead > 0.0)
                ) {
                    Text(
                        formatPlotLabel(endTime, period, effectiveHoursBack + effectiveHoursAhead),
                        modifier = Modifier.align(Alignment.BottomEnd),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                val plotWidth = (maxWidth - 20.dp).coerceAtLeast(0.dp)
                val nowLabelOffset = (12.dp + plotWidth * nowFraction.toFloat() - 18.dp)
                    .coerceIn(0.dp, (maxWidth - 36.dp).coerceAtLeast(0.dp))
                Text(
                    "Now",
                    modifier = Modifier.align(Alignment.TopStart).offset(x = nowLabelOffset),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

private fun plotBounds(
    period: PlotPeriod,
    now: Long,
    centeredWindowHours: Int,
    customHoursBack: Double,
    customHoursAhead: Double
): Pair<Long, Long> {
    val zone = ZoneId.systemDefault()
    val currentDateTime = Instant.ofEpochMilli(now).atZone(zone)
    val bounds = when (period) {
        PlotPeriod.AROUND_NOW -> {
            val windowMillis = centeredWindowHours * 60L * 60L * 1000L
            now - windowMillis / 2L to now + windowMillis / 2L
        }
        PlotPeriod.CUSTOM -> {
            val start = now - (customHoursBack * 3_600_000.0).toLong()
            val end = now + (customHoursAhead * 3_600_000.0).toLong()
            start to end
        }
        PlotPeriod.TODAY -> {
            currentDateTime.toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli() to now
        }
        PlotPeriod.THIS_WEEK -> {
            val monday = currentDateTime.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            monday.atStartOfDay(zone).toInstant().toEpochMilli() to now
        }
    }
    return if (bounds.second > bounds.first) bounds else bounds.first to bounds.first + 1L
}

private fun formatPlotLabel(timestamp: Long, period: PlotPeriod, customSpanHours: Double): String {
    val date = Date(timestamp)
    val includeDate = period == PlotPeriod.THIS_WEEK ||
        (period == PlotPeriod.CUSTOM && customSpanHours >= 24.0)
    return if (includeDate) {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(date)
    } else {
        DateFormat.getTimeInstance(DateFormat.SHORT).format(date)
    }
}

private fun String.toPlotHoursOrNull(): Double? =
    toDoubleOrNull()?.takeIf { it.isFinite() && it in 0.0..720.0 }

private fun String.toPlotHoursOrDefault(): Double = toPlotHoursOrNull() ?: 8.0
