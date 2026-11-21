package com.mamoro.investments.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import com.mamoro.investments.data.*
import com.mamoro.investments.domain.PortfolioMath
import com.mamoro.investments.ui.theme.InvestmentsTheme
import java.time.LocalDate
import java.time.ZoneId

/** Debug-only screenshot entry point. Fixtures live in memory and never enter Room. */
class PremiumCaptureActivity : ComponentActivity() {
    private val vm: InvestmentsViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val mode = intent.getStringExtra("screen") ?: "portfolio"
        val fixture = if (mode == "empty") emptyState() else referenceState()
        setContent {
            InvestmentsTheme {
                InvestmentDashboard(vm, fixture,
                    initialPage = when (mode) { "operations" -> 2; "assets" -> 1; "analytics" -> 3; "more" -> 4; else -> 0 },
                    initiallyShowQuick = mode == "actions")
            }
        }
    }
}

private fun emptyState() = InvestmentsRepository.DashboardState(
    emptyList(), emptyList(), emptyList(), emptyList(),
    PortfolioMath.compute(emptyList(), emptyList(), emptyList()), 0
)

private fun referenceState(): InvestmentsRepository.DashboardState {
    val stocks = SecurityEntity("sber", "Сбер", "SBER", "STOCK", 83340, 0)
    val bonds = SecurityEntity("ofz", "ОФЗ 26242", "SU26242", "BOND", 106500, 0)
    val funds = SecurityEntity("fund", "Фонд", "TMOS", "FUND", 17940, 0)
    val lukoil = SecurityEntity("lkoh", "ЛУКОЙЛ", "LKOH", "STOCK", 526000, 0)
    val gazprom = SecurityEntity("gazp", "Газпром", "GAZP", "STOCK", 15000, 0)
    val rzd = SecurityEntity("rzd", "РЖД", "RZD", "BOND", 195000, 0)
    val securities = listOf(stocks, bonds, funds, lukoil, gazprom, rzd)
    val positions = listOf(
        PortfolioMath.Position(stocks, 1_000_000_000, 83_340_000, 69_916_107, 13_423_893, 0),
        PortfolioMath.Position(bonds, 400_000_000, 42_600_000, 39_775_910, 2_824_090, 0),
        PortfolioMath.Position(funds, 1_000_000_000, 17_940_000, 16_046_512, 1_893_488, 0)
    ) + listOf(lukoil, gazprom, rzd).map { PortfolioMath.Position(it, 0, 0, 0, 0, 0) }
    fun at(daysAgo: Long, hour: Int, minute: Int) = LocalDate.now().minusDays(daysAgo)
        .atTime(hour, minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val trades = listOf(
        TradeEntity("buy-lkoh", "lkoh", "BUY", 3_000_000, 526000, 0, at(0,12,18)),
        TradeEntity("sell-sber", "sber", "SELL", 5_000_000, 251000, 0, at(1,16,34)),
        TradeEntity("buy-rzd", "rzd", "BUY", 5_000_000, 195000, 0, at(2,15,2))
    )
    val income = listOf(
        IncomeEntity("div-gazp", "gazp", "DIVIDEND", 342000, "10 шт.", at(0,10,7)),
        IncomeEntity("coupon-ofz", "ofz", "COUPON", 156000, "", at(1,11,20))
    )
    // These presentation numbers intentionally reproduce the mockup; they aren't financial test inputs.
    val summary = PortfolioMath.Summary(positions, 148_230_000, 1_256_000, 11_974_000, 5_624_000, 2_486_000)
    return InvestmentsRepository.DashboardState(securities, trades, income, emptyList(), summary, 0)
}
