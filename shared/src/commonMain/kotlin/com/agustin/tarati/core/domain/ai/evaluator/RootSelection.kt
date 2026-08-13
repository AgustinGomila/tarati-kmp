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
 * ## Ventana de apertura
 * Con `epsilon=0` la variedad se limita a **empates exactos**, que en la apertura son solo los
 * reflejos izquierda/derecha (mismo juego, espejado) — la elección estratégica real
 * (avanzar vs. ir al costado) tiene scores distintos y **no** varía. Para variedad estratégica
 * genuina donde importa (aperturas equilibradas) sin costo en medio juego/final, los primeros
 * [openingPlies] plies usan [openingEpsilon]/[openingTemperature] (admiten cuasi-óptimas);
 * pasada la apertura se vuelve a [epsilon]/[temperature]. El ply se estima con el total de
 * posiciones del `positionHistory` (topología-agnóstico, cross-plataforma).
 *
 * @property enabled            Si la política se aplica. `false` → comportamiento previo (keep-first/reservoir).
 * @property epsilon            Margen de score por debajo del mejor para admitir una candidata (medio juego/final).
 *                              `0.0` → solo empates exactos (**costo de fuerza cero**).
 * @property temperature        Temperatura del softmax sobre la posición de la candidata (medio juego/final).
 *                              `0.0` → keep-first determinista; mayor → más variedad.
 * @property openingPlies       Cantidad de plies iniciales tratados como "apertura". `0` → sin ventana especial.
 * @property openingEpsilon     [epsilon] durante la apertura (típicamente > `epsilon` para admitir avanzar/costado).
 * @property openingTemperature [temperature] durante la apertura.
 */
data class RootSelection(
    val enabled: Boolean = false,
    val epsilon: Double = 0.0,
    val temperature: Double = 0.0,
    val openingPlies: Int = 0,
    val openingEpsilon: Double = 0.0,
    val openingTemperature: Double = 0.0,
)
