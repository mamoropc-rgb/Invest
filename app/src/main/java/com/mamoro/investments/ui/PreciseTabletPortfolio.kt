package com.mamoro.investments.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.mamoro.investments.data.InvestmentsRepository
import com.mamoro.investments.domain.MoneyMath
import com.mamoro.investments.domain.PortfolioMath
import com.mamoro.investments.ui.theme.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** All page state is outside the size-dependent branches, including the history period. */
@Composable internal fun PrecisePortfolioPage(
    state: InvestmentsRepository.DashboardState, history: List<PortfolioHistoryPoint>, tablet: Boolean,
    onCategory: () -> Unit, onQuick: () -> Unit, onAdd: () -> Unit,
    onSecurity: (String) -> Unit, onOperations: () -> Unit
) {
    var period by rememberSaveable { mutableStateOf("1М") }
    val phoneScroll = rememberLazyListState()
    val tabletScroll = rememberScrollState()
    if (tablet) PreciseTabletPortfolio(state, history, period, { period = it }, tabletScroll,
        onQuick, onSecurity, onCategory, onOperations)
    else PrecisePhonePortfolio(state, history, onCategory, onQuick, onAdd, period, { period = it }, phoneScroll)
}

@Composable internal fun PreciseNavigationRail(selectedPage: Int, onSelect: (Int) -> Unit) {
    val railWidth = if (LocalDensity.current.fontScale > 1.2f) 264.dp else 176.dp
    Column(Modifier.width(railWidth).fillMaxHeight().background(Color(0xFF080F13)).testTag("tablet-navigation")
        .drawWithCache {
            onDrawBehind { drawLine(DataRule.copy(alpha = .55f), Offset(size.width,0f), Offset(size.width,size.height), .7.dp.toPx()) }
        }.verticalScroll(rememberScrollState()).padding(top = 24.dp)) {
        Text("Инвестиции", color = StrongText, fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 14.dp))
        Spacer(Modifier.height(26.dp))
        listOf("Портфель", "Активы", "Операции", "Аналитика", "Ещё").forEachIndexed { index, label ->
            val active = selectedPage == index
            val highlight by animateColorAsState(if (active) Color(0xFF4C3517) else Color.Transparent,tween(150),label = "rail-highlight")
            val tint by animateColorAsState(if (active) Orange else StrongText,tween(150),label = "rail-tint")
            Row(Modifier.padding(horizontal = 7.dp).fillMaxWidth().heightIn(min = 48.dp)
                .clip(RoundedCornerShape(10.dp)).background(highlight)
                .semantics { selected = active }.glassClick { onSelect(index) }
                .padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                DataGlyph(label, Modifier.size(26.dp), tint)
                Spacer(Modifier.width(16.dp))
                Text(label, color = tint, fontSize = 17.sp, lineHeight = 22.sp)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable private fun PreciseTabletPortfolio(
    state: InvestmentsRepository.DashboardState, history: List<PortfolioHistoryPoint>, period: String,
    onPeriod: (String) -> Unit, scroll: ScrollState, onQuick: () -> Unit,
    onSecurity: (String) -> Unit, onCategory: () -> Unit, onOperations: () -> Unit
) {
    val large = LocalDensity.current.fontScale > 1.2f
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 900.dp && maxHeight >= 700.dp && !large
        if (wide) Column(Modifier.fillMaxSize().padding(start = 15.dp, end = 6.dp, top = 24.dp, bottom = 4.dp)) {
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(Modifier.weight(1.55f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    DataPortfolioHero(state.summary, history, period, onPeriod, Modifier.weight(.65f), true, onQuick)
                    DataHoldings(state.summary, onSecurity, onCategory, Modifier.weight(.35f), scrollable = true)
                }
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    DataAllocation(state.summary, Modifier.height(266.dp))
                    DataRecentOperations(state, onOperations)
                }
            }
            Spacer(Modifier.height(16.dp))
            DataCategoryStrip(state.summary, onCategory)
        } else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 760.dp).fillMaxWidth().verticalScroll(scroll)
                .padding(horizontal = 16.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                DataPortfolioHero(state.summary, history, period, onPeriod, Modifier.heightIn(min = if (large) 440.dp else 380.dp), true, onQuick)
                DataHoldings(state.summary, onSecurity, onCategory, Modifier.heightIn(min = 208.dp))
                DataAllocation(state.summary, Modifier.heightIn(min = 266.dp))
                DataRecentOperations(state, onOperations)
                DataCategoryStrip(state.summary, onCategory)
            }
        }
    }
}

@Composable private fun DataPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).glassSurface(), content = content)
}

