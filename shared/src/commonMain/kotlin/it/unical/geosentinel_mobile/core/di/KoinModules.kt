package it.unical.geosentinel_mobile.core.di

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.RoomDatabase
import io.ktor.client.HttpClient
import it.unical.geosentinel_mobile.auth.ui.LoginViewModel
import it.unical.geosentinel_mobile.auth.ui.RegisterViewModel
import it.unical.geosentinel_mobile.core.config.AppConfig
import it.unical.geosentinel_mobile.core.db.AppDatabase
import it.unical.geosentinel_mobile.core.db.OutboxDao
import it.unical.geosentinel_mobile.core.db.clearAfterSession
import it.unical.geosentinel_mobile.core.db.createAppDatabase
import it.unical.geosentinel_mobile.core.network.HttpClientFactory
import it.unical.geosentinel_mobile.core.network.platformHttpEngine
import it.unical.geosentinel_mobile.api.model.RegisterDeviceRequest
import it.unical.geosentinel_mobile.core.push.PushManager
import it.unical.geosentinel_mobile.core.push.PushTokenSink
import it.unical.geosentinel_mobile.core.session.SessionManager
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
import it.unical.geosentinel_mobile.rescue.data.RescueRepository
import it.unical.geosentinel_mobile.rescue.ui.RescueAlertDetailViewModel
import it.unical.geosentinel_mobile.rescue.ui.RescueAlertsListViewModel
import it.unical.geosentinel_mobile.rescue.ui.RescueMapViewModel
import it.unical.geosentinel_mobile.staff.data.StaffRepository
import it.unical.geosentinel_mobile.staff.ui.HazardQueueViewModel
import it.unical.geosentinel_mobile.staff.ui.PoiCreateViewModel
import it.unical.geosentinel_mobile.staff.ui.TrailStatusViewModel
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
    // gli upload delle foto (M4.2) vivono più della schermata: uscendo a metà la foto arriva lo stesso
    val uploads = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ---- Configurazione, database e preferenze ----
    single { config }
    // un solo database per processo: cache del catalogo (M3.3) e outbox (M5.2, M6.1)
    single { createAppDatabase(databaseBuilder()) }
    single { get<AppDatabase>().catalogDao() }
    single { get<AppDatabase>().outboxDao() }
    // un solo DataStore per file: due istanze sullo stesso file lanciano un'eccezione
    single { PreferenceDataStoreFactory.createWithPath(produceFile = { "$filesDir/$PREFERENCES_FILE_NAME".toPath() }) }
    single { ThemeSettings(get()) }
    single { MapSettings(get()) }
    // i token hanno un file tutto loro (Android), escluso dal backup; su iOS stanno nel Keychain
    single<SecureTokenStore> { createSecureTokenStore(filesDir) }

    // ---- Sessione e rete ----
    // Un solo client autenticato e una sola sessione per tutta l'app: il refresh token vale una volta sola.
    // SessionManager riceve il client come funzione perché il client, a sua volta, usa SessionManager per i token.
    // Al logout il dispositivo si toglie dalle push (M7.1) con il token che la piattaforma ha passato a PushManager
    single {
        SessionManager(
            store = get(),
            httpClient = { get<HttpClient>() },
            apiBaseUrl = config.apiBaseUrl,
            pushToken = { get<PushManager>().token.value },
            // Fine sessione: token push sempre invalidato. Le azioni in coda dell'utente (posizione e note mediche di un
            // SOS, date delle prenotazioni) si tolgono quando lascia il telefono (logout, account cancellato), non
            // quando la sessione scade da sola: rientrando l'SOS deve ripartire. Al logout resta solo la verifica di un
            // SOS annullato (senza dati personali), che riparte al suo prossimo login
            onSessionEnded = { userId, reason ->
                get<PushManager>().sessionEnded()
                if (userId != null) get<OutboxDao>().clearAfterSession(userId, reason)
            },
        )
    }
    single { HttpClientFactory.createHttpClient(config, get<SessionManager>(), platformHttpEngine()) }

    // ---- Repository: tutti sul client condiviso ----
    // i sentieri anche con la cache (M3.3)
    single { TrailRepository(httpClient = { get<HttpClient>() }, apiBaseUrl = config.apiBaseUrl, dao = get()) }
    single { HazardRepository(httpClient = { get<HttpClient>() }, apiBaseUrl = config.apiBaseUrl) }
    single { MediaRepository(httpClient = { get<HttpClient>() }, apiBaseUrl = config.apiBaseUrl) }
    single { RefugeRepository(httpClient = { get<HttpClient>() }, apiBaseUrl = config.apiBaseUrl) }
    // prenotazioni (M5.2) con l'outbox: un solo repository, così un solo invio alla volta in tutta l'app
    single {
        val session = get<SessionManager>()
        BookingRepository(httpClient = { get<HttpClient>() }, apiBaseUrl = config.apiBaseUrl, outbox = get(), currentOwner = { session.currentUserId() })
    }
    single { SosRepository(httpClient = { get<HttpClient>() }, apiBaseUrl = config.apiBaseUrl) }
    // soccorso (M7.2): allerte e squadre per l'operatore
    single { RescueRepository(httpClient = { get<HttpClient>() }, apiBaseUrl = config.apiBaseUrl) }
    single { StaffRepository(httpClient = { get<HttpClient>() }, apiBaseUrl = config.apiBaseUrl) }

    // ---- Servizi con stato, uno per processo ----
    // Upload delle foto (M4.2): l'API sul client autenticato, lo storage su un client suo, senza Auth
    single {
        MediaUploader(
            httpClient = { get<HttpClient>() },
            storageClient = { HttpClientFactory.createStorageClient(platformHttpEngine()) },
            apiBaseUrl = config.apiBaseUrl,
        )
    }
    single { ImageEncoder() }
    // Push (M7.1): il token arriva dalla piattaforma, la registrazione la decide App in base alla sessione
    single { PushManager(httpClient = { get<HttpClient>() }, apiBaseUrl = config.apiBaseUrl, platform = pushPlatform) }
    single<PushTokenSink> { get<PushManager>() }
    // SOS (M6.1): un solo controller con uno scope suo, così l'invio continua fuori dalla schermata.
    // L'utente anche senza profilo letto (avvio senza rete): l'SOS non può aspettare GET /users/me
    single {
        val session = get<SessionManager>()
        SosController(get(), get(), ownerId = { session.currentUserId() }, scope = CoroutineScope(SupervisorJob() + Dispatchers.IO))
    }

    // ---- ViewModel, per feature ----
    // accesso e profilo
    viewModel { LoginViewModel(get()) }
    viewModel { RegisterViewModel(get()) }
    viewModel { ProfileViewModel(get()) }
    viewModel { EditNameViewModel(get()) }
    viewModel { ChangePasswordViewModel(get()) }
    // mappa e sentieri; l'id del sentiero arriva dalla rotta (koinViewModel { parametersOf(id) })
    viewModel { TrailMapViewModel(get()) }
    viewModel { (trailId: String) -> TrailDetailViewModel(trailId, get(), get(), get(), get()) }
    // pericoli; trailId facoltativo: null se la segnalazione parte dalla mappa
    viewModel { HazardMapViewModel(get()) }
    viewModel { MyHazardsViewModel(get()) }
    viewModel { params -> ReportHazardViewModel(get(), get(), params.getOrNull<String>(), uploadScope = uploads) }
    // rifugi e prenotazioni; id e posti del rifugio dalla rotta, l'utente dalla sessione al momento dell'invio
    viewModel { RefugeListViewModel(get()) }
    viewModel { RefugeMapViewModel(get()) }
    viewModel { (refugeId: String) -> RefugeDetailViewModel(refugeId, get()) }
    viewModel { (refugeId: String, capacity: Int) ->
        val session = get<SessionManager>()
        BookRefugeViewModel(refugeId, capacity, get(), ownerId = { session.currentUserId() })
    }
    viewModel {
        val session = get<SessionManager>()
        MyBookingsViewModel(get(), get(), ownerId = { session.currentUserId() })
    }
    // SOS dell'escursionista
    viewModel { SosViewModel(get(), get<SessionManager>().state) }
    // soccorso (M7.2); l'id dell'allerta dalla rotta
    viewModel { RescueAlertsListViewModel(get()) }
    viewModel { RescueMapViewModel(get()) }
    viewModel { (alertId: String) -> RescueAlertDetailViewModel(alertId, get()) }

    viewModel { (trailId: String) -> HazardQueueViewModel(trailId, get(), get()) }
    viewModel { (trailId: String) -> TrailStatusViewModel(trailId, get(), get()) }
    viewModel { params -> PoiCreateViewModel(params.getOrNull<String>(), get(), get()) }
}

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
