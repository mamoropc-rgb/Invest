package com.mamoro.investments.domain

import com.mamoro.investments.data.*
import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneOffset

class BondCalendarTest {
    private fun trade(id: String, side: String, count: Long, day: Long, deleted: Boolean = false) =
        TradeEntity(id,"s",side,count*1000000,100000,0,day*86400000,deleted=deleted)
    private fun coupon(key: String = "u/1", fix: Long? = 10, pay: Long = 12, amount: String? = "25.55") =
        BondCouponEntity("s",BondCoupon(key,1,pay,fix,1,10,amount,"RUB","COUPON_TYPE_CONSTANT"))
    private fun calculate(trades: List<TradeEntity>, coupons: List<BondCouponEntity> = listOf(coupon()),
        dates: List<BondTradeDateEntity> = trades.map { BondTradeDateEntity(it.id,it.happenedAt/86400000,it.happenedAt/86400000) },
        income: List<IncomeEntity> = emptyList(), receipts: List<BondCouponReceiptEntity> = emptyList()) =
        BondCalendar.calculate(coupons,trades,dates,income,receipts,ZoneOffset.UTC)

    @Test fun partialSaleChangesOnlyLaterEntitlements() {
        val rows = calculate(listOf(trade("b","BUY",10,1),trade("s","SELL",4,11)),
            listOf(coupon(),coupon("u/2",20,22)))
        assertEquals(listOf(10000000L,6000000L),rows.map { it.quantityMicros })
        assertEquals(listOf(25550L,15330L),rows.map { it.expectedMinor })
        assertFalse(rows[0].estimated)
    }
    @Test fun fullSaleAfterRecordDateKeepsUnpaidCoupon() {
        val rows = calculate(listOf(trade("b","BUY",10,1),trade("s","SELL",10,11)),
            listOf(coupon(),coupon("u/2",20,22)))
        assertEquals(25550L,rows[0].expectedMinor)
        assertEquals(0L,rows[1].expectedMinor)
    }
    @Test fun settlementDateDeterminesOwnership() {
        val trades = listOf(trade("b","BUY",10,9),trade("s","SELL",10,10))
        assertEquals(10000000L,calculate(trades,dates=listOf(BondTradeDateEntity("b",9,10),
            BondTradeDateEntity("s",10,11)))[0].quantityMicros)
        assertEquals(0L,calculate(listOf(trade("b","BUY",10,10)),
            dates=listOf(BondTradeDateEntity("b",10,11)))[0].quantityMicros)
    }
    @Test fun saleBeforeRecordAndRepurchaseRecalculateFuture() {
        val rows = calculate(listOf(trade("b","BUY",10,1),trade("s","SELL",10,5),trade("r","BUY",3,15)),
            listOf(coupon(),coupon("u/2",20,22)))
        assertEquals(listOf(0L,3000000L),rows.map { it.quantityMicros })
    }
    @Test fun missingDatesAndFloatingAmountsAreExplicit() {
        val rows = calculate(listOf(trade("b","BUY",10,1)),
            listOf(coupon(fix=null,amount=null)),dates=emptyList())
        assertTrue(rows[0].estimated)
        assertNull(rows[0].expectedMinor)
    }
    @Test fun paidCouponSurvivesSaleAndScheduleCorrection() {
        val paid = IncomeEntity("i","s","COUPON",20000,"После налога",12*86400000)
        val links = listOf(BondCouponReceiptEntity("s","u/1","i"))
        val row = calculate(listOf(trade("b","BUY",10,1,deleted=true)),
            listOf(coupon().copy(active=false)),income=listOf(paid),receipts=links).single()
        assertEquals(20000L,row.received!!.amountMinor)
        assertEquals(0L,row.quantityMicros)
        assertTrue(calculate(emptyList(),listOf(coupon().copy(active=false)),
            income=listOf(paid.copy(deleted=true)),receipts=links).isEmpty())
    }
    @Test fun amountRoundsAfterMultiplicationAndFutureIsNotIncome() {
        val row = calculate(listOf(trade("b","BUY",3,1)),listOf(coupon(amount="0.004"))).single()
        assertEquals(1L,row.expectedMinor)
        assertNull(row.received)
    }
}
