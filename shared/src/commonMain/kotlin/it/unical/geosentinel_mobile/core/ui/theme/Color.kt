package it.unical.geosentinel_mobile.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

// Palette "Luce di quota" (redesign 11/2026, Figma: "GeoSentinel — Redesign Luce di quota").
// Il colore vivo sta nel cielo delle testate (Sky.kt), che segue l'ora; l'interfaccia resta quieta: fondo salvia
// freddo, card bianche, un solo verde (felce) per selezione e link, il pino quasi nero per l'azione piena.
// Di notte: bosco nero-verde, card un passo sopra, azione in menta. Rapporti di contrasto calcolati sui valori.

// Fondo e superfici
val CanvasLight = Color(0xFFEEF2EE)               // nebbia salvia
val CardLight = Color(0xFFFFFFFF)
val RaisedLight = Color(0xFFE2E8E3)               // binari, tessere, sagome di caricamento
val CanvasDark = Color(0xFF09110E)
val CardDark = Color(0xFF121C18)
val RaisedDark = Color(0xFF1A2621)
val MenuDark = Color(0xFF22302A)

// Testo
val InkLight = Color(0xFF0E1B16)                  // 15,6:1 sul fondo
val Ink2Light = Color(0xFF56645D)                 // 6,2:1 sulla card, 5,0:1 sul rilievo
val InkDark = Color(0xFFE9F0EC)
val Ink2Dark = Color(0xFF9AA9A1)                  // 7,1:1 sulla card scura

// Azione piena: pino (chiaro) e menta (scuro)
val PineLight = Color(0xFF12382B)                 // bianco sopra 12,9:1
val PineDark = Color(0xFFA6E2C0)
val OnPineDark = Color(0xFF08241A)                // 11,2:1 sulla menta

// Accento: felce. Selezione, link, valori "liberi"
val FernLight = Color(0xFF226A49)                 // 6,5:1 sulla card, 5,3:1 sulla felce tenue
val FernSoftLight = Color(0xFFD7EEDF)
val FernDark = Color(0xFF8BD3AB)                  // 10:1 sulla card scura, 7,2:1 sulla felce tenue
val FernSoftDark = Color(0xFF18392A)

// Fili
val LineLight = Color(0xFFD5DDD7)
val LineDark = Color(0xFF24312B)

// Emergenza: riservato a SOS, pericoli CRITICAL e sentieri chiusi. Bianco sopra a 5:1.
val EmergencyRed = Color(0xFFD32F2F)
val EmergencyOnRed = Color(0xFFFFFFFF)
val EmergencySoft = Color(0xFFFBE0E0)
val EmergencyInk = Color(0xFFB71C1C)              // testo rosso sul rosso tenue, 5,3:1

// Errori (form, rete): prugna, così il rosso resta solo all'emergenza. Sempre con icona o testo.
val ErrorPlumLight = Color(0xFF8E2C82)            // 7,4:1 sul bianco
val OnErrorPlumLight = Color(0xFFFFFFFF)
val ErrorPlumContainerLight = Color(0xFFFBE7F8)
val OnErrorPlumContainerLight = Color(0xFF3A0035)
val ErrorPlumDark = Color(0xFFF2B0E8)
val OnErrorPlumDark = Color(0xFF5A0052)
val ErrorPlumContainerDark = Color(0xFF4A1844)
val OnErrorPlumContainerDark = Color(0xFFFFD7F3)

// Stati del sentiero, sempre con icona o etichetta
val TrailOpen = Color(0xFF2E7D32)
val TrailWarning = Color(0xFFB45309)              // linee della mappa e icone (5:1 sul bianco)
val TrailClosed = EmergencyRed
val WarningSoftLight = Color(0xFFFBEBD7)
val WarningInkLight = Color(0xFF9A4A0A)           // testo ambra sull'ambra tenue, 5,4:1
val WarningSoftDark = Color(0xFF3A2610)
val WarningTextDark = Color(0xFFE9A862)           // 8,5:1 sulla card scura

// ---- Mappa: invariata (stili, segnaposti, nomi). Valori identici a prima del redesign. ----
val MarkerShelter = Color(0xFF9A6A44)
val MarkerWater = Color(0xFF2D84D2)
val MarkerViewpoint = Color(0xFF7E57C2)
val MarkerDanger = Color(0xFFC77417)
val MarkerEquipment = Color(0xFF66707A)
val MarkerInfo = Color(0xFF3F6E9E)
val MarkerOther = Color(0xFF6B7A72)
val MarkerRefuge = Color(0xFF24654A)
val MapLabelInkLight = Color(0xFF1A1D1B)
val MapLabelInkDark = Color(0xFFEFF2EF)
// grigio dei sentieri e dei pericoli senza stato noto, fondo dei segnaposti delle allerte
val MapMutedLight = Color(0xFF666B67)
val MapMutedDark = Color(0xFF9BA6A0)
val MapMarkerFillLight = Color(0xFFFFFFFF)
val MapMarkerFillDark = Color(0xFF151D1A)

/** Il tema in uso è scuro? (dal fondo: lo decide l'utente, non solo il sistema) */
@Composable
fun isDarkTheme(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

/** Ambra leggibile come testo sulle card, nel tema in uso. */
@Composable
fun warningTextColor(): Color = if (isDarkTheme()) WarningTextDark else WarningInkLight

/** Rosso leggibile come testo sul rosso tenue (chip "Chiuso", allerte nuove), nel tema in uso. */
@Composable
fun emergencyTextColor(): Color = if (isDarkTheme()) Color(0xFFFF9C94) else EmergencyInk

/** Fondo tenue rosso, nel tema in uso. */
@Composable
fun emergencySoftColor(): Color = if (isDarkTheme()) Color(0xFF3D1716) else EmergencySoft

/** Fondo tenue per avvisi ambra (chip "Attenzione", icone dei pericoli). */
@Composable
fun warningSoftColor(): Color = if (isDarkTheme()) WarningSoftDark else WarningSoftLight
