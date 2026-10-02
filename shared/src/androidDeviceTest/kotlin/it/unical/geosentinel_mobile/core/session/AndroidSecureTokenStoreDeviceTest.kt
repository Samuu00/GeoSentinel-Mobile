package it.unical.geosentinel_mobile.core.session

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.time.Instant

// Sull'emulatore, con il vero Android Keystore e il file nella cartella dell'app (checkpoint di M2.1).
// Nomi senza spazi: D8 non li accetta sotto Android 11. Un solo test: createSecureTokenStore apre un DataStore
// sul file, e due DataStore sullo stesso file nello stesso processo non si possono avere.
class AndroidSecureTokenStoreDeviceTest {

    @Test
    fun tokensAreEncryptedWithTheKeystoreReadBackAndCleared() = runBlocking {
        val filesDir = InstrumentationRegistry.getInstrumentation().targetContext.filesDir
        val store = createSecureTokenStore(filesDir.absolutePath)
        val tokens = AuthTokens("access-device-123", "refresh-device-456", Instant.fromEpochMilliseconds(1_759_400_000_000))

        store.write(tokens)
        assertEquals(tokens, store.read())

        val content = File(filesDir, SESSION_FILE_NAME).readBytes().decodeToString()
        assertFalse("access-device-123" in content)
        assertFalse("refresh-device-456" in content)

        store.clear()
        assertNull(store.read())
    }
}
