package com.mamoro.investments.ui

import android.content.Context
import android.net.http.X509TrustManagerExtensions
import android.os.Build
import android.security.NetworkSecurityPolicy
import com.mamoro.investments.R
import com.mamoro.investments.data.*
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/** Debug-only native Android check. Never reads a stored token or changes any app data. */
internal fun runTInvestNetworkProbe(context: Context) {
    val result=JSONObject().put("sdk",Build.VERSION.SDK_INT)
    try {
        check(Build.HARDWARE in listOf("ranchu","goldfish")) { "Disposable emulator required" }
        val root=context.resources.openRawResource(R.raw.tinvest_russian_root_ca).use {
            CertificateFactory.getInstance("X.509").generateCertificate(it) as X509Certificate }
        root.checkValidity(); root.verify(root.publicKey)
        val factory=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(null as KeyStore?)
        val manager=factory.trustManagers.filterIsInstance<X509TrustManager>().single()
        val extensions=X509TrustManagerExtensions(manager)
        val apiHost="invest-public-api.tbank.ru"
        // Exercise Android's real per-host trust manager, rather than a host-JVM XML parser.
        // This checks trust-anchor scoping; HTTPS hostname validation remains in the platform client.
        val chain=extensions.checkServerTrusted(arrayOf(root),"RSA",apiHost)
        result.put("api_root_trusted",chain.any { it.encoded.contentEquals(root.encoded) })
        val others=listOf("example.com","sub.invest-public-api.tbank.ru","invest-public-api.tbank.ru.example.com")
        result.put("other_hosts_reject_root",others.all { host ->
            runCatching { extensions.checkServerTrusted(arrayOf(root),"RSA",host) }.isFailure })
        result.put("cleartext_blocked",!NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted(apiHost))
        result.put("success",result.getBoolean("api_root_trusted") &&
            result.getBoolean("other_hosts_reject_root") && result.getBoolean("cleartext_blocked"))

        // One optional live TLS check with a deliberately invalid, synthetic credential.
        // A 401 is expected; no personal account or valid token is used. Outages are reported separately.
        try {
            TInvestHttpTransport().post(TInvestMethod.SEARCH,"emulator-only-invalid-network-probe",
                JSONObject().put("query","ОФЗ").put("instrumentKind","INSTRUMENT_TYPE_BOND"))
            result.put("live_tls_completed",true).put("live_http_status",200)
        } catch (error: TInvestApiException) {
            result.put("live_tls_completed",error.httpStatus!=null)
                .put("live_failure",error.failure.name)
            error.httpStatus?.let { result.put("live_http_status",it) }
        }
    } catch (error: Exception) {
        result.put("success",false).put("error_type",error.javaClass.simpleName)
    } finally {
        File(context.filesDir,"tinvest-network-check.json").writeText(result.toString(2))
    }
}
