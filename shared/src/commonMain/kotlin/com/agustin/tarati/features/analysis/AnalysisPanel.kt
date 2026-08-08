package com.agustin.tarati.features.analysis

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.agustin.tarati.core.domain.analysis.EvalMetric
import com.agustin.tarati.core.domain.analysis.PositionAnalyzer
import com.agustin.tarati.core.domain.game.play.GameState
import com.agustin.tarati.services.localization.localizedString
import com.agustin.tarati.shared.generated.resources.Res
import com.agustin.tarati.shared.generated.resources.analysis_black
import com.agustin.tarati.shared.generated.resources.analysis_black_advantage_note
import com.agustin.tarati.shared.generated.resources.analysis_breakdown
import com.agustin.tarati.shared.generated.resources.analysis_material
import com.agustin.tarati.shared.generated.resources.analysis_metric_center
import com.agustin.tarati.shared.generated.resources.analysis_metric_conversion
import com.agustin.tarati.shared.generated.resources.analysis_metric_forced_promotion
import com.agustin.tarati.shared.generated.resources.analysis_metric_home
import com.agustin.tarati.shared.generated.resources.analysis_metric_material
import com.agustin.tarati.shared.generated.resources.analysis_metric_mobility
import com.agustin.tarati.shared.generated.resources.analysis_metric_pressure
import com.agustin.tarati.shared.generated.resources.analysis_metric_upgrade
import com.agustin.tarati.shared.generated.resources.analysis_view_full
import com.agustin.tarati.shared.generated.resources.analysis_white
import com.agustin.tarati.ui.components.game.EvaluationBar
import kotlin.math.abs
import kotlin.math.round
import kotlin.math.roundToInt

/** Verde para aportes que favorecen a Blancas; rojo para los que favorecen a Negras. */
private val WhiteFavorColor = Color(0xFF2E7D32)
private val BlackFavorColor = Color(0xFFC62828)

/**
 * Panel de análisis de una posición: barra de evaluación grande + probabilidad de
 * victoria del bando líder + ventaja material equivalente + **desglose por término**
 * (material, centro, movilidad, base, presión, promoción, promoción forzada,
 * conversión) con su aporte en puntos (óptica de Blancas).
 *
 * Es una función pura de [gameState] — sirve para cualquier modo. Usado por el
 * destino `Analysis` del panel lateral (Expanded) y por el panel colapsable en
 * portrait.
 *
 * @param onOpenFullAnalysis Si no-null, muestra un botón que abre el detalle/análisis
 *   completo de la partida (gráfico por-ply + clasificación de jugadas). El caller decide
 *   el destino (detalle temporal offline o persistente remoto). Ver [openGameAnalysis].
 */
@Composable
fun AnalysisPanel(
    gameState: GameState,
    modifier: Modifier = Modifier,
    onOpenFullAnalysis: (() -> Unit)? = null,
) {
    val analyzer = remember { PositionAnalyzer() }
    val eval = remember(gameState) { analyzer.evaluate(gameState) }

    val whitePct = (eval.winProbWhite * 100f).roundToInt()
    val leadingWhite = eval.winProbWhite >= 0.5f
    val leadingLabel =
        if (leadingWhite) localizedString(Res.string.analysis_white) else localizedString(Res.string.analysis_black)
    val leadingPct = if (leadingWhite) whitePct else 100 - whitePct

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // ── Cabecera: barra + win% + material equivalente ─────────────────────
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EvaluationBar(
                winProbWhite = eval.winProbWhite,
                showLabel = false,
                modifier = Modifier
                    .width(20.dp)
                    .height(140.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "$leadingLabel  $leadingPct%",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = "${localizedString(Res.string.analysis_material)}: ${signed(eval.materialEquiv, 1)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        HorizontalDivider()

        // ── Desglose por término ──────────────────────────────────────────────
        Text(
            text = localizedString(Res.string.analysis_breakdown),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        // Escala relativa a la posición: el mayor aporte llena media barra.
        val maxAbs = eval.contributions.maxOfOrNull { abs(it.points) }?.takeIf { it > 0.0 } ?: 1.0
        eval.contributions.forEach { contribution ->
            MetricBar(
                label = metricLabel(contribution.metric),
                points = contribution.points,
                maxAbs = maxAbs,
            )
        }

        Text(
            text = localizedString(Res.string.analysis_black_advantage_note),
            style = MaterialTheme.typography.bodySmall,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // ── Acceso al análisis completo (gráfico por-ply + clasificación) ─────
        if (onOpenFullAnalysis != null) {
            FilledTonalButton(
                onClick = onOpenFullAnalysis,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(localizedString(Res.string.analysis_view_full))
            }
        }
    }
}

/**
 * Fila de un término del desglose: etiqueta + **barra divergente** desde el centro
 * (derecha = favorece a Blancas, izquierda = Negras), con el valor en puntos en el
 * centro. La longitud es relativa a [maxAbs] (el mayor aporte de la posición llena
 * media barra).
 */
@Composable
private fun MetricBar(label: String, points: Double, maxAbs: Double) {
    val negligible = abs(points) < 0.5
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val centerColor = MaterialTheme.colorScheme.outline
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(104.dp),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(18.dp)
                .clip(RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val cx = w / 2f
                drawRect(trackColor)
                if (!negligible) {
                    val frac = (abs(points) / maxAbs).coerceIn(0.0, 1.0).toFloat()
                    val len = (w / 2f) * frac
                    if (points > 0) {
                        drawRect(WhiteFavorColor.copy(alpha = 0.85f), topLeft = Offset(cx, 0f), size = Size(len, h))
                    } else {
                        drawRect(
                            BlackFavorColor.copy(alpha = 0.85f),
                            topLeft = Offset(cx - len, 0f),
                            size = Size(len, h)
                        )
                    }
                }
                drawLine(centerColor, Offset(cx, 0f), Offset(cx, h), strokeWidth = 1f)
            }
            Text(
                text = if (negligible) "—" else signed(points, 0),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun metricLabel(metric: EvalMetric): String = localizedString(
    when (metric) {
        EvalMetric.MATERIAL -> Res.string.analysis_metric_material
        EvalMetric.CENTER -> Res.string.analysis_metric_center
        EvalMetric.MOBILITY -> Res.string.analysis_metric_mobility
        EvalMetric.HOME -> Res.string.analysis_metric_home
        EvalMetric.PRESSURE -> Res.string.analysis_metric_pressure
        EvalMetric.UPGRADE -> Res.string.analysis_metric_upgrade
        EvalMetric.FORCED_PROMOTION -> Res.string.analysis_metric_forced_promotion
        EvalMetric.CONVERSION -> Res.string.analysis_metric_conversion
    },
)

/** Formatea [value] con signo explícito y [decimals] decimales, p. ej. "+1.5", "-34", "+0". */
private fun signed(value: Double, decimals: Int): String {
    val text = if (decimals <= 0) {
        value.roundToInt().toString()
    } else {
        val factor = generateSequence(1.0) { it * 10 }.elementAt(decimals)
        (round(value * factor) / factor).toString()
    }
    return if (value >= 0 && !text.startsWith("-")) "+$text" else text
}
