// Regole sulla password controllate nell'app prima di inviarla al server, nella registrazione, nel cambio
// password e nel reset. Sono le stesse del backend: così l'utente vede subito l'errore nel form e app e server
// danno sempre la stessa risposta sulla stessa password.
package it.unical.geosentinel_mobile.auth.data

/**
 * Motivo per cui una password non è accettabile.
 *
 * TOO_SHORT: meno di [PASSWORD_MIN_CHARS] caratteri. TOO_LONG: più di [PASSWORD_MAX_BYTES] byte in UTF-8.
 */
enum class PasswordProblem { TOO_SHORT, TOO_LONG }

/**
 * Lunghezza minima, contata come fa la validazione `@Size` di Java sul server: in unità UTF-16
 * (`String.length`), non in caratteri visibili.
 */
const val PASSWORD_MIN_CHARS = 12

/**
 * Lunghezza massima in byte UTF-8. È il limite di BCrypt, che ignora i byte oltre il settantaduesimo: per questo
 * si contano i byte e non i caratteri (40 lettere accentate o emoji possono già superarlo).
 */
const val PASSWORD_MAX_BYTES = 72

/**
 * Controlla [password] contro le regole del server.
 *
 * @return il primo problema trovato, oppure null se la password è valida.
 */
fun passwordProblem(password: String): PasswordProblem? = when {
    password.length < PASSWORD_MIN_CHARS -> PasswordProblem.TOO_SHORT
    password.encodeToByteArray().size > PASSWORD_MAX_BYTES -> PasswordProblem.TOO_LONG
    else -> null
}
