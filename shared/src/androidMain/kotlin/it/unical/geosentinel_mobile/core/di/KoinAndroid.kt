// Avvio della dependency injection (Koin) su Android.
// Raccoglie ciò che solo Android può dare (Context, cartella dei file, builder del database) e lo passa
// alla funzione comune `startKoinWith`, che registra tutti i moduli dell'app.
package it.unical.geosentinel_mobile.core.di

import android.content.Context
import it.unical.geosentinel_mobile.api.model.RegisterDeviceRequest
import it.unical.geosentinel_mobile.core.config.AppConfig
import it.unical.geosentinel_mobile.core.db.androidDatabaseBuilder

/**
 * Avvia Koin per l'app Android. Va chiamata una sola volta per processo, da `GeoSentinelApplication.onCreate`.
 *
 * Il [context] serve per la cartella dei file privati (dove sta lo store dei token) e a Room per la cartella
 * `databases/`; la piattaforma dichiarata è quella con cui il dispositivo si registra per le notifiche push.
 */
fun initKoin(context: Context, config: AppConfig) =
    startKoinWith(config, context.filesDir.absolutePath, RegisterDeviceRequest.Platform.ANDROID) { androidDatabaseBuilder(context) }
