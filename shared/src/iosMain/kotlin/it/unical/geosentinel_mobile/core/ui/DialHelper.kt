package it.unical.geosentinel_mobile.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSURL
import platform.UIKit.UIApplication

@Composable
actual fun rememberDialer(): (phoneNumber: String) -> Unit = remember {
    { phoneNumber ->
        // niente canOpenURL: per "tel" vorrebbe LSApplicationQueriesSchemes. Sul simulatore openURL non fa nulla.
        NSURL.URLWithString("tel:$phoneNumber")?.let { url ->
            UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any?>(), completionHandler = null)
        }
    }
}
