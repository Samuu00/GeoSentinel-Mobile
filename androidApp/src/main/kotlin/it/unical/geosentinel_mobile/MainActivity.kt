package it.unical.geosentinel_mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import it.unical.geosentinel_mobile.core.config.AppConfig

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val config = AppConfig(apiBaseUrl = BuildConfig.API_BASE_URL, isDebug = BuildConfig.DEBUG)
        setContent {
            App(config)
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App(AppConfig(apiBaseUrl = "http://localhost", isDebug = true))
}
