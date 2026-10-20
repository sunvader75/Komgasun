package com.sunvader.komgasun.data.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.security.KeyStore
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CredentialCipherTest {
    @Test fun encryptsWithFreshNonceAndRejectsMissingKey() {
        val alias = "komgasun-test-${System.nanoTime()}"
        val cipher = CredentialCipher(alias)
        try {
            val first = cipher.encrypt("secret-ñ")
            val second = cipher.encrypt("secret-ñ")
            assertNotEquals(first, second)
            assertFalse(first.contains("secret"))
            assertEquals("secret-ñ", CredentialCipher(alias).decrypt(first))
            val parts = first.split(":")
            val corrupted = parts.dropLast(1).joinToString(":") + ":AAAA"
            assertTrue(runCatching { cipher.decrypt(corrupted) }.isFailure)
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            store.deleteEntry(alias)
            assertTrue(runCatching { cipher.decrypt(first) }.isFailure)
        } finally {
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)
        }
    }
}
