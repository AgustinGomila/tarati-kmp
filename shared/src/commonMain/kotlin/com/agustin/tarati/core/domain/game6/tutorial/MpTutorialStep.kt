package com.agustin.tarati.core.domain.game6.tutorial

import com.agustin.tarati.core.domain.game.board.Vertex
import com.agustin.tarati.core.domain.game6.board.Board25
import com.agustin.tarati.core.domain.game6.pieces.Piece
import com.agustin.tarati.core.domain.game6.pieces.PlayerColor
import com.agustin.tarati.core.domain.game6.play.MpGameState
import com.agustin.tarati.core.domain.game6.play.MpMove
import com.agustin.tarati.core.domain.game6.play.Seat
import com.agustin.tarati.core.domain.game6.rules.MpSetup
import com.agustin.tarati.shared.generated.resources.Res
import com.agustin.tarati.shared.generated.resources.mp_tutorial_any_direction_description
import com.agustin.tarati.shared.generated.resources.mp_tutorial_any_direction_title
import com.agustin.tarati.shared.generated.resources.mp_tutorial_base_exit_description
import com.agustin.tarati.shared.generated.resources.mp_tutorial_base_exit_title
import com.agustin.tarati.shared.generated.resources.mp_tutorial_conversion_description
import com.agustin.tarati.shared.generated.resources.mp_tutorial_conversion_title
import com.agustin.tarati.shared.generated.resources.mp_tutorial_end_description
import com.agustin.tarati.shared.generated.resources.mp_tutorial_end_title
import com.agustin.tarati.shared.generated.resources.mp_tutorial_intro_description
import com.agustin.tarati.shared.generated.resources.mp_tutorial_intro_title
import com.agustin.tarati.shared.generated.resources.mp_tutorial_single_piece_description
import com.agustin.tarati.shared.generated.resources.mp_tutorial_single_piece_title
import com.agustin.tarati.shared.generated.resources.mp_tutorial_withdrawal_description
import com.agustin.tarati.shared.generated.resources.mp_tutorial_withdrawal_title
import org.jetbrains.compose.resources.StringResource

/**
 * Identidad estable de cada paso del tutorial multijugador. La usan los tests (identificar el paso
 * sin depender del texto) y la UI (anclar la burbuja a una posición fija por paso).
 */
enum class MpTutorialStepId {
    INTRO,
    BASE_EXIT,
    ANY_DIRECTION,
    SINGLE_PIECE,
    CONVERSION,
    WITHDRAWAL,
    END,
}

/**
 * Un paso guiado del tutorial multijugador (tablero `25`). Muestra una posición scripteada
 * ([state]) y, opcionalmente, resalta una pieza ([selection]) y/o un conjunto de destinos
 * ([legalTargets]) reutilizando los cues que ya dibuja `Board25View`.
 *
 * Paralelo al `TutorialStep` de single, pero sobre el dominio `game6` y **sin** animaciones:
 * los pasos son estáticos (guiados) o interactivos (ver [MpInteractiveTutorialStep]).
 */
open class MpTutorialStep(
    val id: MpTutorialStepId,
    val titleRes: StringResource,
    val descriptionRes: StringResource,
    val state: MpGameState,
    val selection: Vertex? = null,
    val legalTargets: Set<Vertex> = emptySet(),
    /**
     * Milisegundos tras los que un paso **guiado** avanza solo (paridad con el tutorial single). Los
     * pasos interactivos lo ignoran: avanzan al realizar el usuario el movimiento correcto.
     */
    val autoAdvanceDelay: Long? = null,
)

/**
 * Un paso interactivo: espera que el usuario ejecute uno de los [expectedMoves]. El [selection] fija
 * el origen resaltado (la pieza que el usuario debe mover) y [legalTargets] el/los destino(s)
 * válido(s) que se resaltan en el tablero.
 */
class MpInteractiveTutorialStep(
    id: MpTutorialStepId,
    titleRes: StringResource,
    descriptionRes: StringResource,
    state: MpGameState,
    selection: Vertex,
    legalTargets: Set<Vertex>,
    val expectedMoves: List<MpMove>,
) : MpTutorialStep(id, titleRes, descriptionRes, state, selection, legalTargets) {
    /** `true` si [move] es uno de los movimientos esperados de este paso. */
    fun isExpectedMove(move: MpMove): Boolean = move in expectedMoves
}

