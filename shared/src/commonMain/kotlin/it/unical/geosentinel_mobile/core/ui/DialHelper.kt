package it.unical.geosentinel_mobile.core.ui

import androidx.compose.runtime.Composable

/**
 * Apre il compositore del telefono con [phoneNumber] già scritto (`tel:`): non chiama da solo, decide l'utente (M6.1).
 * Composable perché su Android serve il Context dell'Activity. Senza un telefono (tablet, simulatore) non fa nulla.
 */
@Composable
expect fun rememberDialer(): (phoneNumber: String) -> Unit
