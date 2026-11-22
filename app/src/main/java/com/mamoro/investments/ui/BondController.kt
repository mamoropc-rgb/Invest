package com.mamoro.investments.ui

import android.app.Application
import com.mamoro.investments.data.*
import com.mamoro.investments.domain.BondCalendar
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal data class BondBrowserState(val results: List<BondSearchResult> = emptyList(),
    val selected: BondDownload? = null, val busy: Boolean = false, val searched: Boolean = false,
    val error: String? = null)
internal data class BondConnectionState(val saved: Boolean = false, val testing: Boolean = false, val message: String? = null)

internal class BondController(application: Application, private val scope: CoroutineScope,
    private val db: InvestmentDatabase, private val investments: InvestmentsRepository, private val notify: (String) -> Unit) {
    private val repo = BondRepository(db)
    private val token = BondTokenStore(application)
    private val api = TInvestApi(token::read)
    val state = repo.state.stateIn(scope,SharingStarted.WhileSubscribed(5000),BondState())
    val payments = combine(repo.state,investments.dashboard) { bonds, dashboard ->
        BondCalendar.calculate(bonds.coupons,dashboard.trades,bonds.tradeDates,dashboard.income,bonds.receipts)
    }.flowOn(Dispatchers.Default).stateIn(scope,SharingStarted.WhileSubscribed(5000),emptyList())
    private val _browser = MutableStateFlow(BondBrowserState())
    val browser = _browser.asStateFlow()
    private val _connection = MutableStateFlow(BondConnectionState())
    val connection = _connection.asStateFlow()
    private val _refreshing = MutableStateFlow<Set<String>>(emptySet())
    val refreshing = _refreshing.asStateFlow()
    private var browserJob: Job? = null
    private var testJob: Job? = null
    private val refreshJobs = mutableMapOf<String,Job>()

    init { scope.launch { _connection.value = BondConnectionState(saved=withContext(Dispatchers.IO) { token.read() != null }) } }
    fun clearBrowser() { browserJob?.cancel(); _browser.value = BondBrowserState() }
    fun backToResults() { browserJob?.cancel(); _browser.value = _browser.value.copy(selected=null,busy=false,error=null) }
    fun search(query: String) {
        browserJob?.cancel()
        _browser.value = BondBrowserState(busy=true)
        browserJob = scope.launch {
            try { _browser.value = BondBrowserState(results=api.search(query),searched=true) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { _browser.value = BondBrowserState(error=e.message ?: "Не удалось выполнить поиск",searched=true) }
        }
    }
    fun select(uid: String, localId: String?) {
        browserJob?.cancel()
        _browser.value = _browser.value.copy(busy=true,error=null)
        browserJob = scope.launch {
            try {
                val reference = api.bond(uid)
                val result = api.download(reference,coverageStart(reference,localId))
                _browser.value = _browser.value.copy(selected=result,busy=false)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _browser.value = _browser.value.copy(busy=false,error=e.message ?: "Не удалось загрузить расписание") }
        }
    }
    private suspend fun coverageStart(reference: BondReference, id: String?): Long {
        val firstTrade = id?.let { db.dao().getActiveTrades(it).minOfOrNull { trade ->
            Instant.ofEpochMilli(trade.happenedAt).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay() } }
        val previous = id?.let { db.bondDao().reference(it)?.fromDay }
        return listOfNotNull(reference.issueDay,firstTrade,previous,LocalDate.now().minusYears(1).toEpochDay()).min()
    }
    suspend fun accept(download: BondDownload, localId: String?, priceMinor: Long): String =
        if (localId == null) repo.importBond(download,priceMinor) else localId.also { repo.link(it,download) }
    fun refresh(id: String) {
        if (id in _refreshing.value) return
        _refreshing.value = _refreshing.value + id
        refreshJobs[id] = scope.launch {
            try {
                val old = requireNotNull(db.bondDao().reference(id)) { "Сначала подключите выпуск" }
                val reference = api.bond(old.reference.uid)
                repo.link(id,api.download(reference,coverageStart(reference,id)))
                notify("Расписание обновлено")
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { notify(e.message ?: "Не удалось обновить расписание. Сохранённые данные доступны офлайн") }
            finally { _refreshing.value = _refreshing.value - id; refreshJobs.remove(id) }
        }
    }
    suspend fun saveToken(value: String) {
        testJob?.cancel(); clearBrowser()
        withContext(Dispatchers.IO) { token.save(value) }
        _connection.value = BondConnectionState(saved=true,message="Токен сохранён на устройстве. Проверьте доступ к справочнику")
    }
    fun forgetToken() {
        testJob?.cancel(); clearBrowser(); refreshJobs.values.toList().forEach { it.cancel() }
        scope.launch {
            try {
                withContext(Dispatchers.IO) { token.clear() }
                _connection.value = BondConnectionState(message="Токен удалён. Сохранённые расписания доступны офлайн")
            } catch (e: Exception) { notify(e.message ?: "Не удалось удалить токен") }
        }
    }
    fun testConnection() {
        testJob?.cancel()
        _connection.value = _connection.value.copy(testing=true,message=null)
        testJob = scope.launch {
            try { api.search("ОФЗ 26242"); _connection.value = _connection.value.copy(testing=false,message="Доступ к справочнику проверен") }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { _connection.value = _connection.value.copy(testing=false,message=e.message ?: "Нет связи с API") }
        }
    }
    suspend fun confirm(payment: BondCalendar.Payment, amount: Long, account: String, day: Long) =
        investments.createIncome(payment.securityId,"COUPON",amount,"Купон №${payment.coupon.number}",account,payment.coupon.eventKey,day)
    suspend fun linkIncome(payment: BondCalendar.Payment, incomeId: String) = repo.linkIncome(payment.securityId,payment.coupon.eventKey,incomeId)
    suspend fun settlement(tradeId: String, day: Long?) = repo.settlement(tradeId,day)
}
