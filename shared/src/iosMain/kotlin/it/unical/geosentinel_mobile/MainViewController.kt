// Punto d'ingresso del codice condiviso su iOS. Il lato Swift (AppDelegate e ContentView) chiama queste funzioni
// per avviare Koin con la configurazione letta da Info.plist e per ottenere il UIViewController che ospita la UI
// Compose dell'app.
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

/**
 * Avvia Koin una sola volta per processo. Viene chiamata sia dall'AppDelegate sia da MainViewController: il tocco su
 * una notifica ad app chiusa arriva all'AppDelegate prima che esista la UI, quindi le dipendenze devono essere già
 * pronte. Chi arriva per primo avvia Koin, il secondo trova l'istanza esistente ed esce; entrambe le chiamate
 * avvengono sul main thread, quindi il controllo non ha corse.
 * Lancia un'eccezione se in Info.plist manca API_BASE_URL.
 */
@OptIn(ExperimentalNativeApi::class)
fun startShared() {
    if (KoinPlatform.getKoinOrNull() != null) return
    // L'indirizzo del backend è definito in Config.xcconfig e arriva qui attraverso Info.plist.
    val apiBaseUrl = NSBundle.mainBundle.objectForInfoDictionaryKey("API_BASE_URL") as? String
        ?: error("API_BASE_URL mancante in Info.plist")
    startKoinWith(
        config = AppConfig(apiBaseUrl = apiBaseUrl, isDebug = Platform.isDebugBinary),
        filesDir = documentsPath(),
        pushPlatform = RegisterDeviceRequest.Platform.IOS,
        databaseBuilder = ::iosDatabaseBuilder,
    )
}

/**
 * Crea il controller che mostra la UI Compose (App) dentro la gerarchia di UIKit. È chiamata una sola volta da
 * ContentView.swift, la radice dell'app.
 */
fun MainViewController(): UIViewController {
    startShared()
    return ComposeUIViewController { App() }
}

/**
 * Percorso della cartella Documents dell'app, dove il codice condiviso salva i propri file (DataStore e database).
 * Lancia un'eccezione se il sistema non restituisce la cartella.
 */
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
