package com.mamoro.investments.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mamoro.investments.data.*
import com.mamoro.investments.domain.BondCalendar
import com.mamoro.investments.domain.PortfolioMath
import com.mamoro.investments.ui.theme.*
import java.time.LocalDate
import java.time.ZoneOffset

/** Entirely synthetic visual fixtures: debug APK only, never stored, never sent to an API. */
internal object BondCaptureFixtures {
    val today=LocalDate.of(2026,11,22).toEpochDay()
    private fun at(day: Long)=day*86400000L+12*3600000L
    val reference=BondReference("demo-bond","DEMO-FIGI","RU000DEMO000","SU26242","TQOB","ОФЗ 26242 · пример","RUB",
        "1000","12.35",LocalDate.of(2023,1,11).toEpochDay(),LocalDate.of(2023,1,18).toEpochDay(),
        LocalDate.of(2029,8,29).toEpochDay(),2,false,false,false,at(today))
    val security=SecurityEntity("demo","ОФЗ 26242 · пример","SU26242","BOND",99000,at(today-90))
    val trades=listOf(TradeEntity("buy","demo","BUY",10000000,98000,100,at(today-90)),
        TradeEntity("sell","demo","SELL",4000000,99500,40,at(today-2)))
    val income=listOf(IncomeEntity("paid","demo","COUPON",27188,"Купон №11 · после налога",at(today-60)))
    val coupons=listOf(
        BondCoupon("demo-bond/11",11,today-60,today-63,today-150,today-61,"31.25","RUB","COUPON_TYPE_CONSTANT"),
        BondCoupon("demo-bond/12",12,today-1,today-3,today-90,today-2,"31.25","RUB","COUPON_TYPE_CONSTANT"),
        BondCoupon("demo-bond/13",13,today+30,today+27,today-2,today+29,"30.00","RUB","COUPON_TYPE_CONSTANT"),
        BondCoupon("demo-bond/14",14,today+90,null,today+29,today+89,null,"RUB","COUPON_TYPE_FLOATING"))
    val download=BondDownload(reference,coupons,today-365,reference.maturityDay!!)
    val bonds=BondState(listOf(BondReferenceEntity("demo",reference,download.fromDay,download.throughDay)),
        coupons.map { BondCouponEntity("demo",it) },
        listOf(BondTradeDateEntity("buy",today-90,today-89),BondTradeDateEntity("sell",today-2,today-1)),
        listOf(BondCouponReceiptEntity("demo","demo-bond/11","paid")))
    val dashboard=InvestmentsRepository.DashboardState(listOf(security),trades,income,emptyList(),
        PortfolioMath.compute(listOf(security),trades,income),0)
    val payments=BondCalendar.calculate(bonds.coupons,trades,bonds.tradeDates,income,bonds.receipts,ZoneOffset.UTC)
    val results=listOf(BondSearchResult(reference.uid,reference.figi,reference.isin,reference.ticker,reference.name,reference.classCode),
        BondSearchResult("demo-floating","DEMO-2","RU000DEMO002","SU29025","ОФЗ 29025 · пример","TQOB"))
}

@Composable internal fun BondCaptureScreen(mode: String) {
    Box(Modifier.fillMaxSize().premiumBackdrop().safeDrawingPadding(),contentAlignment=Alignment.TopCenter) {
        Box(Modifier.widthIn(max=720.dp).fillMaxWidth().padding(top=8.dp)) {
            when (mode) {
                "bond-search" -> BondSearchContent(BondBrowserState(results=BondCaptureFixtures.results,searched=true),true,null,
                    {},{},{},{},{},{})
                "bond-reference" -> BondSearchContent(BondBrowserState(selected=BondCaptureFixtures.download),true,null,
                    {},{},{},{},{},{})
                else -> BondCalendarContent(BondCaptureFixtures.dashboard,BondCaptureFixtures.bonds,BondCaptureFixtures.payments,
                    null,{}, {}, initialFilter=when(mode) { "bond-received" -> 1; "bond-schedule" -> 2; else -> 0 },today=BondCaptureFixtures.today)
            }
        }
    }
}
