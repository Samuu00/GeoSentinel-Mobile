package it.unical.geosentinel_mobile.core.di

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.ktor.client.HttpClient
import it.unical.geosentinel_mobile.auth.ui.LoginViewModel
import it.unical.geosentinel_mobile.auth.ui.RegisterViewModel
import it.unical.geosentinel_mobile.core.config.AppConfig
import it.unical.geosentinel_mobile.core.network.HttpClientFactory
import it.unical.geosentinel_mobile.core.network.platformHttpEngine
import it.unical.geosentinel_mobile.core.session.SessionManager
import it.unical.geosentinel_mobile.core.session.SecureTokenStore
import it.unical.geosentinel_mobile.core.session.createSecureTokenStore
import it.unical.geosentinel_mobile.core.ui.theme.ThemeSettings
import it.unical.geosentinel_mobile.profile.ui.ChangePasswordViewModel
import it.unical.geosentinel_mobile.profile.ui.EditNameViewModel
import it.unical.geosentinel_mobile.profile.ui.ProfileViewModel
import it.unical.geosentinel_mobile.trail.data.MapSettings
import okio.Path.Companion.toPath
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

fun coreModule(config: AppConfig, filesDir: String) = module {
    single { config }
    // un solo DataStore per file: due istanze sullo stesso file lanciano un'eccezione
    single { PreferenceDataStoreFactory.createWithPath(produceFile = { "$filesDir/$PREFERENCES_FILE_NAME".toPath() }) }
    single { ThemeSettings(get()) }
    single { MapSettings(get()) }
    // i token hanno un file tutto loro (Android), escluso dal backup; su iOS stanno nel Keychain
    single<SecureTokenStore> { createSecureTokenStore(filesDir) }

    // Un solo client autenticato e una sola sessione per tutta l'app: il refresh token vale una volta sola.
    // SessionManager riceve il client come funzione perché il client, a sua volta, usa SessionManager per i token.
    single { SessionManager(store = get(), httpClient = { get<HttpClient>() }, apiBaseUrl = config.apiBaseUrl) }
    single { HttpClientFactory.createHttpClient(config, get<SessionManager>(), platformHttpEngine()) }

    viewModel { LoginViewModel(get()) }
    viewModel { RegisterViewModel(get()) }
    viewModel { ProfileViewModel(get()) }
    viewModel { EditNameViewModel(get()) }
    viewModel { ChangePasswordViewModel(get()) }
}

// Una sola volta per processo: Application.onCreate su Android, MainViewController su iOS.
// filesDir arriva dalla piattaforma (filesDir su Android, Documents su iOS).
// Restituisce Unit perché androidApp non dipende da Koin.
fun initKoin(config: AppConfig, filesDir: String) {
    startKoin { modules(coreModule(config, filesDir)) }
}

const val PREFERENCES_FILE_NAME = "geosentinel.preferences_pb"