// ── Construcción de las posiciones scripteadas ──────────────────────────────────

private fun d(i: Int): Vertex = Vertex(Board25.DOMESTIC, i)
private fun c(i: Int): Vertex = Vertex(Board25.CIRCUMFERENCE, i)
private fun e(i: Int): Vertex = Vertex(Board25.EDGE, i)
private fun b(i: Int): Vertex = Vertex(Board25.BRIDGE, i)

/** Pieza del tutorial; por defecto ya fuera de base (para posiciones de campo). */
private fun piece(color: PlayerColor, hasLeftBase: Boolean = true): Piece =
    Piece(owner = color, hasLeftBase = hasLeftBase)

/** Asientos S (base 17) y N (base 20) — las 2 bases opuestas, como en una partida de 2. */
private val seats2: List<Seat> = listOf(Seat(PlayerColor.P1, 17), Seat(PlayerColor.P2, 20))

/** Asientos S/NW/NE (bases 17/21/19) — 3 bases alternadas, como `MpSetup.selectBaseIndices(3)`. */
private val seats3: List<Seat> =
    listOf(Seat(PlayerColor.P1, 17), Seat(PlayerColor.P2, 21), Seat(PlayerColor.P3, 19))

private fun stateOf(
    seats: List<Seat>,
    pieces: Map<Vertex, Piece>,
    turn: Int = 0,
): MpGameState = MpGameState(pieces = pieces, seats = seats, currentSeatIndex = turn)

/**
 * Los 7 pasos del recorrido multijugador, en orden. Cubren **solo las diferencias** con el juego de
 * dos (el tutorial single ya explica las reglas básicas): tablero de 6 bases, salida de base,
 * movimiento omnidireccional, pieza única, conversión multicolor, retiro y fin al quedar 2.
 */
fun mpTutorialSteps(): List<MpTutorialStep> =
    listOf(
        introStep(),
        baseExitStep(),
        anyDirectionStep(),
        singlePieceStep(),
        conversionStep(),
        withdrawalStep(),
        endStep(),
    )

/** 1 — Tablero grande: 6 bases, hasta 6 colores. Se muestra la posición inicial de 6 jugadores. */
private fun introStep(): MpTutorialStep =
    MpTutorialStep(
        id = MpTutorialStepId.INTRO,
        titleRes = Res.string.mp_tutorial_intro_title,
        descriptionRes = Res.string.mp_tutorial_intro_description,
        state = MpSetup.initialState(6),
        autoAdvanceDelay = 6000L,
    )

/**
 * 2 — Salida de base (interactivo). La pieza en `D1` aún no salió: solo puede ir a su radial `C1`
 * (nunca por el anillo D). Se resalta únicamente `C1` y se acepta solo `D1→C1`.
 */
private fun baseExitStep(): MpInteractiveTutorialStep {
    val pieces = buildMap {
        // Base S (P1): cuadrado {D1, D2, E1, E2}, aún en base.
        listOf(d(1), d(2), e(1), e(2)).forEach { put(it, piece(PlayerColor.P1, hasLeftBase = false)) }
        // Base N (P2): cuadrado {D10, D11, E7, E8}, para dar contexto de dos jugadores.
        listOf(d(10), d(11), e(7), e(8)).forEach { put(it, piece(PlayerColor.P2, hasLeftBase = false)) }
    }
    return MpInteractiveTutorialStep(
        id = MpTutorialStepId.BASE_EXIT,
        titleRes = Res.string.mp_tutorial_base_exit_title,
        descriptionRes = Res.string.mp_tutorial_base_exit_description,
        state = stateOf(seats2, pieces),
        selection = d(1),
        legalTargets = setOf(c(1)),
        expectedMoves = listOf(MpMove(d(1), c(1))),
    )
}

/**
 * 3 — Movimiento omnidireccional (guiado). Una pieza ya fuera de base en `C1` puede ir a cualquier
 * vecino vacío: el puente `B1`, sus vecinos de circunferencia `C2`/`C12`, o hacia `D1`/`D18`.
 */
