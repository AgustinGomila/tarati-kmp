package com.agustin.tarati.ui.components.game.draw.common

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure

/**
 * Abstracción multiplataforma para medir y extraer segmentos de un Path.
 *
 * Esta interfaz permite operaciones avanzadas sobre paths que son necesarias
 * para animaciones complejas como el anillo de selección animado de piezas.
 *
 * ## Implementaciones
 * - **Android**: Usa `android.graphics.PathMeasure` con precisión completa
 * - **Desktop**: Implementación simplificada o aproximada
 *
 * ## Uso típico
 * ```kotlin
 * val measure = PathMeasureApi.create(path, closed = false)
 * val totalLength = measure.length
 * val segment = measure.getSegment(start = 0f, end = totalLength * 0.5f)
 * val (pos, tan) = measure.getPosTan(totalLength * 0.75f)
 * ```
 */
interface PathMeasureApi {
    /**
     * Longitud total del path en píxeles.
     */
    val length: Float

    /**
     * Extrae un segmento del path desde [startDistance] hasta [endDistance].
     *
     * @param startDistance Distancia de inicio en el path (0 a length)
     * @param endDistance Distancia final en el path (0 a length)
     * @param startWithMoveTo Si true, el segmento empieza con moveTo; si false, con lineTo
     * @return Path que representa el segmento extraído, o null si la operación falla
     */
    fun getSegment(
        startDistance: Float,
        endDistance: Float,
        startWithMoveTo: Boolean = true,
    ): Path?

    /**
     * Obtiene la posición y tangente en un punto específico del path.
     *
     * @param distance Distancia desde el inicio del path (0 a length)
     * @return Par de (posición, tangente) donde cada uno es FloatArray(2) con [x, y].
     *         Retorna null si la operación falla o el punto está fuera de rango.
     */
    fun getPosTan(distance: Float): PosTan?
}

/**
 * Implementación **multiplataforma** sobre [androidx.compose.ui.graphics.PathMeasure] (respaldado por
 * Skia en Desktop/Web/iOS y por `android.graphics.PathMeasure` en Android). Da precisión completa en
 * **todas** las plataformas — antes solo Android tenía una implementación real (Desktop/Web devolvían
 * `null` en [getSegment] → el segmento animado del anillo de selección poligonal caía al anillo
 * estático, sin el pulso giratorio; iOS lanzaba `TODO()`).
 */
private class ComposePathMeasure(path: Path, closed: Boolean) : PathMeasureApi {
    private val measure = PathMeasure().apply { setPath(path, closed) }

    override val length: Float get() = measure.length

    override fun getSegment(
        startDistance: Float,
        endDistance: Float,
        startWithMoveTo: Boolean,
    ): Path? {
        val destination = Path()
        val ok = measure.getSegment(startDistance, endDistance, destination, startWithMoveTo)
        return if (ok) destination else null
    }

    override fun getPosTan(distance: Float): PosTan? {
        val position = measure.getPosition(distance)
        if (!position.isSpecified) return null
        val tangent = measure.getTangent(distance)
        val t = if (tangent.isSpecified) tangent else Offset(1f, 0f)
        return PosTan(
            position = floatArrayOf(position.x, position.y),
            tangent = floatArrayOf(t.x, t.y),
        )
    }
}

/**
 * Crea una instancia de PathMeasureApi para medir el [path] dado.
 *
 * @param path Path a medir
 * @param closed Si el path debe tratarse como cerrado (conectar fin con inicio)
 * @return Instancia de PathMeasureApi (misma implementación en todas las plataformas)
 */
fun createPathMeasure(path: Path, closed: Boolean = false): PathMeasureApi =
    ComposePathMeasure(path, closed)

/**
 * Resultado de [PathMeasureApi.getPosTan].
 *
 * @property position Posición [x, y] en el path
 * @property tangent Vector tangente unitario [x, y] en ese punto
 */
data class PosTan(
    val position: FloatArray,
    val tangent: FloatArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as PosTan

        if (!position.contentEquals(other.position)) return false
        if (!tangent.contentEquals(other.tangent)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = position.contentHashCode()
        result = 31 * result + tangent.contentHashCode()
        return result
    }
}