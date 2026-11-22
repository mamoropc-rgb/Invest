package com.mamoro.investments.ui

import android.content.Context
import android.os.Build
import com.mamoro.investments.data.BondTokenStore
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Disposable emulator check, available only in the debug variant. No request is made to the API. */
internal fun runBondKeystoreProbe(context: Context) {
    val result=JSONObject().put("sdk",Build.VERSION.SDK_INT)
    val store=BondTokenStore(context)
    val prefs=context.getSharedPreferences("t_invest_connection",Context.MODE_PRIVATE)
    var touched=false
    try {
        check(Build.HARDWARE in listOf("ranchu","goldfish")) { "Disposable emulator required" }
        check(!prefs.contains("credential")) { "Refusing to replace an existing credential" }
        val value="emulator-only-"+UUID.randomUUID().toString()
        touched=true; store.save(value)
        result.put("round_trip",store.read() == value)
        val encoded=prefs.getString("credential",null).orEmpty()
        result.put("encrypted_at_rest",encoded.isNotEmpty() && !encoded.contains(value) && encoded != value)
        store.clear(); touched=false
        result.put("cleared",store.read() == null && !prefs.contains("credential"))
        result.put("success",result.getBoolean("round_trip") && result.getBoolean("encrypted_at_rest") && result.getBoolean("cleared"))
    } catch (e: Exception) {
        result.put("success",false).put("error",e.javaClass.simpleName)
    } finally {
        if (touched) runCatching { store.clear() }
        File(context.filesDir,"bond-keystore-check.json").writeText(result.toString(2))
    }
}
