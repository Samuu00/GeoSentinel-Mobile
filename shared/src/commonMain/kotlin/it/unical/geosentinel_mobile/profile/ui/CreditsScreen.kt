// Schermata "Crediti e licenze" del Profilo. Raccoglie in un solo posto le attribuzioni richieste dalle licenze
// dei dati e delle risorse usate (mappe, immagini satellitari, foto, icone, font).
package it.unical.geosentinel_mobile.profile.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.credits_archivo
import geosentinel_mobile.shared.generated.resources.credits_archivo_license
import geosentinel_mobile.shared.generated.resources.credits_eox
import geosentinel_mobile.shared.generated.resources.credits_eox_license
import geosentinel_mobile.shared.generated.resources.credits_intro
import geosentinel_mobile.shared.generated.resources.credits_lucide
import geosentinel_mobile.shared.generated.resources.credits_lucide_license
import geosentinel_mobile.shared.generated.resources.credits_ofm
import geosentinel_mobile.shared.generated.resources.credits_ofm_license
import geosentinel_mobile.shared.generated.resources.credits_osm
import geosentinel_mobile.shared.generated.resources.credits_osm_license
import geosentinel_mobile.shared.generated.resources.credits_section_app
import geosentinel_mobile.shared.generated.resources.credits_section_map
import geosentinel_mobile.shared.generated.resources.credits_section_media
import geosentinel_mobile.shared.generated.resources.credits_terrain
import geosentinel_mobile.shared.generated.resources.credits_terrain_license
import geosentinel_mobile.shared.generated.resources.credits_title
import geosentinel_mobile.shared.generated.resources.credits_wikimedia
import geosentinel_mobile.shared.generated.resources.credits_wikimedia_license
import it.unical.geosentinel_mobile.core.ui.AppIcons
import it.unical.geosentinel_mobile.core.ui.GroupCard
import it.unical.geosentinel_mobile.core.ui.GroupDivider
import it.unical.geosentinel_mobile.core.ui.GroupHeader
import it.unical.geosentinel_mobile.core.ui.GroupIconIndent
import it.unical.geosentinel_mobile.core.ui.GroupRow
import it.unical.geosentinel_mobile.core.ui.LargeTitlePage
import it.unical.geosentinel_mobile.core.ui.PageMaxWidth
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Pagina dei crediti, divisa in sezioni (mappa, foto, app). Raccogliere qui le attribuzioni permette di tenerle
 * nascoste dietro un pulsante "i" sulla mappa e sulle foto. Le righe sono solo da leggere e non hanno azioni.
 */
@Composable
fun CreditsScreen(onBack: () -> Unit) {
    LargeTitlePage(title = stringResource(Res.string.credits_title), subtitle = stringResource(Res.string.credits_intro), onBack = onBack) {
        item(key = "credits") {
            Column(Modifier.widthIn(max = PageMaxWidth).fillMaxWidth().padding(horizontal = 20.dp)) {
                Section(
                    Res.string.credits_section_map,
                    listOf(
                        Res.string.credits_osm to Res.string.credits_osm_license,
                        Res.string.credits_ofm to Res.string.credits_ofm_license,
                        Res.string.credits_terrain to Res.string.credits_terrain_license,
                        Res.string.credits_eox to Res.string.credits_eox_license,
                    ),
                )
                Section(Res.string.credits_section_media, listOf(Res.string.credits_wikimedia to Res.string.credits_wikimedia_license))
                Section(
                    Res.string.credits_section_app,
                    listOf(
                        Res.string.credits_lucide to Res.string.credits_lucide_license,
                        Res.string.credits_archivo to Res.string.credits_archivo_license,
                    ),
                )
            }
        }
    }
}

/** Una sezione dei crediti: titolo e una card con una riga per ogni risorsa (nome e licenza). */
@Composable
private fun Section(title: StringResource, rows: List<Pair<StringResource, StringResource>>) {
    GroupHeader(stringResource(title))
    GroupCard {
        rows.forEachIndexed { index, (name, license) ->
            if (index > 0) GroupDivider(GroupIconIndent)
            GroupRow(
                title = stringResource(name),
                subtitle = stringResource(license),
                onClick = {},
                enabled = false,
                chevron = false,
                icon = AppIcons.Info,
            )
        }
    }
}
