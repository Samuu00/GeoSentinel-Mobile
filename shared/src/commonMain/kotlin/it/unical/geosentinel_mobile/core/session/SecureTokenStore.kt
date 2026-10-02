package it.unical.geosentinel_mobile.core.session

/**
 * Dove stanno i token della sessione: Keystore + DataStore cifrato su Android, Keychain su iOS (M2.1).
 * Interfaccia e non `expect class`: così SessionManager (M2.2) si testa in commonTest con un fake.
 * `read()` restituisce null anche quando i dati salvati non si leggono più (es. backup ripristinato su un altro
 * telefono, chiave persa): li cancella e l'utente rifà il login, invece di andare in crash.
 */
interface SecureTokenStore {
    suspend fun read(): AuthTokens?
    suspend fun write(tokens: AuthTokens)
    suspend fun clear()
}

// filesDir: la cartella dei dati dell'app (Android). iOS non la usa: i token stanno nel Keychain
internal expect fun createSecureTokenStore(filesDir: String): SecureTokenStore

// Escluso dal backup in androidApp/src/main/res/xml (data_extraction_rules.xml e backup_rules.xml)
const val SESSION_FILE_NAME = "geosentinel.session.preferences_pb"
