package it.unical.geosentinel_mobile

import androidx.compose.ui.window.ComposeUIViewController
import it.unical.geosentinel_mobile.api.model.RegisterDeviceRequest
import it.unical.geosentinel_mobile.core.config.AppConfig
import it.unical.geosentinel_mobile.core.db.iosDatabaseBuilder
import it.unical.geosentinel_mobile.core.di.startKoinWith
import kotlinx.cinterop.ExperimentalForeignApi
import org.koin.mp.KoinPlatform
import platform.Foundation.NSBundle
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask
import platform.UIKit.UIViewController
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform

// Koin una volta per processo (M7.1): dall'AppDelegate, perché il tocco su una notifica ad app chiusa arriva prima della
// UI, e da MainViewController; chi arriva per primo lo avvia, il secondo non fa nulla (sempre sul main thread)
@OptIn(ExperimentalNativeApi::class)
fun startShared() {
    if (KoinPlatform.getKoinOrNull() != null) return
    // API_BASE_URL arriva da Config.xcconfig attraverso Info.plist
    val apiBaseUrl = NSBundle.mainBundle.objectForInfoDictionaryKey("API_BASE_URL") as? String
        ?: error("API_BASE_URL mancante in Info.plist")
    startKoinWith(
        config = AppConfig(apiBaseUrl = apiBaseUrl, isDebug = Platform.isDebugBinary),
        filesDir = documentsPath(),
        pushPlatform = RegisterDeviceRequest.Platform.IOS,
        databaseBuilder = ::iosDatabaseBuilder,
    )
}

// Chiamata una sola volta da ContentView.swift, la radice dell'app
fun MainViewController(): UIViewController {
    startShared()
    return ComposeUIViewController { App() }
}

@OptIn(ExperimentalForeignApi::class)
private fun documentsPath(): String {
    val url = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null,
    )
    return requireNotNull(url?.path) { "cartella Documents non disponibile" }
}
