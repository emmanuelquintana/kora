package com.emmanuelquintana.kora.core.notifications

import android.content.Context
import com.emmanuelquintana.kora.core.util.KoraFormats
import com.emmanuelquintana.kora.data.model.Medication
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * Lo mínimo para reprogramar y mostrar un recordatorio sin red ni sesión
 * activa (p. ej. tras reiniciar el teléfono).
 */
data class AlarmSpec(
    val medicationId: String,
    val name: String,
    val doseLabel: String,
    val unitsPerDose: Int,
    val times: List<LocalTime>,
    val days: Set<DayOfWeek>,
) {
    /** Clave estable de una hora concreta: identifica la alarma y su PendingIntent. */
    fun timeKey(time: LocalTime): String = timeKey(medicationId, time)

    companion object {
        fun from(medication: Medication) = AlarmSpec(
            medicationId = medication.id,
            name = medication.name,
            doseLabel = "${medication.dose} ${medication.unit}".trim(),
            unitsPerDose = medication.unitsPerDose,
            times = medication.times.sorted(),
            days = medication.daysOfWeek,
        )

        fun timeKey(medicationId: String, time: LocalTime) = "$medicationId@${KoraFormats.storageTime(time)}"

        fun doseKey(medicationId: String, date: LocalDate, time: LocalTime) = "${timeKey(medicationId, time)}@$date"
    }
}

/**
 * Registro local de alarmas programadas (SharedPreferences privadas, excluidas
 * de respaldos). Permite cancelar alarmas de medicamentos eliminados y
 * reprogramar tras reinicio. Se borra al cerrar sesión.
 */
class AlarmRegistry(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun load(): List<AlarmSpec> {
        val raw = prefs.getString(KEY_SPECS, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index -> array.optJSONObject(index)?.toSpec() }
        } catch (_: JSONException) {
            emptyList()
        }
    }

    @Synchronized
    fun save(specs: List<AlarmSpec>) {
        val array = JSONArray()
        specs.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_SPECS, array.toString()).apply()
    }

    fun find(medicationId: String): AlarmSpec? = load().firstOrNull { it.medicationId == medicationId }

    @Synchronized
    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun AlarmSpec.toJson() = JSONObject()
        .put("id", medicationId)
        .put("name", name)
        .put("dose", doseLabel)
        .put("units", unitsPerDose)
        .put("times", JSONArray(times.map(KoraFormats::storageTime)))
        .put("days", JSONArray(days.map { it.value }))

    private fun JSONObject.toSpec(): AlarmSpec? {
        val id = optString("id").takeIf { it.isNotBlank() } ?: return null
        val timesJson = optJSONArray("times") ?: return null
        val daysJson = optJSONArray("days") ?: return null
        val times = (0 until timesJson.length()).mapNotNull { KoraFormats.parseStorageTime(timesJson.optString(it)) }
        val days = (0 until daysJson.length()).mapNotNull { daysJson.optInt(it).takeIf { d -> d in 1..7 }?.let(DayOfWeek::of) }.toSet()
        return AlarmSpec(id, optString("name"), optString("dose"), optInt("units", 1), times, days)
    }

    private companion object {
        const val PREFS = "kora_alarm_registry"
        const val KEY_SPECS = "specs"
    }
}
