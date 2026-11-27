// Configurazione della dependency injection con Koin.
// Qui si dichiara come costruire ogni componente dell'app (database, client HTTP, sessione, repository, servizi e
// ViewModel) e quali sono condivisi in un'unica istanza. Le schermate ottengono i ViewModel da Koin, che passa loro
// automaticamente le dipendenze.
package it.unical.geosentinel_mobile.core.di

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.RoomDatabase
import io.ktor.client.HttpClient
import it.unical.geosentinel_mobile.auth.ui.LoginViewModel
import it.unical.geosentinel_mobile.auth.ui.PasswordResetViewModel
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
import it.unical.geosentinel_mobile.profile.ui.MfaViewModel
import it.unical.geosentinel_mobile.profile.data.MfaRepository
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
import it.unical.geosentinel_mobile.staff.data.TicketRepository
import it.unical.geosentinel_mobile.staff.ui.HazardQueueViewModel
import it.unical.geosentinel_mobile.staff.ui.MfaResetViewModel
import it.unical.geosentinel_mobile.staff.ui.PoiCreateViewModel
import it.unical.geosentinel_mobile.staff.ui.RefugeCapacityViewModel
import it.unical.geosentinel_mobile.staff.ui.RefugePriceViewModel
import it.unical.geosentinel_mobile.staff.ui.RescueTeamCreateViewModel
import it.unical.geosentinel_mobile.staff.ui.TicketCreateViewModel
import it.unical.geosentinel_mobile.staff.ui.TicketDetailViewModel
import it.unical.geosentinel_mobile.staff.ui.TicketListViewModel
import it.unical.geosentinel_mobile.staff.ui.TrailStatusViewModel
import it.unical.geosentinel_mobile.trail.data.MapSettings
import it.unical.geosentinel_mobile.trail.data.TrailRepository
import it.unical.geosentinel_mobile.trail.data.ElevationRepository
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

/**
 * Il modulo Koin con tutte le dipendenze dell'app.
 *
 * @param config la configurazione letta dalla piattaforma.
 * @param filesDir la cartella dei dati privati, dove stanno i file DataStore.
 * @param pushPlatform la piattaforma con cui il dispositivo si registra per le notifiche push.
 * @param databaseBuilder crea il builder di Room; arriva dalla piattaforma perché su Android Room richiede il Context.
 */
