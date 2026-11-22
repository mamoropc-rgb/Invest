package com.mamoro.investments.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** This installation's credential is encrypted with a non-exportable Android Keystore key. */
class BondTokenStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("t_invest_connection",Context.MODE_PRIVATE)
    private val alias = "investments.t_invest.readonly.v1"
    @Synchronized fun read(): String? {
        val value = preferences.getString("credential",null) ?: return null
        return runCatching {
            val pieces = value.split(".")
            require(pieces.size == 2)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE,key(false),GCMParameterSpec(128,Base64.decode(pieces[0],Base64.NO_WRAP)))
            String(cipher.doFinal(Base64.decode(pieces[1],Base64.NO_WRAP)),Charsets.UTF_8)
        }.getOrNull()
    }
    @Synchronized fun save(token: String) {
        val value = token.trim()
        require(value.length in 10..4096 && value.none { it.isWhitespace() || it.isISOControl() }) { "Проверьте токен API" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE,key(true))
        val encoded = Base64.encodeToString(cipher.iv,Base64.NO_WRAP) + "." +
            Base64.encodeToString(cipher.doFinal(value.toByteArray(Charsets.UTF_8)),Base64.NO_WRAP)
        check(preferences.edit().putString("credential",encoded).commit()) { "Не удалось сохранить токен" }
    }
    @Synchronized fun clear() {
        check(preferences.edit().clear().commit()) { "Не удалось отключить API" }
        KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(alias) }
    }
    private fun key(create: Boolean): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias,null) as? SecretKey)?.let { return it }
        check(create) { "Ключ недоступен" }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
        }.generateKey()
    }
}
