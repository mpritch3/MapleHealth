package com.example.medtrackv1.data.util

import com.example.medtrackv1.data.model.Counter
import com.example.medtrackv1.data.model.CounterConcentrationSnapshot
import com.example.medtrackv1.data.model.CounterConcentrationSnapshotList
import com.example.medtrackv1.data.model.CounterHit
import com.example.medtrackv1.data.model.DoseLog
import com.example.medtrackv1.data.model.Medication
import com.example.medtrackv1.data.model.NotificationMode
import com.example.medtrackv1.domain.pk.PkEngine
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class BackupData(
    val medications: List<Medication>,
    val doseLogs: List<DoseLog>
)

data class ImportedCounterHit(
    val counterName: String,
    val timestamp: Long,
    val elapsedMillis: Long,
    val snapshots: List<CounterConcentrationSnapshot>
)

object DataExporterImporter {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    private val moshi: Moshi by lazy {
        Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    }

    private val backupAdapter by lazy { moshi.adapter(BackupData::class.java) }
    private val counterSnapshotsAdapter by lazy { moshi.adapter(CounterConcentrationSnapshotList::class.java) }

    fun encodeCounterConcentrationSnapshots(snapshots: List<CounterConcentrationSnapshot>): String =
        counterSnapshotsAdapter.toJson(CounterConcentrationSnapshotList(snapshots))

    fun decodeCounterConcentrationSnapshots(json: String): List<CounterConcentrationSnapshot> =
        runCatching { counterSnapshotsAdapter.fromJson(json)?.medications.orEmpty() }.getOrDefault(emptyList())

    fun exportCounterCsv(counters: List<Counter>, hits: List<CounterHit>): String {
        val sortedHits = hits.sortedWith(compareBy<CounterHit> { it.timestamp }.thenBy { it.id })
        val counterNames = counters.associate { it.id to it.name }
        val snapshotsByHit = sortedHits.associate { it.id to decodeCounterConcentrationSnapshots(it.concentrationsJson) }
        val medicationsById = linkedMapOf<Long, CounterConcentrationSnapshot>()
        sortedHits.forEach { hit -> snapshotsByHit[hit.id].orEmpty().forEach { medicationsById.putIfAbsent(it.medicationId, it) } }
        val duplicateNames = medicationsById.values.groupingBy { it.medicationName }.eachCount()
        val columns = medicationsById.values.map { med ->
            val uniqueName = if ((duplicateNames[med.medicationName] ?: 0) > 1) "${med.medicationName} #${med.medicationId}" else med.medicationName
            "$uniqueName Concentration (${med.unit})"
        }
        val output = StringBuilder()
        output.append((listOf("Tracker", "Reset Date/Time", "Timer Total (duration)") + columns).joinToString(",") { csvField(it) }).append('\n')
        sortedHits.forEach { hit ->
            val current = snapshotsByHit[hit.id].orEmpty().associateBy { it.medicationId }
            val row = mutableListOf(hit.counterName.ifBlank { counterNames[hit.counterId].orEmpty() }, dateFormat.format(Date(hit.timestamp)), formatTimerTotal(hit.elapsedMillis))
            medicationsById.keys.forEach { id ->
                val value = current[id]?.concentration
                row += if (value == null || !value.isFinite()) "NA" else String.format(Locale.US, "%.4f", value)
            }
            output.append(row.joinToString(",") { csvField(it) }).append('\n')
        }
        return output.toString()
    }

    fun importCounterCsv(csv: String): List<ImportedCounterHit> {
        val rows = parseCsv(csv)
        if (rows.isEmpty()) return emptyList()
        val headers = rows.first().map(::normalizeHeader)
        fun index(vararg aliases: String) = headers.indexOfFirst { it in aliases.map(::normalizeHeader) }
        val nameIndex = index("Tracker", "Counter", "Tracker Name", "Counter Name")
        val timeIndex = index("Reset Date/Time", "Hit Date/Time", "Timestamp", "Date/Time")
        val timerIndex = index("Timer Total (HH:MM:SS)", "Timer Total", "Elapsed")
        if (nameIndex < 0 || timeIndex < 0) return emptyList()
        return rows.drop(1).mapNotNull { row ->
            val name = row.getOrNull(nameIndex)?.trim().orEmpty()
            val timestamp = row.getOrNull(timeIndex)?.trim()?.let { runCatching { dateFormat.parse(it)?.time }.getOrNull() }
            if (name.isEmpty() || timestamp == null) return@mapNotNull null
            val snapshots = headers.mapIndexedNotNull { column, header ->
                val match = Regex("^(.+?)\\s+Concentration\\s*\\((.*?)\\)$", RegexOption.IGNORE_CASE).matchEntire(header) ?: return@mapIndexedNotNull null
                if (column >= row.size) return@mapIndexedNotNull null
                val rawName = match.groupValues[1].trim().replace(Regex("\\s+#-?\\d+$"), "")
                val unit = match.groupValues[2].trim()
                val rawValue = row[column].trim()
                val value = rawValue.takeUnless { it.equals("NA", true) || it.isEmpty() }?.toDoubleOrNull()?.takeIf { it.isFinite() }
                CounterConcentrationSnapshot(0L, rawName, unit, value)
            }
            ImportedCounterHit(name, timestamp, parseTimer(row.getOrNull(timerIndex).orEmpty()), snapshots)
        }
    }

