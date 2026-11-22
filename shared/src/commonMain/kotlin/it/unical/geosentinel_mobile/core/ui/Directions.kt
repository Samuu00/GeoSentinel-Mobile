package it.unical.geosentinel_mobile.core.ui

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * "Indicazioni" fino a un punto con un nome: porta alla mappa e chiede il percorso al nostro motore (`GET /trails/route`,
 * ADR-0028 del backend), mai le mappe del telefono. Lo fornisce `App`; fuori da `App` (anteprime, test) non fa nulla.
 */
val LocalDirections = staticCompositionLocalOf<(name: String, lat: Double, lon: Double) -> Unit> { { _, _, _ -> } }
