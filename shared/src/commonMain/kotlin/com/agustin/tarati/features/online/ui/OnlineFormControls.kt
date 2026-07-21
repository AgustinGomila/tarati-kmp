package com.agustin.tarati.features.online.ui


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.agustin.tarati.core.domain.game.time.TimeControl
import com.agustin.tarati.ui.theme.timeControlIcon

/**
 * Controles de formulario compartidos por los diálogos y filtros del online
 * (nueva búsqueda, desafío, crear torneo, filtros de historial).
 */

/**
 * Chips de selección de control de tiempo, uno por [TimeControl.list].
 *
 * Emite solo los chips (sin contenedor): el caller decide el layout
 * ([Row] con scroll, `FlowRow`, ítem de `LazyRow`, etc.). [onSelect] recibe el
 * chip tocado incluso si ya estaba seleccionado — la lógica de toggle/deselección
 * es del caller.
 */
@Composable
internal fun TimeControlChips(
    selected: String?,
    onSelect: (String) -> Unit,
) {
    TimeControl.list().forEach { tc ->
        FilterChip(
            selected = selected == tc,
            onClick = { onSelect(tc) },
            label = { Text(tc.replaceFirstChar { it.titlecase() }) },
            leadingIcon = {
                Icon(timeControlIcon(tc), null, Modifier.size(16.dp))
            },
        )
    }
}

/** Fila "etiqueta + switch a la derecha", estándar de los diálogos del online. */
@Composable
internal fun LabeledSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}
