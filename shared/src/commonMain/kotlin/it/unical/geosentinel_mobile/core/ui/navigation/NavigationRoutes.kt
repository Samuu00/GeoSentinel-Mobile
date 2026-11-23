// Rotte di navigazione dell'app e voci della barra in basso.
// Le rotte sono classi `@Serializable`: Navigation Compose le converte in automatico negli argomenti della
// destinazione, quindi i parametri di una schermata sono tipizzati e controllati dal compilatore.
package it.unical.geosentinel_mobile.core.ui.navigation

import androidx.compose.ui.graphics.vector.ImageVector
import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.nav_alerts
import geosentinel_mobile.shared.generated.resources.nav_management
import geosentinel_mobile.shared.generated.resources.nav_map
import geosentinel_mobile.shared.generated.resources.nav_profile
import geosentinel_mobile.shared.generated.resources.nav_refuges
import it.unical.geosentinel_mobile.core.session.UserRole
import it.unical.geosentinel_mobile.core.ui.AppIcons
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.StringResource

/**
 * Tutte le destinazioni del NavHost di `App`. Le schede della barra in basso sono `data object`; le schermate
 * "spinte" sopra una scheda (dettagli e form) sono `data class` quando hanno bisogno di parametri.
 */
sealed interface Route {
    // Schede della barra in basso.

    /** Mappa dei sentieri, scheda iniziale. */
    @Serializable data object Map : Route
    /** Lista dei rifugi vicini. */
    @Serializable data object Refuges : Route
    /** Allerte SOS aperte, per gli operatori del soccorso. */
    @Serializable data object Alerts : Route
    /** Sezione "Gestione" per gestori del parco e amministratori. */
    @Serializable data object Management : Route
    /** Profilo e impostazioni dell'utente. */
    @Serializable data object Profile : Route

    // Mappa e sentieri: schermate aperte sopra la mappa, senza barra in basso.

    /** Dettaglio del sentiero con l'id dato. */
    @Serializable data class TrailDetail(val id: String) : Route
    /**
     * Form di segnalazione di un pericolo. La mappa del form parte da [lat], [lon] e [zoom]; [trailId] è presente
     * se la segnalazione parte dal dettaglio di un sentiero.
     */
    @Serializable data class ReportHazard(val lat: Double, val lon: Double, val zoom: Double, val trailId: String? = null) : Route
    /** Schermata dell'SOS. */
    @Serializable data object SosEmergency : Route

    // Rifugi: pagina aperta sopra la scheda Rifugi o sopra la mappa. "Prenota" è un foglio della pagina e le
    // prenotazioni sono un segmento della scheda, quindi non hanno rotte proprie.

    /** Pagina del rifugio con l'id dato. */
    @Serializable data class RefugeDetail(val refugeId: String) : Route

    // Soccorso: dettaglio aperto dalla scheda Allerte, dalla mappa o dal tocco su una notifica push.

    /** Dettaglio dell'allerta con l'id dato. */
    @Serializable data class AlertDetail(val alertId: String) : Route

    // Gestore del parco: schermate aperte sopra il dettaglio del sentiero o sopra la mappa.

    /** Coda delle segnalazioni da verificare per un sentiero. */
    @Serializable data class HazardQueue(val trailId: String) : Route
    /** Form di un nuovo POI; parametri con lo stesso significato di [ReportHazard]. */
    @Serializable data class PoiCreate(val lat: Double, val lon: Double, val zoom: Double, val trailId: String? = null) : Route
    /** Dettaglio di un ticket di manutenzione, aperto dalla scheda Gestione. */
    @Serializable data class TicketDetail(val ticketId: String) : Route
    /** Form di un nuovo ticket, aperto dal dettaglio del sentiero. */
    @Serializable data class TicketCreate(val trailId: String) : Route
    /** Modifica dei posti letto di un rifugio; [capacity] è il valore attuale da cui parte il form. */
    @Serializable data class RefugeCapacity(val refugeId: String, val capacity: Int) : Route
    /** Creazione di una squadra di soccorso (solo `ADMIN`), con la mappa che parte da [lat], [lon] e [zoom]. */
    @Serializable data class RescueTeamCreate(val lat: Double, val lon: Double, val zoom: Double) : Route
    /** Azzeramento della verifica in due passaggi di un utente, solo `ADMIN`, dalla scheda Gestione. */
    @Serializable data object MfaReset : Route

    // Account: schermate aperte sopra il Profilo.

    /** Accesso con email e password. */
    @Serializable data object Login : Route
    /** Registrazione di un nuovo account. */
    @Serializable data object Register : Route
    /**
     * Password dimenticata (dal login) oppure da impostare (dal Profilo di un account Google).
     * [email] è l'indirizzo già scritto, se c'è.
     */
    @Serializable data class PasswordReset(val email: String = "") : Route
    /** Modifica del nome. */
    @Serializable data object EditName : Route
    /** Cambio della password. */
    @Serializable data object ChangePassword : Route
    /** Attivazione e gestione della verifica in due passaggi. */
    @Serializable data object Mfa : Route
    /** "Le mie segnalazioni". */
    @Serializable data object MyHazards : Route
    /** Crediti e licenze delle fonti esterne (mappa, foto, icone, caratteri). */
    @Serializable data object Credits : Route
}

// Schermate con un form. Non mostrano la barra in basso, che con la tastiera aperta toglierebbe spazio.
val FormRoutes = setOf(
    Route.Login::class, Route.Register::class, Route.PasswordReset::class, Route.EditName::class, Route.ChangePassword::class, Route.ReportHazard::class,
    Route.PoiCreate::class, Route.TicketCreate::class,
    Route.RefugeCapacity::class, Route.RescueTeamCreate::class, Route.Mfa::class, Route.MfaReset::class,
)

// Tutte le schermate senza barra in basso: i form, i dettagli di sentiero, rifugio e allerta, le liste personali
// (che hanno il proprio pulsante Indietro) e l'SOS, che resta una schermata unica senza distrazioni.
val NoBottomBarRoutes = FormRoutes + Route.TrailDetail::class + Route.RefugeDetail::class + Route.AlertDetail::class +
    Route.MyHazards::class + Route.HazardQueue::class + Route.TicketDetail::class + Route.SosEmergency::class + Route.Credits::class

/**
 * Una voce della barra in basso.
 *
 * @property isVisible decide, in base al ruolo, se la voce va mostrata. Serve solo all'interfaccia: i permessi
 *   veri li controlla il server.
 */
data class BottomNavItem(
    val route: Route,
    val label: StringResource,
    val icon: ImageVector,
    val isVisible: (UserRole) -> Boolean,
)

// Le voci della barra in basso, al massimo cinque: le vede tutte solo un ADMIN.
val BottomNavItems = listOf(
    BottomNavItem(Route.Map, Res.string.nav_map, AppIcons.Map) { true },
    BottomNavItem(Route.Refuges, Res.string.nav_refuges, AppIcons.House) { it.canViewRefuges() },
    // Per le allerte si usa la campanella e non il triangolo, che resta riservato ai pericoli sui sentieri.
    BottomNavItem(Route.Alerts, Res.string.nav_alerts, AppIcons.Bell) { it.canViewAlerts() },
    BottomNavItem(Route.Management, Res.string.nav_management, AppIcons.Wrench) { it.canManagePark() },
    BottomNavItem(Route.Profile, Res.string.nav_profile, AppIcons.User) { true },
)
