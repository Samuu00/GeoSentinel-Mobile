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
import it.unical.geosentinel_mobile.api.model.RegisterDeviceRequest
import it.unical.geosentinel_mobile.core.push.PushManager
import it.unical.geosentinel_mobile.core.push.PushTokenSink
import it.unical.geosentinel_mobile.core.session.SessionManager
import it.unical.geosentinel_mobile.core.session.SessionState
import it.unical.geosentinel_mobile.core.session.SecureTokenStore
import it.unical.geosentinel_mobile.core.session.createSecureTokenStore
import it.unical.geosentinel_mobile.core.ui.theme.ThemeSettings
import it.unical.geosentinel_mobile.profile.ui.ChangePasswordViewModel
import it.unical.geosentinel_mobile.profile.ui.EditNameViewModel
import it.unical.geosentinel_mobile.profile.ui.ProfileViewModel
import it.unical.geosentinel_mobile.hazard.data.HazardRepository
import it.unical.geosentinel_mobile.hazard.ui.HazardMapViewModel
import it.unical.geosentinel_mobile.hazard.ui.MyHazardsViewModel
import it.unical.geosentinel_mobile.hazard.ui.ReportHazardViewModel
import it.unical.geosentinel_mobile.media.data.ImageEncoder
import it.unical.geosentinel_mobile.media.data.MediaRepository
import it.unical.geosentinel_mobile.media.data.MediaUploader
import it.unical.geosentinel_mobile.refuge.data.BookingRepository
import it.unical.geosentinel_mobile.refuge.data.RefugeRepository
import it.unical.geosentinel_mobile.refuge.ui.BookRefugeViewModel
import it.unical.geosentinel_mobile.refuge.ui.MyBookingsViewModel
import it.unical.geosentinel_mobile.refuge.ui.RefugeDetailViewModel
import it.unical.geosentinel_mobile.refuge.ui.RefugeListViewModel
import it.unical.geosentinel_mobile.refuge.ui.RefugeMapViewModel
import it.unical.geosentinel_mobile.trail.data.MapSettings
import it.unical.geosentinel_mobile.trail.data.TrailRepository
import it.unical.geosentinel_mobile.trail.ui.TrailDetailViewModel
import it.unical.geosentinel_mobile.trail.ui.TrailMapViewModel
import it.unical.geosentinel_mobile.sos.data.SosRepository
import it.unical.geosentinel_mobile.sos.ui.SosController
import it.unical.geosentinel_mobile.sos.ui.SosViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import okio.Path.Companion.toPath
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.koin.mp.KoinPlatform

