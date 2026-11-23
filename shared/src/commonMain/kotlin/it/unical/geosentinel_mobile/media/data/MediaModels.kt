// Modelli usati dal caricamento delle foto (MediaRepository e MediaUploader).
package it.unical.geosentinel_mobile.media.data

import it.unical.geosentinel_mobile.api.model.InitUploadRequest

/**
 * Entità a cui si allega una foto: il tipo (sentiero, segnalazione, rifugio, utente) e il suo identificativo.
 * È il server a decidere chi può allegare cosa: per esempio `TRAIL` è permesso solo a `PARK_MANAGER` e `HAZARD_REPORT`
 * solo all'autore della segnalazione.
 */
data class MediaTarget(
    val type: InitUploadRequest.EntityType,
    val entityId: String,
)