    fun exportMedicationCsv(medications: List<Medication>): String {
        val header = listOf("Name", "Unit", "Dose Amount", "Half-life Min Hours", "Half-life Max Hours", "Tmax Min Hours", "Tmax Max Hours", "Notification Mode", "Scheduled Times", "Low Concentration", "Average Concentration", "Group", "Sort Order", "Group Dose Quantity")
        val output = StringBuilder(header.joinToString(",") { csvField(it) }).append('\n')
        medications.sortedWith(compareBy<Medication> { it.sortOrder }.thenBy { it.name.lowercase(Locale.ROOT) }).forEach { med ->
            val values = listOf(
                med.name, med.unit, med.doseAmount.toString(),
                (med.halfLifeMinHours ?: med.halfLifeHours).toString(),
                (med.halfLifeMaxHours ?: med.halfLifeHours).toString(),
                (med.tmaxMinHours ?: med.absorptionTimeHours).toString(),
                (med.tmaxMaxHours ?: med.absorptionTimeHours).toString(),
                med.notificationMode.name, med.scheduledTimes ?: med.scheduledTime.orEmpty(),
                med.targetLowConcentration?.toString().orEmpty(), med.targetAvgConcentration?.toString().orEmpty(),
                med.groupName.orEmpty(), med.sortOrder.toString(), med.groupDoseMultiplier.toString()
            )
            output.append(values.joinToString(",") { csvField(it) }).append('\n')
        }
        return output.toString()
    }

    fun importMedicationCsv(csv: String): List<Medication> {
        val rows = parseCsv(csv)
        if (rows.isEmpty()) return emptyList()
        val headers = rows.first().map(::normalizeHeader)
        fun value(row: List<String>, vararg keys: String): String? {
            val i = headers.indexOfFirst { it in keys.map(::normalizeHeader) }
            return row.getOrNull(i)?.trim()?.takeIf(String::isNotEmpty)
        }
        return rows.drop(1).mapNotNull { row ->
            val name = value(row, "Name", "Medication Name", "Medication") ?: return@mapNotNull null
            val dose = value(row, "Dose Amount", "Dose")?.toDoubleOrNull() ?: return@mapNotNull null
            val halfMin = value(row, "Half-life Min Hours", "Half Life Min Hours", "Half-life Hours", "Half Life Hours")?.toDoubleOrNull() ?: return@mapNotNull null
            val halfMax = value(row, "Half-life Max Hours", "Half Life Max Hours")?.toDoubleOrNull() ?: halfMin
            val tmaxMin = value(row, "Tmax Min Hours", "Tmax Hours", "Absorption Time Hours")?.toDoubleOrNull() ?: return@mapNotNull null
            val tmaxMax = value(row, "Tmax Max Hours")?.toDoubleOrNull() ?: tmaxMin
            if (dose <= 0 || halfMin <= 0 || halfMax <= 0 || tmaxMin < 0 || tmaxMax < 0) return@mapNotNull null
            val schedule = value(row, "Scheduled Times", "Scheduled Time")
            Medication(
                name = name, unit = value(row, "Unit") ?: "mg", doseAmount = dose,
                halfLifeHours = (halfMin + halfMax) / 2.0,
                absorptionTimeHours = (tmaxMin + tmaxMax) / 2.0,
                notificationMode = runCatching { NotificationMode.valueOf(value(row, "Notification Mode") ?: "TIME_BASED") }.getOrDefault(NotificationMode.TIME_BASED),
                scheduledTime = schedule?.substringBefore(','),
                scheduledTimes = schedule,
                targetLowConcentration = value(row, "Low Concentration", "Target Low Concentration")?.toDoubleOrNull(),
                targetAvgConcentration = value(row, "Average Concentration", "Target Average Concentration")?.toDoubleOrNull(),
                groupName = value(row, "Group"),
                halfLifeMinHours = halfMin, halfLifeMaxHours = halfMax,
                tmaxMinHours = tmaxMin, tmaxMaxHours = tmaxMax,
                sortOrder = value(row, "Sort Order")?.toIntOrNull() ?: 0,
                groupDoseMultiplier = (value(row, "Group Dose Quantity", "Group Dose Multiplier")?.toIntOrNull() ?: 1).coerceAtLeast(1)
            )
        }
    }

