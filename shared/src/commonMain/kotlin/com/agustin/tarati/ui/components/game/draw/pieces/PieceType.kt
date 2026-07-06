package com.agustin.tarati.ui.components.game.draw.pieces

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import com.agustin.tarati.services.billing.PieceProducts
import com.agustin.tarati.shared.generated.resources.Res
import com.agustin.tarati.shared.generated.resources.piece_type_capsule
import com.agustin.tarati.shared.generated.resources.piece_type_circle
import com.agustin.tarati.shared.generated.resources.piece_type_diamond
import com.agustin.tarati.shared.generated.resources.piece_type_hexagon
import com.agustin.tarati.shared.generated.resources.piece_type_pentagon
import com.agustin.tarati.shared.generated.resources.piece_type_square
import com.agustin.tarati.shared.generated.resources.piece_type_triangle
import com.agustin.tarati.ui.components.game.draw.common.MorphShape
import org.jetbrains.compose.resources.StringResource

// ─────────────────────────────────────────────────────────────────────────────
// PieceType
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Descriptor de un tipo de pieza seleccionable.
 *
 * ## KMP
 * Migrado a shared/commonMain. Usa [StringResource] (Compose Multiplatform Resources)
 * en lugar de @StringRes Int, compatible con Android y Desktop.
 * [PieceProducts] también está en commonMain como constantes de String puras.
 */
data class PieceType(
    val id: String,
    val nameRes: StringResource,
    val shape: MorphShape,
    /**
     * ID del producto en Google Play Console, o `null` para tipos gratuitos.
     * En Desktop, [IBillingManager] retorna siempre desbloqueado para estos IDs.
     */
    val productId: String? = null,
    val borderPattern: BorderPattern = BorderPattern.None,
    val centerMotif: CenterMotif = CenterMotif.Default,
) {
    val isPremium: Boolean get() = productId != null
}

// ─────────────────────────────────────────────────────────────────────────────
// PieceTypes — catálogo
// ─────────────────────────────────────────────────────────────────────────────

object PieceTypes {

    val Circle: PieceType = PieceType(
        id = "circle",
        nameRes = Res.string.piece_type_circle,
        shape = MorphShape(sides = 1, sizeFrac = 0.82f),
    )

    val Hexagon: PieceType = PieceType(
        id = "hexagon",
        nameRes = Res.string.piece_type_hexagon,
        shape = MorphShape(sides = 6, cornerRadius = 12f, sizeFrac = 1.05f),
        productId = PieceProducts.HEXAGON,
        borderPattern = BorderPattern.Meander,
        centerMotif = CenterMotif.Compass,
    )

    val Square: PieceType = PieceType(
        id = "square",
        nameRes = Res.string.piece_type_square,
        shape = MorphShape(sides = 4, cornerRadius = 18f, rotationDeg = 45f, sizeFrac = 1.16f),
        productId = PieceProducts.SQUARE,
        borderPattern = BorderPattern.DoubleRing,
        centerMotif = CenterMotif.Cross,
    )

    val Triangle: PieceType = PieceType(
        id = "triangle",
        nameRes = Res.string.piece_type_triangle,
        // sizeFrac alto: un triángulo inscrito tiene mucha menos área que el resto → necesita más
        // radio para verse del mismo tamaño (paridad visual con las otras formas, incl. Settings).
        shape = MorphShape(sides = 3, cornerRadius = 14f, rotationDeg = -90f, sizeFrac = 1.42f),
        productId = PieceProducts.TRIANGLE,
        borderPattern = BorderPattern.Chevron,
        centerMotif = CenterMotif.Trefoil,
    )

    val Diamond: PieceType = PieceType(
        id = "diamond",
        nameRes = Res.string.piece_type_diamond,
        shape = MorphShape(sides = 4, cornerRadius = 18f, sizeFrac = 1.14f),
        productId = PieceProducts.DIAMOND,
        borderPattern = BorderPattern.Diamonds,
        centerMotif = CenterMotif.DiamondCross,
    )

    val Pentagon: PieceType = PieceType(
        id = "pentagon",
        nameRes = Res.string.piece_type_pentagon,
        shape = MorphShape(sides = 5, cornerRadius = 12f, rotationDeg = -90f, sizeFrac = 1.1f),
        productId = PieceProducts.PENTAGON,
        borderPattern = BorderPattern.Fishtail,
        centerMotif = CenterMotif.Star5,
    )

    val Capsule: PieceType = PieceType(
        id = "capsule",
        nameRes = Res.string.piece_type_capsule,
        shape = MorphShape(sides = 2, cornerRadius = 70f, rotationDeg = -90f, edgeCurveStrength = 0.4f),
        productId = PieceProducts.CAPSULE,
        borderPattern = BorderPattern.DoubleRing,
        centerMotif = CenterMotif.Ring,
    )

    val all: List<PieceType> = listOf(
        Circle, Hexagon, Square, Triangle, Diamond, Pentagon, Capsule,
    )

    val default: PieceType get() = all.first()

    fun findById(id: String): PieceType = all.firstOrNull { it.id == id } ?: default
}

// ─────────────────────────────────────────────────────────────────────────────
// PieceTypeList
// ─────────────────────────────────────────────────────────────────────────────

data class PieceTypeList(val items: List<PieceType>) {
    companion object {
        val All: PieceTypeList = PieceTypeList(PieceTypes.all)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PieceTypeManager
// ─────────────────────────────────────────────────────────────────────────────

object PieceTypeManager {

    private val _current = mutableStateOf(PieceTypes.default)

    val currentPieceType: PieceType get() = _current.value

    fun setPieceType(pieceType: PieceType) {
        _current.value = pieceType
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// LocalPieceType
// ─────────────────────────────────────────────────────────────────────────────

val LocalPieceType: ProvidableCompositionLocal<PieceType> = compositionLocalOf { PieceTypes.default }