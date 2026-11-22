package com.mamoro.investments.domain

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mamoro.investments.R
import com.mamoro.investments.data.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.URL
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
class TInvestTransportTest {
    private val credential="test-only-never-a-real-token"
    private class Connection(url: URL, private val status: Int=200, private val body: String="{\"ok\":true}",
        private val failure: IOException?=null) : HttpURLConnection(url) {
        val written=ByteArrayOutputStream()
        var closed=false
        override fun connect() {}
        override fun disconnect() { closed=true }
        override fun usingProxy()=false
        override fun getOutputStream()=written.also { failure?.let { error -> throw error } }
        override fun getInputStream()=ByteArrayInputStream(body.toByteArray(Charsets.UTF_8))
        override fun getResponseCode()=status
    }
    private fun failure(action: () -> Unit): TInvestApiException {
        val result=runCatching(action).exceptionOrNull()
        assertTrue("Expected a sanitized API failure",result is TInvestApiException)
        return result as TInvestApiException
    }
    @Test fun onlyThreeReadMethodsUseFixedHttpsHostAndNeverFollowRedirects() {
        for (method in TInvestMethod.entries) {
            lateinit var connection: Connection
            val transport=TInvestHttpTransport { address ->
                assertEquals("https",address.protocol)
                assertEquals("invest-public-api.tbank.ru",address.host)
                assertEquals("/rest/tinkoff.public.invest.api.contract.v1.InstrumentsService/"+method.rpc,address.path)
                Connection(address).also { connection=it }
            }
            assertTrue(transport.post(method,credential,JSONObject().put("query","ОФЗ")).getBoolean("ok"))
            assertEquals("POST",connection.requestMethod)
            assertFalse(connection.instanceFollowRedirects)
            assertEquals("Bearer $credential",connection.getRequestProperty("Authorization"))
            assertEquals("ОФЗ",JSONObject(connection.written.toString("UTF-8")).getString("query"))
            assertTrue(connection.closed)
        }
        assertEquals(setOf("FindInstrument","BondBy","GetBondCoupons"),TInvestMethod.entries.map { it.rpc }.toSet())
    }
    @Test fun httpFailuresRemainDistinctWithoutLeakingResponseOrToken() {
        for ((status,reason) in mapOf(401 to TInvestFailure.AUTH,403 to TInvestFailure.ACCESS,
            429 to TInvestFailure.RATE_LIMIT,302 to TInvestFailure.REDIRECT,503 to TInvestFailure.SERVICE)) {
            lateinit var connection: Connection
            val transport=TInvestHttpTransport { Connection(it,status,"private response: $credential").also { c -> connection=c } }
            val error=failure { transport.post(TInvestMethod.SEARCH,credential,JSONObject()) }
            assertEquals(reason,error.failure)
            assertEquals(status,error.httpStatus)
            assertTrue(error.message!!.contains("HTTP $status"))
            assertFalse(error.message!!.contains(credential))
            assertNull(error.cause)
            assertTrue(connection.closed)
        }
    }
    @Test fun certificateTimeoutDnsAndOtherNetworkFailuresHaveSafeSpecificMessages() {
        for ((exception,reason) in listOf(
            SSLHandshakeException(credential) to TInvestFailure.TLS,
            SSLPeerUnverifiedException(credential) to TInvestFailure.TLS,
            IOException(credential,SSLHandshakeException(credential)) to TInvestFailure.TLS,
            SocketTimeoutException(credential) to TInvestFailure.TIMEOUT,
            UnknownHostException(credential) to TInvestFailure.DNS,
            IOException(credential) to TInvestFailure.NETWORK)) {
            lateinit var connection: Connection
            val transport=TInvestHttpTransport { Connection(it,failure=exception).also { c -> connection=c } }
            val error=failure { transport.post(TInvestMethod.SEARCH,credential,JSONObject()) }
            assertEquals(reason,error.failure)
            assertFalse(error.message!!.contains(credential))
            assertNull(error.cause)
            assertTrue(connection.closed)
        }
    }
    @Test fun openerFailuresAreAlsoSanitized() {
        val transport=TInvestHttpTransport { throw UnknownHostException(credential) }
        val error=failure { transport.post(TInvestMethod.SEARCH,credential,JSONObject()) }
        assertEquals(TInvestFailure.DNS,error.failure)
        assertFalse(error.message!!.contains(credential))
    }
    @Test fun malformedAndOversizeResponsesAreRejectedAndDisconnected() {
        for ((body,reason) in listOf("private $credential" to TInvestFailure.RESPONSE,
            "x".repeat(8_000_001) to TInvestFailure.RESPONSE_SIZE)) {
            lateinit var connection: Connection
            val transport=TInvestHttpTransport { Connection(it,body=body).also { c -> connection=c } }
            val error=failure { transport.post(TInvestMethod.SEARCH,credential,JSONObject()) }
            assertEquals(reason,error.failure)
            assertFalse(error.message!!.contains(credential))
            assertTrue(connection.closed)
        }
    }
    @Test fun malformedCredentialNeverOpensAConnection() {
        var opened=false
        val transport=TInvestHttpTransport { opened=true; Connection(it) }
        for (value in listOf("", "token with space", "token\nInjected: value", "тестовый-токен", "token\u200Bhidden")) {
            assertTrue(runCatching { transport.post(TInvestMethod.SEARCH,value,JSONObject()) }.isFailure)
        }
        assertFalse(opened)
    }
    @Test fun bundledRootMatchesTwoOfficialSourcesAndHasAValidSelfSignature() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val certificate=context.resources.openRawResource(R.raw.tinvest_russian_root_ca).use {
            CertificateFactory.getInstance("X.509").generateCertificate(it) as X509Certificate }
        certificate.checkValidity()
        certificate.verify(certificate.publicKey)
        assertTrue(certificate.basicConstraints >= 0)
        assertEquals(certificate.subjectX500Principal,certificate.issuerX500Principal)
        val hash=MessageDigest.getInstance("SHA-256").digest(certificate.encoded).joinToString("") { "%02x".format(it) }
        assertEquals("d26d2d0231b7c39f92cc738512ba54103519e4405d68b5bd703e9788ca8ecf31",hash)
    }
    @Test fun additionalCaIsScopedToExactApiDomainAndCleartextRemainsDisabled() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val domains=mutableListOf<String>()
        val baseSources=mutableListOf<String>()
        val domainSources=mutableListOf<String>()
        var config=""
        var baseCount=0
        var domainCount=0
        context.resources.getXml(R.xml.network_security_config).use { xml ->
            while (xml.eventType!=XmlPullParser.END_DOCUMENT) {
                if (xml.eventType==XmlPullParser.START_TAG) when (xml.name) {
                    "base-config", "domain-config" -> {
                        config=xml.name
                        assertEquals("false",xml.getAttributeValue(null,"cleartextTrafficPermitted"))
                        if (config=="base-config") baseCount++ else domainCount++
                    }
                    "domain" -> {
                        assertEquals("false",xml.getAttributeValue(null,"includeSubdomains"))
                        domains+=xml.nextText()
                    }
                    "certificates" -> {
                        val id=xml.getAttributeResourceValue(null,"src",0)
                        val source=if (id==0) xml.getAttributeValue(null,"src") else context.resources.getResourceEntryName(id)
                        if (config=="base-config") baseSources+=source else domainSources+=source
                    }
                    "debug-overrides" -> fail("No debug trust override is needed")
                }
                xml.next()
            }
        }
        assertEquals(1,baseCount)
        assertEquals(1,domainCount)
        assertEquals(listOf("invest-public-api.tbank.ru"),domains)
        assertEquals(listOf("system"),baseSources)
        assertEquals(listOf("system","tinvest_russian_root_ca"),domainSources)
    }
}
