// Contratto dell'archivio sicuro dei token di sessione.
// Le implementazioni sono specifiche della piattaforma: su Android un DataStore cifrato con AES-GCM e chiave
// nel Keystore, su iOS il Keychain. Il resto dell'app vede solo questa interfaccia.
package it.unical.geosentinel_mobile.core.session

/**
 * Archivio persistente e cifrato dei token della sessione (access token e refresh token).
 *
 * È un'interfaccia e non una `expect class` perché così `SessionManager` si può testare nel codice comune
 * con un'implementazione finta. [read] restituisce null anche quando i dati salvati non sono più leggibili
 * (per esempio un backup ripristinato su un altro telefono o una chiave persa): in quel caso li cancella e
 * l'utente rifà il login, invece di far chiudere l'app con un errore.
 */
interface SecureTokenStore {
    /** Legge i token salvati; null se non ce ne sono o se non si possono decifrare. */
    suspend fun read(): AuthTokens?
    /** Salva i token, sostituendo quelli precedenti. */
    suspend fun write(tokens: AuthTokens)
    /** Cancella i token salvati (logout o sessione scaduta). */
    suspend fun clear()
}

/**
 * Crea l'archivio adatto alla piattaforma.
 *
 * @param filesDir la cartella dei dati privati dell'app, dove su Android si salva il file della sessione.
 *   Su iOS non viene usata, perché i token stanno nel Keychain.
 */
internal expect fun createSecureTokenStore(filesDir: String): SecureTokenStore

// Nome del file DataStore della sessione su Android. È escluso dal backup dalle regole in
// androidApp/src/main/res/xml (data_extraction_rules.xml e backup_rules.xml): un file cifrato con una chiave
// del Keystore non sarebbe comunque leggibile su un altro dispositivo.
const val SESSION_FILE_NAME = "geosentinel.session.preferences_pb"
