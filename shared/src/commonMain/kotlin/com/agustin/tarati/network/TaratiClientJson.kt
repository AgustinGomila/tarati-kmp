package com.agustin.tarati.network

import kotlinx.serialization.json.Json

/**
 * Configuración `Json` canónica del cliente HTTP (ContentNegotiation), compartida por
 * todas las plataformas (Android/Desktop/Web).
 *
 * Existe para eliminar el drift que había entre los módulos de Koin: cada plataforma
 * declaraba su propio `Json { ... }` y no coincidían (Android tenía `isLenient`, el resto
 * no). Se toma la unión de flags — solo afecta la **decodificación** (aceptar más), nunca
 * rompe el parseo de JSON válido:
 *
 * - `ignoreUnknownKeys`: el servidor puede agregar campos sin romper clientes viejos.
 * - `encodeDefaults`: los defaults viajan en el body de los requests.
 * - `isLenient`: tolera JSON no estricto en las respuestas.
 */
val taratiClientJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    isLenient = true
}
