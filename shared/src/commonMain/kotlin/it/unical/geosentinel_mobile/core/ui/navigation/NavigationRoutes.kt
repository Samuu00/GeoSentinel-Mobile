package it.unical.geosentinel_mobile.core.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.ui.graphics.vector.ImageVector
import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.nav_alerts
import geosentinel_mobile.shared.generated.resources.nav_management
import geosentinel_mobile.shared.generated.resources.nav_map
import geosentinel_mobile.shared.generated.resources.nav_profile
import geosentinel_mobile.shared.generated.resources.nav_refuges
import it.unical.geosentinel_mobile.core.session.UserRole
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.StringResource

sealed interface Route {
    @Serializable data object Map : Route
    @Serializable data object Refuges : Route
    @Serializable data object Alerts : Route
    @Serializable data object Management : Route
    @Serializable data object Profile : Route
    @Serializable data object SosEmergency : Route
    // "spinta" sopra la mappa (M3.2), senza barra in basso
    @Serializable data class TrailDetail(val id: String) : Route
    // form "spinto" (M4.1): la mappa del form parte da lat/lon/zoom; trailId se si parte da un sentiero
    @Serializable data class ReportHazard(val lat: Double, val lon: Double, val zoom: Double, val trailId: String? = null) : Route
    // "spinte" sopra il Profilo, senza barra in basso
    @Serializable data object Login : Route
    @Serializable data object Register : Route
    @Serializable data object EditName : Route
    @Serializable data object ChangePassword : Route
}

// Schermate "spinte" con un form: niente barra in basso (con la tastiera aperta ruberebbe spazio)
val FormRoutes = setOf(Route.Login::class, Route.Register::class, Route.EditName::class, Route.ChangePassword::class, Route.ReportHazard::class)

// Schermate senza barra in basso: i form e il dettaglio di un sentiero (a tutto schermo, con il suo Indietro)
val NoBottomBarRoutes = FormRoutes + Route.TrailDetail::class

data class BottomNavItem(
    val route: Route,
    val label: StringResource,
    val icon: ImageVector,
    val isVisible: (UserRole) -> Boolean,
)

// Al massimo 5 voci (le vede tutte solo ADMIN)
val BottomNavItems = listOf(
    BottomNavItem(Route.Map, Res.string.nav_map, Icons.Filled.Place) { true },
    BottomNavItem(Route.Refuges, Res.string.nav_refuges, Icons.Filled.Home) { it.canViewRefuges() },
    // campanella e non triangolo: il triangolo resta ai pericoli sui sentieri
    BottomNavItem(Route.Alerts, Res.string.nav_alerts, Icons.Filled.Notifications) { it.canViewAlerts() },
    BottomNavItem(Route.Management, Res.string.nav_management, Icons.Filled.Build) { it.canManagePark() },
    BottomNavItem(Route.Profile, Res.string.nav_profile, Icons.Filled.Person) { true },
)
