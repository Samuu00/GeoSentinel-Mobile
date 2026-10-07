package it.unical.geosentinel_mobile.core.di

import android.content.Context
import it.unical.geosentinel_mobile.api.model.RegisterDeviceRequest
import it.unical.geosentinel_mobile.core.config.AppConfig
import it.unical.geosentinel_mobile.core.db.androidDatabaseBuilder

// Da GeoSentinelApplication.onCreate, una volta per processo. Il Context serve a Room (cartella databases/).
fun initKoin(context: Context, config: AppConfig) =
    startKoinWith(config, context.filesDir.absolutePath, RegisterDeviceRequest.Platform.ANDROID) { androidDatabaseBuilder(context) }
