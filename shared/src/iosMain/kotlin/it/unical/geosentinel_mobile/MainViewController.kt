package it.unical.geosentinel_mobile

import androidx.compose.ui.window.ComposeUIViewController
import it.unical.geosentinel_mobile.core.config.AppConfig
import it.unical.geosentinel_mobile.core.di.PREFERENCES_FILE_NAME
import it.unical.geosentinel_mobile.core.di.initKoin
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSBundle
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask
import platform.UIKit.UIViewController
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform

// Chiamata una sola volta da ContentView.swift, la radice dell'app: qui parte anche Koin
@OptIn(ExperimentalNativeApi::class)
fun MainViewController(): UIViewController {
    // API_BASE_URL arriva da Config.xcconfig attraverso Info.plist
    val apiBaseUrl = NSBundle.mainBundle.objectForInfoDictionaryKey("API_BASE_URL") as? String
        ?: error("API_BASE_URL mancante in Info.plist")
    initKoin(
        config = AppConfig(apiBaseUrl = apiBaseUrl, isDebug = Platform.isDebugBinary),
        preferencesPath = documentsPath() + "/" + PREFERENCES_FILE_NAME,
    )
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
