package com.mamoro.investments.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

/** All persisted RUB monetary amounts use integer kopeks. Quantities use millionths. */
object MoneyMath {
    private val million = BigDecimal(1_000_000L)
    private val locale = Locale.forLanguageTag("ru-RU")

    fun parseMoney(input: String): Long {
        val normalized = input.trim().replace("\u00A0", "").replace(" ", "").replace(',', '.')
        require(normalized.isNotEmpty()) { "Введите сумму" }
        val number = BigDecimal(normalized)
        require(number >= BigDecimal.ZERO) { "Сумма не может быть отрицательной" }
        return number.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact()
    }

    fun parseQuantity(input: String): Long {
        val normalized = input.trim().replace("\u00A0", "").replace(" ", "").replace(',', '.')
        require(normalized.isNotEmpty()) { "Введите количество" }
        val value = BigDecimal(normalized).setScale(6, RoundingMode.UNNECESSARY).movePointRight(6).longValueExact()
        require(value > 0L) { "Количество должно быть больше нуля" }
        return value
    }

    fun totalMinor(priceMinor: Long, quantityMicros: Long): Long =
        BigDecimal.valueOf(priceMinor).multiply(BigDecimal.valueOf(quantityMicros))
            .divide(million, 0, RoundingMode.HALF_UP).longValueExact()

    fun formatMoney(minor: Long): String {
        val formatter = NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = 0
            maximumFractionDigits = 2
        }
        return "${formatter.format(BigDecimal.valueOf(minor, 2))} ₽"
    }

    fun formatQuantity(quantityMicros: Long): String =
        BigDecimal.valueOf(quantityMicros).divide(million).stripTrailingZeros().toPlainString()

    fun toUnits(quantityMicros: Long): BigDecimal = BigDecimal.valueOf(quantityMicros).divide(million)

    fun minorRounded(value: BigDecimal): Long = value.setScale(0, RoundingMode.HALF_UP).longValueExact()
}
