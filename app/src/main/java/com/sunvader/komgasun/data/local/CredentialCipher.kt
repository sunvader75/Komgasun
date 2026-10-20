package com.sunvader.komgasun.data.local

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Conserva solo versión, nonce y texto cifrado autenticado; la clave no sale de Android Keystore. */
class CredentialCipher(private val alias: String = "komgasun.credentials.v1") {
    /** Abre y carga Android Keystore; propaga los errores del proveedor. */
    private fun keyStore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    /** Reutiliza la clave del alias o crea una AES de 256 bits para GCM con nonce aleatorio. */
    @Synchronized
    private fun encryptionKey(): SecretKey {
        val store = keyStore()
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build())
        }.generateKey()
    }

    /**
     * Cifra la contraseña UTF-8 con AES-GCM y devuelve `v1:nonce:cifrado` en Base64 sin saltos.
     * Crea la clave si falta y propaga los errores criptográficos.
     */
    fun encrypt(password: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey())
        val encrypted = cipher.doFinal(password.toByteArray(Charsets.UTF_8))
        return "v1:${encode(cipher.iv)}:${encode(encrypted)}"
    }

    /**
     * Autentica y descifra un valor v1 con nonce de 12 bytes; devuelve la contraseña UTF-8.
     * Rechaza formatos inválidos con IllegalArgumentException y propaga errores de seguridad,
     * incluida una clave ausente o una autenticación GCM fallida; nunca crea una clave al leer.
     */
    fun decrypt(value: String): String {
        val parts = value.split(':')
        require(parts.size == 3 && parts[0] == "v1") { "Invalid credential format" }
        val nonce = Base64.decode(parts[1], Base64.NO_WRAP)
        require(nonce.size == 12) { "Invalid credential nonce" }
        val key = keyStore().getKey(alias, null) as? SecretKey
            ?: throw GeneralSecurityException("Credential key unavailable")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, nonce))
        return String(cipher.doFinal(Base64.decode(parts[2], Base64.NO_WRAP)), Charsets.UTF_8)
    }

    /** Codifica bytes en Base64 sin saltos de línea para el formato persistido. */
    private fun encode(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
}
