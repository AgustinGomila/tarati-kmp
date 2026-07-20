package com.agustin.tarati.tools.openingminer

import com.agustin.tarati.core.domain.game.play.GameResult
import java.io.File

/**
 * Etapa 1 del pipeline del opening book: lee el corpus exportado (TSV), reproduce las aperturas y
 * escribe la tabla cruda `opening_stats`. La compilación del book (Wilson, poda, serialización) es
 * la Etapa 2; la integración en el motor, la Etapa 3.
 *
 * Uso:
 * ```
 * opening-miner --input games_export.tsv --output opening_stats.tsv \
 *               [--horizon 10] [--min-rating 1600] [--exclude-end triple]
 * ```
 *
 * Formato de entrada: TSV con cabecera. Se eligió tab (no coma) porque el PGN contiene espacios pero
 * nunca tabs. Columnas requeridas (orden libre, se mapean por nombre): `pgn`, `result`, `end_method`,
 * `is_rated`, `time_control`, `w_rating`, `b_rating`, `w_bot`, `b_bot`.
 *
 * Formato de salida: TSV `pos_hash  move  games  wins  losses  draws`, ordenado de forma determinista.
 */
fun main(args: Array<String>) {
    val opts = parseArgs(args) ?: run {
        System.err.println(USAGE)
        return
    }

    val input = File(opts.inputPath)
    if (!input.isFile) {
        System.err.println("Input no encontrado: ${input.absolutePath}")
        return
    }

    val filter = QualityFilter(excludeEndMethods = opts.excludeEndMethods, minRating = opts.minRating)
    val aggregator = OpeningAggregator(opts.horizon)

    var read = 0
    var accepted = 0
    input.bufferedReader().useLines { lines ->
        parseGameRecords(lines).forEach { record ->
            read++
            if (filter.accepts(record)) {
                accepted++
                aggregator.add(record)
            }
        }
    }

    writeStats(aggregator, File(opts.outputPath))

    System.err.println(
        """
        ✅ opening-miner
           leídas:     $read partidas
           aceptadas:  $accepted (filtro: rating≥${opts.minRating}, excluye ${opts.excludeEndMethods})
           horizonte:  ${opts.horizon} plies
           posiciones: ${aggregator.positionCount()}
           plies:      ${aggregator.pliesRecorded}
           salida:     ${opts.outputPath}
        """.trimIndent()
    )
}

// ── I/O ─────────────────────────────────────────────────────────────────────────

/**
 * Parsea líneas TSV con cabecera a [GameRecord]s. Mapea columnas por nombre (orden independiente) y
 * salta en silencio las filas malformadas — un registro corrupto no debe abortar la corrida.
 */
fun parseGameRecords(lines: Sequence<String>): Sequence<GameRecord> = sequence {
    val iterator = lines.iterator()
    if (!iterator.hasNext()) return@sequence

    val header = iterator.next().split('\t').map { it.trim() }
    val index = header.withIndex().associate { (i, name) -> name to i }

    fun columnOrNull(cols: List<String>, name: String): String? =
        index[name]?.let { cols.getOrNull(it) }

    while (iterator.hasNext()) {
        val line = iterator.next()
        if (line.isBlank()) continue
        val cols = line.split('\t')

        val record = runCatching {
            GameRecord(
                pgn = columnOrNull(cols, "pgn").orEmpty(),
                result = GameResult.fromKey(columnOrNull(cols, "result")!!),
                endMethod = columnOrNull(cols, "end_method").orEmpty(),
                rated = columnOrNull(cols, "is_rated").toBool(),
                timeControl = columnOrNull(cols, "time_control").orEmpty(),
                whiteRating = columnOrNull(cols, "w_rating")!!.trim().toInt(),
                blackRating = columnOrNull(cols, "b_rating")!!.trim().toInt(),
                whiteIsBot = columnOrNull(cols, "w_bot").toBool(),
                blackIsBot = columnOrNull(cols, "b_bot").toBool(),
            )
        }.getOrNull()

        if (record != null) yield(record)
    }
}

/** Escribe la tabla `opening_stats` en TSV, ordenada por posición y jugada para salida determinista. */
fun writeStats(aggregator: OpeningAggregator, output: File) {
    output.bufferedWriter().use { writer ->
        writer.append("pos_hash\tmove\tgames\twins\tlosses\tdraws\n")
        for ((posHash, byMove) in aggregator.stats.toSortedMap()) {
            for ((move, counts) in byMove.toSortedMap()) {
                writer.append("$posHash\t$move\t${counts.games}\t${counts.wins}\t${counts.losses}\t${counts.draws}\n")
            }
        }
    }
}

/** Postgres exporta booleanos como `t`/`f`; se aceptan también `true`/`1` por robustez. */
private fun String?.toBool(): Boolean = this?.trim()?.lowercase() in setOf("t", "true", "1")

// ── Argumentos ──────────────────────────────────────────────────────────────────

private class Options(
    val inputPath: String,
    val outputPath: String,
    val horizon: Int,
    val minRating: Int,
    val excludeEndMethods: Set<String>,
)

private const val USAGE =
    "Uso: opening-miner --input <games.tsv> --output <opening_stats.tsv> " +
            "[--horizon ${OpeningExtractor.DEFAULT_HORIZON_PLIES}] [--min-rating 1600] [--exclude-end triple]"

private fun parseArgs(args: Array<String>): Options? {
    val map = HashMap<String, String>()
    var i = 0
    while (i < args.size) {
        val key = args[i]
        if (key.startsWith("--") && i + 1 < args.size) {
            map[key.removePrefix("--")] = args[i + 1]
            i += 2
        } else {
            i++
        }
    }

    val input = map["input"] ?: return null
    val output = map["output"] ?: return null

    return Options(
        inputPath = input,
        outputPath = output,
        horizon = map["horizon"]?.toIntOrNull() ?: OpeningExtractor.DEFAULT_HORIZON_PLIES,
        minRating = map["min-rating"]?.toIntOrNull() ?: 1600,
        excludeEndMethods = map["exclude-end"]?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet()
            ?: setOf("triple"),
    )
}
