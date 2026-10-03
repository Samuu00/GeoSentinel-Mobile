package it.unical.geosentinel_mobile.core.ui.theme

import androidx.compose.ui.graphics.Color

// Palette "carta topografica": i colori di una carta escursionistica e del bosco che descrive.
// Chiaro = carta color pietra con l'inchiostro verde abete; scuro = bosco di notte con il lichene come accento.
// Le superfici si separano con un passo di tono e un filo di bordo, non con le ombre.
// Il rosso resta solo all'emergenza.

// Accento: verde abete (chiaro) e lichene (scuro)
val FirLight = Color(0xFF1F4535)                  // 10:1 sulle card
val OnFirLight = Color(0xFFFFFFFF)
val LichenDark = Color(0xFFA9D3B6)                // 10:1 sulle card scure
val OnLichenDark = Color(0xFF0F2A1D)

// Muschio: contenitori morbidi (avatar, scheda attiva)
val MossLight = Color(0xFFDAE6D3)
val OnMossLight = Color(0xFF12301F)
val MossDark = Color(0xFF223A2C)
val OnMossDark = Color(0xFFCBE8D4)

// Sfondi
val StoneLight = Color(0xFFEDF0E9)                // carta color pietra, appena verde
val NightForestDark = Color(0xFF0D1411)           // bosco di notte (non nero puro: sempre scuro sugli OLED)

// Card, barre e menu: un passo sopra lo sfondo
val CardLight = Color(0xFFFAFBF7)
val CardDark = Color(0xFF151E19)
val MenuLight = Color(0xFFFFFFFF)
val MenuDark = Color(0xFF1C2721)
val TrackLight = Color(0xFFE2E7DD)                // binario del selettore del tema
val TrackDark = Color(0xFF222D27)

// Testo
val InkLight = Color(0xFF13201A)
val InkDark = Color(0xFFE5ECE7)
val InkSecondaryLight = Color(0xFF4E5D54)         // 6:1 sulla pietra
val InkSecondaryDark = Color(0xFFA0B0A6)          // 7:1 sulle card scure

// Fili: bordi delle card e curve di livello
val LineLight = Color(0xFFD3DBD0)
val LineDark = Color(0xFF29352E)

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
