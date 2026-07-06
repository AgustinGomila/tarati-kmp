package com.agustin.tarati.core.domain.game6.play

/**
 * Una celda de la lista de movimientos columnar (§D7 del plan). La lista se dibuja con una
 * **columna por jugador** (en orden de asiento), de modo que el jugador lo da la columna y el
 * token va sin prefijo.
 */
sealed interface MpMoveCell {
    /**
     * El jugador movió en esta ronda: se muestra `from-to` ([MpMove.name]). [ply] es el índice
     * **lineal** de la jugada en el historial (0-based) → permite navegar a esa posición (undo/redo).
     */
    data class Played(val move: MpMove, val ply: Int) : MpMoveCell

    /** El jugador está **retirado** (perdió todas sus piezas): la columna muestra `-`. */
    data object Retired : MpMoveCell

    /** El jugador aún no movió en la ronda en curso (celda en blanco, pendiente). */
    data object Empty : MpMoveCell
}

/** Una fila de la lista = una **ronda** (una vuelta por los asientos); una celda por columna. */
data class MpMoveRow(
    val number: Int,
    val cells: List<MpMoveCell>,
)

/**
 * Convierte un historial lineal de movimientos en filas de columnas alineadas por jugador.
 *
 * Regla de armado: cada movimiento cae en la columna de su jugador; se abre una **fila nueva**
 * cuando el jugador entrante ya tiene celda ocupada en la fila actual (es decir, al cerrar una
 * ronda). Una celda vacía se muestra como `-` si el asiento está [SeatStatus.RETIRED], y en
 * blanco ([MpMoveCell.Empty]) si sigue [SeatStatus.ACTIVE] (pendiente en la ronda en curso).
 */
object MpMoveList {

    fun build(history: List<PlayerMove>, seats: List<Seat>): List<MpMoveRow> {
        val n = seats.size
        if (n == 0) return emptyList()

        val columnByColor = seats.withIndex().associate { (i, seat) -> seat.color to i }
        val retiredColumns = seats.withIndex()
            .filter { it.value.status == SeatStatus.RETIRED }
            .map { it.index }
            .toSet()

        val rows = mutableListOf<MutableList<MpMoveCell>>()
        for ((ply, pm) in history.withIndex()) {
            val col = columnByColor[pm.color] ?: continue
            val last = rows.lastOrNull()
            val row = if (last == null || last[col] !is MpMoveCell.Empty) {
                MutableList<MpMoveCell>(n) { MpMoveCell.Empty }.also { rows += it }
            } else {
                last
            }
            row[col] = MpMoveCell.Played(pm.move, ply)
        }

        // Las columnas de asientos retirados muestran `-` donde no hay movimiento.
        for (row in rows) {
            for (col in retiredColumns) {
                if (row[col] is MpMoveCell.Empty) row[col] = MpMoveCell.Retired
            }
        }

        return rows.mapIndexed { i, cells -> MpMoveRow(number = i + 1, cells = cells) }
    }
}
