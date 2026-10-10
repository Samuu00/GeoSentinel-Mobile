package it.unical.geosentinel_mobile

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.sos_button
import geosentinel_mobile.shared.generated.resources.sos_button_description
import it.unical.geosentinel_mobile.auth.ui.LoginScreen
import it.unical.geosentinel_mobile.auth.ui.RegisterScreen
import it.unical.geosentinel_mobile.core.session.SessionManager
import it.unical.geosentinel_mobile.core.push.PushEvent
import it.unical.geosentinel_mobile.core.push.PushManager
import it.unical.geosentinel_mobile.core.push.RequestNotificationPermission
import it.unical.geosentinel_mobile.core.session.SessionState
import it.unical.geosentinel_mobile.core.session.UserRole
import it.unical.geosentinel_mobile.core.session.role
import it.unical.geosentinel_mobile.core.ui.navigation.BottomNavItem
import it.unical.geosentinel_mobile.core.ui.navigation.BottomNavItems
import it.unical.geosentinel_mobile.core.ui.navigation.NoBottomBarRoutes
import it.unical.geosentinel_mobile.core.ui.navigation.Route
import it.unical.geosentinel_mobile.core.ui.theme.EmergencyOnRed
import it.unical.geosentinel_mobile.core.ui.theme.EmergencyRed
import it.unical.geosentinel_mobile.core.ui.theme.GeoSentinelTheme
import it.unical.geosentinel_mobile.core.ui.theme.ThemeSettings
import it.unical.geosentinel_mobile.profile.ui.ChangePasswordScreen
import it.unical.geosentinel_mobile.profile.ui.EditNameScreen
import it.unical.geosentinel_mobile.profile.ui.ProfileScreen
import it.unical.geosentinel_mobile.trail.ui.MapScreen
import it.unical.geosentinel_mobile.refuge.data.BookingRepository
import it.unical.geosentinel_mobile.refuge.ui.BookRefugeScreen
import it.unical.geosentinel_mobile.refuge.ui.MyBookingsScreen
import it.unical.geosentinel_mobile.refuge.ui.RefugeDetailScreen
import it.unical.geosentinel_mobile.refuge.ui.RefugeListScreen
import it.unical.geosentinel_mobile.rescue.ui.RescueAlertDetailScreen
import it.unical.geosentinel_mobile.rescue.ui.RescueAlertsListScreen
import it.unical.geosentinel_mobile.trail.ui.TrailDetailScreen
import it.unical.geosentinel_mobile.hazard.ui.MyHazardsScreen
import it.unical.geosentinel_mobile.hazard.ui.ReportHazardScreen
import it.unical.geosentinel_mobile.staff.ui.HazardQueueScreen
import it.unical.geosentinel_mobile.staff.ui.PoiCreateScreen
import it.unical.geosentinel_mobile.staff.ui.TrailManagementSection
import it.unical.geosentinel_mobile.core.location.GeoPoint
import it.unical.geosentinel_mobile.sos.data.SosState
import it.unical.geosentinel_mobile.sos.ui.SosController
import it.unical.geosentinel_mobile.sos.ui.SosLocationUpdates
import it.unical.geosentinel_mobile.sos.ui.SosScreen
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

// Durate delle transizioni "spinte": l'uscita è più rapida dell'entrata
private const val PushMillis = 320
private const val PopMillis = 240
// Dal dettaglio di un sentiero la mappa della segnalazione parte dall'inizio del percorso, da vicino
private const val ReportFromTrailZoom = 15.0

