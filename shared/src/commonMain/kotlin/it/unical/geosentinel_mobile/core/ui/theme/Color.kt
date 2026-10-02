package it.unical.geosentinel_mobile.core.ui.theme

import androidx.compose.ui.graphics.Color

// Palette "foresta": nebbia del mattino in chiaro, bosco di notte in scuro.
// Pochi colori: un verde pino come accento, neutri con una punta di verde, il rosso solo per l'emergenza.

// Accento: verde pino (chiaro) e salvia (scuro)
val PineLight = Color(0xFF1F5B3F)                 // 8:1 sulle card bianche
val OnPineLight = Color(0xFFFFFFFF)
val SageDark = Color(0xFF8FD4A8)                  // 9:1 sulle card scure
val OnSageDark = Color(0xFF0B2817)

// Muschio: contenitori morbidi (avatar, illustrazioni, scheda attiva)
val MossLight = Color(0xFFDCE9D5)
val OnMossLight = Color(0xFF10301F)
val MossDark = Color(0xFF1F3D2C)
val OnMossDark = Color(0xFFC8EBD3)

// Sfondi
val MistLight = Color(0xFFF3F5F0)                 // nebbia
val NightForestDark = Color(0xFF0E1612)           // bosco di notte (non nero puro: più caldo, sempre scuro sugli OLED)

// Card, barre e menu
val CardLight = Color(0xFFFCFDFA)
val CardDark = Color(0xFF17211B)
val MenuLight = Color(0xFFFFFFFF)
val MenuDark = Color(0xFF1F2B24)
val TrackLight = Color(0xFFE4EADF)                // binario del selettore del tema
val TrackDark = Color(0xFF24302A)

// Testo
val InkLight = Color(0xFF14231B)
val InkDark = Color(0xFFE7EEE9)
val InkSecondaryLight = Color(0xFF55665B)         // 5,5:1 sulla nebbia
val InkSecondaryDark = Color(0xFFA3B3A8)          // 7,3:1 sulle card scure

// Separatori
val LineLight = Color(0xFFDDE3DA)
val LineDark = Color(0xFF2A362F)

// Emergenza: riservato a SOS e pericoli CRITICAL. Bianco sopra a 5:1, e si stacca da entrambi gli sfondi.
val EmergencyRed = Color(0xFFD32F2F)
val EmergencyOnRed = Color(0xFFFFFFFF)

// Errori (form, rete, validazione): prugna, così il rosso resta solo a SOS e CRITICAL.
// Si distingue dal rosso anche con la protanopia, e va sempre con icona e testo.
val ErrorPlumLight = Color(0xFF8E2C82)            // 7,4:1 sul bianco
val OnErrorPlumLight = Color(0xFFFFFFFF)
val ErrorPlumContainerLight = Color(0xFFFFD7F3)
val OnErrorPlumContainerLight = Color(0xFF3A0035)
val ErrorPlumDark = Color(0xFFF2B0E8)             // 9:1 sulle card scure
val OnErrorPlumDark = Color(0xFF5A0052)
val ErrorPlumContainerDark = Color(0xFF74266A)
val OnErrorPlumContainerDark = Color(0xFFFFD7F3)

// Stati del sentiero, sempre affiancati da icona o etichetta (almeno 4,5:1 sul bianco)
val TrailOpen = Color(0xFF2E7D32)
val TrailWarning = Color(0xFFB45309)
val TrailClosed = EmergencyRed
