package com.mamoro.investments.domain

import com.mamoro.investments.data.IncomeEntity
import com.mamoro.investments.data.SecurityEntity
import com.mamoro.investments.data.TradeEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PortfolioMathTest {
    private val asset = SecurityEntity("a", "ОФЗ", "SU26242", "BOND", 12_000L, 1L)

    @Test fun buySellAndIncomeNoDoubleCount() {
        val trades = listOf(
            TradeEntity("buy", "a", "BUY", 10_000_000L, 10_000L, 100L, 1L),
            TradeEntity("sell", "a", "SELL", 4_000_000L, 11_000L, 40L, 2L)
        )
        val income = listOf(IncomeEntity("coupon", "a", "COUPON", 500L, "", 3L))
        val result = PortfolioMath.compute(listOf(asset), trades, income)
        assertEquals(6_000_000L, result.positions[0].quantityMicros)
        assertEquals(72_000L, result.marketValueMinor)
        assertEquals(60_060L, result.positions[0].costMinor)
        assertEquals(11_940L, result.unrealizedMinor)
        assertEquals(3_920L, result.realizedMinor)
        assertEquals(500L, result.couponsMinor)
        assertEquals(0L, result.dividendsMinor)
    }

    @Test fun cannotSellMoreThanHeld() {
        val bad = listOf(TradeEntity("x", "a", "SELL", 1_000_000L, 10_000L, 0L, 1L))
        assertThrows(IllegalArgumentException::class.java) { PortfolioMath.compute(listOf(asset), bad, emptyList()) }
    }
}
