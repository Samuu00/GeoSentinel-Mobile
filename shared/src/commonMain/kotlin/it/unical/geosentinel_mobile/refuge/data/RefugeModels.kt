// Modelli dei rifugi definiti nell'app. I dati del rifugio vero e proprio (nome, posti, descrizione) arrivano dal
// modello generato RefugeResponse; qui c'è solo la disponibilità per notte, ricavata dalla risposta del server.
package it.unical.geosentinel_mobile.refuge.data

import kotlinx.datetime.LocalDate

/** Numero di notti mostrate nel calendario del dettaglio a partire da stanotte. È anche il massimo che il server accetta. */
const val AVAILABILITY_NIGHTS = 14

/** Posti liberi nella notte che comincia il giorno [date]. [isFull] è vero quando non ne resta nessuno. */
data class NightAvailability(val date: LocalDate, val freeBeds: Int) {
    val isFull: Boolean get() = freeBeds <= 0
}
