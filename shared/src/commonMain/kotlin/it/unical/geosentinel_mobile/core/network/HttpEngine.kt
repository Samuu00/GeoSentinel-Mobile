// Dichiarazione comune del motore HTTP di Ktor.
// Il codice condiviso costruisce i client senza sapere su quale piattaforma gira: il motore concreto
// lo fornisce ogni piattaforma con un `actual` (OkHttp su Android, Darwin su iOS).
package it.unical.geosentinel_mobile.core.network

import io.ktor.client.engine.HttpClientEngine

/**
 * Restituisce il motore HTTP nativo della piattaforma: OkHttp su Android, Darwin (basato su NSURLSession) su iOS.
 * Lo usa la factory dei client in `HttpClientFactory.kt`.
 */
internal expect fun platformHttpEngine(): HttpClientEngine
