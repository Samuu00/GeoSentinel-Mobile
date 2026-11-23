// Implementazione Android del motore HTTP usato da Ktor.
// Su Android le richieste di rete passano da OkHttp, la libreria HTTP standard della piattaforma.
package it.unical.geosentinel_mobile.core.network

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp

/** Crea il motore OkHttp su cui si appoggiano tutti gli `HttpClient` dell'app su Android. */
internal actual fun platformHttpEngine(): HttpClientEngine = OkHttp.create()
