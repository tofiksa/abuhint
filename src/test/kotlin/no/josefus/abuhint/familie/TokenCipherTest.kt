package no.josefus.abuhint.familie

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class TokenCipherTest {

    private val keyB64: String = Base64.getEncoder().encodeToString(ByteArray(32) { it.toByte() })

    @Test
    fun `encrypt then decrypt returns original plaintext`() {
        val cipher = TokenCipher(keyB64)
        val plaintext = "ya29.super-secret-refresh-token"

        val encrypted = cipher.encrypt(plaintext)
        val decrypted = cipher.decrypt(encrypted)

        assertEquals(plaintext, decrypted)
    }

    @Test
    fun `encrypt produces different ciphertext for same plaintext due to random iv`() {
        val cipher = TokenCipher(keyB64)
        val plaintext = "same-input"

        val a = cipher.encrypt(plaintext)
        val b = cipher.encrypt(plaintext)

        assertNotEquals(a, b, "IV must be random each call so ciphertexts differ")
    }

    @Test
    fun `decrypt with wrong key returns null`() {
        val cipherA = TokenCipher(keyB64)
        val otherKey = Base64.getEncoder().encodeToString(ByteArray(32) { (255 - it).toByte() })
        val cipherB = TokenCipher(otherKey)

        val encrypted = cipherA.encrypt("payload")
        val decrypted = cipherB.decrypt(encrypted)

        assertNull(decrypted, "Wrong key must fail authentication and return null")
    }

    @Test
    fun `constructor rejects missing key`() {
        assertThrows<IllegalArgumentException> { TokenCipher("") }
    }

    @Test
    fun `constructor rejects key that is not 32 bytes`() {
        val shortKey = Base64.getEncoder().encodeToString(ByteArray(16) { 1 })
        assertThrows<IllegalArgumentException> { TokenCipher(shortKey) }
    }

    @Test
    fun `decrypt of garbage input returns null instead of throwing`() {
        val cipher = TokenCipher(keyB64)
        assertNull(cipher.decrypt("not-valid-base64!!"))
        assertNull(cipher.decrypt(Base64.getEncoder().encodeToString(ByteArray(5))))
    }
}
