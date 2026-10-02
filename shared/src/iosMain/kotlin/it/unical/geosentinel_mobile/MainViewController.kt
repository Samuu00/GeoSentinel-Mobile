package it.unical.geosentinel_mobile

import androidx.compose.ui.window.ComposeUIViewController
import it.unical.geosentinel_mobile.core.config.AppConfig
import platform.Foundation.NSBundle
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform

@OptIn(ExperimentalNativeApi::class)
fun MainViewController() = ComposeUIViewController {
    // API_BASE_URL arriva da Config.xcconfig attraverso Info.plist
    val apiBaseUrl = NSBundle.mainBundle.objectForInfoDictionaryKey("API_BASE_URL") as? String
        ?: error("API_BASE_URL mancante in Info.plist")
    App(AppConfig(apiBaseUrl = apiBaseUrl, isDebug = Platform.isDebugBinary))
}
