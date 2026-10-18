package com.sunvader.komgasun.ui.books

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.sunvader.komgasun.data.local.EpubCoverProgressStore
import com.sunvader.komgasun.data.model.EpubCoverProgress
import com.sunvader.komgasun.data.model.ServerConfig
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class EpubCoverProgressStoreTest {
    @Test fun persistsReaderTotalAndIsolatesAccountServerAndBook() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "epub-covers-test-${UUID.randomUUID()}"
        val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        try {
            val config = ServerConfig("https://one.invalid", "alice", "not-a-real-password")
            val summary = EpubCoverProgress(0.35, 240)
            val store = EpubCoverProgressStore(preferences)
            store.save(config, "book", summary)
            val reopened = EpubCoverProgressStore(preferences)
            assertEquals(summary, reopened.read(config, "book"))
            assertNull(reopened.read(config.copy(username = "bob"), "book"))
            assertNull(reopened.read(config.copy(serverUrl = "https://two.invalid"), "book"))
            assertNull(reopened.read(config, "other-book"))
            for (invalid in listOf(EpubCoverProgress(Double.NaN), EpubCoverProgress(-0.1), EpubCoverProgress(0.5, -1))) {
                assertThrows(IllegalArgumentException::class.java) { reopened.save(config, "book", invalid) }
                assertEquals(summary, reopened.read(config, "book"))
            }
            reopened.clear(config.copy(username = "bob"), "book")
            assertEquals(summary, reopened.read(config, "book"))
            reopened.clear(config, "book")
            assertNull(EpubCoverProgressStore(preferences).read(config, "book"))
        } finally { context.deleteSharedPreferences(name) }
    }
}
