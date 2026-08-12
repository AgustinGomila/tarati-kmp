@file:OptIn(ExperimentalMaterial3Api::class)

package com.agustin.tarati.ui.components.sidebar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.agustin.tarati.core.domain.ai.services.Difficulty
import com.agustin.tarati.core.domain.ai.services.displayNameRes
import com.agustin.tarati.services.localization.localizedString

/**
 * Compact borderless difficulty dropdown, sized to fill whatever space remains
 * in the row after the band indicator and player-type chip.
 */
@Composable
internal fun CompactDifficultySelector(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    difficulty: Difficulty,
    onDifficultyChange: (Difficulty) -> Unit,
) {
    // En modo preview, ExposedDropdownMenu usa un Popup que no se renderiza.
    // Con LocalInspectionMode renderizamos las opciones inline para que los
    // previews de Play Store muestren el dropdown expandido correctamente.
    val inPreview = LocalInspectionMode.current

    if (inPreview && expanded) {
        DifficultyInlineExpanded(
            difficulty = difficulty,
            onDifficultyChange = onDifficultyChange,
            onExpandedChange = onExpandedChange,
        )
    } else {
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = onExpandedChange) {
            DifficultyTextField(
                difficulty = difficulty,
                expanded = expanded,
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                    .fillMaxWidth(),
            )
            ExposedDropdownMenu(expanded, { onExpandedChange(false) }) {
                DifficultyMenuItems(onDifficultyChange, onExpandedChange)
            }
        }
    }
}

/** Campo de texto del selector — compartido entre la versión normal y la inline. */
@Composable
private fun DifficultyTextField(
    difficulty: Difficulty,
    expanded: Boolean,
    modifier: Modifier = Modifier,
) {
    TextField(
        value = localizedString(difficulty.displayNameRes),
        onValueChange = {},
        readOnly = true,
        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
        modifier = modifier,
        textStyle = MaterialTheme.typography.bodyMedium,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        shape = RoundedCornerShape(10.dp),
    )
}

/** Ítems del menú — compartidos entre el popup normal y el modo inline de preview. */
@Composable
private fun DifficultyMenuItems(
    onDifficultyChange: (Difficulty) -> Unit,
    onExpandedChange: (Boolean) -> Unit,
) {
    Difficulty.ALL.forEach { opt ->
        DropdownMenuItem(
            text = {
                Text(localizedString(opt.displayNameRes), color = MaterialTheme.colorScheme.onSurface)
            },
            onClick = { onDifficultyChange(opt); onExpandedChange(false) },
        )
    }
}

/**
 * Versión inline del selector de dificultad para previews de Compose.
 *
 * El campo de texto ocupa su altura normal en el layout. Las opciones se
 * renderizan en un [Box] cuya altura reportada al padre es **cero** (via
 * [Modifier.layout]), de modo que el contenido posterior no se desplaza.
 * Las opciones desbordan hacia abajo visualmente, replicando el comportamiento
 * de un [androidx.compose.ui.window.Popup] sin usar ventanas del sistema.
 *
 * Para que las opciones queden por encima de los siblings posteriores del
 * sidebar (p. ej. [MoveHistorySection]), el llamador debe asegurarse de que
 * la sección padre tenga un [Modifier.zIndex] mayor (ver [SidebarContent]).
 */
@Composable
private fun DifficultyInlineExpanded(
    difficulty: Difficulty,
    onDifficultyChange: (Difficulty) -> Unit,
    onExpandedChange: (Boolean) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        DifficultyTextField(
            difficulty = difficulty,
            expanded = true,
            modifier = Modifier.fillMaxWidth(),
        )
        // Cero altura reportada al padre → no desplaza el contenido posterior.
        // Las opciones desbordan visualmente hacia abajo sobre los controles
        // de abajo, igual que lo haría un Popup real.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .layout { measurable, constraints ->
                    val p = measurable.measure(
                        constraints.copy(maxHeight = Constraints.Infinity)
                    )
                    layout(p.width, 0) { p.place(0, 0) }
                },
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 8.dp,
                shadowElevation = 4.dp,
                shape = RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp),
            ) {
                Column {
                    DifficultyMenuItems(onDifficultyChange, onExpandedChange)
                }
            }
        }
    }
}