private fun anyDirectionStep(): MpTutorialStep {
    val pieces = buildMap {
        put(c(1), piece(PlayerColor.P1))
        listOf(d(10), d(11), e(7), e(8)).forEach { put(it, piece(PlayerColor.P2, hasLeftBase = false)) }
    }
    return MpTutorialStep(
        id = MpTutorialStepId.ANY_DIRECTION,
        titleRes = Res.string.mp_tutorial_any_direction_title,
        descriptionRes = Res.string.mp_tutorial_any_direction_description,
        state = stateOf(seats2, pieces),
        selection = c(1),
        legalTargets = setOf(b(1), c(2), c(12), d(1), d(18)),
        autoAdvanceDelay = 6000L,
    )
}

/** 4 — Pieza única (guiado): sin promoción, roks ni piezas muertas. Posición mixta de campo. */
private fun singlePieceStep(): MpTutorialStep {
    val pieces = buildMap {
        put(c(1), piece(PlayerColor.P1))
        put(b(1), piece(PlayerColor.P1))
        put(c(7), piece(PlayerColor.P2))
        put(b(4), piece(PlayerColor.P2))
    }
    return MpTutorialStep(
        id = MpTutorialStepId.SINGLE_PIECE,
        titleRes = Res.string.mp_tutorial_single_piece_title,
        descriptionRes = Res.string.mp_tutorial_single_piece_description,
        state = stateOf(seats2, pieces),
        autoAdvanceDelay = 6000L,
    )
}

/**
 * 5 — Captura por conversión (interactivo) con pre-adyacencia y multicolor. La `P2` en `B2` es
 * vecina de **dos** destinos del atacante `C2`: `B1` y `C3` — ambos la voltean (no era pre-adyacente
 * al origen). Los **dos** movimientos son válidos y se resaltan/aceptan. Cada uno deja ver la
 * pre-adyacencia con una pieza distinta: `C2→B1` protege la `P3` en `C1`; `C2→C3` protege la `P3` en
 * `D3` (ambas ya adyacentes a `C2`). `C2` es la única pieza `P1` (así no hay capturas de otras
 * piezas) y `P2` conserva `C8` para no retirarse tras la conversión.
 */
private fun conversionStep(): MpInteractiveTutorialStep {
    val pieces = buildMap {
        put(c(2), piece(PlayerColor.P1)) // atacante (única pieza P1)
        put(b(2), piece(PlayerColor.P2)) // se convierte desde B1 o C3 (no era pre-adyacente a C2)
        put(c(8), piece(PlayerColor.P2)) // evita el retiro de P2 tras perder B2
        put(c(1), piece(PlayerColor.P3)) // protegida al ir a B1 (pre-adyacente a C2)
        put(d(3), piece(PlayerColor.P3)) // protegida al ir a C3 (pre-adyacente a C2)
        put(c(10), piece(PlayerColor.P3))
    }
    return MpInteractiveTutorialStep(
        id = MpTutorialStepId.CONVERSION,
        titleRes = Res.string.mp_tutorial_conversion_title,
        descriptionRes = Res.string.mp_tutorial_conversion_description,
        state = stateOf(seats3, pieces),
        selection = c(2),
        legalTargets = setOf(b(1), c(3)),
        expectedMoves = listOf(MpMove(c(2), b(1)), MpMove(c(2), c(3))),
    )
}

/** 6 — Retiro (guiado): un jugador sin movimientos o sin piezas se retira; sus piezas salen. */
private fun withdrawalStep(): MpTutorialStep {
    val pieces = buildMap {
        put(c(1), piece(PlayerColor.P1))
        put(c(2), piece(PlayerColor.P1))
        put(c(7), piece(PlayerColor.P2))
        put(c(9), piece(PlayerColor.P3))
        put(c(10), piece(PlayerColor.P3))
    }
    return MpTutorialStep(
        id = MpTutorialStepId.WITHDRAWAL,
        titleRes = Res.string.mp_tutorial_withdrawal_title,
        descriptionRes = Res.string.mp_tutorial_withdrawal_description,
        state = stateOf(seats3, pieces),
        autoAdvanceDelay = 7000L,
    )
}

/** 7 — Fin (guiado): se juega hasta que quedan 2; más piezas gana, empate = victoria compartida. */
private fun endStep(): MpTutorialStep =
    MpTutorialStep(
        id = MpTutorialStepId.END,
        titleRes = Res.string.mp_tutorial_end_title,
        descriptionRes = Res.string.mp_tutorial_end_description,
        state = MpSetup.initialState(2),
        autoAdvanceDelay = 7000L,
    )
