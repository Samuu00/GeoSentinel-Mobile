// Dichiarazioni comuni per proteggere i contenuti sensibili sullo schermo e negli appunti.
// Servono alla schermata della verifica in due passaggi, che mostra la chiave e i codici di recupero.
package it.unical.geosentinel_mobile.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ClipEntry

/**
 * Finché resta nella composizione impedisce che lo schermo finisca in screenshot, registrazioni e anteprime
 * dell'elenco delle app recenti. Su Android imposta `FLAG_SECURE` sulla finestra; su iOS copre il contenuto
 * quando l'app esce dal primo piano. Si usa per i codici di recupero della verifica in due passaggi.
 */
@Composable
expect fun SecureScreen()

/** Crea una voce per gli appunti segnata come sensibile: da Android 13 il sistema non ne mostra l'anteprima. */
expect fun sensitiveClipEntry(text: String): ClipEntry
