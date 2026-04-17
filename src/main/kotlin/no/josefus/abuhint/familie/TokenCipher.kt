package no.josefus.abuhint.familie

import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM cipher for symmetric at-rest encryption of Google OAuth tokens.
 *
 * Output format (base64): [12-byte IV][ciphertext + 16-byte GCM auth tag].
 * The IV is randomly generated per encryption and authenticated by GCM, so the same
 * plaintext encrypts to different ciphertexts each call.
 *
 * Construction requires a base64-encoded 32-byte key (AES-256). Generate one with:
 *   openssl rand -base64 32
 */
class TokenCipher(keyBase64: String) {

    private val key: SecretKey

    init {
        require(keyBase64.isNotBlank()) { "TokenCipher key must be a non-blank base64 string" }
        val rawKey = try {
            Base64.getDecoder().decode(keyBase64)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("TokenCipher key must be valid base64", e)
        }
        require(rawKey.size == AES_KEY_BYTES) {
            "TokenCipher key must decode to $AES_KEY_BYTES bytes (AES-256); got ${rawKey.size}"
        }
        key = SecretKeySpec(rawKey, "AES")
    }

    fun encrypt(plaintext: String): String {
        val iv = ByteArray(IV_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val ct = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val packed = ByteBuffer.allocate(iv.size + ct.size).put(iv).put(ct).array()
        return Base64.getEncoder().encodeToString(packed)
    }

    fun decrypt(encryptedBase64: String): String? {
        return try {
            val packed = Base64.getDecoder().decode(encryptedBase64)
            if (packed.size <= IV_BYTES) return null
            val iv = packed.copyOfRange(0, IV_BYTES)
            val ct = packed.copyOfRange(IV_BYTES, packed.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(ct), Charsets.UTF_8)
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: javax.crypto.AEADBadTagException) {
            null
        } catch (_: javax.crypto.BadPaddingException) {
            null
        } catch (_: javax.crypto.IllegalBlockSizeException) {
            null
        }
    }

    companion object {
        private const val AES_KEY_BYTES = 32
        private const val IV_BYTES = 12
        private const val GCM_TAG_BITS = 128
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
