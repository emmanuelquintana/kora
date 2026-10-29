package com.emmanuelquintana.kora.ui.state

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.emmanuelquintana.kora.AppContainer
import com.emmanuelquintana.kora.KoraApplication

/**
 * Fábrica única para los ViewModels de Kora. Uso en Compose:
 * `viewModel<KoraViewModel>(factory = KoraViewModelFactory)`.
 */
val KoraViewModelFactory: ViewModelProvider.Factory = viewModelFactory {
    initializer { KoraViewModel(container()) }
    initializer { GlucoseEntryViewModel(createSavedStateHandle(), container()) }
    initializer { BloodPressureEntryViewModel(createSavedStateHandle(), container()) }
    initializer { MedicationEditorViewModel(createSavedStateHandle(), container()) }
    initializer { AuthViewModel(createSavedStateHandle(), container()) }
}

private fun CreationExtras.container(): AppContainer =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as KoraApplication).container