// databaseBuilder arriva dalla piattaforma: su Android Room vuole il Context
fun coreModule(
    config: AppConfig,
    filesDir: String,
    pushPlatform: RegisterDeviceRequest.Platform,
    databaseBuilder: () -> RoomDatabase.Builder<AppDatabase>,
) = module {
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
    // Al logout il dispositivo si toglie dalle push (M7.1) con il token che la piattaforma ha passato a PushManager
    single {
        SessionManager(
            store = get(),
            httpClient = { get<HttpClient>() },
            apiBaseUrl = config.apiBaseUrl,
            pushToken = { get<PushManager>().token.value },
        )
    }
    single { HttpClientFactory.createHttpClient(config, get<SessionManager>(), platformHttpEngine()) }

    // Repository del catalogo (M3.2): tutti sul client condiviso; i sentieri anche con la cache (M3.3)
    single { TrailRepository(httpClient = { get<HttpClient>() }, apiBaseUrl = config.apiBaseUrl, dao = get()) }
    single { HazardRepository(httpClient = { get<HttpClient>() }, apiBaseUrl = config.apiBaseUrl) }
    single { MediaRepository(httpClient = { get<HttpClient>() }, apiBaseUrl = config.apiBaseUrl) }
    single { RefugeRepository(httpClient = { get<HttpClient>() }, apiBaseUrl = config.apiBaseUrl) }
    // Prenotazioni (M5.2) con l'outbox nel database: un solo repository, così un solo invio alla volta in tutta l'app
    single { get<AppDatabase>().outboxDao() }
    single { BookingRepository(httpClient = { get<HttpClient>() }, apiBaseUrl = config.apiBaseUrl, outbox = get()) }
    // Upload delle foto (M4.2): l'API sul client autenticato, lo storage su un client suo, senza Auth
    single {
        MediaUploader(
            httpClient = { get<HttpClient>() },
            storageClient = { HttpClientFactory.createStorageClient(platformHttpEngine()) },
            apiBaseUrl = config.apiBaseUrl,
        )
    }
    // Push (M7.1): il token arriva dalla piattaforma, la registrazione la decide App in base alla sessione
    single { PushManager(httpClient = { get<HttpClient>() }, apiBaseUrl = config.apiBaseUrl, platform = pushPlatform) }
    single<PushTokenSink> { get<PushManager>() }
    single { ImageEncoder() }
    // SOS (M6.1): un solo controller per processo, con uno scope suo, così l'invio continua fuori dalla schermata.
    // L'utente anche senza profilo letto (avvio senza rete): l'SOS non può aspettare GET /users/me
    single { SosRepository(httpClient = { get<HttpClient>() }, apiBaseUrl = config.apiBaseUrl) }
    single {
        val session = get<SessionManager>()
        SosController(get(), get(), ownerId = { session.currentUserId() }, scope = CoroutineScope(SupervisorJob() + Dispatchers.IO))
    }

    viewModel { LoginViewModel(get()) }
    viewModel { RegisterViewModel(get()) }
    viewModel { ProfileViewModel(get()) }
    viewModel { EditNameViewModel(get()) }
    viewModel { ChangePasswordViewModel(get()) }
    viewModel { TrailMapViewModel(get()) }
    viewModel { MyHazardsViewModel(get()) }
    viewModel { HazardMapViewModel(get()) }
    // l'id del sentiero arriva dalla rotta (koinViewModel { parametersOf(id) })
    viewModel { (trailId: String) -> TrailDetailViewModel(trailId, get(), get(), get(), get()) }
    // trailId facoltativo: null se la segnalazione parte dalla mappa
    viewModel { params -> ReportHazardViewModel(get(), get(), params.getOrNull<String>()) }
    viewModel { RefugeListViewModel(get()) }
    viewModel { RefugeMapViewModel(get()) }
    // l'id del rifugio arriva dalla rotta, come per il sentiero
    viewModel { (refugeId: String) -> RefugeDetailViewModel(refugeId, get()) }
    // id e posti del rifugio dalla rotta; l'utente dalla sessione, letto al momento dell'invio
    viewModel { (refugeId: String, capacity: Int) ->
        val session = get<SessionManager>()
        BookRefugeViewModel(refugeId, capacity, get(), ownerId = { session.userId() })
    }
    viewModel {
        val session = get<SessionManager>()
        MyBookingsViewModel(get(), get(), ownerId = { session.userId() })
    }
    viewModel { SosViewModel(get(), get<SessionManager>().state) }
}

// L'id dell'utente loggato (null da ospite o senza profilo letto): l'outbox lega a lui le azioni in coda
private fun SessionManager.userId(): String? = (state.value as? SessionState.LoggedIn)?.profile?.id

// Una sola volta per processo: initKoin(context, config) da Application.onCreate su Android, MainViewController su
// iOS. filesDir e il database arrivano dalla piattaforma (filesDir e databases/ su Android, Documents e Application
// Support su iOS). Restituisce Unit perché androidApp non dipende da Koin.
internal fun startKoinWith(
    config: AppConfig,
    filesDir: String,
    pushPlatform: RegisterDeviceRequest.Platform,
    databaseBuilder: () -> RoomDatabase.Builder<AppDatabase>,
) {
    startKoin { modules(coreModule(config, filesDir, pushPlatform, databaseBuilder)) }
}

// Per il codice di piattaforma che non usa Koin: FirebaseMessagingService e MainActivity (Android), AppDelegate (Swift)
fun pushManager(): PushManager = KoinPlatform.getKoin().get()

const val PREFERENCES_FILE_NAME = "geosentinel.preferences_pb"
