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
    // ---- schede della barra in basso ----
    @Serializable data object Map : Route
    // la lista dei rifugi vicini (M5.1)
    @Serializable data object Refuges : Route
    // le allerte aperte per l'operatore (M7.2)
    @Serializable data object Alerts : Route
    @Serializable data object Management : Route
    @Serializable data object Profile : Route

    // ---- mappa e sentieri: "spinte" sopra la mappa, senza barra in basso ----
    @Serializable data class TrailDetail(val id: String) : Route
    // form (M4.1): la mappa del form parte da lat/lon/zoom; trailId se si parte da un sentiero
    @Serializable data class ReportHazard(val lat: Double, val lon: Double, val zoom: Double, val trailId: String? = null) : Route
    @Serializable data object SosEmergency : Route

    // ---- rifugi: "spinte" sopra la scheda Rifugi o la mappa ----
    @Serializable data class RefugeDetail(val refugeId: String) : Route
    // form dal dettaglio del rifugio (M5.2): nome e posti per il form, senza richiederli
    @Serializable data class BookRefuge(val refugeId: String, val name: String, val capacity: Int) : Route
    @Serializable data object MyBookings : Route

    // ---- soccorso: "spinta" sopra la scheda Allerte, dalla mappa o da una push (M7.2) ----
    @Serializable data class AlertDetail(val alertId: String) : Route

    // ---- account: "spinte" sopra il Profilo ----
    @Serializable data object Login : Route
    @Serializable data object Register : Route
    @Serializable data object EditName : Route
    @Serializable data object ChangePassword : Route
    @Serializable data object MyHazards : Route
}

// Schermate "spinte" con un form: niente barra in basso (con la tastiera aperta ruberebbe spazio)
val FormRoutes = setOf(
    Route.Login::class, Route.Register::class, Route.EditName::class, Route.ChangePassword::class, Route.ReportHazard::class,
    Route.BookRefuge::class,
)

// Schermate senza barra in basso: i form, i dettagli di sentiero, rifugio e allerta e le liste "mie" (con il loro Indietro)
val NoBottomBarRoutes = FormRoutes + Route.TrailDetail::class + Route.RefugeDetail::class + Route.AlertDetail::class +
    Route.MyHazards::class + Route.MyBookings::class

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