fun coreModule(
    config: AppConfig,
    filesDir: String,
    pushPlatform: RegisterDeviceRequest.Platform,
    databaseBuilder: () -> RoomDatabase.Builder<AppDatabase>,
) = module {
    // Scope per il caricamento delle foto, che vive più a lungo della schermata: se l'utente esce a metà
    // caricamento, la foto arriva lo stesso. SupervisorJob evita che un caricamento fallito annulli gli altri.
    val uploads = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Configurazione, database e preferenze.
    single { config }
    // Un solo database per processo, con la cache del catalogo e l'outbox.
    single { createAppDatabase(databaseBuilder()) }
    single { get<AppDatabase>().catalogDao() }
    single { get<AppDatabase>().outboxDao() }
    // Un solo DataStore per file: due istanze aperte sullo stesso file fanno lanciare un'eccezione.
    single { PreferenceDataStoreFactory.createWithPath(produceFile = { "$filesDir/$PREFERENCES_FILE_NAME".toPath() }) }
    single { ThemeSettings(get()) }
    single { MapSettings(get()) }
    // I token hanno un archivio separato dalle preferenze: su Android un file proprio escluso dal backup,
    // su iOS il Keychain.
    single<SecureTokenStore> { createSecureTokenStore(filesDir) }

    // Sessione e rete.
    // Un solo client autenticato e una sola sessione in tutta l'app, perché il refresh token vale una volta sola.
    // SessionManager riceve il client come funzione perché il client, a sua volta, usa SessionManager per i token:
    // così la dipendenza circolare si risolve solo al primo uso. Al logout il dispositivo viene rimosso dalle push
    // usando il token che la piattaforma ha consegnato a PushManager.
    single {
        SessionManager(
            store = get(),
            httpClient = { get<HttpClient>() },
            apiBaseUrl = config.apiBaseUrl,
            pushToken = { get<PushManager>().token.value },
            // A ogni fine sessione il token push viene invalidato. Le azioni in coda dell'utente (posizione e note
            // mediche di un SOS, date delle prenotazioni) si eliminano quando l'utente lascia il telefono (logout,
            // account cancellato), non quando la sessione scade da sola: dopo il nuovo login l'SOS deve ripartire.
            // Al logout resta solo la verifica di un SOS annullato, che non contiene dati personali e riparte al
            // successivo login dello stesso utente.
            onSessionEnded = { userId, reason ->
                get<PushManager>().sessionEnded()
                if (userId != null) get<OutboxDao>().clearAfterSession(userId, reason)
            },
        )
    }
    single { HttpClientFactory.createHttpClient(config, get<SessionManager>(), platformHttpEngine()) }

    // Repository: usano tutti il client condiviso.
    // Il repository dei sentieri usa anche la cache in Room.
    single { TrailRepository(httpClient = { get<HttpClient>() }, apiBaseUrl = config.apiBaseUrl, dao = get()) }
    single { HazardRepository(httpClient = { get<HttpClient>() }, apiBaseUrl = config.apiBaseUrl) }
    single { MediaRepository(httpClient = { get<HttpClient>() }, apiBaseUrl = config.apiBaseUrl) }
    single { RefugeRepository(httpClient = { get<HttpClient>() }, apiBaseUrl = config.apiBaseUrl) }
    // Prenotazioni con l'outbox: un solo repository in tutta l'app, così le prenotazioni in coda si inviano
    // una alla volta. Il proprietario delle azioni è l'utente della sessione corrente.
    single {
        val session = get<SessionManager>()
        BookingRepository(httpClient = { get<HttpClient>() }, apiBaseUrl = config.apiBaseUrl, outbox = get(), currentOwner = { session.currentUserId() })
    }
    single { SosRepository(httpClient = { get<HttpClient>() }, apiBaseUrl = config.apiBaseUrl) }
    // Soccorso: allerte e squadre, per gli operatori.
    single { RescueRepository(httpClient = { get<HttpClient>() }, apiBaseUrl = config.apiBaseUrl) }
    single { StaffRepository(httpClient = { get<HttpClient>() }, apiBaseUrl = config.apiBaseUrl) }
    single { TicketRepository(httpClient = { get<HttpClient>() }, apiBaseUrl = config.apiBaseUrl) }
    single { MfaRepository(httpClient = { get<HttpClient>() }, apiBaseUrl = config.apiBaseUrl) }

    // Servizi con stato, un'istanza per processo.
    // Caricamento delle foto: le chiamate all'API passano dal client autenticato, il caricamento sullo storage da
    // un client separato senza autenticazione.
    single {
        MediaUploader(
            httpClient = { get<HttpClient>() },
            storageClient = { HttpClientFactory.createStorageClient(platformHttpEngine()) },
            apiBaseUrl = config.apiBaseUrl,
        )
    }
    single { ImageEncoder() }
    // Quote per il profilo altimetrico, lette dalle Terrain Tiles di Mapzen: è un altro host, quindi si usa un
    // client senza autenticazione.
    single { ElevationRepository(client = { HttpClientFactory.createStorageClient(platformHttpEngine()) }) }
    // Push: il token arriva dalla piattaforma, la registrazione la decide App in base alla sessione. La stessa
    // istanza è esposta anche come PushTokenSink per il codice di piattaforma.
    single { PushManager(httpClient = { get<HttpClient>() }, apiBaseUrl = config.apiBaseUrl, platform = pushPlatform) }
    single<PushTokenSink> { get<PushManager>() }
    // SOS: un solo controller con uno scope proprio, così l'invio continua anche lasciando la schermata.
    // L'utente si ricava anche senza aver letto il profilo (avvio senza rete): l'SOS non può aspettare GET /users/me.
    single {
        val session = get<SessionManager>()
        SosController(get(), get(), ownerId = { session.currentUserId() }, scope = CoroutineScope(SupervisorJob() + Dispatchers.IO))
    }

    // ViewModel, raggruppati per funzionalità. I parametri tra parentesi arrivano dalla rotta della schermata
    // (per esempio `koinViewModel { parametersOf(id) }`).
    // Accesso e profilo.
    viewModel { LoginViewModel(get()) }
    viewModel { RegisterViewModel(get()) }
    viewModel { (email: String) -> PasswordResetViewModel(get(), email) }
    viewModel { ProfileViewModel(get(), get(), get()) }
    viewModel { MfaViewModel(get(), get()) }
    viewModel { EditNameViewModel(get()) }
    viewModel { ChangePasswordViewModel(get()) }
    // Mappa e sentieri.
    viewModel { TrailMapViewModel(get()) }
    viewModel { (trailId: String) -> TrailDetailViewModel(trailId, get(), get(), get(), get()) }
    // Pericoli. L'id del sentiero è facoltativo: è null se la segnalazione parte dalla mappa.
    viewModel { HazardMapViewModel(get()) }
    viewModel { MyHazardsViewModel(get()) }
    viewModel { params -> ReportHazardViewModel(get(), get(), get(), params.getOrNull<String>(), uploadScope = uploads) }
    // Rifugi e prenotazioni. L'utente si legge dalla sessione al momento dell'invio, non alla creazione.
    viewModel { RefugeListViewModel(get(), get()) }
    viewModel { RefugeMapViewModel(get()) }
    viewModel { (refugeId: String) -> RefugeDetailViewModel(refugeId, get(), get(), get()) }
    viewModel { (refugeId: String, capacity: Int) ->
        val session = get<SessionManager>()
        val refuges = get<RefugeRepository>()
        BookRefugeViewModel(
            refugeId,
            capacity,
            get(),
            ownerId = { session.currentUserId() },
            loadNights = { from, nights -> refuges.availability(refugeId, from, nights) },
        )
    }
    viewModel {
        val session = get<SessionManager>()
        MyBookingsViewModel(get(), get(), ownerId = { session.currentUserId() })
    }
    // SOS dell'escursionista, che osserva lo stato della sessione.
    viewModel { SosViewModel(get(), get<SessionManager>().state) }
    // Soccorso.
    viewModel { RescueAlertsListViewModel(get()) }
    viewModel { RescueMapViewModel(get()) }
    viewModel { (alertId: String) -> RescueAlertDetailViewModel(alertId, get()) }

    // Gestione del parco. Con l'id del sentiero null, HazardQueueViewModel mostra la coda "Da verificare"
    // di tutto il parco (scheda Gestione).
    viewModel { params -> HazardQueueViewModel(params.getOrNull<String>(), get(), get()) }
    viewModel { (trailId: String) -> TrailStatusViewModel(trailId, get(), get()) }
    viewModel { params -> PoiCreateViewModel(params.getOrNull<String>(), get(), get()) }
    viewModel { TicketListViewModel(get(), get()) }
    viewModel { (ticketId: String) -> TicketDetailViewModel(ticketId, get(), get()) }
    viewModel { (trailId: String) -> TicketCreateViewModel(trailId, get()) }
    // Posti letto dei rifugi, squadre di soccorso e azzeramento della verifica in due passaggi.
    viewModel { (refugeId: String, capacity: Int) -> RefugeCapacityViewModel(refugeId, capacity, get()) }
    viewModel { (refugeId: String, priceDraft: String) -> RefugePriceViewModel(refugeId, priceDraft, get()) }
    viewModel { RescueTeamCreateViewModel(get()) }
    viewModel { MfaResetViewModel(get()) }
}

/**
 * Avvia Koin con il modulo dell'app. Va chiamata una sola volta per processo: da `initKoin(context, config)` in
 * `Application.onCreate` su Android e da `MainViewController` su iOS. Cartella dei dati e builder del database
 * arrivano dalla piattaforma. Restituisce Unit, e non l'oggetto di Koin, perché il modulo androidApp non dipende da Koin.
 */
internal fun startKoinWith(
    config: AppConfig,
    filesDir: String,
    pushPlatform: RegisterDeviceRequest.Platform,
    databaseBuilder: () -> RoomDatabase.Builder<AppDatabase>,
) {
    startKoin { modules(coreModule(config, filesDir, pushPlatform, databaseBuilder)) }
}

/**
 * Restituisce il PushManager condiviso al codice di piattaforma che non usa Koin direttamente:
 * FirebaseMessagingService e MainActivity su Android, l'AppDelegate in Swift (tramite PushBridge) su iOS.
 */
fun pushManager(): PushManager = KoinPlatform.getKoin().get()

// Nome del file DataStore con le preferenze dell'app (tema, impostazioni della mappa).
const val PREFERENCES_FILE_NAME = "geosentinel.preferences_pb"
