package it.unical.geosentinel_mobile.core.di

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.RoomDatabase
import io.ktor.client.HttpClient
import it.unical.geosentinel_mobile.auth.ui.LoginViewModel
import it.unical.geosentinel_mobile.auth.ui.RegisterViewModel
import it.unical.geosentinel_mobile.core.config.AppConfig
import it.unical.geosentinel_mobile.core.db.AppDatabase
import it.unical.geosentinel_mobile.core.db.createAppDatabase
import it.unical.geosentinel_mobile.core.network.HttpClientFactory
import it.unical.geosentinel_mobile.core.network.platformHttpEngine
import it.unical.geosentinel_mobile.core.session.SessionManager
import it.unical.geosentinel_mobile.core.session.SecureTokenStore
import it.unical.geosentinel_mobile.core.session.createSecureTokenStore
import it.unical.geosentinel_mobile.core.ui.theme.ThemeSettings
import it.unical.geosentinel_mobile.profile.ui.ChangePasswordViewModel
import it.unical.geosentinel_mobile.profile.ui.EditNameViewModel
import it.unical.geosentinel_mobile.profile.ui.ProfileViewModel
import it.unical.geosentinel_mobile.hazard.data.HazardRepository
import it.unical.geosentinel_mobile.hazard.ui.ReportHazardViewModel
import it.unical.geosentinel_mobile.media.data.MediaRepository
import it.unical.geosentinel_mobile.trail.data.MapSettings
import it.unical.geosentinel_mobile.trail.data.TrailRepository
import it.unical.geosentinel_mobile.trail.ui.TrailDetailViewModel
import it.unical.geosentinel_mobile.trail.ui.TrailMapViewModel
import okio.Path.Companion.toPath
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

// databaseBuilder arriva dalla piattaforma: su Android Room vuole il Context
fun coreModule(config: AppConfig, filesDir: String, databaseBuilder: () -> RoomDatabase.Builder<AppDatabase>) = module {
    single { config }
    // un solo database per processo (cache del catalogo, poi outbox)
    single { createAppDatabase(databaseBuilder()) }
    single { get<AppDatabase>().catalogDao() }
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

    // Repository del catalogo (M3.2): tutti sul client condiviso; i sentieri anche con la cache (M3.3)
    single { TrailRepository(httpClient = { get<HttpClient>() }, apiBaseUrl = config.apiBaseUrl, dao = get()) }
    single { HazardRepository(httpClient = { get<HttpClient>() }, apiBaseUrl = config.apiBaseUrl) }
    single { MediaRepository(httpClient = { get<HttpClient>() }, apiBaseUrl = config.apiBaseUrl) }

    viewModel { LoginViewModel(get()) }
    viewModel { RegisterViewModel(get()) }
    viewModel { ProfileViewModel(get()) }
    viewModel { EditNameViewModel(get()) }
    viewModel { ChangePasswordViewModel(get()) }
    viewModel { TrailMapViewModel(get()) }
    // l'id del sentiero arriva dalla rotta (koinViewModel { parametersOf(id) })
    viewModel { (trailId: String) -> TrailDetailViewModel(trailId, get(), get(), get(), get()) }
    // trailId facoltativo: null se la segnalazione parte dalla mappa
    viewModel { params -> ReportHazardViewModel(get(), params.getOrNull<String>()) }
}

// Una sola volta per processo: initKoin(context, config) da Application.onCreate su Android, MainViewController su
// iOS. filesDir e il database arrivano dalla piattaforma (filesDir e databases/ su Android, Documents e Application
// Support su iOS). Restituisce Unit perché androidApp non dipende da Koin.
internal fun startKoinWith(config: AppConfig, filesDir: String, databaseBuilder: () -> RoomDatabase.Builder<AppDatabase>) {
    startKoin { modules(coreModule(config, filesDir, databaseBuilder)) }
}

const val PREFERENCES_FILE_NAME = "geosentinel.preferences_pb"
