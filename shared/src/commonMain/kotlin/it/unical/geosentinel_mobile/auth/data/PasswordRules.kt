package it.unical.geosentinel_mobile.auth.data

// Regola del backend (RegisterRequest, ADR-0011): almeno 12 caratteri e al massimo 72 byte UTF-8, il limite di
// BCrypt. Il massimo si conta in byte: 40 caratteri accentati o con emoji possono superare i 72 byte.
// Il minimo come @Size di Java, in unità UTF-16 (String.length), così app e server danno sempre la stessa risposta.
enum class PasswordProblem { TOO_SHORT, TOO_LONG }

const val PASSWORD_MIN_CHARS = 12
const val PASSWORD_MAX_BYTES = 72

fun passwordProblem(password: String): PasswordProblem? = when {
    password.length < PASSWORD_MIN_CHARS -> PasswordProblem.TOO_SHORT
    password.encodeToByteArray().size > PASSWORD_MAX_BYTES -> PasswordProblem.TOO_LONG
    else -> null
}
