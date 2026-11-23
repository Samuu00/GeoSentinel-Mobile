// Palette dei colori dell'app, in versione chiara e scura.
// Il colore vivo sta nel cielo delle testate (Sky.kt), che segue l'ora del giorno; il resto dell'interfaccia è
// quieto: fondo salvia freddo, card bianche, un solo verde (felce) per selezione e link e un verde pino quasi nero
// per l'azione principale. Di notte: fondo verde molto scuro, card appena più chiare, azione in verde menta.
// I rapporti di contrasto indicati accanto ai colori sono calcolati sui valori e rispettano almeno WCAG AA.
// Questi valori vanno usati attraverso `MaterialTheme.colorScheme` (vedi Theme.kt), non direttamente nei
// componenti; fanno eccezione il rosso d'emergenza e i colori della mappa.
package it.unical.geosentinel_mobile.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

// Fondo e superfici.
// Fondo chiaro: una nebbia color salvia.
val CanvasLight = Color(0xFFEEF2EE)
val CardLight = Color(0xFFFFFFFF)
// Superficie in rilievo: binari dei segmenti, tessere, sagome di caricamento.
val RaisedLight = Color(0xFFE2E8E3)
val CanvasDark = Color(0xFF09110E)
val CardDark = Color(0xFF121C18)
val RaisedDark = Color(0xFF1A2621)
val MenuDark = Color(0xFF22302A)

// Testo.
// Testo principale: contrasto 15,6:1 sul fondo.
val InkLight = Color(0xFF0E1B16)
// Testo secondario: 6,2:1 sulla card, 5,0:1 sulla superficie in rilievo.
val Ink2Light = Color(0xFF56645D)
val InkDark = Color(0xFFE9F0EC)
// Testo secondario scuro: 7,1:1 sulla card scura.
val Ink2Dark = Color(0xFF9AA9A1)

// Azione principale: pino nel tema chiaro, menta nel tema scuro.
// Il bianco sopra il pino ha contrasto 12,9:1.
val PineLight = Color(0xFF12382B)
val PineDark = Color(0xFFA6E2C0)
// Testo sopra la menta: 11,2:1.
val OnPineDark = Color(0xFF08241A)

// Accento: felce. Usato per selezione, link e valori positivi come i posti liberi.
// Felce chiara: 6,5:1 sulla card, 5,3:1 sulla felce tenue.
val FernLight = Color(0xFF226A49)
val FernSoftLight = Color(0xFFD7EEDF)
// Felce scura: 10:1 sulla card scura, 7,2:1 sulla felce tenue scura.
val FernDark = Color(0xFF8BD3AB)
val FernSoftDark = Color(0xFF18392A)

// Linee di separazione.
val LineLight = Color(0xFFD5DDD7)
val LineDark = Color(0xFF24312B)

// Emergenza: riservato a SOS, pericoli CRITICAL e sentieri chiusi. Il bianco sopra ha contrasto 5:1.
val EmergencyRed = Color(0xFFD32F2F)
val EmergencyOnRed = Color(0xFFFFFFFF)
val EmergencySoft = Color(0xFFFBE0E0)
// Testo rosso sul rosso tenue: 5,3:1.
val EmergencyInk = Color(0xFFB71C1C)

// Errori (form, rete): color prugna, così il rosso resta riservato all'emergenza. Sono sempre accompagnati da
// un'icona o da un testo, mai affidati al solo colore.
// Prugna chiara: 7,4:1 sul bianco.
val ErrorPlumLight = Color(0xFF8E2C82)
val OnErrorPlumLight = Color(0xFFFFFFFF)
val ErrorPlumContainerLight = Color(0xFFFBE7F8)
val OnErrorPlumContainerLight = Color(0xFF3A0035)
val ErrorPlumDark = Color(0xFFF2B0E8)
val OnErrorPlumDark = Color(0xFF5A0052)
val ErrorPlumContainerDark = Color(0xFF4A1844)
val OnErrorPlumContainerDark = Color(0xFFFFD7F3)

// Stati del sentiero, sempre accompagnati da un'icona o da un'etichetta.
val TrailOpen = Color(0xFF2E7D32)
// Ambra per linee della mappa e icone: 5:1 sul bianco.
val TrailWarning = Color(0xFFB45309)
val TrailClosed = EmergencyRed
val WarningSoftLight = Color(0xFFFBEBD7)
// Testo ambra sull'ambra tenue: 5,4:1.
val WarningInkLight = Color(0xFF9A4A0A)
val WarningSoftDark = Color(0xFF3A2610)
// Testo ambra sulla card scura: 8,5:1.
val WarningTextDark = Color(0xFFE9A862)

// Mappa: colori di segnaposti e nomi disegnati sopra la mappa. Sono fissi e non vengono dalla palette
// dell'interfaccia, perché la mappa ha un aspetto proprio che resta uguale anche cambiando il resto.
// Colori dei bollini dei POI per categoria e dei rifugi.
val MarkerShelter = Color(0xFF9A6A44)
val MarkerWater = Color(0xFF2D84D2)
val MarkerViewpoint = Color(0xFF7E57C2)
val MarkerDanger = Color(0xFFC77417)
val MarkerEquipment = Color(0xFF66707A)
val MarkerInfo = Color(0xFF3F6E9E)
val MarkerOther = Color(0xFF6B7A72)
val MarkerRefuge = Color(0xFF24654A)
// Colore dei nomi scritti sulla mappa.
val MapLabelInkLight = Color(0xFF1A1D1B)
val MapLabelInkDark = Color(0xFFEFF2EF)
// Grigio per sentieri e pericoli con stato sconosciuto e per il fondo dei segnaposti delle allerte.
val MapMutedLight = Color(0xFF666B67)
val MapMutedDark = Color(0xFF9BA6A0)
// Riempimento interno dei segnaposti.
val MapMarkerFillLight = Color(0xFFFFFFFF)
val MapMarkerFillDark = Color(0xFF151D1A)

/**
 * Dice se il tema in uso è scuro. Si ricava dalla luminanza del fondo e non dall'impostazione del sistema,
 * perché l'utente può forzare il tema chiaro o scuro dall'app.
 */
@Composable
fun isDarkTheme(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

/** Ambra leggibile come testo sulle card, nel tema in uso. */
@Composable
fun warningTextColor(): Color = if (isDarkTheme()) WarningTextDark else WarningInkLight

/** Rosso leggibile come testo sul rosso tenue (chip "Chiuso", allerte nuove), nel tema in uso. */
@Composable
fun emergencyTextColor(): Color = if (isDarkTheme()) Color(0xFFFF9C94) else EmergencyInk

/** Fondo rosso tenue, nel tema in uso. */
@Composable
fun emergencySoftColor(): Color = if (isDarkTheme()) Color(0xFF3D1716) else EmergencySoft

/** Fondo tenue per gli avvisi ambra (chip "Attenzione", icone dei pericoli), nel tema in uso. */
@Composable
fun warningSoftColor(): Color = if (isDarkTheme()) WarningSoftDark else WarningSoftLight
