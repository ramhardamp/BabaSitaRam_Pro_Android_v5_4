package com.babasitaram.pro

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class PasswordEntry(
    val id: String = java.util.UUID.randomUUID().toString(),
    val site: String = "",
    val url: String = "",
    val username: String = "",
    val password: String = "",
    val notes: String = "",
    val category: String = "Other",
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

object VaultManager {
    private const val PREFS_FILE = "bsr_vault_secure"
    private const val KEY_MASTER_HASH = "master_hash"
    private const val KEY_PASSWORDS = "passwords_enc"
    private const val KEY_SETUP_DONE = "setup_done"
    private val gson = Gson()

    private fun getPrefs(ctx: Context): android.content.SharedPreferences {
        val masterKey = MasterKey.Builder(ctx)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            ctx, PREFS_FILE, masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // SHA-256 hash of master password
    private fun hashMaster(password: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(password.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    // Derive AES key from master password using PBKDF2
    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, 100000, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }

    fun isSetupDone(ctx: Context) = getPrefs(ctx).getBoolean(KEY_SETUP_DONE, false)

    fun setupMaster(ctx: Context, masterPassword: String) {
        getPrefs(ctx).edit()
            .putString(KEY_MASTER_HASH, hashMaster(masterPassword))
            .putBoolean(KEY_SETUP_DONE, true)
            .apply()
    }

    fun verifyMaster(ctx: Context, masterPassword: String): Boolean {
        val stored = getPrefs(ctx).getString(KEY_MASTER_HASH, "") ?: return false
        return stored == hashMaster(masterPassword)
    }

    // AES-256-GCM encrypt
    private fun encrypt(data: String, password: String): String {
        val salt = ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { java.security.SecureRandom().nextBytes(it) }
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val ct = cipher.doFinal(data.toByteArray())
        val combined = salt + iv + ct
        return android.util.Base64.encodeToString(combined, android.util.Base64.NO_WRAP)
    }

    // AES-256-GCM decrypt
    private fun decrypt(data: String, password: String): String {
        val combined = android.util.Base64.decode(data, android.util.Base64.NO_WRAP)
        val salt = combined.sliceArray(0..15)
        val iv = combined.sliceArray(16..27)
        val ct = combined.sliceArray(28 until combined.size)
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return String(cipher.doFinal(ct))
    }

    fun savePasswords(ctx: Context, passwords: List<PasswordEntry>, masterPassword: String) {
        val json = gson.toJson(passwords)
        val encrypted = encrypt(json, masterPassword)
        getPrefs(ctx).edit().putString(KEY_PASSWORDS, encrypted).apply()
    }

    fun loadPasswords(ctx: Context, masterPassword: String): List<PasswordEntry> {
        val encrypted = getPrefs(ctx).getString(KEY_PASSWORDS, null) ?: return emptyList()
        return try {
            val json = decrypt(encrypted, masterPassword)
            val type = object : TypeToken<List<PasswordEntry>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    // In-memory session
    private var _master: String = ""
    private var _passwords: MutableList<PasswordEntry> = mutableListOf()
    val isUnlocked get() = _master.isNotEmpty()

    fun unlock(ctx: Context, masterPassword: String): Boolean {
        if (!verifyMaster(ctx, masterPassword)) return false
        _master = masterPassword
        _passwords = loadPasswords(ctx, masterPassword).toMutableList()
        return true
    }

    fun lock() { _master = ""; _passwords.clear() }

    fun getPasswords() = _passwords.toList()

    fun addPassword(ctx: Context, entry: PasswordEntry) {
        _passwords.add(entry)
        savePasswords(ctx, _passwords, _master)
    }

    fun updatePassword(ctx: Context, entry: PasswordEntry) {
        val idx = _passwords.indexOfFirst { it.id == entry.id }
        if (idx >= 0) { _passwords[idx] = entry; savePasswords(ctx, _passwords, _master) }
    }

    fun deletePassword(ctx: Context, id: String) {
        _passwords.removeAll { it.id == id }
        savePasswords(ctx, _passwords, _master)
    }

    fun searchPasswords(query: String) = _passwords.filter {
        it.site.contains(query, true) || it.username.contains(query, true) ||
        it.url.contains(query, true) || it.category.contains(query, true)
    }

    fun getPasswordsForUrl(url: String) = _passwords.filter {
        val domain = try { java.net.URI(url).host?.removePrefix("www.") ?: "" } catch(e: Exception) { "" }
        val siteDomain = it.url.let { u ->
            try { java.net.URI(if(u.startsWith("http")) u else "https://$u").host?.removePrefix("www.") ?: u }
            catch(e: Exception) { u }
        }
        domain.isNotEmpty() && (siteDomain.contains(domain) || domain.contains(siteDomain) ||
        it.site.contains(domain, true))
    }
}
