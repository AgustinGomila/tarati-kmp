package com.agustin.tarati.core.utils

import androidx.lifecycle.SavedStateHandle
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

/**
 * Extensiones para SavedStateHandle con soporte para kotlinx.serialization.
 *
 * Permite guardar/recuperar objetos @Serializable serializándolos como JSON strings.
 */

/**
 * Configuración de Json tolerante usada por [putSerializable]/[getSerializable].
 * Pública porque las funciones inline reified la referencian desde el call site.
 */
val savedStateJson: Json = Json {
    ignoreUnknownKeys = true
    prettyPrint = false
    allowStructuredMapKeys = true
}

/**
 * Guarda un objeto serializable en SavedStateHandle como JSON string.
 */
inline fun <reified T> SavedStateHandle.putSerializable(key: String, value: T?) {
    if (value == null) {
        remove<String>(key)
    } else {
        val serializer = serializer<T>()
        val jsonString = savedStateJson.encodeToString(serializer, value)
        set(key, jsonString)
    }
}

/**
 * Recupera un objeto serializable desde SavedStateHandle (deserializa desde JSON).
 */
inline fun <reified T> SavedStateHandle.getSerializable(key: String): T? {
    val jsonString = get<String>(key) ?: return null
    return try {
        val serializer = serializer<T>()
        savedStateJson.decodeFromString(serializer, jsonString)
    } catch (e: Exception) {
        null
    }
}
