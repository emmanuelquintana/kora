package com.emmanuelquintana.kora.core.database

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.emmanuelquintana.kora.data.model.HealthRanges
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant

private val Context.koraDataStore: DataStore<Preferences> by preferencesDataStore(name = "kora_preferences")

/**
 * Preferencias NO sensibles (DataStore). Los datos clínicos viven en Firestore
 * (o en memoria en modo demostración), nunca aquí.
 */
class KoraPreferences(context: Context) {
    private val store = context.applicationContext.koraDataStore

    private object Keys {
        val GLUCOSE_LOW = intPreferencesKey("glucose_low_mg_dl")
        val GLUCOSE_HIGH = intPreferencesKey("glucose_high_mg_dl")
        val LAST_UPDATE_CHECK = longPreferencesKey("last_update_check_epoch_ms")
    }

    val ranges: Flow<HealthRanges> = store.data.map { prefs ->
        HealthRanges(
            glucoseLowMgDl = prefs[Keys.GLUCOSE_LOW] ?: HealthRanges.DEFAULT_GLUCOSE_LOW,
            glucoseHighMgDl = prefs[Keys.GLUCOSE_HIGH] ?: HealthRanges.DEFAULT_GLUCOSE_HIGH,
        )
    }

    suspend fun setRanges(ranges: HealthRanges) {
        store.edit {
            it[Keys.GLUCOSE_LOW] = ranges.glucoseLowMgDl
            it[Keys.GLUCOSE_HIGH] = ranges.glucoseHighMgDl
        }
    }

    suspend fun lastUpdateCheck(): Instant? =
        store.data.first()[Keys.LAST_UPDATE_CHECK]?.let(Instant::ofEpochMilli)

    suspend fun setLastUpdateCheck(at: Instant) {
        store.edit { it[Keys.LAST_UPDATE_CHECK] = at.toEpochMilli() }
    }

    /** Al cerrar sesión: vuelve a los rangos por defecto para la siguiente cuenta. */
    suspend fun clearUserScoped() {
        store.edit {
            it.remove(Keys.GLUCOSE_LOW)
            it.remove(Keys.GLUCOSE_HIGH)
        }
    }
}
