package ru.bl3xand.pancake.utils.security

import android.util.Base64
import com.google.firebase.database.DataSnapshot
import com.google.gson.Gson
import ru.bl3xand.pancake.data.model.EncryptedNode
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Шифрование/дешифрование данных пространства по ключу spaceId (UUID).
 */
object SpaceCrypto {

    @PublishedApi
    internal const val TRANSFORMATION = "AES/GCM/NoPadding"
    @PublishedApi
    internal const val GCM_TAG_BITS = 128
    @PublishedApi
    internal const val IV_SIZE_BYTES = 12
    @PublishedApi
    internal val gson = Gson()
    @PublishedApi
    internal val secureRandom = SecureRandom()

    fun <T> encryptModel(spaceId: String, model: T): EncryptedNode {
        val plaintext = gson.toJson(model).toByteArray(Charsets.UTF_8)
        val iv = ByteArray(IV_SIZE_BYTES).also { secureRandom.nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keyFromSpaceId(spaceId), GCMParameterSpec(GCM_TAG_BITS, iv))
        val encrypted = cipher.doFinal(plaintext)
        return EncryptedNode(
            payload = Base64.encodeToString(encrypted, Base64.NO_WRAP),
            iv = Base64.encodeToString(iv, Base64.NO_WRAP)
        )
    }

    inline fun <reified T> decryptNode(spaceId: String, node: EncryptedNode): T? {
        if (node.payload.isBlank() || node.iv.isBlank()) return null
        return runCatching {
            val encrypted = Base64.decode(node.payload, Base64.NO_WRAP)
            val iv = Base64.decode(node.iv, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, keyFromSpaceId(spaceId), GCMParameterSpec(GCM_TAG_BITS, iv))
            val plaintext = cipher.doFinal(encrypted).toString(Charsets.UTF_8)
            gson.fromJson(plaintext, T::class.java)
        }.getOrNull()
    }

    inline fun <reified T> decodeSnapshot(spaceId: String, snapshot: DataSnapshot): T? {
        val encrypted = snapshot.getValue(EncryptedNode::class.java)
        if (encrypted != null && encrypted.payload.isNotBlank() && encrypted.iv.isNotBlank()) {
            return decryptNode(spaceId, encrypted)
        }
        return snapshot.getValue(T::class.java)
    }

    fun keyFromSpaceId(spaceId: String): SecretKeySpec {
        val hash = MessageDigest.getInstance("SHA-256").digest(spaceId.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(hash.copyOf(16), "AES")
    }
}

