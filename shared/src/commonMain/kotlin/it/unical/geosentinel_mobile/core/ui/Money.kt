// Importi in euro: il server li manda sempre in centesimi interi (pricePerNightCents, totalPriceCents) e qui si
// formattano solo per la UI, in formato italiano. Nessuna conversione passa da Double, che arrotonderebbe male.
package it.unical.geosentinel_mobile.core.ui

/** Prezzo massimo a persona per notte accettato dal server, in centesimi (1000 €). */
const val MAX_PRICE_PER_NIGHT_CENTS = 100_000

/**
 * Formatta [cents] in formato italiano, per esempio 3500 → "35,00 €" e 123456 → "1.234,56 €". Tra cifra e simbolo
 * c'è uno spazio non separabile ([EURO_SPACE]): l'importo non va mai a capo lasciando il "€" da solo.
 */
fun formatEuro(cents: Int): String {
    val sign = if (cents < 0) "-" else ""
    val abs = kotlin.math.abs(cents.toLong())
    val euros = (abs / 100).toString().reversed().chunked(3).joinToString(".").reversed()
    val rest = (abs % 100).toString().padStart(2, '0')
    return "$sign$euros,$rest$EURO_SPACE€"
}

/** Spazio non separabile (U+00A0) tra importo e simbolo dell'euro. */
const val EURO_SPACE = '\u00A0'

/**
 * Converte il testo scritto dal gestore ("35", "35,5", "35.50") in centesimi, senza passare da un Double. Accetta la
 * virgola o il punto come separatore decimale e al massimo due decimali; restituisce null per un testo non valido,
 * negativo o oltre [MAX_PRICE_PER_NIGHT_CENTS]. Il testo vuoto non è un prezzo: lo gestisce chi chiama (toglie il prezzo).
 */
fun parseEuroToCents(text: String): Int? {
    val match = Regex("""^(\d{1,4})(?:[.,](\d{1,2}))?$""").matchEntire(text.trim()) ?: return null
    val euros = match.groupValues[1].toInt()
    val decimals = match.groupValues[2].padEnd(2, '0').toInt()
    return (euros * 100 + decimals).takeIf { it <= MAX_PRICE_PER_NIGHT_CENTS }
}

/** Testo da mettere nel campo per un prezzo esistente: 3500 → "35,00", null → "" (nessun prezzo). */
fun centsToEuroInput(cents: Int?): String = cents?.let { formatEuro(it).removeSuffix("$EURO_SPACE€").replace(".", "") } ?: ""

/**
 * Totale indicativo di un soggiorno, calcolato come il server: prezzo × notti × ospiti. Null se il rifugio non ha un
 * prezzo. È solo da mostrare: l'importo che fa fede è il `totalPriceCents` restituito con la prenotazione.
 */
fun stayTotalCents(pricePerNightCents: Int?, nights: Int, guests: Int): Int? =
    pricePerNightCents?.let { (it.toLong() * nights * guests).coerceAtMost(Int.MAX_VALUE.toLong()).toInt() }
