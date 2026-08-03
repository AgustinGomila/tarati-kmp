package com.agustin.tarati.features.online.ui


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.agustin.tarati.core.domain.game.time.TimeControl
import com.agustin.tarati.services.localization.localizedString
import com.agustin.tarati.shared.generated.resources.Res
import com.agustin.tarati.shared.generated.resources.cancel
import com.agustin.tarati.shared.generated.resources.rated
import com.agustin.tarati.shared.generated.resources.social_challenge
import com.agustin.tarati.shared.generated.resources.social_challenge_dialog_title

/**
 * Diálogo de desafío directo: control de tiempo + partida puntuada.
 *
 * Compartido por el tab "Conectados" del lobby y el perfil público.
 *
 * @param forceNonRated True si alguno de los dos jugadores es invitado —
 *        fuerza partida amistosa y deshabilita el switch.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChallengeDialog(
    targetName: String,
    forceNonRated: Boolean = false,
    onConfirm: (timeControl: String, rated: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedTc by remember { mutableStateOf(TimeControl.BLITZ.key) }
    var isRated by remember(forceNonRated) { mutableStateOf(!forceNonRated) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                localizedString(Res.string.social_challenge_dialog_title, targetName),
                style = MaterialTheme.typography.titleMedium,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // FlowRow: los chips envuelven a varias líneas para que TODOS queden visibles
                // en portrait (sin scroll horizontal que oculte opciones).
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    TimeControlChips(selected = selectedTc, onSelect = { selectedTc = it })
                }
                LabeledSwitchRow(
                    label = localizedString(Res.string.rated),
                    checked = isRated,
                    onCheckedChange = { isRated = it },
                    enabled = !forceNonRated,
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selectedTc, isRated) }) {
                Text(localizedString(Res.string.social_challenge))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(localizedString(Res.string.cancel))
            }
        },
    )
}
