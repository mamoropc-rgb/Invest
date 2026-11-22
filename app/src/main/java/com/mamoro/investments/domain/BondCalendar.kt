package com.mamoro.investments.domain

import com.mamoro.investments.data.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.ZoneId

/** A forecast is never an IncomeEntity. Only explicit receipt confirmation can create income. */
object BondCalendar {
    data class Payment(val securityId: String, val coupon: BondCoupon, val quantityMicros: Long,
        val expectedMinor: Long?, val estimated: Boolean, val received: IncomeEntity?, val sourceActive: Boolean)

    fun calculate(coupons: List<BondCouponEntity>, trades: List<TradeEntity>,
        dates: List<BondTradeDateEntity>, income: List<IncomeEntity>,
        receipts: List<BondCouponReceiptEntity>, zone: ZoneId = ZoneId.systemDefault()): List<Payment> {
        val tradeGroups = trades.filterNot { it.deleted }.groupBy { it.securityId }
        val dateIndex = dates.associateBy { it.tradeId }
        val incomeIndex = income.filter { !it.deleted && it.kind == "COUPON" }.associateBy { it.id }
        val receiptIndex = receipts.associateBy { it.securityId to it.eventKey }
        return coupons.mapNotNull { row ->
            val coupon = row.coupon
            val receipt = receiptIndex[row.securityId to coupon.eventKey]?.incomeId?.let(incomeIndex::get)
                ?.takeIf { it.securityId == row.securityId }
            if (!row.active && receipt == null) return@mapNotNull null
            val cutoff = coupon.fixDay ?: coupon.endDay ?: coupon.payDay
            var estimated = coupon.fixDay == null
            var quantity = 0L
            tradeGroups[row.securityId].orEmpty().forEach { trade ->
                val timing = dateIndex[trade.id]
                val execution = timing?.executionDay ?: Instant.ofEpochMilli(trade.happenedAt)
                    .atZone(zone).toLocalDate().toEpochDay()
                if (execution <= cutoff) {
                    // Missing settlement is explicitly an estimate using the execution date.
                    val settlement = timing?.settlementDay ?: execution.also { estimated = true }
                    if (settlement <= cutoff) quantity = Math.addExact(quantity,
                        if (trade.side == "BUY") trade.quantityMicros else -trade.quantityMicros)
                }
            }
            if (quantity < 0L) estimated = true // Incomplete settlement history; do not forecast negative cash.
            quantity = quantity.coerceAtLeast(0L)
            val expected = coupon.amountPerBond?.takeIf { coupon.currency == "RUB" }?.let { amount ->
                runCatching {
                    require(BigDecimal(amount).signum() >= 0)
                    BigDecimal(amount).multiply(BigDecimal.valueOf(quantity,6))
                        .movePointRight(2).setScale(0,RoundingMode.HALF_UP).longValueExact()
                }.getOrNull()
            }
            Payment(row.securityId,coupon,quantity,expected,estimated,receipt,row.active)
        }.sortedWith(compareBy<Payment> { it.coupon.payDay }.thenBy { it.coupon.eventKey })
    }
}