@Composable private fun DataHoldings(summary: PortfolioMath.Summary, onSecurity: (String) -> Unit,
    onAll: () -> Unit, modifier: Modifier, scrollable: Boolean = false) {
    val large = LocalDensity.current.fontScale > 1.2f
    val positions = summary.positions.filter { !it.security.archived && it.quantityMicros > 0 }.take(3)
    DataPanel(modifier.testTag("portfolio-holdings")) {
        // Only the bounded landscape table scrolls independently. Portrait uses the page scroll.
        val tableScroll = rememberScrollState()
        Column(Modifier.fillMaxWidth().then(if (scrollable) Modifier.verticalScroll(tableScroll) else Modifier)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 40.dp).glassClick(onAll).padding(start = 20.dp, end = 48.dp, top = 8.dp, bottom = 8.dp)) {
                Text(if (large) "Актив · стоимость" else "Актив", Modifier.weight(.5f), fontSize = 16.sp, color = StrongText)
                if (!large) Text("Стоимость", Modifier.weight(.22f), fontSize = 16.sp, color = StrongText)
                Text("Доходность", Modifier.weight(.28f), fontSize = 16.sp, color = StrongText, textAlign = androidx.compose.ui.text.style.TextAlign.End)
            }
            HorizontalDivider(thickness = .7.dp, color = DataRule)
            if (positions.isEmpty()) Text("Открытых позиций пока нет", color = SoftText, modifier = Modifier.padding(20.dp))
            positions.forEachIndexed { index, position ->
                Row(Modifier.fillMaxWidth().heightIn(min = 55.dp).glassClick { onSecurity(position.security.id) }
                    .padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(.5f), verticalAlignment = Alignment.CenterVertically) {
                        SecurityMark(position.security.ticker, position.security.kind, Modifier.size(36.dp))
                        Spacer(Modifier.width(24.dp))
                        Column(Modifier.weight(1f)) {
                            Text(position.security.name, fontSize = 17.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold)
                            Text(when (position.security.kind) { "BOND" -> "Облигации"; "FUND" -> "Фонды · ${position.security.ticker}"; else -> "Акции · ${position.security.ticker}" },
                                fontSize = 12.sp, lineHeight = 16.sp, color = SoftText)
                            if (large) Text(MoneyMath.formatMoney(position.currentValueMinor), fontSize = 16.sp)
                        }
                    }
                    if (!large) Text(MoneyMath.formatMoney(position.currentValueMinor), Modifier.weight(.22f), fontSize = 17.sp,
                        fontWeight = FontWeight.Medium)
                    val percent = if (position.costMinor > 0) String.format(Locale.forLanguageTag("ru-RU"), "%+.2f%%",
                        position.unrealizedMinor.toDouble()*100/position.costMinor) else ""
                    Text(dataMoney(position.unrealizedMinor)+if (percent.isNotEmpty()) " · $percent" else "",
                        Modifier.weight(.28f), fontSize = 14.sp, lineHeight = 19.sp,
                        color = if (position.unrealizedMinor >= 0) Green else Red, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Outlined.ChevronRight, null, tint = SoftText, modifier = Modifier.size(20.dp))
                }
                if (index != positions.lastIndex) HorizontalDivider(Modifier.padding(horizontal = 18.dp), .7.dp, DataRule)
            }
        }
    }
}

@Composable internal fun SecurityMark(ticker: String, kind: String, modifier: Modifier = Modifier.size(36.dp)) {
    val tone = when (ticker) { "LKOH" -> Color(0xFFC6292B); "SBER" -> Color(0xFF228C35); "GAZP" -> Color(0xFF1477CE); else -> Frost }
    Box(modifier.clip(RoundedCornerShape(5.dp)).background(tone), contentAlignment = Alignment.Center) {
        val key = when {
            ticker in listOf("SBER","LKOH","GAZP") -> ticker
            ticker.startsWith("SU") && kind == "BOND" -> "OFZ"
            else -> null
        }
        if (key != null) ReferenceSecurityGlyph(key,Modifier.fillMaxSize())
        else if (kind == "BOND") Icon(Icons.Outlined.AccountBalance,null,tint = StrongText,modifier = Modifier.fillMaxSize().padding(3.dp))
        else Text(ticker.take(2),color = StrongText,fontSize = 13.sp,fontWeight = FontWeight.SemiBold)
    }
}

@Composable private fun DataAllocation(summary: PortfolioMath.Summary, modifier: Modifier = Modifier) {
    val values = dataCategoryValues(summary)
    val palette = listOf(Color(0xFF4A94F2),Color(0xFF6ADA59),Color(0xFFC48FF1),Color(0xFFFFD756),Color(0xFFF797C4))
    val invested = values.take(3).sum()
    DataPanel(modifier) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp)) {
            Text("Структура портфеля", fontSize = 21.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(22.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(180.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize().padding(12.dp)) {
                        var start = -90f
                        if (invested <= 0L) drawArc(DataRule,0f,360f,false,style = Stroke(24.dp.toPx()))
                        else values.take(3).forEachIndexed { i,value ->
                            val sweep = (value.toDouble()/invested*360).toFloat()
                            if (sweep > 0f) drawArc(palette[i],start+.5f,(sweep-1f).coerceAtLeast(0f),false,style = Stroke(24.dp.toPx()))
                            start+=sweep
                        }
                    }
                    Text(MoneyMath.formatMoney(summary.marketValueMinor), fontSize = 17.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(20.dp))
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(15.dp)) {
                    values.forEachIndexed { i,value ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(9.dp).clip(RoundedCornerShape(5.dp)).background(if (i < 3) palette[i] else Color.Transparent))
                            Spacer(Modifier.width(7.dp))
                            Text(dataCategories[i], Modifier.weight(1f), fontSize = 14.sp, lineHeight = 20.sp)
                            Spacer(Modifier.width(3.dp))
                            Text(if (i < 3) String.format(Locale.forLanguageTag("ru-RU"), "%.1f %%", if (invested>0) value.toDouble()*100/invested else 0.0)
                                else MoneyMath.formatMoney(value), fontSize = if (i < 3) 15.sp else 12.sp, lineHeight = 20.sp,
                                color = if (i < 3) StrongText else SoftText,
                                modifier = Modifier.semantics { if (i >= 3) contentDescription = "Получено: ${MoneyMath.formatMoney(value)}" })
                        }
                    }
                }
            }
        }
    }
}

