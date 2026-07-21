package com.agustin.tarati.core.domain.history

/**
 * Historial lineal navegable con **truncado de rama**, compartido por el juego single
 * (`GameManager`) y el multijugador (`MpLocalGameViewModel`).
 *
 * Modela una **única línea** de jugadas [M] con un snapshot de estado [S] por ply:
 * - [initialState] = estado antes de la primera jugada (posición del [cursor] `-1`).
 * - la jugada `i` (0-based) produce el estado [stateAt] `(i)`.
 * - [cursor] marca la posición visualizada: `-1` = inicial; [tip] = última jugada.
 *
 * Aplicar una jugada estando en el pasado (tras un undo) **descarta la línea futura** y continúa
 * desde el cursor, como el undo/redo de un editor de texto. La navegación (undo/redo/moveTo) solo
 * mueve el cursor; no altera la línea.
 *
 * Estructura de datos **pura y mutable**: no expone `StateFlow` ni dispara efectos. Cada dueño la
 * envuelve con su propia superficie observable y sus efectos (turno, bots, estado del juego,
 * serialización). No es thread-safe.
 */
class LinearHistory<S, M>(initial: S) {

    private var _initial: S = initial
    private val moves = ArrayList<M>()

    // states[i] = estado tras moves[i]; paralelo a moves (mismo tamaño).
    private val states = ArrayList<S>()

    /** Índice de la jugada visualizada: `-1` = posición inicial; [tip] = última jugada. */
    var cursor: Int = -1
        private set

    /** Estado antes de la primera jugada (posición del cursor `-1`). */
    val initialState: S get() = _initial

    /** Cantidad de jugadas en la línea. */
    val size: Int get() = moves.size

    /** Índice de la última jugada (`-1` si la línea está vacía). */
    val tip: Int get() = moves.size - 1

    /** `true` si el cursor está en la última jugada (o la línea está vacía y el cursor en `-1`). */
    val isAtTip: Boolean get() = cursor == tip

    val canUndo: Boolean get() = cursor >= 0
    val canRedo: Boolean get() = cursor < tip

    /** Copia inmutable de las jugadas, en orden. */
    val movesList: List<M> get() = moves.toList()

    /** Copia inmutable de la línea como pares `(jugada, estado resultante)`, en orden. */
    val entries: List<Pair<M, S>> get() = moves.indices.map { moves[it] to states[it] }

    /** Estado en la posición visualizada ([cursor]). */
    fun currentState(): S = stateAt(cursor)

    /** Estado tras [index] jugadas; `index == -1` devuelve [initialState]. */
    fun stateAt(index: Int): S = if (index < 0) _initial else states[index]

    /** La jugada en [index] (0-based), o `null` fuera de rango. */
    fun moveAt(index: Int): M? = moves.getOrNull(index)

    /**
     * Aplica [move] (con estado resultante [resultingState]) desde el cursor: si el cursor está en el
     * pasado, **trunca** la línea futura primero; luego agrega la jugada y deja el cursor en el [tip].
     */
    fun append(move: M, resultingState: S) {
        truncateAfterCursor()
        moves.add(move)
        states.add(resultingState)
        cursor = tip
    }

    private fun truncateAfterCursor() {
        while (moves.size > cursor + 1) {
            moves.removeAt(moves.size - 1)
            states.removeAt(states.size - 1)
        }
    }

    /** Retrocede una jugada. Devuelve `false` (sin moverse) si ya está en la posición inicial. */
    fun undo(): Boolean =
        if (canUndo) {
            cursor--
            true
        } else {
            false
        }

    /** Avanza una jugada. Devuelve `false` (sin moverse) si ya está en el tip. */
    fun redo(): Boolean =
        if (canRedo) {
            cursor++
            true
        } else {
            false
        }

    /**
     * Salta a la posición tras la jugada [index] (`-1` = inicial). Devuelve `false` (sin moverse) si
     * [index] está fuera de `-1..tip`.
     */
    fun moveTo(index: Int): Boolean =
        if (index in -1..tip) {
            cursor = index
            true
        } else {
            false
        }

    /** Salta al [tip] (última jugada; `-1` si la línea está vacía). */
    fun moveToTip() {
        cursor = tip
    }

    /** Reinicia la línea con [base] como nueva posición inicial (sin jugadas, cursor `-1`). */
    fun reset(base: S) {
        _initial = base
        moves.clear()
        states.clear()
        cursor = -1
    }

    /**
     * Fija solo la posición inicial ([initialState]) sin tocar jugadas ni cursor. Faithful a
     * "cambiar la base" cuando la línea está vacía (p. ej. `GameManager.setInitialGameState`).
     */
    fun rebase(base: S) {
        _initial = base
    }

    /**
     * Reemplaza la línea completa: usa [base] como posición inicial y reconstruye los snapshots
     * aplicando cada jugada de [newMoves] con [apply] `(estadoPrevio, jugada) -> estadoResultante`.
     * Deja el cursor en el [tip]. Usado para importar/cargar una partida.
     */
    fun replay(base: S, newMoves: List<M>, apply: (S, M) -> S) {
        _initial = base
        moves.clear()
        states.clear()
        var current = base
        for (m in newMoves) {
            current = apply(current, m)
            moves.add(m)
            states.add(current)
        }
        cursor = tip
    }

    /**
     * Restaura la línea desde [entries] `(jugada, estado resultante)` ya calculados, dejando el cursor
     * en [at] (acotado a `-1..tip`). No recalcula estados. Usado para rehidratar desde un estado
     * persistido, sin tocar [initialState].
     */
    fun restore(entries: List<Pair<M, S>>, at: Int) {
        moves.clear()
        states.clear()
        for ((m, s) in entries) {
            moves.add(m)
            states.add(s)
        }
        cursor = at.coerceIn(-1, tip)
    }

    /**
     * Reescribe la línea aplicando [mapState] a la posición inicial y a cada snapshot, y [mapMove] a
     * cada jugada, **preservando el cursor**. Usado para rotar la perspectiva del tablero (game6):
     * como la rotación es un automorfismo del grafo, la línea rotada sigue siendo consistente.
     */
    fun transform(mapState: (S) -> S, mapMove: (M) -> M) {
        _initial = mapState(_initial)
        for (i in states.indices) states[i] = mapState(states[i])
        for (i in moves.indices) moves[i] = mapMove(moves[i])
    }
}