@Composable
fun App() {
    val themeSettings = koinInject<ThemeSettings>()
    val session = koinInject<SessionManager>()
    val sessionState by session.state.collectAsState()
    // null finché DataStore non ha letto il file (pochi ms): meglio un frame vuoto che un lampo del tema sbagliato
    val themeMode by themeSettings.mode.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    // Il ruolo arriva dalla sessione (GET /users/me) e decide solo cosa mostrare
    val role = sessionState.role
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination

    // Sessione salvata letta una volta all'avvio (una seconda chiamata non fa nulla)
    LaunchedEffect(session) { session.restore() }
    // Prenotazioni rimaste in coda (app chiusa prima della risposta, M5.2): ripartono appena si sa chi è l'utente,
    // con la stessa Idempotency-Key, quindi senza doppioni
    val bookings = koinInject<BookingRepository>()
    // l'utente anche senza profilo letto (dal token), come per l'SOS: con poca rete /users/me può non arrivare
    val loggedInNow = sessionState is SessionState.LoggedIn
    LaunchedEffect(loggedInNow) { if (loggedInNow) session.currentUserId()?.let { bookings.resendPending(it) } }
    // SOS (M6.1): da loggati riparte quello rimasto in coda o si rilegge l'allerta attiva (anche senza profilo letto:
    // con poca rete non si aspetta /users/me); da ospite o con la sessione chiusa si smette di inviare
    val sos = koinInject<SosController>()
    val loggedIn = sessionState is SessionState.LoggedIn
    // Se il ruolo cambia (logout, sessione scaduta) e la scheda aperta non è più visibile, si torna alla mappa
    // (non durante il caricamento: si vedrebbe GUEST e una scheda del personale ripristinata verrebbe chiusa)
    val loading = sessionState == SessionState.Loading
    // Da ospite (avvio o dopo un logout) le foto dei pericoli e dei sentieri, scaricate da loggati, non restano in cache
    val imageContext = LocalPlatformContext.current
    LaunchedEffect(loggedIn, loading) {
        if (loading) return@LaunchedEffect
        if (loggedIn) {
            sos.restore()
        } else {
            sos.reset()
            SingletonImageLoader.get(imageContext).run {
                memoryCache?.clear()
                diskCache?.clear()
            }
        }
    }
    // M6.2: con un'allerta attiva la posizione va al soccorso ogni 60 s, in primo piano e su ogni schermata
    val sosState by sos.state.collectAsState()
    if (sosState is SosState.Raised) SosLocationUpdates(sos)

    // Push (M7.1): da loggati il token si registra a ogni avvio e a ogni rinnovo (il server tollera i doppioni);
    // al logout lo toglie SessionManager
    val push = koinInject<PushManager>()
    val pushToken by push.token.collectAsState()
    LaunchedEffect(loggedIn, pushToken) {
        val token = pushToken
        if (loggedIn && token != null) push.register(token)
    }
    // Il permesso delle notifiche la prima volta che serve: l'operatore da loggato, l'escursionista dal primo SOS
    if (role.canViewAlerts() || sosState !is SosState.Idle) RequestNotificationPermission()
    // Con l'app aperta lo stato dell'SOS si rilegge subito, senza aspettare il giro dei 30 s
    LaunchedEffect(push) {
        push.received.collect { event -> if (event !is PushEvent.SosRaised) sos.refresh() }
    }
    // Notifica toccata (anche ad app chiusa: resta in PushManager finché la sessione non è letta).
    // Una notifica doppia apre la stessa schermata.
    val openedPush by push.opened.collectAsState()
    // il grafo esiste solo con il NavHost composto, cioè dopo che il tema è letto: navigare prima è un crash
    val navReady = themeMode != null
    // il ruolo vale solo con il profilo letto: LoggedIn(null) dà HIKER anche a un operatore
    val roleKnown = when (val current = sessionState) {
        is SessionState.LoggedIn -> current.profile != null
        SessionState.Loading -> false
        SessionState.Anonymous, SessionState.Expired -> true
    }
    LaunchedEffect(openedPush, role, roleKnown, navReady) {
        val event = openedPush ?: return@LaunchedEffect
        if (!roleKnown || !navReady) return@LaunchedEffect
        when (event) {
            // all'operatore: il dettaglio dell'allerta sopra la scheda Allerte (M7.2). A chi non può vederla la notifica
            // non serve: si scarta, così non si apre da sola per un operatore che entra dopo
            is PushEvent.SosRaised -> {
                push.consumeOpened(event)
                if (!role.canViewAlerts()) return@LaunchedEffect
                // già aperto sulla stessa allerta (notifica doppia): non si ricrea, un dialogo aperto resta
                val current = navController.currentBackStackEntry
                if (current?.destination?.hasRoute<Route.AlertDetail>() == true && current.toRoute<Route.AlertDetail>().alertId == event.alertId) {
                    return@LaunchedEffect
                }
                navController.navigate(Route.Alerts) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                }
                navController.navigate(Route.AlertDetail(event.alertId)) { launchSingleTop = true }
            }
            // all'escursionista: la sua allerta. Prima si naviga: consumeOpened cambia la chiave e annulla questo effetto
            // (refresh() gira nello scope del controller e finisce lo stesso)
            is PushEvent.SosDispatched, is PushEvent.SosResolved -> {
                navController.navigate(Route.SosEmergency) { launchSingleTop = true }
                push.consumeOpened(event)
                sos.refresh()
            }
        }
    }
    LaunchedEffect(role, destination, loading) {
        if (loading) return@LaunchedEffect
        val tab = BottomNavItems.firstOrNull { item -> destination?.hierarchy?.any { it.hasRoute(item.route::class) } == true }
        if (tab != null && !tab.isVisible(role)) navController.navigate(Route.Map) { popUpTo(navController.graph.findStartDestination().id) }
    }

    // stessa funzione tra una ricomposizione e l'altra: il livello delle allerte sulla mappa non si riconfigura ogni volta
    val openAlert: (String) -> Unit = remember(navController) { { id -> navController.navigate(Route.AlertDetail(id)) { launchSingleTop = true } } }

    val mode = themeMode ?: return
    GeoSentinelTheme(mode) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            // safeDrawing e non systemBars (il default): include anche tacca e Dynamic Island,
            // che in orizzontale stanno di lato. Vale per contenuto e pulsante SOS.
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                // nei form la barra non serve (con la tastiera aperta ruberebbe spazio), nel dettaglio del sentiero nemmeno
                if (NoBottomBarRoutes.any { route -> destination?.hasRoute(route) == true }) return@Scaffold
                TabBar(role, destination) { route ->
                    navController.navigate(route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            },
            floatingActionButton = {
                // Solo sopra la mappa (§2). Il tocco apre la schermata, dove l'SOS parte tenendo premuto 3 s (M6.1).
                if (destination?.hasRoute<Route.Map>() == true) {
                    SosButton { navController.navigate(Route.SosEmergency) }
                }
            },
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Route.Map,
                // niente margine in alto: le testate (ForestHeader) arrivano sotto la barra di stato, e ogni
                // schermata sposta il suo contenuto con WindowInsets.safeDrawing.only(Top)
                modifier = Modifier.padding(
                    start = innerPadding.calculateStartPadding(LocalLayoutDirection.current),
                    end = innerPadding.calculateEndPadding(LocalLayoutDirection.current),
                    bottom = innerPadding.calculateBottomPadding(),
                ),
                // cambio di scheda: dissolvenza breve, niente movimento
                enterTransition = { fadeIn(tween(150)) },
                exitTransition = { fadeOut(tween(100)) },
            ) {
                composable<Route.Map>(
                    // quando entra l'SOS, un sentiero, un rifugio o un'allerta la mappa scivola un po' a sinistra e lascia il posto (parallasse)
                    exitTransition = {
                        val destination = targetState.destination
                        if (destination.hasRoute<Route.SosEmergency>() || destination.hasRoute<Route.TrailDetail>() ||
                            destination.hasRoute<Route.RefugeDetail>() || destination.hasRoute<Route.AlertDetail>()
                        ) {
                            slideOutHorizontally(tween(PushMillis)) { -it / 4 }
                        } else {
                            fadeOut(tween(100))
                        }
                    },
                    popEnterTransition = { slideInHorizontally(tween(PopMillis)) { -it / 4 } },
                ) {
                    MapScreen(
                        onOpenTrail = { id -> navController.navigate(Route.TrailDetail(id)) { launchSingleTop = true } },
                        // segnalare richiede il login (HIKER e superiori): l'ospite non vede il pulsante
                        onReportHazard = if (role.canReportHazards()) {
                            { center, zoom -> navController.navigate(Route.ReportHazard(center.lat, center.lon, zoom)) { launchSingleTop = true } }
                        } else {
                            null
                        },
                        // /hazards/** richiede il login: l'ospite non ha il livello né "Pericoli vicini"
                        showHazards = role != UserRole.GUEST,
                        // nuovo POI solo per il gestore del parco (M8.1): il server risponde comunque 403 agli altri
                        onCreatePoi = if (role.canManagePark()) {
                            { center, zoom -> navController.navigate(Route.PoiCreate(center.lat, center.lon, zoom)) { launchSingleTop = true } }
                        } else {
                            null
                        },
                        // /refuges/** richiede il login: l'ospite non vede i rifugi
                        onOpenRefuge = if (role.canViewRefuges()) {
                            { id -> navController.navigate(Route.RefugeDetail(id)) { launchSingleTop = true } }
                        } else {
                            null
                        },
                        // le allerte aperte solo per chi vede la scheda Allerte (M7.2): il tocco apre il dettaglio
                        onOpenAlert = if (role.canViewAlerts()) openAlert else null,
                    )
                }
                composable<Route.TrailDetail>(
                    enterTransition = { slideInHorizontally(tween(PushMillis)) { it } },
                    popExitTransition = { slideOutHorizontally(tween(PopMillis)) { it } },
                ) { entry ->
                    val trailId = entry.toRoute<Route.TrailDetail>().id
                    TrailDetailScreen(
                        trailId,
                        onBack = { navController.popBackStack() },
                        onLogin = { navController.navigate(Route.Login) },
                        onReportHazard = { start -> navController.navigate(Route.ReportHazard(start.lat, start.lon, ReportFromTrailZoom, trailId)) { launchSingleTop = true } },
                        manage = { trail ->
                            if (role.canManagePark()) {
                                TrailManagementSection(
                                    trail,
                                    onOpenQueue = { navController.navigate(Route.HazardQueue(trailId)) { launchSingleTop = true } },
                                    onCreatePoi = { start ->
                                        navController.navigate(Route.PoiCreate(start.lat, start.lon, ReportFromTrailZoom, trailId)) { launchSingleTop = true }
                                    },
                                )
                            }
                        },
                    )
                }
                composable<Route.HazardQueue>(
                    enterTransition = { slideInHorizontally(tween(PushMillis)) { it } },
                    popExitTransition = { slideOutHorizontally(tween(PopMillis)) { it } },
                ) { entry ->
                    HazardQueueScreen(entry.toRoute<Route.HazardQueue>().trailId, onBack = { navController.popBackStack() })
                }
                composable<Route.PoiCreate>(
                    enterTransition = { slideInHorizontally(tween(PushMillis)) { it } },
                    popExitTransition = { slideOutHorizontally(tween(PopMillis)) { it } },
                ) { entry ->
                    val route = entry.toRoute<Route.PoiCreate>()
                    PoiCreateScreen(
                        route.trailId,
                        GeoPoint(route.lat, route.lon),
                        route.zoom,
                        onBack = { navController.popBackStack() },
                        onCreated = { navController.popBackStack() },
                    )
                }
                composable<Route.ReportHazard>(
                    enterTransition = { slideInHorizontally(tween(PushMillis)) { it } },
                    popExitTransition = { slideOutHorizontally(tween(PopMillis)) { it } },
                ) { entry ->
                    val route = entry.toRoute<Route.ReportHazard>()
                    ReportHazardScreen(route.trailId, GeoPoint(route.lat, route.lon), route.zoom, onBack = { navController.popBackStack() })
                }
                composable<Route.Refuges>(
                    // come la mappa: il dettaglio entra da destra e la lista scivola via in parallasse
                    exitTransition = {
                        val pushed = targetState.destination.hasRoute<Route.RefugeDetail>() || targetState.destination.hasRoute<Route.MyBookings>()
                        if (pushed) slideOutHorizontally(tween(PushMillis)) { -it / 4 } else fadeOut(tween(100))
                    },
                    popEnterTransition = { slideInHorizontally(tween(PopMillis)) { -it / 4 } },
                ) {
                    RefugeListScreen(
                        onOpenRefuge = { id -> navController.navigate(Route.RefugeDetail(id)) { launchSingleTop = true } },
                        onOpenBookings = { navController.navigate(Route.MyBookings) { launchSingleTop = true } },
                    )
                }
                composable<Route.RefugeDetail>(
                    enterTransition = { slideInHorizontally(tween(PushMillis)) { it } },
                    popExitTransition = { slideOutHorizontally(tween(PopMillis)) { it } },
                ) { entry ->
                    RefugeDetailScreen(
                        entry.toRoute<Route.RefugeDetail>().refugeId,
                        onBack = { navController.popBackStack() },
                        onBook = { id, name, capacity -> navController.navigate(Route.BookRefuge(id, name, capacity)) { launchSingleTop = true } },
                        onOpenTrail = { id -> navController.navigate(Route.TrailDetail(id)) { launchSingleTop = true } },
                    )
                }
                composable<Route.BookRefuge>(
                    enterTransition = { slideInHorizontally(tween(PushMillis)) { it } },
                    popExitTransition = { slideOutHorizontally(tween(PopMillis)) { it } },
                ) { entry ->
                    val route = entry.toRoute<Route.BookRefuge>()
                    BookRefugeScreen(route.refugeId, route.name, route.capacity, onBack = { navController.popBackStack() })
                }
                composable<Route.MyBookings>(
                    enterTransition = { slideInHorizontally(tween(PushMillis)) { it } },
                    popExitTransition = { slideOutHorizontally(tween(PopMillis)) { it } },
                ) {
                    MyBookingsScreen(onBack = { navController.popBackStack() })
                }
                composable<Route.Alerts> {
                    RescueAlertsListScreen(onOpenAlert = { id -> navController.navigate(Route.AlertDetail(id)) { launchSingleTop = true } })
                }
                composable<Route.AlertDetail>(
                    enterTransition = { slideInHorizontally(tween(PushMillis)) { it } },
                    popExitTransition = { slideOutHorizontally(tween(PopMillis)) { it } },
                ) { entry ->
                    val route = entry.toRoute<Route.AlertDetail>()
                    RescueAlertDetailScreen(route.alertId, onBack = { navController.popBackStack() })
                }
                composable<Route.Management>(
                    // il sentiero di una segnalazione entra da destra e la coda scivola via in parallasse
                    exitTransition = {
                        if (targetState.destination.hasRoute<Route.TrailDetail>()) slideOutHorizontally(tween(PushMillis)) { -it / 4 } else fadeOut(tween(100))
                    },
                    popEnterTransition = { slideInHorizontally(tween(PopMillis)) { -it / 4 } },
                ) {
                    // M8.1: le segnalazioni da verificare di tutto il parco (anche lontane dai sentieri)
                    HazardQueueScreen(
                        trailId = null,
                        onBack = null,
                        onOpenTrail = { id -> navController.navigate(Route.TrailDetail(id)) { launchSingleTop = true } },
                    )
                }
                composable<Route.Profile> {
                    ProfileScreen(
                        session = sessionState,
                        onLogin = { navController.navigate(Route.Login) },
                        onRegister = { navController.navigate(Route.Register) },
                        onEditName = { navController.navigate(Route.EditName) },
                        onChangePassword = { navController.navigate(Route.ChangePassword) },
                        onMyHazards = { navController.navigate(Route.MyHazards) { launchSingleTop = true } },
                        themeMode = mode,
                        onThemeModeSelected = { scope.launch { themeSettings.setMode(it) } },
                    )
                }
                composable<Route.MyHazards>(
                    enterTransition = { slideInHorizontally(tween(PushMillis)) { it } },
                    popExitTransition = { slideOutHorizontally(tween(PopMillis)) { it } },
                ) {
                    MyHazardsScreen(
                        onBack = { navController.popBackStack() },
                        onOpenTrail = { id -> navController.navigate(Route.TrailDetail(id)) { launchSingleTop = true } },
                        // la segnalazione parte dalla mappa: si passa alla scheda Mappa come dalla barra in basso
                        onGoToMap = {
                            navController.navigate(Route.Map) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
                composable<Route.Login>(
                    enterTransition = { slideInHorizontally(tween(PushMillis)) { it } },
                    popExitTransition = { slideOutHorizontally(tween(PopMillis)) { it } },
                ) {
                    LoginScreen(
                        onBack = { navController.popBackStack() },
                        // la registrazione prende il posto dell'accesso: Indietro torna al Profilo
                        onRegister = { navController.navigate(Route.Register) { popUpTo<Route.Login> { inclusive = true } } },
                        // dal Profilo si torna al Profilo; dal dettaglio di un sentiero (Profilo non nella pila) al sentiero
                        onLoggedIn = { if (!navController.popBackStack<Route.Profile>(inclusive = false)) navController.popBackStack() },
                    )
                }
                composable<Route.Register>(
                    enterTransition = { slideInHorizontally(tween(PushMillis)) { it } },
                    popExitTransition = { slideOutHorizontally(tween(PopMillis)) { it } },
                ) {
                    RegisterScreen(
                        onBack = { navController.popBackStack() },
                        onLogin = { navController.navigate(Route.Login) { popUpTo<Route.Register> { inclusive = true } } },
                        onRegistered = { if (!navController.popBackStack<Route.Profile>(inclusive = false)) navController.popBackStack() },
                    )
                }
                composable<Route.EditName>(
                    enterTransition = { slideInHorizontally(tween(PushMillis)) { it } },
                    popExitTransition = { slideOutHorizontally(tween(PopMillis)) { it } },
                ) {
                    EditNameScreen(onBack = { navController.popBackStack() }, onSaved = { navController.popBackStack() })
                }
                composable<Route.ChangePassword>(
                    enterTransition = { slideInHorizontally(tween(PushMillis)) { it } },
                    popExitTransition = { slideOutHorizontally(tween(PopMillis)) { it } },
                ) {
                    ChangePasswordScreen(onBack = { navController.popBackStack() }, onChanged = { navController.popBackStack() })
                }
                // Schermata "spinta" sopra la mappa: entra da destra e copre tutto (ha uno sfondo pieno)
                composable<Route.SosEmergency>(
                    enterTransition = { slideInHorizontally(tween(PushMillis)) { it } },
                    exitTransition = { ExitTransition.None },
                    popEnterTransition = { EnterTransition.None },
                    popExitTransition = { slideOutHorizontally(tween(PopMillis)) { it } },
                ) {
                    SosScreen(onBack = { navController.popBackStack() }, onLogin = { navController.navigate(Route.Login) })
                }
            }
        }
    }
}

