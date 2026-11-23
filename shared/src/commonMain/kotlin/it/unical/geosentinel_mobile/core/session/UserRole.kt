// Ruolo dell'utente corrente, letto da `GET /users/me`.
// Il ruolo serve soltanto a decidere quali parti dell'interfaccia mostrare: i permessi veri li controlla
// sempre il server, che risponde 403 se un'azione non è consentita.
package it.unical.geosentinel_mobile.core.session

/**
 * I ruoli previsti dal backend, più [GUEST] che rappresenta l'utente non autenticato (nel backend non esiste).
 *
 * Le funzioni `can...` riproducono la gerarchia del backend: [ADMIN] comprende [PARK_MANAGER] e
 * [RESCUE_OPERATOR], che a loro volta comprendono [HIKER].
 */
enum class UserRole {
    /** Utente non autenticato: vede solo mappa e sentieri. */
    GUEST,
    /** Escursionista registrato. */
    HIKER,
    /** Gestore del parco: verifica le segnalazioni, gestisce sentieri, POI e ticket. */
    PARK_MANAGER,
    /** Operatore del soccorso: riceve e gestisce le allerte SOS. */
    RESCUE_OPERATOR,
    /** Amministratore: ha i permessi di tutti gli altri ruoli. */
    ADMIN;

    /** Vero per ogni utente autenticato: rifugi e prenotazioni richiedono il login. */
    fun canViewRefuges(): Boolean = this != GUEST
    /** Vero per ogni utente autenticato: l'SOS va associato a un account. */
    fun canRaiseSos(): Boolean = this != GUEST
    /** Vero per ogni utente autenticato. */
    fun canReportHazards(): Boolean = this != GUEST
    /** Vero per chi gestisce le allerte del soccorso (operatori e amministratori). */
    fun canViewAlerts(): Boolean = this == RESCUE_OPERATOR || this == ADMIN
    /** Vero per chi vede la sezione "Gestione" del parco (gestori e amministratori). */
    fun canManagePark(): Boolean = this == PARK_MANAGER || this == ADMIN
}
