// Supporto alla concorrenza ottimistica con gli ETag.
// Alcune risorse (per esempio i ticket di manutenzione) si modificano solo indicando la versione che si è letta,
// con l'header `If-Match`: qui si conserva l'ETag della risposta insieme al suo contenuto.
package it.unical.geosentinel_mobile.core.network

import io.ktor.http.HttpHeaders
import it.unical.geosentinel_mobile.api.infrastructure.HttpResponse

/**
 * Un valore restituito dal server insieme alla sua versione.
 *
 * @property value il corpo della risposta già decodificato.
 * @property etag l'header `ETag` della risposta, da rimandare in `If-Match` alla modifica successiva;
 *   null se il server non l'ha inviato.
 */
data class Versioned<T>(val value: T, val etag: String?)

/**
 * Esegue la chiamata come [apiCall] (stessa gestione degli errori) e in più legge l'header `ETag` della risposta.
 * Restituisce il valore e l'ETag insieme in un [Versioned], oppure il fallimento con l'[ApiError] corrispondente.
 */
suspend fun <T : Any> apiCallVersioned(block: suspend () -> HttpResponse<T>): Result<Versioned<T>> {
    var etag: String? = null
    return apiCall {
        block().also { etag = it.response.headers[HttpHeaders.ETag] }
    }.map { Versioned(it, etag) }
}
