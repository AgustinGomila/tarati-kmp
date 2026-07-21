package com.agustin.tarati.network


import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.request
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlin.coroutines.cancellation.CancellationException

/**
 * Helpers para los endpoints REST autenticados del servidor de Tarati.
 *
 * Convergen el patrón que se repetía en los repositorios y services del cliente
 * (`runCatching { httpClient.get(...) { bearerAuth(token) }.body() }`) en un solo punto:
 *
 * - El token JWT viaja como `Authorization: Bearer`.
 * - Las respuestas no-2xx fallan con [ApiHttpException]. Los clientes no usan `expectSuccess`,
 *   así que sin este chequeo un error del servidor podía deserializarse "con éxito" en un DTO
 *   con defaults, o fallar con un error de parseo críptico.
 * - La cancelación estructurada se propaga: una corrutina cancelada nunca produce
 *   `Result.failure` (a diferencia de `runCatching`, que atrapa [CancellationException]).
 */

/** Falla de un endpoint REST: el servidor respondió con status no-2xx. */
class ApiHttpException(
    val status: HttpStatusCode,
    url: String,
) : Exception("HTTP ${status.value} ${status.description} — $url")

/**
 * Como [runCatching], pero relanza [CancellationException] para no romper la
 * cancelación estructurada de corrutinas.
 */
inline fun <T> runCatchingCancellable(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    Result.failure(e)
}

/** Lanza [ApiHttpException] si el status no es 2xx; devuelve la respuesta intacta si lo es. */
fun HttpResponse.requireSuccess(): HttpResponse {
    if (!status.isSuccess()) throw ApiHttpException(status, request.url.toString())
    return this
}

/**
 * GET autenticado que deserializa la respuesta como [T].
 *
 * @param params Query params; los valores `null` se omiten (mismo contrato que [parameter]).
 */
suspend inline fun <reified T> HttpClient.authGet(
    url: String,
    token: String,
    vararg params: Pair<String, Any?>,
): Result<T> = runCatchingCancellable {
    get(url) {
        bearerAuth(token)
        params.forEach { (key, value) -> parameter(key, value) }
    }.requireSuccess().body()
}

/**
 * POST autenticado que deserializa la respuesta como [T] (usar `Unit` para ignorarla).
 *
 * @param configure Bloque opcional para el body (`contentType(...)` + `setBody(...)`).
 */
suspend inline fun <reified T> HttpClient.authPost(
    url: String,
    token: String,
    crossinline configure: HttpRequestBuilder.() -> Unit = {},
): Result<T> = runCatchingCancellable {
    post(url) {
        bearerAuth(token)
        configure()
    }.requireSuccess().body()
}

/** DELETE autenticado que deserializa la respuesta como [T] (usar `Unit` para ignorarla). */
suspend inline fun <reified T> HttpClient.authDelete(
    url: String,
    token: String,
): Result<T> = runCatchingCancellable {
    delete(url) {
        bearerAuth(token)
    }.requireSuccess().body()
}
