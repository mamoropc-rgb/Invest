package com.mamoro.investments.domain

import com.mamoro.investments.data.IncomeEntity
import com.mamoro.investments.data.SecurityEntity
import com.mamoro.investments.data.TradeEntity
import java.math.BigDecimal
import java.math.RoundingMode

/** Weighted-average acquisition cost. Realized gain and cash payments never inflate asset value. */
object PortfolioMath {
    data class Position(
        val security: SecurityEntity,
        val quantityMicros: Long,
        val currentValueMinor: Long,
        val costMinor: Long,
        val unrealizedMinor: Long,
        val realizedMinor: Long
    )

    data class Summary(
        val positions: List<Position>,
        val marketValueMinor: Long,
        val unrealizedMinor: Long,
        val realizedMinor: Long,
        val dividendsMinor: Long,
        val couponsMinor: Long
    )

    fun compute(
        securities: List<SecurityEntity>,
        trades: List<TradeEntity>,
        income: List<IncomeEntity>
    ): Summary {
        val positions = securities.map { security ->
            val sorted = trades.asSequence().filter { !it.deleted && it.securityId == security.id }
                .sortedWith(compareBy<TradeEntity> { it.happenedAt }.thenBy { it.id })
            var quantity = 0L
            var cost = BigDecimal.ZERO
            var realized = BigDecimal.ZERO
            for (trade in sorted) {
                val gross = BigDecimal.valueOf(MoneyMath.totalMinor(trade.unitPriceMinor, trade.quantityMicros))
                if (trade.side == "BUY") {
                    quantity = Math.addExact(quantity, trade.quantityMicros)
                    cost += gross + BigDecimal.valueOf(trade.feeMinor)
                } else if (trade.side == "SELL") {
                    require(trade.quantityMicros <= quantity) { "Продажа превышает остаток: ${security.ticker}" }
                    val basis = if (trade.quantityMicros == quantity) cost else cost.multiply(
                        BigDecimal.valueOf(trade.quantityMicros)
                    ).divide(BigDecimal.valueOf(quantity), 12, RoundingMode.HALF_UP)
                    realized += gross - BigDecimal.valueOf(trade.feeMinor) - basis
                    cost -= basis
                    quantity -= trade.quantityMicros
                } else error("Неизвестный тип сделки")
            }
            val value = MoneyMath.totalMinor(security.lastPriceMinor, quantity)
            val roundedCost = MoneyMath.minorRounded(cost)
            Position(
                security = security,
                quantityMicros = quantity,
                currentValueMinor = value,
                costMinor = roundedCost,
                unrealizedMinor = value - roundedCost,
                realizedMinor = MoneyMath.minorRounded(realized)
            )
        }
        val activeIncome = income.filterNot { it.deleted }
        return Summary(
            positions = positions,
            marketValueMinor = positions.sumOf { it.currentValueMinor },
            unrealizedMinor = positions.sumOf { it.unrealizedMinor },
            realizedMinor = positions.sumOf { it.realizedMinor },
            dividendsMinor = activeIncome.filter { it.kind == "DIVIDEND" }.sumOf { it.amountMinor },
            couponsMinor = activeIncome.filter { it.kind == "COUPON" }.sumOf { it.amountMinor }
        )
    }
}
