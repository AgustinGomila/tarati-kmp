package com.agustin.tarati.core.domain.ai.evaluator

/**
 * Política de **selección en la raíz**: variedad controlada partida a partida sin
 * debilitar el juego, aplicada **solo en el nodo raíz** de
 * [com.agustin.tarati.core.domain.ai.engine.MinimaxStrategy] (la jugada que efectivamente
 * se juega). La búsqueda profunda sigue siendo determinista: esto separa el *valor* de la
 * posición (el mejor score, intacto) de *cuál* de las jugadas cuasi-óptimas se juega.
 *
 * Es la respuesta al problema "dos CHAMPION juegan siempre la misma partida" (desempate
 * keep-first + `evalNoise=0`). A diferencia de [BehaviorConfig.evalNoise] —que perturba
 * cada hoja y por tanto **debilita**— aquí solo se elige entre jugadas que la búsqueda ya
 * consideró (casi) igual de buenas, con un sesgo hacia la más afilada.
 *
 * @property enabled     Si la política se aplica. `false` → comportamiento previo (keep-first/reservoir).
 * @property epsilon     Margen de score por debajo del mejor para admitir una jugada como candidata.
 *                       `0.0` → solo empates exactos (**costo de fuerza cero**). `>0` → admite cuasi-empates
 *                       (variedad extra a un costo acotado y medible; ver `ChampionVarietyTest`). Para que
 *                       los cuasi-empates lleguen con score preciso, la raíz relaja su ventana alpha/beta
 *                       en `epsilon` (menos poda solo en la raíz, costo proporcional a `epsilon`).
 * @property temperature Temperatura del softmax sobre la **posición** de la candidata (0 = la más afilada,
 *                       ordenada por score y luego por el orden de calidad de `sortMoves`). `0.0` → keep-first
 *                       determinista (la más afilada siempre). Mayor → más peso a las siguientes → más variedad.
 *                       Con `epsilon=0` y `temperature>0` se obtiene variedad **gratis** (solo entre óptimos
 *                       exactos), sesgada hacia la jugada afilada → esquiva OBS-1 (no abre con la pasiva).
 */
data class RootSelection(
    val enabled: Boolean = false,
    val epsilon: Double = 0.0,
    val temperature: Double = 0.0,
)
