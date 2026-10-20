package com.sunvader.komgasun.data.local

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import com.sunvader.komgasun.data.model.ServerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import java.security.KeyStore
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PreferencesCredentialsTest {
    @Test fun persistsMigratesAndClearsCredentialsWithoutChangingHttp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dir = File(context.cacheDir, "credentials-test-${System.nanoTime()}").apply { mkdirs() }
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val alias = "credentials-test-${System.nanoTime()}"
        val store = PreferenceDataStoreFactory.create(scope = scope) { File(dir, "test.preferences_pb") }
        val manager = PreferencesManager(context, store, CredentialCipher(alias))
        try {
            manager.saveCredentials(ServerConfig("http://komga:25600", "reader", "secret"))
            assertEquals("secret", manager.serverConfigFlow.first()!!.password)
            assertNull(store.data.first()[PreferencesManager.KEY_PASSWORD])
            assertEquals("http://komga:25600", manager.savedUrlFlow.first())
            manager.clearCredentials()
            assertNull(manager.serverConfigFlow.first())
            assertEquals("reader", manager.savedUsernameFlow.first())
            store.edit {
                it[PreferencesManager.KEY_PASSWORD] = "legacy"
                it[PreferencesManager.KEY_IS_LOGGED_IN] = true
            }
            assertEquals("legacy", manager.serverConfigFlow.first()!!.password)
            assertNull(store.data.first()[PreferencesManager.KEY_PASSWORD])
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)
            assertNull(manager.serverConfigFlow.first())
            assertFalse(store.data.first()[PreferencesManager.KEY_IS_LOGGED_IN] ?: true)
        } finally {
            scope.cancel()
            dir.deleteRecursively()
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)
        }
    }
}
