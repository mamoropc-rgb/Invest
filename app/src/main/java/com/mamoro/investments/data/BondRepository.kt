package com.mamoro.investments.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.combine
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import java.util.UUID

data class BondState(val references: List<BondReferenceEntity> = emptyList(),
    val coupons: List<BondCouponEntity> = emptyList(), val tradeDates: List<BondTradeDateEntity> = emptyList(),
    val receipts: List<BondCouponReceiptEntity> = emptyList())

class BondRepository(private val db: InvestmentDatabase) {
    private val dao = db.bondDao()
    val state = combine(dao.observeReferences(),dao.observeCoupons(),dao.observeTradeDates(),dao.observeReceipts(), ::BondState)

    suspend fun importBond(download: BondDownload, priceMinor: Long): String = db.withTransaction {
        require(priceMinor > 0) { "Введите текущую цену за облигацию в рублях" }
        val ref = download.reference
        require(dao.byUid(ref.uid) == null) { "Эта облигация уже подключена" }
        val ticker = (ref.ticker.ifBlank { ref.isin }).trim().uppercase(Locale.ROOT)
        require(ticker.isNotBlank()) { "В справочнике нет тикера или ISIN" }
        require(db.dao().getSecurityByTicker(ticker) == null) { "Бумага уже добавлена. Подключите её из карточки актива" }
        val id = UUID.randomUUID().toString()
        db.dao().insertSecurity(SecurityEntity(id,ref.name,ticker,"BOND",priceMinor,System.currentTimeMillis()))
        saveDownload(id,download)
        id
    }
    suspend fun link(id: String, download: BondDownload) = db.withTransaction { saveDownload(id,download) }

    private suspend fun saveDownload(id: String, download: BondDownload) {
        val security = requireNotNull(db.dao().getSecurity(id)) { "Бумага не найдена" }
        require(security.kind == "BOND") { "Справочник доступен только для облигаций" }
        require(download.reference.currency == "RUB") { "Сейчас приложение ведёт учёт только рублёвых облигаций" }
        val previous = dao.reference(id)
        require(previous == null || previous.reference.uid == download.reference.uid) { "Для другого выпуска создайте отдельную бумагу" }
        require(dao.byUid(download.reference.uid)?.securityId.let { it == null || it == id }) { "Этот выпуск уже связан с другой бумагой" }
        require(download.throughDay >= download.fromDay)
        dao.invalidateCovered(id,download.fromDay,download.throughDay)
        dao.saveCoupons(download.coupons.map { BondCouponEntity(id,it) })
        dao.saveReference(BondReferenceEntity(id,download.reference,
            minOf(download.fromDay,previous?.fromDay ?: download.fromDay),
            maxOf(download.throughDay,previous?.throughDay ?: download.throughDay)))
    }
    suspend fun settlement(tradeId: String, day: Long?) = db.withTransaction {
        val trade = requireNotNull(db.dao().getTrade(tradeId)) { "Сделка не найдена" }
        require(!trade.deleted && db.dao().getSecurity(trade.securityId)?.kind == "BOND")
        val execution = dao.tradeDate(tradeId)?.executionDay ?: Instant.ofEpochMilli(trade.happenedAt)
            .atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
        require(day == null || day >= execution) { "Дата расчётов не может предшествовать сделке" }
        dao.saveTradeDate(BondTradeDateEntity(tradeId,execution,day))
    }
    /** Link an existing receipt without creating a second income/sync operation. */
    suspend fun linkIncome(id: String, key: String, incomeId: String) = db.withTransaction {
        requireNotNull(dao.coupon(id,key)) { "Купон не найден" }
        val income = requireNotNull(db.dao().getIncome(incomeId))
        require(!income.deleted && income.kind == "COUPON" && income.securityId == id) { "Выберите купон этой облигации" }
        val previous = dao.receipt(id,key)?.incomeId?.let { db.dao().getIncome(it) }
        require(previous == null || previous.deleted) { "Этот купон уже отмечен полученным" }
        require(dao.receiptForIncome(incomeId) == null) { "Операция уже связана с купоном" }
        dao.saveReceipt(BondCouponReceiptEntity(id,key,incomeId))
    }
}
