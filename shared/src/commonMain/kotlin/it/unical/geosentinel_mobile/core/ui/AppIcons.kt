// Icone vettoriali dell'app, definite direttamente in codice.
// Ogni icona è costruita come ImageVector a partire dai tracciati SVG della libreria Lucide, così l'app non
// dipende da file di risorse o da un pacchetto di icone esterno.
package it.unical.geosentinel_mobile.core.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Raccolta delle icone dell'app: disegnate a tratto, 24 dp, linea spessa 2 con estremità e angoli arrotondati.
 * I tracciati sono quelli di Lucide (lucide.dev, licenza ISC, © Lucide Contributors; il testo della licenza è in
 * composeResources/files/LICENSE-Lucide.txt).
 *
 * Hanno la stessa misura delle icone di Material, quindi si usano con `Icon(...)`, che le colora con il tint.
 * Ogni icona è creata in modo pigro (`by lazy`) la prima volta che serve. Il nome della proprietà descrive
 * il disegno, non l'uso.
 */
object AppIcons {
    val Alert by lazy { icon("alert", "m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3", "M12 9v4", "M12 17h.01") }
    val Bed by lazy { icon("bed", "M2 4v16", "M2 8h18a2 2 0 0 1 2 2v10", "M2 17h20", "M6 8v9") }
    val Bell by lazy { icon("bell", "M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9", "M10.3 21a1.94 1.94 0 0 0 3.4 0") }
    val Calendar by lazy { icon("calendar", "M8 2v4", "M16 2v4", rect(3f, 4f, 18f, 18f, 2f), "M3 10h18") }
    val Camera by lazy {
        icon("camera", "M14.5 4h-5L7 7H4a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2h-3l-2.5-3z", circle(12f, 13f, 3f))
    }
    val Check by lazy { icon("check", "M20 6 9 17l-5-5") }
    val CheckCircle by lazy { icon("check-circle", circle(12f, 12f, 10f), "m9 12 2 2 4-4") }
    val ChevronLeft by lazy { icon("chevron-left", "m15 18-6-6 6-6") }
    val ChevronRight by lazy { icon("chevron-right", "m9 18 6-6-6-6") }
    val ChevronDown by lazy { icon("chevron-down", "m6 9 6 6 6-6") }
    val ChevronUp by lazy { icon("chevron-up", "m18 15-6-6-6 6") }
    val Clock by lazy { icon("clock", circle(12f, 12f, 10f), "M12 6v6l4 2") }
    val Close by lazy { icon("x", "M18 6 6 18", "m6 6 12 12") }
    val Flag by lazy { icon("flag", "M4 15s1-1 4-1 5 2 8 2 4-1 4-1V3s-1 1-4 1-5-2-8-2-4 1-4 1z", "M4 22v-7") }
    val House by lazy {
        icon(
            "house",
            "M15 21v-8a1 1 0 0 0-1-1h-4a1 1 0 0 0-1 1v8",
            "M3 10a2 2 0 0 1 .709-1.528l7-5.999a2 2 0 0 1 2.582 0l7 5.999A2 2 0 0 1 21 10v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z",
        )
    }
    val Info by lazy { icon("info", circle(12f, 12f, 10f), "M12 16v-4", "M12 8h.01") }
    val Key by lazy { icon("key", "m15.5 7.5 2.3 2.3a1 1 0 0 0 1.4 0l2.1-2.1a1 1 0 0 0 0-1.4L19 4", "m21 2-9.6 9.6", circle(7.5f, 15.5f, 5.5f)) }
    val Layers by lazy {
        icon(
            "layers",
            "M12.83 2.18a2 2 0 0 0-1.66 0L2.6 6.08a1 1 0 0 0 0 1.83l8.58 3.91a2 2 0 0 0 1.66 0l8.58-3.9a1 1 0 0 0 0-1.83Z",
            "m22 17.65-9.17 4.16a2 2 0 0 1-1.66 0L2 17.65",
            "m22 12.65-9.17 4.16a2 2 0 0 1-1.66 0L2 12.65",
        )
    }
    val Locate by lazy { icon("locate", "M2 12h3", "M19 12h3", "M12 2v3", "M12 19v3", circle(12f, 12f, 7f), circle(12f, 12f, 3f)) }
    val Lock by lazy { icon("lock", rect(3f, 11f, 18f, 11f, 2f), "M7 11V7a5 5 0 0 1 10 0v4") }
    val LogOut by lazy { icon("log-out", "M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4", "m16 17 5-5-5-5", "M21 12H9") }
    val Mail by lazy { icon("mail", rect(2f, 4f, 20f, 16f, 2f), "m22 7-8.97 5.7a1.94 1.94 0 0 1-2.06 0L2 7") }
    val Map by lazy {
        icon(
            "map",
            "M14.106 5.553a2 2 0 0 0 1.788 0l3.659-1.83A1 1 0 0 1 21 4.619v12.764a1 1 0 0 1-.553.894l-4.553 2.277a2 2 0 0 1-1.788 0l-4.212-2.106a2 2 0 0 0-1.788 0l-3.659 1.83A1 1 0 0 1 3 19.381V6.618a1 1 0 0 1 .553-.894l4.553-2.277a2 2 0 0 1 1.788 0z",
            "M15 5.764v15",
            "M9 3.236v15",
        )
    }
    val MapPin by lazy { icon("map-pin", "M20 10c0 4.993-5.539 10.193-7.399 11.799a1 1 0 0 1-1.202 0C9.539 20.193 4 14.993 4 10a8 8 0 0 1 16 0", circle(12f, 10f, 3f)) }
    val Minus by lazy { icon("minus", "M5 12h14") }
    val Moon by lazy { icon("moon", "M12 3a6 6 0 0 0 9 9 9 9 0 1 1-9-9Z") }
    val Mountain by lazy { icon("mountain", "m8 3 4 8 5-5 5 15H2L8 3z") }
    val Navigation by lazy { icon("navigation", "M3 11 22 2 13 21 11 13z") }
    val Pencil by lazy {
        icon(
            "pencil",
            "M21.174 6.812a1 1 0 0 0-3.986-3.987L3.842 16.174a2 2 0 0 0-.5.83l-1.321 4.352a.5.5 0 0 0 .623.622l4.353-1.32a2 2 0 0 0 .83-.497z",
            "m15 5 4 4",
        )
    }
    val Phone by lazy {
        icon(
            "phone",
            "M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z",
        )
    }
    val Plus by lazy { icon("plus", "M5 12h14", "M12 5v14") }
    val Refresh by lazy {
        icon(
            "refresh",
            "M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8",
            "M21 3v5h-5",
            "M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16",
            "M8 16H3v5",
        )
    }
    val Route by lazy { icon("route", circle(6f, 19f, 3f), "M9 19h8.5a3.5 3.5 0 0 0 0-7h-11a3.5 3.5 0 0 1 0-7H15", circle(18f, 5f, 3f)) }
    val Search by lazy { icon("search", circle(11f, 11f, 8f), "m21 21-4.3-4.3") }
    val Shield by lazy {
        icon(
            "shield",
            "M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z",
            "m9 12 2 2 4-4",
        )
    }
    val Sun by lazy {
        icon("sun", circle(12f, 12f, 4f), "M12 2v2", "M12 20v2", "m4.93 4.93 1.41 1.41", "m17.66 17.66 1.41 1.41", "M2 12h2", "M20 12h2", "m6.34 17.66-1.41 1.41", "m19.07 4.93-1.41 1.41")
    }
    val Sunrise by lazy {
        icon("sunrise", "M12 2v8", "m4.93 10.93 1.41 1.41", "M2 18h2", "M20 18h2", "m19.07 10.93-1.41 1.41", "M22 22H2", "m8 6 4-4 4 4", "M16 18a4 4 0 0 0-8 0")
    }
    val Sunset by lazy {
        icon("sunset", "M12 10V2", "m4.93 10.93 1.41 1.41", "M2 18h2", "M20 18h2", "m19.07 10.93-1.41 1.41", "M22 22H2", "m16 6-4 4-4-4", "M16 18a4 4 0 0 0-8 0")
    }
    val TrendingUp by lazy { icon("trending-up", "M22 7 13.5 15.5 8.5 10.5 2 17", "M16 7h6v6") }
    val Users by lazy {
        icon("users", "M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2", circle(9f, 7f, 4f), "M22 21v-2a4 4 0 0 0-3-3.87", "M16 3.13a4 4 0 0 1 0 7.75")
    }
    val User by lazy { icon("user", "M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2", circle(12f, 7f, 4f)) }
    val Wrench by lazy {
        icon(
            "wrench",
            "M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z",
        )
    }
}