    private fun normalizeHeader(value: String) = value.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
    private fun parseTimer(value: String): Long = DurationFormatter.parse(value)

    private fun parseCsv(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                quoted && c == '"' && i + 1 < text.length && text[i + 1] == '"' -> { cell.append('"'); i++ }
                c == '"' -> quoted = !quoted
                c == ',' && !quoted -> { row += cell.toString(); cell.setLength(0) }
                (c == '\n' || c == '\r') && !quoted -> {
                    if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                    row += cell.toString(); cell.setLength(0)
                    if (row.any(String::isNotEmpty)) rows += row
                    row = mutableListOf()
                }
                else -> cell.append(c)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) { row += cell.toString(); rows += row }
        return rows
    }

    private fun formatTimerTotal(elapsedMillis: Long): String = DurationFormatter.format(elapsedMillis)
    private fun csvField(value: String): String =
        if (value.contains(',') || value.contains('"') || value.contains('\n') || value.contains('\r')) "\"${value.replace("\"", "\"\"")}\"" else value

    /** CSV Export format: Dose Time, Medication Name, Dose Amount (unit), Concentration When Taken */
    fun exportCsv(medications: List<Medication>, doseLogs: List<DoseLog>): String {
        val medMap = medications.associateBy { it.id }
        val logsByMed = doseLogs.groupBy { it.medicationId }
        val sb = StringBuilder("Dose Time, Medication Name, Dose Amount (unit), Concentration When Taken\n")
        doseLogs.sortedBy { it.timestamp }.forEach { dose ->
            val med = medMap[dose.medicationId]
            val timeStr = dateFormat.format(Date(dose.timestamp))
            val medName = med?.name ?: "Unknown Medication"
            val unit = med?.unit ?: "mg"
            val conc = if (med != null) PkEngine.calculateConcentrationAt(med, logsByMed[med.id] ?: emptyList(), dose.timestamp) else 0.0
            sb.append(listOf(timeStr, medName, "${dose.doseAmount} $unit", String.format(Locale.US, "%.4f", conc)).joinToString(",") { csvField(it) }).append('\n')
        }
        return sb.toString()
    }

    fun exportJson(medications: List<Medication>, doseLogs: List<DoseLog>): String =
        backupAdapter.indent("  ").toJson(BackupData(medications, doseLogs))

    fun importJson(jsonString: String): Pair<List<Medication>, List<DoseLog>> {
        val backup = backupAdapter.fromJson(jsonString) ?: BackupData(emptyList(), emptyList())
        val medications = backup.medications.map { medication ->
            if (medication.tmaxMinHours != null && medication.tmaxMaxHours != null) medication else {
                val effectiveHalfLife = PkEngine.effectiveHalfLifeHours(medication)
                val ke = PkEngine.calculateKe(effectiveHalfLife)
                val ka = if (medication.absorptionTimeHours > 0.0) kotlin.math.ln(2.0) / medication.absorptionTimeHours else 0.0
                val tmax = when { ka <= 0.0 || ke <= 0.0 -> 0.0; kotlin.math.abs(ka - ke) < 1e-12 -> 1.0 / ke; else -> kotlin.math.ln(ka / ke) / (ka - ke) }
                medication.copy(absorptionTimeHours = tmax, tmaxMinHours = tmax, tmaxMaxHours = tmax)
            }
        }
        return Pair(medications, backup.doseLogs)
    }

    fun exportCsvToStream(medications: List<Medication>, doseLogs: List<DoseLog>, outputStream: OutputStream) {
        outputStream.bufferedWriter().use { it.write(exportCsv(medications, doseLogs)) }
    }
    fun exportMedicationCsvToStream(medications: List<Medication>, outputStream: OutputStream) {
        outputStream.bufferedWriter().use { it.write(exportMedicationCsv(medications)) }
    }
    fun exportJsonToStream(medications: List<Medication>, doseLogs: List<DoseLog>, outputStream: OutputStream) {
        outputStream.bufferedWriter().use { it.write(exportJson(medications, doseLogs)) }
    }
    fun exportCounterCsvToStream(counters: List<Counter>, hits: List<CounterHit>, outputStream: OutputStream) {
        outputStream.bufferedWriter().use { it.write(exportCounterCsv(counters, hits)) }
    }
    fun importJsonFromStream(inputStream: InputStream): Pair<List<Medication>, List<DoseLog>> =
        importJson(inputStream.bufferedReader().use { it.readText() })
    fun importCsvFromStream(inputStream: InputStream): String =
        inputStream.bufferedReader().use { it.readText() }
}
