package com.agustin.tarati.tools.openingminer

import java.io.File

/**
 * Etapa 2 — CLI: compila la tabla `opening_stats` (salida de la Etapa 1) en el opening book.
 *
 * Uso:
 * ```
 * compileBook --input opening_stats.tsv --output book.tsv [--min-support 100] [--z 1.96]
 * ```
 *
 * Entrada: TSV con cabecera `pos_hash  move  games  wins  losses  draws` (mapeada por nombre).
 * Salida: TSV `pos_hash  move  games  score  wilson` — una fila por posición con jugada soportada,
 * ordenada por hash. El encoding binario compacto para el bundle (WASM) es tarea de la Etapa 3.
 */
fun main(args: Array<String>) {
    val opts = parseBookArgs(args) ?: run {
        System.err.println(BOOK_USAGE)
        return
    }

    val input = File(opts.inputPath)
    if (!input.isFile) {
        System.err.println("Input no encontrado: ${input.absolutePath}")
        return
    }

    val stats = input.bufferedReader().useLines { parseOpeningStats(it) }
    val book = OpeningBookCompiler.compile(stats, opts.minSupport, opts.z)

    writeBook(book, File(opts.outputPath))
    opts.kotlinPath?.let { writeKotlin(book, File(it), opts.minSupport) }

    val positionsTotal = stats.size
    val losingBest = book.count { it.score < 0.5 }
    System.err.println(
        """
        opening-book compiler
           posiciones en stats:   $positionsTotal
           entradas del book:     ${book.size} (min-support=${opts.minSupport}, z=${opts.z})
           cobertura:             ${percent(book.size, positionsTotal)}
           best-move perdedor:    $losingBest (score < 0.5 — el bando al turno está peor aun con la mejor)
           salida:                ${opts.outputPath}
        """.trimIndent()
    )
}

// ── I/O ─────────────────────────────────────────────────────────────────────────

/** Agrupa `opening_stats.tsv` en `pos_hash → [MoveStat]`. Salta filas malformadas en silencio. */
fun parseOpeningStats(lines: Sequence<String>): Map<String, List<MoveStat>> {
    val iterator = lines.iterator()
    if (!iterator.hasNext()) return emptyMap()

    val header = iterator.next().split('\t').map { it.trim() }
    val index = header.withIndex().associate { (i, name) -> name to i }
    fun col(cols: List<String>, name: String): String? = index[name]?.let { cols.getOrNull(it) }

    val grouped = LinkedHashMap<String, MutableList<MoveStat>>()
    while (iterator.hasNext()) {
        val line = iterator.next()
        if (line.isBlank()) continue
        val cols = line.split('\t')
        val stat = runCatching {
            MoveStat(
                move = col(cols, "move")!!,
                games = col(cols, "games")!!.trim().toInt(),
                wins = col(cols, "wins")!!.trim().toInt(),
                losses = col(cols, "losses")!!.trim().toInt(),
                draws = col(cols, "draws")!!.trim().toInt(),
            ) to col(cols, "pos_hash")!!
        }.getOrNull() ?: continue
        grouped.getOrPut(stat.second) { mutableListOf() }.add(stat.first)
    }
    return grouped
}

/** Escribe el book en TSV, ordenado de forma determinista (la lista ya viene ordenada por hash). */
fun writeBook(book: List<BookEntry>, output: File) {
    output.bufferedWriter().use { writer ->
        writer.append("pos_hash\tmove\tgames\tscore\twilson\n")
        for (entry in book) {
            writer.append(
                "${entry.posHash}\t${entry.move}\t${entry.games}\t${format(entry.score)}\t${format(entry.wilson)}\n"
            )
        }
    }
}

/**
 * Emite el book como fuente Kotlin compilable (`OpeningBookData.kt`) para embeberlo en el motor: una
 * `Map` legible, una entrada por línea. Se eligió Kotlin generado (no un recurso) porque funciona en
 * los 4 targets y en el servidor headless, que excluye el runtime de composeResources.
 */
fun writeKotlin(book: List<BookEntry>, output: File, minSupport: Int) {
    output.parentFile?.mkdirs()
    output.bufferedWriter().use { writer ->
        writer.append(
            """
            |package com.agustin.tarati.core.domain.ai.book
            |
            |// GENERADO por :tools:opening-miner (compileBook --kotlin). NO editar a mano.
            |// ${book.size} posiciones · min-support=$minSupport · horizonte 10 plies.
            |// Clave = hash canónico de la posición (BoardSymmetry); valor = jugada recomendada al bando al turno.
            |internal val OPENING_BOOK_ENTRIES: Map<String, String> = mapOf(
            |
            """.trimMargin()
        )
        for (entry in book) {
            writer.append("    \"${entry.posHash}\" to \"${entry.move}\",\n")
        }
        writer.append(")\n")
    }
}

private fun format(value: Double): String = ((value * 10000).toInt() / 10000.0).toString()

private fun percent(part: Int, total: Int): String =
    if (total > 0) "${(part * 1000 / total) / 10.0}%" else "n/a"

// ── Argumentos ──────────────────────────────────────────────────────────────────

private class BookOptions(
    val inputPath: String,
    val outputPath: String,
    val minSupport: Int,
    val z: Double,
    val kotlinPath: String?,
)

private const val BOOK_USAGE =
    "Uso: compileBook --input <opening_stats.tsv> --output <book.tsv> " +
            "[--min-support ${OpeningBookCompiler.DEFAULT_MIN_SUPPORT}] [--z ${Wilson.Z_95}] [--kotlin <OpeningBookData.kt>]"

private fun parseBookArgs(args: Array<String>): BookOptions? {
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
    return BookOptions(
        inputPath = input,
        outputPath = output,
        minSupport = map["min-support"]?.toIntOrNull() ?: OpeningBookCompiler.DEFAULT_MIN_SUPPORT,
        z = map["z"]?.toDoubleOrNull() ?: Wilson.Z_95,
        kotlinPath = map["kotlin"],
    )
}
