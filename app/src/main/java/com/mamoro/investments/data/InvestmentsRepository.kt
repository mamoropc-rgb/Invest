package com.mamoro.investments.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.mamoro.investments.domain.MoneyMath
import com.mamoro.investments.domain.PortfolioMath
import kotlinx.coroutines.flow.combine
import org.json.JSONObject
import java.util.Locale
import java.util.UUID
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class InvestmentsRepository(private val context: Context, private val db: InvestmentDatabase) {
    private val dao = db.dao()
    private val eventsUri = Uri.parse("content://com.mamoro.investments.sync/events")

    data class DashboardState(
        val securities: List<SecurityEntity>,
        val trades: List<TradeEntity>,
        val income: List<IncomeEntity>,
        val accounts: List<WalletAccountEntity>,
        val summary: PortfolioMath.Summary,
        val pendingEvents: Long
    )

    val dashboard = combine(
        dao.observeSecurities(), dao.observeTrades(), dao.observeIncome(),
        dao.observeWalletAccounts(), dao.observeMaxSequence(), dao.observeSyncState()
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val securities = values[0] as List<SecurityEntity>
        @Suppress("UNCHECKED_CAST")
        val trades = values[1] as List<TradeEntity>
        @Suppress("UNCHECKED_CAST")
        val income = values[2] as List<IncomeEntity>
        @Suppress("UNCHECKED_CAST")
        val accounts = values[3] as List<WalletAccountEntity>
        val max = values[4] as Long? ?: 0L
        val ack = (values[5] as SyncStateEntity?)?.ackSequence ?: 0L
        DashboardState(securities, trades, income, accounts,
            PortfolioMath.compute(securities, trades, income), (max - ack).coerceAtLeast(0L))
    }

    suspend fun createSecurity(name: String, ticker: String, kind: String, priceMinor: Long) {
        require(name.isNotBlank() && ticker.isNotBlank()) { "Введите название и тикер" }
        require(kind in listOf("STOCK", "BOND", "FUND"))
        require(priceMinor >= 0L)
        db.withTransaction {
            val normalizedTicker = ticker.trim().uppercase(Locale.ROOT)
            require(dao.getSecurityByTicker(normalizedTicker) == null) { "Такой тикер уже добавлен" }
            dao.insertSecurity(SecurityEntity(UUID.randomUUID().toString(), name.trim(),
                normalizedTicker, kind, priceMinor, System.currentTimeMillis()))
        }
    }

    suspend fun updatePrice(securityId: String, priceMinor: Long) {
        require(priceMinor >= 0L)
        db.withTransaction {
            val security = requireNotNull(dao.getSecurity(securityId))
            dao.updateSecurity(security.copy(lastPriceMinor = priceMinor))
        }
    }

    suspend fun archiveSecurity(securityId: String) {
        db.withTransaction {
            val security = requireNotNull(dao.getSecurity(securityId))
            val holdings = PortfolioMath.compute(listOf(security), dao.getActiveTrades(securityId), emptyList())
            require(holdings.positions.first().quantityMicros == 0L) { "Нельзя скрыть бумагу с открытой позицией" }
            dao.updateSecurity(security.copy(archived = !security.archived))
        }
    }

    suspend fun createTrade(
        securityId: String, side: String, quantityMicros: Long, priceMinor: Long,
        feeMinor: Long, walletAccountId: String, executionDay: Long? = null, settlementDay: Long? = null
    ) {
        require(side == "BUY" || side == "SELL")
        require(quantityMicros > 0L && priceMinor >= 0L && feeMinor >= 0L)
        val now = operationTimestamp(executionDay)
        val day = executionDay ?: LocalDate.now().toEpochDay()
        require(settlementDay == null || settlementDay >= day) { "Дата расчётов не может предшествовать сделке" }
        db.withTransaction {
            val security = requireNotNull(dao.getSecurity(securityId)) { "Бумага не найдена" }
            require(!security.archived) { "Бумага архивирована" }
            if (side == "SELL") {
                val held = PortfolioMath.compute(listOf(security), dao.getActiveTrades(securityId), emptyList())
                    .positions.first().quantityMicros
                require(held >= quantityMicros) { "Недостаточно бумаг для продажи" }
            }
            if (walletAccountId.isNotEmpty()) require(dao.getAccount(walletAccountId) != null)
            val trade = TradeEntity(UUID.randomUUID().toString(), securityId, side, quantityMicros,
                priceMinor, feeMinor, now, walletAccountId)
            // Backdated trades must also leave every subsequent sale covered.
            PortfolioMath.compute(listOf(security),dao.getActiveTrades(securityId) + trade,emptyList())
            dao.insertTrade(trade)
            if (security.kind == "BOND") db.bondDao().saveTradeDate(BondTradeDateEntity(trade.id,day,settlementDay))
            addEvent("TRADE_UPSERT", trade.id, tradeJson(trade, security.ticker))
        }
        notifyWallet()
    }

    suspend fun createIncome(
        securityId: String, kind: String, amountMinor: Long, note: String, walletAccountId: String,
        couponEventKey: String? = null, receivedDay: Long? = null
    ) {
        require(kind == "DIVIDEND" || kind == "COUPON")
        require(amountMinor > 0L) { "Доход должен быть больше нуля" }
        val now = operationTimestamp(receivedDay)
        db.withTransaction {
            val security = requireNotNull(dao.getSecurity(securityId))
            if (couponEventKey != null) {
                require(kind == "COUPON" && security.kind == "BOND")
                if (walletAccountId.isNotEmpty()) require(dao.getAccount(walletAccountId)?.currency.equals("RUB",ignoreCase=true)) {
                    "Для рублёвого купона выберите рублёвый счёт или сохраните выплату локально"
                }
                val coupon = requireNotNull(db.bondDao().coupon(securityId,couponEventKey)) { "Купон не найден" }
                require(coupon.active) { "Выплата отсутствует в обновлённом расписании" }
                require(coupon.coupon.payDay <= LocalDate.now().toEpochDay()) { "Будущую выплату пока нельзя подтвердить" }
                val previous = db.bondDao().receipt(securityId,couponEventKey)?.incomeId?.let { dao.getIncome(it) }
                require(previous == null || previous.deleted) { "Этот купон уже отмечен полученным" }
            }
            if (walletAccountId.isNotEmpty()) require(dao.getAccount(walletAccountId) != null)
            val item = IncomeEntity(UUID.randomUUID().toString(), securityId, kind, amountMinor,
                note.trim(), now, walletAccountId)
            dao.insertIncome(item)
            if (couponEventKey != null) db.bondDao().saveReceipt(BondCouponReceiptEntity(securityId,couponEventKey,item.id))
            addEvent("INCOME_UPSERT", item.id, incomeJson(item, security.ticker))
        }
        notifyWallet()
    }

    suspend fun deleteTrade(id: String) {
        db.withTransaction {
            val trade = requireNotNull(dao.getTrade(id))
            if (trade.deleted) return@withTransaction
            val security = requireNotNull(dao.getSecurity(trade.securityId))
            val remaining = dao.getActiveTrades(trade.securityId).filterNot { it.id == id }
            // Prevent deleting a purchase that later sales depend upon.
            PortfolioMath.compute(listOf(security), remaining, emptyList())
            dao.updateTrade(trade.copy(deleted = true))
            addEvent("TRADE_DELETE", id, JSONObject().put("operation_id", id)
                .put("security_id", trade.securityId).put("wallet_account_id", trade.walletAccountId))
        }
        notifyWallet()
    }

    suspend fun deleteIncome(id: String) {
        db.withTransaction {
            val income = requireNotNull(dao.getIncome(id))
            if (income.deleted) return@withTransaction
            dao.updateIncome(income.copy(deleted = true))
            addEvent("INCOME_DELETE", id, JSONObject().put("operation_id", id)
                .put("security_id", income.securityId).put("wallet_account_id", income.walletAccountId))
        }
        notifyWallet()
    }

    private suspend fun addEvent(kind: String, operationId: String, body: JSONObject) {
        val eventId = UUID.randomUUID().toString()
        body.put("schema_version", 1)
            .put("event_id", eventId)
            .put("source_app", "com.mamoro.investments")
            .put("event_kind", kind)
        dao.appendEvent(SyncEventEntity(eventId = eventId, operationId = operationId,
            eventKind = kind, payloadJson = body.toString(), createdAt = System.currentTimeMillis()))
    }

    private fun operationTimestamp(day: Long?): Long {
        if (day == null) return System.currentTimeMillis()
        val date = LocalDate.ofEpochDay(day)
        require(!date.isAfter(LocalDate.now())) { "Операция не может быть в будущем" }
        return date.atTime(LocalTime.now()).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    private fun tradeJson(trade: TradeEntity, ticker: String): JSONObject {
        val gross = MoneyMath.totalMinor(trade.unitPriceMinor, trade.quantityMicros)
        val cashDelta = if (trade.side == "BUY") -Math.addExact(gross, trade.feeMinor)
            else Math.subtractExact(gross, trade.feeMinor)
        return JSONObject().put("operation_id", trade.id).put("security_id", trade.securityId)
            .put("ticker", ticker).put("side", trade.side)
            .put("quantity_micros", trade.quantityMicros).put("unit_price_minor", trade.unitPriceMinor)
            .put("fee_minor", trade.feeMinor).put("currency", "RUB")
            .put("cash_delta_minor", cashDelta).put("wallet_account_id", trade.walletAccountId)
            .put("happened_at", trade.happenedAt)
    }

    private fun incomeJson(income: IncomeEntity, ticker: String): JSONObject =
        JSONObject().put("operation_id", income.id).put("security_id", income.securityId)
            .put("ticker", ticker).put("income_kind", income.kind)
            .put("cash_delta_minor", income.amountMinor).put("currency", "RUB")
            .put("wallet_account_id", income.walletAccountId).put("happened_at", income.happenedAt)
            .put("note", income.note)

    private fun notifyWallet() = context.contentResolver.notifyChange(eventsUri, null)
}
