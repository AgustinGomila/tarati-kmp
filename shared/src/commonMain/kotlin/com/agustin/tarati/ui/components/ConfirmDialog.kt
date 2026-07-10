package com.agustin.tarati.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.agustin.tarati.services.localization.LocalizedText
import com.agustin.tarati.shared.generated.resources.Res
import com.agustin.tarati.shared.generated.resources.cancel
import org.jetbrains.compose.resources.StringResource

/**
 * Diálogo de confirmación reutilizable con la estructura estándar de la app:
 * título · cuerpo · botón de confirmación · botón de descarte (Cancelar).
 *
 * El botón de confirmación descarta el diálogo ([onDismiss]) antes de ejecutar
 * [onConfirm], por lo que el caller solo necesita cerrar su flag en [onDismiss]:
 *
 * ```
 * if (showResignDialog) {
 *     ConfirmDialog(
 *         title = Res.string.resign,
 *         body = Res.string.confirm_resign,
 *         confirmLabel = Res.string.resign,
 *         destructive = true,
 *         onConfirm = onResign,
 *         onDismiss = { showResignDialog = false },
 *     )
 * }
 * ```
 *
 * @param destructive Tiñe el botón de confirmación con el color de error (acciones
 *   irreversibles como resignar o desconectar).
 */
@Composable
fun ConfirmDialog(
    title: StringResource,
    body: StringResource,
    confirmLabel: StringResource,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
    dismissLabel: StringResource = Res.string.cancel,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { LocalizedText(title, style = MaterialTheme.typography.titleMedium) },
        text = { LocalizedText(body, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            Button(
                onClick = {
                    onDismiss()
                    onConfirm()
                },
                colors = if (destructive) {
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                } else {
                    ButtonDefaults.buttonColors()
                },
            ) {
                LocalizedText(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                LocalizedText(dismissLabel)
            }
        },
        modifier = modifier,
    )
}
