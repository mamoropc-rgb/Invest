package com.mamoro.investments.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MoneyMathTest {
    @Test fun priceAndQuantityWithComma() {
        assertEquals(123456L, MoneyMath.parseMoney("1 234,56"))
        assertEquals(2_500_000L, MoneyMath.parseQuantity("2,5"))
        assertEquals(308_640L, MoneyMath.totalMinor(123_456L, 2_500_000L))
    }

    @Test fun rejectNonPositiveQuantities() {
        assertThrows(IllegalArgumentException::class.java) { MoneyMath.parseQuantity("0") }
        assertThrows(IllegalArgumentException::class.java) { MoneyMath.parseMoney("-1") }
    }
}
