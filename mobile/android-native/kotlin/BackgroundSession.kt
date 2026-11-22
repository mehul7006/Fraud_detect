package com.fraudshield.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 7.11: what the background scanner needs when the Flutter app is not running: API address, login token, and the
 * user's link settings (apps turned off, trusted sites). The token is encrypted with an AES key that never leaves
 * the Android Keystore (same protection as the app's own token store). Cleared on logout.
 */
object BackgroundSession {
    private const val PREFS = "fs_background"
    private const val KEY_ALIAS = "fraudshield_bg_token"

    /** Set by MainActivity: while the app is on screen the Flutter side handles links itself. */
    @Volatile var appInForeground = false

    data class Session(val apiBase: String, val token: String, val refresh: String?, val disabled: Set<String>, val trusted: List<String>)

    /** token / refresh null = keep the ones already stored (only the settings changed). */
    fun save(ctx: Context, apiBase: String, token: String?, refresh: String?, disabled: Collection<String>, trusted: Collection<String>) {
        val e = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("api", apiBase)
            .putStringSet("disabled", disabled.toSet()).putString("trusted", trusted.joinToString("\n"))
        if (token != null) e.putString("token", encrypt(token))
        if (refresh != null) e.putString("refresh", encrypt(refresh))
        e.apply()
    }

    /** 8.2: after the background scanner renewed its own 15-minute token. */
    fun updateTokens(ctx: Context, token: String, refresh: String?) {
        val e = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("token", encrypt(token))
        if (refresh != null) e.putString("refresh", encrypt(refresh))
        e.commit()
    }

    fun clear(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()

    fun load(ctx: Context): Session? {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val api = p.getString("api", null) ?: return null
        val token = p.getString("token", null)?.let { runCatching { decrypt(it) }.getOrNull() } ?: return null
        val refresh = p.getString("refresh", null)?.let { runCatching { decrypt(it) }.getOrNull() }
        return Session(api, token, refresh, p.getStringSet("disabled", emptySet()) ?: emptySet(),
            p.getString("trusted", "")!!.split("\n").filter { it.isNotBlank() })
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        g.init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build())
        return g.generateKey()
    }

    private fun encrypt(plain: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val out = c.iv + c.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): String {
        val all = Base64.decode(stored, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, all, 0, 12))
        return String(c.doFinal(all, 12, all.size - 12), Charsets.UTF_8)
    }
}
