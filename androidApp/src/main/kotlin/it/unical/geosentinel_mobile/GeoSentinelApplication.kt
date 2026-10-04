package it.unical.geosentinel_mobile

import android.app.Application
import it.unical.geosentinel_mobile.core.config.AppConfig
import it.unical.geosentinel_mobile.core.di.initKoin

// Koin parte qui e non in MainActivity: l'Activity si ricrea a ogni rotazione,
// e un secondo startKoin lancerebbe KoinApplicationAlreadyStartedException.
class GeoSentinelApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin(
            context = this,
            config = AppConfig(apiBaseUrl = BuildConfig.API_BASE_URL, isDebug = BuildConfig.DEBUG),
        )
    }
}
