package com.agustin.tarati.ui.components.game.animation

import androidx.compose.ui.geometry.Offset
import com.agustin.tarati.core.domain.game.board.Vertex
import com.agustin.tarati.core.domain.game.pieces.Cob
import com.agustin.tarati.core.domain.game.pieces.CobColor
import com.agustin.tarati.ui.components.game.draw.pieces.ConversionAnimationType

data class AnimatedCob(
    val vertex: Vertex,
    val cob: Cob,
    val currentPos: Vertex,
    val targetPos: Vertex,
    val targetColor: CobColor? = null,
    val animationProgress: Float = 1f,
    /**
     * Offset absoluto (px del contenedor) desde el que arranca el tramo de movimiento,
     * en lugar de la posición del vértice [currentPos]. Se usa en arrastrar-y-soltar:
     * la pieza vuela desde donde se soltó hasta [targetPos], sin "teletransportarse"
     * antes a [currentPos]. `null` = animación normal desde el vértice de origen.
     */
    val startOverride: Offset? = null,
    val upgradeProgress: Float = 1f,
    val conversionProgress: Float = 1f,
    val isConverting: Boolean = false,
    val conversionType: ConversionAnimationType = ConversionAnimationType.FROM_CENTER,
    /** Ángulo de tilt (grados) al inicio del movimiento — tilt del vértice origen. */
    val fromTiltDeg: Float = 0f,
    /** Ángulo de tilt (grados) al finalizar el movimiento — nuevo tilt asignado al destino. */
    val toTiltDeg: Float = 0f,
)