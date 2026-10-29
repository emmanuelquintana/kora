package com.emmanuelquintana.kora.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.ui.graphics.vector.ImageVector
import com.emmanuelquintana.kora.R
import com.emmanuelquintana.kora.core.notifications.KoraNotifications
import com.emmanuelquintana.kora.data.model.BloodPressureReading
import com.emmanuelquintana.kora.data.model.GlucoseReading
import com.emmanuelquintana.kora.data.model.Medication
import com.emmanuelquintana.kora.domain.usecase.DoseEntry

/** Secciones de primer nivel. La selección se comparte entre todas las posturas. */
enum class KoraDestination(
    @StringRes val label: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    TODAY(R.string.nav_today, Icons.Outlined.CalendarToday, Icons.Rounded.CalendarToday),
    HISTORY(R.string.nav_history, Icons.Outlined.ShowChart, Icons.Rounded.ShowChart),
    MEDICATIONS(R.string.nav_medications, Icons.Outlined.Medication, Icons.Rounded.Medication),
    PROFILE(R.string.nav_profile, Icons.Outlined.Person, Icons.Rounded.Person),
    ;

    companion object {
        /** Destino pedido por una notificación (extra de MainActivity). */
        fun fromNotification(value: String?): KoraDestination? = when (value) {
            KoraNotifications.DESTINATION_MEDICATIONS -> TODAY
            KoraNotifications.DESTINATION_BLOOD_PRESSURE -> TODAY
            else -> null
        }
    }
}

/** Qué hoja de registro está abierta. */
enum class EntrySheet { GLUCOSE, PRESSURE, MEDICATION }

/**
 * Acciones de la app como funciones: las pantallas no conocen los ViewModels,
 * lo que permite reutilizarlas en las tres posturas sin duplicar lógica.
 */
class KoraActions(
    val openGlucoseEntry: (GlucoseReading?) -> Unit,
    val openPressureEntry: (BloodPressureReading?) -> Unit,
    val openMedicationEditor: (Medication?) -> Unit,
    val toggleDose: (DoseEntry) -> Unit,
    val snoozeDose: (DoseEntry) -> Unit,
    val skipDose: (DoseEntry) -> Unit,
    val deleteGlucose: (GlucoseReading) -> Unit,
    val deletePressure: (BloodPressureReading) -> Unit,
    val requestNotifications: () -> Unit,
    val openExactAlarmSettings: () -> Unit,
    val navigate: (KoraDestination) -> Unit,
)