/**
 * Costruisce un'icona a tratto in un riquadro di 24x24 unità con il nome [name] e uno o più tracciati SVG
 * ([paths], nel formato dell'attributo `d`). Il colore nero è solo un segnaposto: `Icon` lo sostituisce con il tint.
 */
private fun icon(name: String, vararg paths: String): ImageVector =
    ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
        paths.forEach { data ->
            addPath(
                pathData = addPathNodes(data),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()

// Lucide usa anche gli elementi SVG circle e rect con angoli arrotondati; queste due funzioni li convertono in
// tracciati equivalenti, perché ImageVector accetta solo tracciati.

/** Cerchio di centro ([cx], [cy]) e raggio [r], disegnato con due archi di mezzo giro. */
private fun circle(cx: Float, cy: Float, r: Float) = "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0"

/** Rettangolo con angolo in alto a sinistra in ([x], [y]), dimensioni [w] per [h] e raggio degli angoli [rx]. */
private fun rect(x: Float, y: Float, w: Float, h: Float, rx: Float) =
    "M${x + rx} ${y}h${w - 2 * rx}a$rx $rx 0 0 1 $rx ${rx}v${h - 2 * rx}a$rx $rx 0 0 1 ${-rx} ${rx}h${-(w - 2 * rx)}a$rx $rx 0 0 1 ${-rx} ${-rx}v${-(h - 2 * rx)}a$rx $rx 0 0 1 $rx ${-rx}z"
