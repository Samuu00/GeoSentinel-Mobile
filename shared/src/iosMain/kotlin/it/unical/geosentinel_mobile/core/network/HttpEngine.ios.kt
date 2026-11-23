// Implementazione iOS del motore HTTP usato da Ktor.
// Su iOS le richieste passano dal motore Darwin, che usa NSURLSession del sistema.
package it.unical.geosentinel_mobile.core.network

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin

/** Crea il motore Darwin su cui si appoggiano tutti gli `HttpClient` dell'app su iOS. */
internal actual fun platformHttpEngine(): HttpClientEngine = Darwin.create()
