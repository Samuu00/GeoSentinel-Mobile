// Classe di esempio rimasta dal modello di progetto Kotlin Multiplatform. Non è usata dall'app: mostra soltanto
// come il codice comune possa chiedere alla piattaforma (Android o iOS) il proprio nome tramite getPlatform().
package it.unical.geosentinel_mobile

/** Saluto di prova che include il nome della piattaforma su cui gira l'app. Non ha chiamanti nel progetto. */
class Greeting {
    private val platform = getPlatform()

    /** Restituisce il saluto, per esempio "Hello, Android 35!". */
    fun greet(): String {
        return sayHello(platform.name)
    }
}