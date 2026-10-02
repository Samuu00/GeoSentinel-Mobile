package it.unical.geosentinel_mobile.core.session

// Serve solo a decidere cosa mostrare: è il server a decidere cosa si può fare.
// GUEST non esiste nel backend: è l'utente non loggato.
enum class UserRole {
    GUEST,
    HIKER,
    PARK_MANAGER,
    RESCUE_OPERATOR,
    ADMIN;

    // Gerarchia del backend: ADMIN include PARK_MANAGER e RESCUE_OPERATOR, che includono HIKER
    fun canViewRefuges(): Boolean = this != GUEST
    fun canRaiseSos(): Boolean = this != GUEST
    fun canViewAlerts(): Boolean = this == RESCUE_OPERATOR || this == ADMIN
    fun canManagePark(): Boolean = this == PARK_MANAGER || this == ADMIN
}
