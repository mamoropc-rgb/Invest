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
        androidx.core.view.WindowInsetsControllerCompat(window,window.decorView).apply {
            isAppearanceLightStatusBars=false
            isAppearanceLightNavigationBars=false
        }
        val mode = intent.getStringExtra("screen") ?: "portfolio"
        if (mode == "bond-keystore-check") runBondKeystoreProbe(this)
        val fixture = if (mode == "empty") emptyState() else referenceState()
        setContent {
            InvestmentsTheme {
                if (mode.startsWith("bond-")) BondCaptureScreen(mode) else InvestmentDashboard(vm, fixture,
                    initialPage = when (mode) { "operations" -> 2; "assets" -> 1; "analytics" -> 3; "more" -> 4; else -> 0 },
                    initiallyShowQuick = mode == "actions",
                    history = if (mode == "empty") emptyList() else referenceHistory())
            }
        }
    }
}

private fun emptyState() = InvestmentsRepository.DashboardState(
    emptyList(), emptyList(), emptyList(), emptyList(),
    PortfolioMath.compute(emptyList(), emptyList(), emptyList()), 0
)

private fun referenceState(): InvestmentsRepository.DashboardState {
    val lukoil = SecurityEntity("lkoh", "ЛУКОЙЛ", "LKOH", "STOCK", 287400, 0)
    val stocks = SecurityEntity("sber", "Сбер", "SBER", "STOCK", 19650, 0)
    val bonds = SecurityEntity("ofz", "ОФЗ 26242", "SU26242", "BOND", 106500, 0)
    val funds = SecurityEntity("fund", "Фонд", "TMOS", "FUND", 17940, 0)
    val gazprom = SecurityEntity("gazp", "Газпром", "GAZP", "STOCK", 15000, 0)
    val rzd = SecurityEntity("rzd", "РЖД", "RZD", "BOND", 195000, 0)
    val securities = listOf(lukoil, stocks, bonds, funds, gazprom, rzd)
    val positions = listOf(
        PortfolioMath.Position(lukoil, 100_000_000, 28_740_000, 24_898_000, 3_842_000, 0),
        PortfolioMath.Position(stocks, 1_000_000_000, 19_650_000, 17_186_000, 2_464_000, 0),
        PortfolioMath.Position(bonds, 400_000_000, 42_600_000, 39_612_000, 2_988_000, 0),
        PortfolioMath.Position(funds, 1_000_000_000, 17_940_000, 16_046_512, 1_893_488, 0),
        PortfolioMath.Position(gazprom, 2_330_000_000, 34_950_000, 30_000_000, 4_950_000, 0),
        PortfolioMath.Position(rzd, 0, 0, 0, 0, 0)
    )
    fun at(day: Int, hour: Int, minute: Int) = LocalDate.of(2026,3,day)
        .atTime(hour,minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val trades = listOf(
        TradeEntity("buy-lkoh", "lkoh", "BUY", 3_000_000, 526000, 0, at(29,14,21)),
        TradeEntity("sell-sber", "sber", "SELL", 5_000_000, 251000, 0, at(27,16,34)),
        TradeEntity("buy-rzd", "rzd", "BUY", 5_000_000, 195000, 0, at(26,15,2))
    )
    val income = listOf(
        IncomeEntity("div-gazp", "gazp", "DIVIDEND", 342000, "10 шт.", at(28,10,5)),
        IncomeEntity("coupon-ofz", "ofz", "COUPON", 156000, "", at(27,11,20))
    )
    // These presentation numbers intentionally reproduce the mockup; they aren't financial test inputs.
    val summary = PortfolioMath.Summary(positions, 148_230_000, 1_256_000, 11_974_000, 5_624_000, 2_486_000)
    return InvestmentsRepository.DashboardState(securities, trades, income, emptyList(), summary, 0)
}

/** The reference curve exists only in src/debug; it is never persisted or bundled in Release. */
private fun referenceHistory(): List<PortfolioHistoryPoint> {
    val values = listOf<Long>(
        137774262, 137600000, 137948525, 138471311, 138819836, 139255492, 139952541, 139691148, 139865410, 140213934, 139952541, 139604016, 139516885, 140388197, 140910984, 141608033, 141259508, 142479344, 142827869, 142915000, 143350656, 142305082, 140910984, 140910984, 140736721, 140562459, 141172377, 141520902, 141782295, 142130820, 141956557, 141956557, 141259508, 140649590, 140736721, 140736721, 140998115, 141608033, 142130820, 142566475, 143176393, 144047705, 145006148, 145441803, 145093279, 145267541, 145616066, 145267541, 145093279, 145528934, 146400246, 147358689, 147271557, 147097295, 146835902, 147358689, 147532951, 146974000, 148230000
    )
    val end = LocalDate.of(2026,3,29).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    return values.mapIndexed { index, value ->
        PortfolioHistoryPoint(end - 28L * 86_400_000L * (values.lastIndex-index)/values.lastIndex, value)
    }
}
