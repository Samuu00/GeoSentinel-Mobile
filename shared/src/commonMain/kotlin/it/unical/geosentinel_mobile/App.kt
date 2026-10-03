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
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Warning
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
import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.placeholder_alerts_body
import geosentinel_mobile.shared.generated.resources.placeholder_alerts_title
import geosentinel_mobile.shared.generated.resources.placeholder_management_body
import geosentinel_mobile.shared.generated.resources.placeholder_management_title
import geosentinel_mobile.shared.generated.resources.placeholder_map_body
import geosentinel_mobile.shared.generated.resources.placeholder_map_title
import geosentinel_mobile.shared.generated.resources.placeholder_refuges_body
import geosentinel_mobile.shared.generated.resources.placeholder_refuges_title
import geosentinel_mobile.shared.generated.resources.placeholder_sos_body
import geosentinel_mobile.shared.generated.resources.placeholder_sos_title
import geosentinel_mobile.shared.generated.resources.sos_button
import geosentinel_mobile.shared.generated.resources.sos_button_description
import it.unical.geosentinel_mobile.auth.ui.LoginScreen
import it.unical.geosentinel_mobile.auth.ui.RegisterScreen
import it.unical.geosentinel_mobile.core.session.SessionManager
import it.unical.geosentinel_mobile.core.session.SessionState
import it.unical.geosentinel_mobile.core.session.UserRole
import it.unical.geosentinel_mobile.core.session.role
import it.unical.geosentinel_mobile.core.ui.PlaceholderScreen
import it.unical.geosentinel_mobile.core.ui.navigation.BottomNavItem
import it.unical.geosentinel_mobile.core.ui.navigation.BottomNavItems
import it.unical.geosentinel_mobile.core.ui.navigation.FormRoutes
import it.unical.geosentinel_mobile.core.ui.navigation.Route
import it.unical.geosentinel_mobile.core.ui.theme.EmergencyOnRed
import it.unical.geosentinel_mobile.core.ui.theme.EmergencyRed
import it.unical.geosentinel_mobile.core.ui.theme.GeoSentinelTheme
import it.unical.geosentinel_mobile.core.ui.theme.ThemeSettings
import it.unical.geosentinel_mobile.profile.ui.ChangePasswordScreen
import it.unical.geosentinel_mobile.profile.ui.EditNameScreen
import it.unical.geosentinel_mobile.profile.ui.ProfileScreen
import it.unical.geosentinel_mobile.trail.ui.MapScreen
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

// Durate delle transizioni "spinte": l'uscita è più rapida dell'entrata
private const val PushMillis = 320
private const val PopMillis = 240

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
    // Se il ruolo cambia (logout, sessione scaduta) e la scheda aperta non è più visibile, si torna alla mappa
    // (non durante il caricamento: si vedrebbe GUEST e una scheda del personale ripristinata verrebbe chiusa)
    val loading = sessionState == SessionState.Loading
    LaunchedEffect(role, destination, loading) {
        if (loading) return@LaunchedEffect
        val tab = BottomNavItems.firstOrNull { item -> destination?.hierarchy?.any { it.hasRoute(item.route::class) } == true }
        if (tab != null && !tab.isVisible(role)) navController.navigate(Route.Map) { popUpTo(navController.graph.findStartDestination().id) }
    }

    val mode = themeMode ?: return
    GeoSentinelTheme(mode) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            // safeDrawing e non systemBars (il default): include anche tacca e Dynamic Island,
            // che in orizzontale stanno di lato. Vale per contenuto e pulsante SOS.
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                // nei form la barra non serve, e con la tastiera aperta ruberebbe spazio
                if (FormRoutes.any { route -> destination?.hasRoute(route) == true }) return@Scaffold
                TabBar(role, destination) { route ->
                    navController.navigate(route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            },
            floatingActionButton = {
                // Solo sopra la mappa (§2). Per l'ospite la schermata propone 112 e accesso (M6).
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
                    // quando entra l'SOS la mappa scivola un po' a sinistra e lascia il posto (parallasse)
                    exitTransition = {
                        if (targetState.destination.hasRoute<Route.SosEmergency>()) {
                            slideOutHorizontally(tween(PushMillis)) { -it / 4 }
                        } else {
                            fadeOut(tween(100))
                        }
                    },
                    popEnterTransition = { slideInHorizontally(tween(PopMillis)) { -it / 4 } },
                ) {
                    MapScreen()
                }
                composable<Route.Refuges> {
                    PlaceholderScreen(Icons.Filled.Home, Res.string.placeholder_refuges_title, Res.string.placeholder_refuges_body)
                }
                composable<Route.Alerts> {
                    PlaceholderScreen(Icons.Filled.Notifications, Res.string.placeholder_alerts_title, Res.string.placeholder_alerts_body)
                }
                composable<Route.Management> {
                    PlaceholderScreen(Icons.Filled.Build, Res.string.placeholder_management_title, Res.string.placeholder_management_body)
                }
                composable<Route.Profile> {
                    ProfileScreen(
                        session = sessionState,
                        onLogin = { navController.navigate(Route.Login) },
                        onRegister = { navController.navigate(Route.Register) },
                        onEditName = { navController.navigate(Route.EditName) },
                        onChangePassword = { navController.navigate(Route.ChangePassword) },
                        themeMode = mode,
                        onThemeModeSelected = { scope.launch { themeSettings.setMode(it) } },
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
                        onLoggedIn = { navController.popBackStack<Route.Profile>(inclusive = false) },
                    )
                }
                composable<Route.Register>(
                    enterTransition = { slideInHorizontally(tween(PushMillis)) { it } },
                    popExitTransition = { slideOutHorizontally(tween(PopMillis)) { it } },
                ) {
                    RegisterScreen(
                        onBack = { navController.popBackStack() },
                        onLogin = { navController.navigate(Route.Login) { popUpTo<Route.Register> { inclusive = true } } },
                        onRegistered = { navController.popBackStack<Route.Profile>(inclusive = false) },
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
                    PlaceholderScreen(
                        Icons.Filled.Warning,
                        Res.string.placeholder_sos_title,
                        Res.string.placeholder_sos_body,
                        emergency = true,
                        onBack = { navController.popBackStack() },
                    )
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
    // La pressione lunga di 3 secondi arriva in M6.
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