private data class RecentDataOperation(val securityId: String, val title: String, val time: Long, val delta: Long)
@Composable private fun DataRecentOperations(state: InvestmentsRepository.DashboardState, onOperations: () -> Unit) {
    val securities = state.securities.associateBy { it.id }
    val operations = remember(state.trades,state.income) {
        (state.trades.filterNot { it.deleted }.map { item ->
            val gross = MoneyMath.totalMinor(item.unitPriceMinor,item.quantityMicros)
            RecentDataOperation(item.securityId,if (item.side == "BUY") "Покупка" else "Продажа",item.happenedAt,
                if (item.side == "BUY") -(gross+item.feeMinor) else gross-item.feeMinor)
        } + state.income.filterNot { it.deleted }.map { item ->
            RecentDataOperation(item.securityId,if (item.kind == "DIVIDEND") "Дивиденды" else "Купон",item.happenedAt,item.amountMinor)
        }).sortedByDescending { it.time }.take(2)
    }
    val date = remember { DateTimeFormatter.ofPattern("d MMM, HH:mm",Locale.forLanguageTag("ru-RU")) }
    DataPanel(Modifier.testTag("recent-operations")) {
        Text("Последние операции", fontSize = 21.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 13.dp))
        HorizontalDivider(thickness = .7.dp, color = DataRule)
        if (operations.isEmpty()) Text("Операций пока нет", color = SoftText, modifier = Modifier.padding(18.dp))
        operations.forEach { operation ->
            val security = securities[operation.securityId]
            Row(Modifier.fillMaxWidth().heightIn(min = 73.dp).glassClick(onOperations).padding(start = 24.dp,end = 14.dp,top = 12.dp,bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                SecurityMark(security?.ticker.orEmpty(),security?.kind.orEmpty(),Modifier.size(36.dp))
                Spacer(Modifier.width(24.dp))
                Column(Modifier.weight(1f)) {
                    Text("${operation.title} · ${security?.name ?: "Бумага"}", fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold)
                    Text(date.format(Instant.ofEpochMilli(operation.time).atZone(ZoneId.systemDefault())).replace(".,",","),
                        fontSize = 13.sp, lineHeight = 18.sp,color = SoftText)
                }
                Spacer(Modifier.width(8.dp))
                Text(dataMoney(operation.delta),fontSize = 17.sp,fontWeight = FontWeight.Medium,color = if (operation.delta>0) Green else StrongText)
            }
            HorizontalDivider(thickness = .7.dp, color = DataRule)
        }
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).glassClick(onOperations).padding(horizontal = 18.dp,vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("Все операции",fontSize = 16.sp,color = Orange,modifier = Modifier.weight(1f))
            Icon(Icons.Outlined.ChevronRight,null,tint = Orange,modifier = Modifier.size(24.dp))
        }
    }
}

@Composable private fun DataCategoryStrip(summary: PortfolioMath.Summary,onCategory: () -> Unit) {
    val values = dataCategoryValues(summary)
    BoxWithConstraints(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).glassSurface()) {
        val split = maxWidth < 720.dp || LocalDensity.current.fontScale > 1.2f
        Column(Modifier.fillMaxWidth()) {
            val groups = if (split) listOf((0..2).toList(),(3..4).toList()) else listOf((0..4).toList())
            groups.forEachIndexed { groupIndex, indices ->
                Row(Modifier.fillMaxWidth(),verticalAlignment = Alignment.CenterVertically) {
                    indices.forEachIndexed { cell,index ->
                        Column(Modifier.weight(1f).heightIn(min = 110.dp).glassClick(onCategory).padding(horizontal = 6.dp,vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,verticalArrangement = Arrangement.Center) {
                            DataGlyph(dataCategories[index],Modifier.size(28.dp))
                            Spacer(Modifier.height(6.dp))
                            Text(dataCategories[index],fontSize = 16.sp,lineHeight = 21.sp)
                            Spacer(Modifier.height(5.dp))
                            Text(MoneyMath.formatMoney(values[index]),fontSize = 17.sp,lineHeight = 22.sp,fontWeight = FontWeight.Medium)
                        }
                        if (cell != indices.lastIndex) VerticalDivider(Modifier.height(80.dp),.7.dp,DataRule)
                    }
                }
                if (groupIndex != groups.lastIndex) HorizontalDivider(thickness = .7.dp,color = DataRule)
            }
        }
    }
}
