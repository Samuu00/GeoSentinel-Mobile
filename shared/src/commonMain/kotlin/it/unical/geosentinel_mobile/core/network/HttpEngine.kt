package it.unical.geosentinel_mobile.core.network

import io.ktor.client.engine.HttpClientEngine

// OkHttp su Android, Darwin (NSURLSession) su iOS
internal expect fun platformHttpEngine(): HttpClientEngine
