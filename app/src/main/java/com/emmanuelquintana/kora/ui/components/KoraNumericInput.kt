package com.emmanuelquintana.kora.ui.components

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.emmanuelquintana.kora.core.designsystem.Kora
import com.emmanuelquintana.kora.domain.usecase.FieldIssue

/**
 * Captura numérica grande: dígitos condensados a tamaño de lectura, teclado
 * numérico, unidad visible y el error con sus límites ("Entre 60 y 260").
 * La validación se muestra cuando [showError] es true (tras intentar guardar
 * o si el valor ya está fuera de los límites físicos).
 */
@Composable
fun KoraNumericInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    unit: String,
    issue: FieldIssue?,
    showError: Boolean,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: () -> Unit = {},
    supporting: String? = null,
) {
    val error = issue?.takeIf { showError }
    val supportingContent: (@Composable () -> Unit)? = when {
        error != null -> { { Text(error.message()) } }
        supporting != null -> { { Text(supporting) } }
        else -> null
    }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text(label) },
        textStyle = Kora.numerals.valueMedium.copy(color = MaterialTheme.colorScheme.onSurface),
        suffix = { Text(unit, style = Kora.numerals.unit) },
        singleLine = true,
        isError = error != null,
        supportingText = supportingContent,
        // Number (no NumberPassword): TalkBack debe poder leer los dígitos. Los caracteres no numéricos se filtran.
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = imeAction),
        keyboardActions = KeyboardActions(onAny = { onImeAction() }),
    )
}
