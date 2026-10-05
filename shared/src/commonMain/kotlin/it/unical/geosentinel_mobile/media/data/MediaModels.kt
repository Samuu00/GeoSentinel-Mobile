package it.unical.geosentinel_mobile.media.data

import it.unical.geosentinel_mobile.api.model.InitUploadRequest

/** A cosa si allega la foto. `TRAIL` solo per `PARK_MANAGER`, `HAZARD_REPORT` solo per chi ha fatto la segnalazione. */
data class MediaTarget(
    val type: InitUploadRequest.EntityType,
    val entityId: String,
)