// Barra in basso fluttuante: una capsula staccata dai bordi, la voce attiva su una pillola color muschio.
// Si stacca dallo sfondo con un filo di bordo e un'ombra appena accennata, come le card.
@Composable
private fun TabBar(role: UserRole, destination: NavDestination?, onNavigate: (Route) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            // indicatore Home / barra di navigazione in basso, tacca sui lati in orizzontale
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            // in orizzontale e sui tablet non si allunga da bordo a bordo
            modifier = Modifier.widthIn(max = 560.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 3.dp,
        ) {
            // voci centrate in altezza: con l'allineamento di default (in alto) la pillola toccava il bordo
            Row(
                Modifier.fillMaxWidth().height(68.dp).padding(horizontal = 6.dp).selectableGroup(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BottomNavItems.filter { it.isVisible(role) }.forEach { item ->
                    TabBarItem(
                        item = item,
                        // la schermata SOS è "spinta" sopra la mappa: la scheda Mappa resta attiva
                        selected = destination?.hierarchy?.any { it.hasRoute(item.route::class) } == true ||
                            (item.route == Route.Map && destination?.hasRoute<Route.SosEmergency>() == true),
                        onClick = { onNavigate(item.route) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun TabBarItem(item: BottomNavItem, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val pill by animateColorAsState(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
    val content = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = modifier
            .heightIn(min = 48.dp)
            .selectable(
                selected = selected,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 32.dp),
                role = Role.Tab,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // l'etichetta è sempre visibile: l'icona è decorativa, altrimenti TalkBack la legge due volte
        Box(
            Modifier.background(pill, CircleShape).padding(horizontal = 16.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(item.icon, contentDescription = null, tint = content, modifier = Modifier.size(24.dp))
        }
        Text(
            text = stringResource(item.label),
            style = MaterialTheme.typography.labelSmall,
            color = content,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun SosButton(onClick: () -> Unit) {
    val description = stringResource(Res.string.sos_button_description)
    val haptics = LocalHapticFeedback.current
    // Capsula con la scritta "SOS": si capisce subito, anche al sole. 72 dp = azione d'emergenza (§2).
    // Un tocco apre la schermata SOS e basta: lì l'invio vuole 3 secondi di pressione (niente SOS dalla tasca).
    Surface(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        shape = CircleShape,
        color = EmergencyRed,
        contentColor = EmergencyOnRed,
        shadowElevation = 4.dp,
        modifier = Modifier.semantics { contentDescription = description },
    ) {
        Box(Modifier.heightIn(min = 72.dp).widthIn(min = 96.dp), contentAlignment = Alignment.Center) {
            // lettere da cartello, un po' spaziate: si leggono da lontano
            Text(
                text = stringResource(Res.string.sos_button),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
    }
}
