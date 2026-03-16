package com.babasitaram.pro

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
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
    private const val TAG = "BSR_Vault"
    private val gson = Gson()
    private var _master = ""
    private var _passwords: MutableList<PasswordEntry> = mutableListOf()
    val isUnlocked get() = _master.isNotEmpty()

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences("bsr_v3", Context.MODE_PRIVATE)

    private fun hash(pw: String): String =
        MessageDigest.getInstance("SHA-256").digest(pw.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun key(pw: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(pw.toCharArray(), salt, 65536, 256)
        return SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(spec).encoded, "AES")
    }

    private fun enc(data: String, pw: String): String {
        val salt = ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }
        val iv   = ByteArray(12).also { java.security.SecureRandom().nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key(pw, salt), GCMParameterSpec(128, iv))
        val ct = c.doFinal(data.toByteArray(Charsets.UTF_8))
        return android.util.Base64.encodeToString(salt + iv + ct, android.util.Base64.NO_WRAP)
    }

    private fun dec(data: String, pw: String): String {
        val b = android.util.Base64.decode(data, android.util.Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(pw, b.sliceArray(0..15)), GCMParameterSpec(128, b.sliceArray(16..27)))
        return String(c.doFinal(b.sliceArray(28 until b.size)), Charsets.UTF_8)
    }

    fun isSetupDone(ctx: Context) = try { prefs(ctx).getBoolean("sd", false) } catch (e: Exception) { false }

    fun setupMaster(ctx: Context, pw: String): Boolean = try {
        prefs(ctx).edit().putString("mh", hash(pw)).putBoolean("sd", true).commit()
    } catch (e: Exception) { false }

    fun verifyMaster(ctx: Context, pw: String) = try {
        prefs(ctx).getString("mh", null) == hash(pw)
    } catch (e: Exception) { false }

    fun unlock(ctx: Context, pw: String): Boolean {
        if (!verifyMaster(ctx, pw)) return false
        _master = pw
        _passwords = loadPw(ctx, pw).toMutableList()
        return true
    }

    fun lock() { _master = ""; _passwords.clear() }

    private fun savePw(ctx: Context) = try {
        prefs(ctx).edit().putString("pw", enc(gson.toJson(_passwords), _master)).apply()
    } catch (e: Exception) { Log.e(TAG, "savePw: ${e.message}") }

    private fun loadPw(ctx: Context, pw: String): List<PasswordEntry> = try {
        val raw = prefs(ctx).getString("pw", null) ?: return emptyList()
        gson.fromJson(dec(raw, pw), object : TypeToken<List<PasswordEntry>>() {}.type)
    } catch (e: Exception) { emptyList() }

    fun getPasswords() = _passwords.toList()
    fun getFavorites() = _passwords.filter { it.isFavorite }
    fun getByCategory(cat: String) = _passwords.filter { it.category == cat }
    fun search(q: String) = _passwords.filter {
        it.site.contains(q, true) || it.username.contains(q, true) || it.url.contains(q, true)
    }

    fun add(ctx: Context, e: PasswordEntry) { _passwords.add(e); savePw(ctx) }
    fun update(ctx: Context, e: PasswordEntry) {
        val i = _passwords.indexOfFirst { it.id == e.id }
        if (i >= 0) { _passwords[i] = e.copy(updatedAt = System.currentTimeMillis()); savePw(ctx) }
    }
    fun delete(ctx: Context, id: String) { _passwords.removeAll { it.id == id }; savePw(ctx) }
    fun toggleFav(ctx: Context, id: String) {
        val i = _passwords.indexOfFirst { it.id == id }
        if (i >= 0) { _passwords[i] = _passwords[i].copy(isFavorite = !_passwords[i].isFavorite); savePw(ctx) }
    }

    fun resetAll(ctx: Context) {
        _master = ""; _passwords.clear()
        prefs(ctx).edit().clear().apply()
    }

    // ── Public encrypt/decrypt for BackupManager ──
    fun encryptString(data: String, password: String): String = enc(data, password)
    fun decryptString(data: String, password: String): String = dec(data, password)

    fun getForUrl(url: String): List<PasswordEntry> {
        val domain = try { java.net.URI(if (url.startsWith("http")) url else "https://$url")
            .host?.removePrefix("www.") ?: "" } catch (e: Exception) { "" }
        return _passwords.filter { e ->
            val ed = try { java.net.URI(if (e.url.startsWith("http")) e.url else "https://${e.url}")
                .host?.removePrefix("www.") ?: e.site.lowercase() } catch (ex: Exception) { e.site.lowercase() }
            domain.isNotEmpty() && (ed.contains(domain) || domain.contains(ed) || e.site.contains(domain, true))
        }
    }

    fun strengthScore(pw: String): Int {
        if (pw.isEmpty()) return 0
        var s = 0
        if (pw.length >= 8) s += 20
        if (pw.length >= 12) s += 20
        if (pw.length >= 16) s += 10
        if (pw.contains(Regex("[A-Z]"))) s += 15
        if (pw.contains(Regex("[a-z]"))) s += 10
        if (pw.contains(Regex("[0-9]"))) s += 15
        if (pw.contains(Regex("[^A-Za-z0-9]"))) s += 20
        if (pw.contains(Regex("(.)\\1{2}"))) s -= 15 // repeated chars
        return s.coerceIn(0, 100)
    }
}
