package it.unical.geosentinel_mobile.core.ui.theme

import androidx.compose.ui.graphics.Color

// Palette "Alba alpina" (redesign 11/2026, terza passata): le superfici sono quelle di iOS (card bianche sul fondo
// raggruppato), ma il fondo è la nebbia calda di una mattina in quota e non un grigio da ufficio; di notte è il bosco
// blu-verde, non il nero. Un solo accento, l'abete (lichene di notte), per azioni, selezione e valori "liberi".
// La montagna sta nella testata delle schede (cielo d'alba e creste), nei titoli Barlow e nelle foto.
// I nomi restano quelli di prima (FirLight, StoneLight...): li usano Theme e qualche componente.

// Accento: abete (chiaro) e lichene (scuro)
val FirLight = Color(0xFF24654A)                  // 6,9:1 con il bianco sopra; 6,2:1 come testo sul fondo
val OnFirLight = Color(0xFFFFFFFF)
val LichenDark = Color(0xFF8BD3AB)                // 10,4:1 sul fondo scuro
val OnLichenDark = Color(0xFF0A2A1B)

// Muschio: contenitori morbidi (pillola della scheda attiva, avatar, pulsanti tonali)
val MossLight = Color(0xFFDDEDE3)
val OnMossLight = Color(0xFF123726)               // 11:1 sul muschio
val MossDark = Color(0xFF1B3428)
val OnMossDark = Color(0xFFCBEBD8)

// Sfondi: nebbia calda (chiaro) e bosco di notte, un blu-verde profondo (scuro)
val StoneLight = Color(0xFFF2F1EC)
val NightForestDark = Color(0xFF0B1210)

// Card, barre e menu: bianco sulla nebbia; di notte un passo sopra il bosco
val CardLight = Color(0xFFFFFFFF)
val CardDark = Color(0xFF151D1A)
val MenuLight = Color(0xFFFFFFFF)
val MenuDark = Color(0xFF202A26)
val TrackLight = Color(0xFFE4E3DC)                // binari (segmenti, tema), sagome di caricamento
val TrackDark = Color(0xFF25302B)

// Testo
val InkLight = Color(0xFF1A1D1B)                  // 16:1 sul bianco
val InkDark = Color(0xFFEFF2EF)
val InkSecondaryLight = Color(0xFF666B67)         // 5,6:1 sul bianco, 5,0:1 sulla nebbia
val InkSecondaryDark = Color(0xFF9BA6A0)          // 6,6:1 sulle card scure

// Fili: separatori sottili e curve di livello
val LineLight = Color(0xFFDCDCD4)
val LineDark = Color(0xFF29342F)

// Cielo dell'alba nella testata delle schede: solo decorazione, il testo non ci sta mai sopra
val DawnLight = Color(0xFFF7E4CF)                 // pesca chiaro all'orizzonte
val DawnDark = Color(0xFF15263A)                  // blu della sera sopra le creste

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
val TrailWarning = Color(0xFFB45309)             // 4,8:1 sulle card chiare, solo 3,4:1 su quelle scure
val TrailClosed = EmergencyRed

// Segnaposti della mappa (11/2026), come le categorie di Apple Maps: un colore per tipo, simbolo bianco sopra (almeno
// 3:1 col bianco). Sono colori della mappa, uguali nei due temi, come quelli degli stili. Mai rosso: il pericolo è ambra.
val MarkerShelter = Color(0xFF9A6A44)            // bivacco: legno
val MarkerWater = Color(0xFF2D84D2)              // sorgente
val MarkerViewpoint = Color(0xFF7E57C2)          // panorama
val MarkerDanger = Color(0xFFC77417)             // zona pericolosa
val MarkerEquipment = Color(0xFF66707A)          // attrezzatura
val MarkerInfo = Color(0xFF3F6E9E)               // informazioni
val MarkerOther = Color(0xFF6B7A72)
val MarkerRefuge = FirLight                      // rifugi: il verde dell'app, più grandi dei POI

// Ambra per il testo nel tema scuro (posti "ultimi" nel calendario dei rifugi): 8,3:1 su CardDark.
// Le linee della mappa restano TrailWarning, uguale nei due temi.
val WarningTextDark = Color(0xFFE9A862)

/** Ambra leggibile come testo sulle card, nel tema in uso. */
@androidx.compose.runtime.Composable
fun warningTextColor(): Color =
    if (androidx.compose.material3.MaterialTheme.colorScheme.background == NightForestDark) WarningTextDark else TrailWarning

/** Il colore del cielo all'orizzonte nella testata, nel tema in uso. */
@androidx.compose.runtime.Composable
fun dawnColor(): Color =
    if (androidx.compose.material3.MaterialTheme.colorScheme.background == NightForestDark) DawnDark else DawnLight
