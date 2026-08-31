package com.letters2my.app.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * `.letterstomy` archive crypto + JSON codec, matching the iOS production
 * implementation byte-for-byte (Sources/LettersToMyCore/Backup.swift):
 *
 *  - Key: SHA-256(passphrase UTF-8) used directly as the AES-256 key
 *    (matching the SHIPPED iOS format — do NOT change derivation here).
 *  - Cipher: AES/GCM/NoPadding with 12-byte random nonce (CryptoKit default).
 *  - Envelope: "combined" = nonce(12) || ciphertext || tag(16).
 *  - Payload: the entire BackupPayload JSON (manifest included) is encrypted.
 *  - JSON encoding: Swift-compatible (reference-date doubles, null omitted).
 */
object LetterstomyArchive {

    const val FORMAT_VERSION = 1
    const val GCM_NONCE_LENGTH = 12
    const val GCM_TAG_LENGTH_BITS = 128

    class ArchiveException(message: String, cause: Throwable? = null) : Exception(message, cause)

    private val json = Json { ignoreUnknownKeys = true }

    /** Derive the AES-256 key exactly like iOS: SHA-256(passphrase UTF-8). */
    fun deriveKey(passphrase: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(passphrase.toByteArray(Charsets.UTF_8))

    /**
     * Encrypt a BackupPayload into a `.letterstomy` archive.
     * Mirrors BackupService.serializeAndEncrypt.
     */
    fun encrypt(payload: BackupPayload, passphrase: String): ByteArray {
        val jsonBytes = payload.toJson().toString().toByteArray(Charsets.UTF_8)
        return encryptBytes(jsonBytes, passphrase)
    }

    fun encryptBytes(plaintext: ByteArray, passphrase: String): ByteArray {
        val key = deriveKey(passphrase)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
        val nonce = cipher.iv // 12 bytes, CryptoKit/AES-GCM default
        val ciphertextAndTag = cipher.doFinal(plaintext)
        // combined = nonce || ciphertext || tag
        return nonce + ciphertextAndTag
    }

    /**
     * Decrypt a `.letterstomy` archive and parse the payload.
     * Mirrors BackupService.decryptAndDeserialize.
     *
     * @throws ArchiveException on wrong passphrase, corruption, or bad JSON.
     */
    fun decrypt(data: ByteArray, passphrase: String): BackupPayload {
        val plaintext = decryptBytes(data, passphrase)
        val root: JsonObject = try {
            json.parseToJsonElement(plaintext.decodeToString()).jsonObject
        } catch (e: Exception) {
            throw ArchiveException("Payload deserialization failed: ${e.message}", e)
        }
        return BackupPayload.fromJson(root)
    }

    fun decryptBytes(data: ByteArray, passphrase: String): ByteArray {
        if (data.size < GCM_NONCE_LENGTH + GCM_TAG_LENGTH_BITS / 8) {
            throw ArchiveException("Invalid ciphertext format.")
        }
        val key = deriveKey(passphrase)
        val nonce = data.copyOfRange(0, GCM_NONCE_LENGTH)
        val sealed = data.copyOfRange(GCM_NONCE_LENGTH, data.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        return try {
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(key, "AES"),
                GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce)
            )
            cipher.doFinal(sealed)
        } catch (e: Exception) {
            // JCE throws AEADBadTagException for both wrong passphrase and
            // corrupted tag — same semantics as CryptoKit authenticationFailure.
            throw ArchiveException("Wrong passphrase or corrupted archive.", e)
        }
    }
}