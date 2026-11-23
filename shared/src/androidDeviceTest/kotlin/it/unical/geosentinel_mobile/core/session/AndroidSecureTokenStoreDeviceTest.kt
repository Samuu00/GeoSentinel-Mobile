// Test strumentato che gira sull'emulatore o su un dispositivo Android, con il vero Android Keystore. Controlla che
// lo store dei token cifri davvero i dati scritti nel file dell'app. I nomi dei test non hanno spazi perché D8 non
// li accetta per le versioni di Android precedenti alla 11.
package it.unical.geosentinel_mobile.core.session

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.time.Instant

/**
 * Verifica lo store sicuro dei token sul dispositivo. Contiene un solo test perché createSecureTokenStore apre un
 * DataStore sul file, e nello stesso processo non possono esistere due DataStore sullo stesso file.
 */
class AndroidSecureTokenStoreDeviceTest {

    /**
     * Scrive i token, li rilegge uguali, controlla che nel file su disco non compaiano in chiaro e infine li cancella:
     * dopo la cancellazione la lettura restituisce null.
     */
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
