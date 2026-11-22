package com.mamoro.investments.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.URL
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.coroutines.coroutineContext
import javax.net.ssl.SSLException

/** Deliberately allowlisted information-only methods. No account, order or transfer endpoints. */
enum class TInvestMethod(val rpc: String) { SEARCH("FindInstrument"), BOND("BondBy"), COUPONS("GetBondCoupons") }
fun interface TInvestTransport {
    fun post(method: TInvestMethod, token: String, request: JSONObject): JSONObject
}
internal enum class TInvestFailure(val userMessage: String) {
    AUTH("Токен API недействителен или отозван. Замените токен"),
    ACCESS("Нет доступа к справочнику. Проверьте права токена «Только чтение»"),
    RATE_LIMIT("Лимит запросов Т‑Инвестиций. Повторите позже"),
    REDIRECT("Сервис изменил адрес. Требуется обновление приложения"),
    SERVICE("Т‑Инвестиции временно недоступны. Повторите позже"),
    TLS("Не удалось проверить сертификат Т‑Инвестиций. Проверьте дату и время устройства и обновите приложение"),
    TIMEOUT("Т‑Инвестиции не ответили вовремя. Повторите проверку или смените сеть"),
    DNS("Не найден адрес API Т‑Инвестиций. Проверьте сеть, DNS и VPN"),
    NETWORK("Не удалось подключиться к Т‑Инвестициям. Проверьте сеть и VPN"),
    RESPONSE("Неверный ответ справочника"),
    RESPONSE_SIZE("Слишком большой ответ справочника")
}
internal class TInvestApiException(val failure: TInvestFailure, val httpStatus: Int? = null) :
    IOException(failure.userMessage + (httpStatus?.let { " (HTTP $it)" } ?: ""))

class TInvestHttpTransport internal constructor(private val openConnection: (URL) -> HttpURLConnection) : TInvestTransport {
    constructor() : this({ it.openConnection() as HttpURLConnection })
    override fun post(method: TInvestMethod, token: String, request: JSONObject): JSONObject {
        require(token.isNotEmpty() && token.all { it.code in 0x21..0x7e }) { "Проверьте токен API" }
        try { return execute(method,token,request) }
        catch (error: IOException) {
            // No raw exception/cause is exposed: it may contain a URL, header or credential.
            if (error is TInvestApiException) throw error
            val causes = generateSequence<Throwable>(error) { it.cause }.take(8).toList()
            throw when {
                causes.any { it is SSLException } -> TInvestApiException(TInvestFailure.TLS)
                causes.any { it is SocketTimeoutException } -> TInvestApiException(TInvestFailure.TIMEOUT)
                causes.any { it is UnknownHostException } -> TInvestApiException(TInvestFailure.DNS)
                else -> TInvestApiException(TInvestFailure.NETWORK)
            }
        }
    }
    private fun execute(method: TInvestMethod, token: String, request: JSONObject): JSONObject {
        val address = "https://invest-public-api.tbank.ru/rest/tinkoff.public.invest.api.contract.v1.InstrumentsService/" + method.rpc
        val connection = openConnection(URL(address))
        try {
            connection.requestMethod = "POST"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 12_000
            connection.readTimeout = 20_000
            connection.doOutput = true
            connection.setRequestProperty("Authorization","Bearer " + token)
            connection.setRequestProperty("Content-Type","application/json; charset=utf-8")
            connection.setRequestProperty("Accept","application/json")
            val bytes = request.toString().toByteArray(Charsets.UTF_8)
            connection.setFixedLengthStreamingMode(bytes.size)
            connection.outputStream.use { it.write(bytes) }
            val status = connection.responseCode
            if (status !in 200..299) throw TInvestApiException(when (status) {
                401 -> TInvestFailure.AUTH
                403 -> TInvestFailure.ACCESS
                429 -> TInvestFailure.RATE_LIMIT
                in 300..399 -> TInvestFailure.REDIRECT
                else -> TInvestFailure.SERVICE
            },status)
            val response = connection.inputStream.bufferedReader().use { reader ->
                val result = StringBuilder()
                val buffer = CharArray(8192)
                while (true) {
                    val count = reader.read(buffer)
                    if (count < 0) break
                    result.append(buffer,0,count)
                    if (result.length > 8_000_000) throw TInvestApiException(TInvestFailure.RESPONSE_SIZE)
                }
                result.toString()
            }
            return try { JSONObject(response) } catch (_: Exception) { throw TInvestApiException(TInvestFailure.RESPONSE) }
        } finally { connection.disconnect() }
    }
}
class TInvestApi(
    private val token: () -> String?,
    private val transport: TInvestTransport = TInvestHttpTransport(),
    private val intervalMs: Long = 650
) {
    private val mutex = Mutex()
    private var previousRequest = 0L
    private suspend fun request(method: TInvestMethod, body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        mutex.withLock {
            coroutineContext.ensureActive()
            val credential = token()?.takeIf { it.isNotBlank() } ?: error("Добавьте токен Т‑Инвестиций во вкладке «Ещё»")
            val remaining = intervalMs - (android.os.SystemClock.elapsedRealtime()-previousRequest)
            if (remaining > 0) delay(remaining)
            previousRequest = android.os.SystemClock.elapsedRealtime()
            transport.post(method,credential,body)
        }
    }
    suspend fun search(query: String): List<BondSearchResult> {
        require(query.trim().length in 2..160) { "Введите от 2 до 160 символов названия, тикера или ISIN" }
        return TInvestJson.search(request(TInvestMethod.SEARCH,JSONObject().put("query",query.trim())
            .put("instrumentKind","INSTRUMENT_TYPE_BOND")))
    }
    suspend fun bond(uid: String): BondReference {
        require(uid.isNotBlank())
        return TInvestJson.reference(request(TInvestMethod.BOND,JSONObject()
            .put("idType","INSTRUMENT_ID_TYPE_UID").put("id",uid)),System.currentTimeMillis())
    }
    /** One-year requests avoid long-range API restrictions. Cache is only replaced after every request succeeds. */
    suspend fun download(reference: BondReference, fromDay: Long? = null): BondDownload {
        val today = LocalDate.now(ZoneOffset.UTC)
        val end = reference.maturityDay ?: today.plusYears(5).toEpochDay()
        val start = fromDay ?: minOf(today.minusYears(1).toEpochDay(), LocalDate.ofEpochDay(end).minusYears(1).toEpochDay())
        require(end >= start) { "Облигация погашена раньше запрашиваемого периода" }
        require(end - start <= 366L * 100) { "Слишком большой период. Уточните даты выпуска" }
        val all = linkedMapOf<String,BondCoupon>()
        var cursor = LocalDate.ofEpochDay(start)
        val last = LocalDate.ofEpochDay(end)
        while (!cursor.isAfter(last)) {
            coroutineContext.ensureActive()
            val next = minOf(cursor.plusYears(1),last.plusDays(1))
            val response = request(TInvestMethod.COUPONS,JSONObject().put("instrumentId",reference.uid)
                .put("from",TInvestJson.utc(cursor.toEpochDay()))
                .put("to",next.atStartOfDay(ZoneOffset.UTC).toInstant().minusNanos(1).toString()))
            TInvestJson.coupons(response,reference.uid).filter { it.payDay in start..end }
                .forEach { all[it.eventKey] = it }
            cursor = next
        }
        return BondDownload(reference,all.values.sortedBy { it.payDay },start,end)
    }
    suspend fun download(uid: String, fromDay: Long? = null) = download(bond(uid),fromDay)
}
