package it.unical.geosentinel_mobile.refuge.data

import kotlinx.datetime.LocalDate

// Rifugio, nome e posti arrivano dal modello generato (RefugeResponse): il backend non ha altitudine né contatti

// Il calendario del dettaglio: da stanotte, quante notti. 14 è anche il massimo che il backend accetta
const val AVAILABILITY_NIGHTS = 14

/** Posti liberi in una notte (quella che comincia il giorno [date]). */
data class NightAvailability(val date: LocalDate, val freeBeds: Int) {
    val isFull: Boolean get() = freeBeds <= 0
}
