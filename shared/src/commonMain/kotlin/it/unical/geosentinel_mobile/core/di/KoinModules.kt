package it.unical.geosentinel_mobile.core.di

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import it.unical.geosentinel_mobile.core.config.AppConfig
import it.unical.geosentinel_mobile.core.ui.theme.ThemeSettings
import okio.Path.Companion.toPath
import org.koin.core.context.startKoin
import org.koin.dsl.module

fun coreModule(config: AppConfig, preferencesPath: String) = module {
    single { config }
    // un solo DataStore per file: due istanze sullo stesso file lanciano un'eccezione
    single { PreferenceDataStoreFactory.createWithPath(produceFile = { preferencesPath.toPath() }) }
    single { ThemeSettings(get()) }
}

// Una sola volta per processo: Application.onCreate su Android, MainViewController su iOS.
// preferencesPath arriva dalla piattaforma (filesDir su Android, Documents su iOS).
// Restituisce Unit perché androidApp non dipende da Koin.
fun initKoin(config: AppConfig, preferencesPath: String) {
    startKoin { modules(coreModule(config, preferencesPath)) }
}

const val PREFERENCES_FILE_NAME = "geosentinel.preferences_pb"
