package com.mamoro.investments.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.mamoro.investments.data.InvestmentsRepository
import com.mamoro.investments.domain.MoneyMath
import com.mamoro.investments.domain.PortfolioMath
import com.mamoro.investments.ui.theme.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.*

/** Actual observations, supplied by a history source. Production currently has none. */
internal data class PortfolioHistoryPoint(val time: Long, val valueMinor: Long)
internal val dataCategories = listOf("Акции", "Облигации", "Фонды", "Дивиденды", "Купоны")
internal fun dataMoney(value: Long) = (if (value >= 0L) "+" else "−") + MoneyMath.formatMoney(abs(value))
internal fun dataCategoryValues(summary: PortfolioMath.Summary) = listOf(
    summary.positions.filter { it.security.kind == "STOCK" }.sumOf { it.currentValueMinor },
    summary.positions.filter { it.security.kind == "BOND" }.sumOf { it.currentValueMinor },
    summary.positions.filter { it.security.kind == "FUND" }.sumOf { it.currentValueMinor },
    summary.dividendsMinor, summary.couponsMinor
)

@Composable
internal fun PrecisePhonePortfolio(
    state: InvestmentsRepository.DashboardState, history: List<PortfolioHistoryPoint>,
    onCategory: () -> Unit, onQuick: () -> Unit, onAdd: () -> Unit
) {
    var period by rememberSaveable { mutableStateOf("1М") }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 4.dp, end = 4.dp, top = 5.dp, bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { DataPortfolioHero(state.summary, history, period, { period = it }) }
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).glassSurface()) {
                dataCategoryValues(state.summary).forEachIndexed { index, value ->
                    DataCategoryRow(dataCategories[index], value, onCategory)
                    if (index < 4) HorizontalDivider(Modifier.padding(start = 68.dp, end = 12.dp), .7.dp, DataRule)
                }
            }
        }
        item {
            GlassActionButton("Быстрое действие", onQuick,
                Modifier.fillMaxWidth().padding(horizontal = 8.dp).padding(top = 6.dp))
        }
        if (state.securities.isEmpty()) item {
            TextButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) { Text("Добавить первую бумагу", color = Orange) }
        }
    }
}

@Composable
private fun DataCategoryRow(title: String, value: Long, onClick: () -> Unit) {
    val large = LocalDensity.current.fontScale > 1.2f
    Row(Modifier.fillMaxWidth().heightIn(min = 49.5.dp).glassClick(onClick)
        .padding(start = 20.dp, end = 10.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        DataGlyph(title, Modifier.size(28.dp))
        Spacer(Modifier.width(18.dp))
        if (large) Column(Modifier.weight(1f)) {
            Text(title, fontSize = 17.sp, color = StrongText)
            Text(MoneyMath.formatMoney(value), fontSize = 17.sp, fontWeight = FontWeight.Medium)
        } else {
            Text(title, modifier = Modifier.weight(1f), fontSize = 17.sp, color = StrongText)
            Text(MoneyMath.formatMoney(value), fontSize = 17.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.width(7.dp))
        Icon(Icons.Outlined.ChevronRight, null, tint = SoftText, modifier = Modifier.size(20.dp))
    }
}

@Composable
internal fun DataPortfolioHero(
    summary: PortfolioMath.Summary, history: List<PortfolioHistoryPoint>, period: String, onPeriod: (String) -> Unit,
    modifier: Modifier = Modifier, tablet: Boolean = false, onQuick: (() -> Unit)? = null
) {
    val observations = remember(history) { history.sortedBy { it.time } }
    val visible = remember(observations, period) {
        val days = when (period) { "1М" -> 31; "3М" -> 93; "1Г" -> 366; else -> null }
        if (days == null || observations.isEmpty()) observations
        else observations.filter { it.time >= observations.last().time - days * 86_400_000L }
    }
    val change = if (observations.size >= 2) observations.last().valueMinor - observations[observations.lastIndex-1].valueMinor
        else summary.unrealizedMinor
    val prior = observations.getOrNull(observations.lastIndex-1)?.valueMinor
    val percent = if (prior != null && prior > 0L) String.format(Locale.forLanguageTag("ru-RU"), "%+.2f%%", change.toDouble()*100/prior) else null
    val profit = summary.unrealizedMinor + summary.realizedMinor + summary.dividendsMinor + summary.couponsMinor
    val large = LocalDensity.current.fontScale > 1.2f
    Column(modifier.fillMaxWidth().heightIn(min = if (tablet) 350.dp else 284.dp)
        .clip(RoundedCornerShape(14.dp)).glassSurface().padding(start = if (tablet) 22.dp else 18.dp,
            end = if (tablet) 20.dp else 12.dp, top = 14.dp, bottom = 4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text("Стоимость портфеля", color = SoftText, fontSize = 17.sp, lineHeight = 22.sp)
                Spacer(Modifier.height(3.dp))
                AnimatedContent(summary.marketValueMinor, label = "data-portfolio-total") { value ->
                    Text(MoneyMath.formatMoney(value), color = StrongText, fontSize = if (tablet) 42.sp else 46.sp,
                        lineHeight = 52.sp, fontWeight = FontWeight.Bold)
                }
                Text(dataMoney(change) + if (percent != null) " · $percent" else " по позициям",
                    color = if (change >= 0) Green else Red, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                Text("Прибыль ${dataMoney(profit)}", color = SoftText, fontSize = 16.sp, lineHeight = 21.sp)
            }
            if (tablet && onQuick != null && !large) {
                Spacer(Modifier.width(12.dp))
                GlassActionButton("Быстрое действие", onQuick, Modifier.padding(top = 20.dp))
            }
        }
        if (tablet) {
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                DataPeriodSelector(period, onPeriod, Modifier.widthIn(max = 270.dp).fillMaxWidth())
            }
        }
        DataHistoryChart(visible, Modifier.fillMaxWidth().then(if (tablet) Modifier.weight(1f).heightIn(min = 148.dp) else Modifier.height(92.dp)), tablet)
        if (!tablet) DataPeriodSelector(period, onPeriod, Modifier.fillMaxWidth())
        if (tablet && onQuick != null && large) GlassActionButton("Быстрое действие", onQuick, Modifier.fillMaxWidth().padding(vertical = 8.dp))
    }
}

@Composable
internal fun DataPeriodSelector(period: String, onPeriod: (String) -> Unit, modifier: Modifier = Modifier) {
    val periods = listOf("1М", "3М", "1Г", "Все")
    val large = LocalDensity.current.fontScale > 1.2f
    BoxWithConstraints(modifier.height(if (large) 48.dp else 40.dp).testTag("history-periods")) {
        val cell = maxWidth / 4
        val offset by animateDpAsState(cell * periods.indexOf(period).coerceAtLeast(0), tween(160), label = "history-period")
        Box(Modifier.fillMaxSize().padding(vertical = 4.dp).clip(RoundedCornerShape(8.dp)).border(.8.dp, DataRule, RoundedCornerShape(8.dp)))
        Box(Modifier.offset(x = offset).width(cell).fillMaxHeight().padding(vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp)).background(Orange))
        Row(Modifier.fillMaxSize()) {
            periods.forEach { item ->
                Box(Modifier.weight(1f).fillMaxHeight().semantics { selected = period == item }
                    .glassClick { onPeriod(item) }, contentAlignment = Alignment.Center) {
                    Text(item, fontSize = 14.sp, lineHeight = 18.sp,
                        fontWeight = if (item == period) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (item == period) Ink else SoftText)
                }
            }
        }
    }
}

@Composable
internal fun DataHistoryChart(points: List<PortfolioHistoryPoint>, modifier: Modifier, axes: Boolean) {
    if (points.size < 2) {
        Box(modifier.semantics { contentDescription = "История стоимости портфеля пока недоступна" }, contentAlignment = Alignment.Center) {
            Text("История стоимости пока недоступна", color = SoftText, fontSize = 12.sp, lineHeight = 18.sp,
                textAlign = TextAlign.Center, modifier = Modifier.padding(12.dp))
        }
        return
    }
    val minValue = points.minOf { it.valueMinor }.toDouble()
    val maxValue = points.maxOf { it.valueMinor }.toDouble()
    val range = (maxValue-minValue).coerceAtLeast(maxValue*.005).coerceAtLeast(1.0)
    val magnitude = 10.0.pow(floor(log10(range / 3)))
    val step = (ceil(range / 3 / magnitude) * magnitude).coerceAtLeast(1.0)
    val low = (floor((minValue-range*.025)/step)*step).coerceAtLeast(0.0)
    val high = (ceil((maxValue+range*.05)/step)*step).coerceAtLeast(low+step)
    val date = remember { DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("ru-RU")) }
    Column(modifier.padding(top = if (axes) 4.dp else 8.dp).semantics { contentDescription = "История стоимости: ${points.size} наблюдений" }) {
        Row(Modifier.weight(1f)) {
            if (axes) Column(Modifier.width(76.dp).fillMaxHeight().padding(end = 8.dp), verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End) {
                for (i in 4 downTo 0) Text(MoneyMath.formatMoney((low+(high-low)*i/4).roundToLong()).removeSuffix(" ₽"),
                    color = SoftText, fontSize = 12.sp, lineHeight = 14.sp, maxLines = 1)
            }
            Canvas(Modifier.weight(1f).fillMaxHeight().drawWithCache {
                val first = points.first().time
                val duration = (points.last().time-first).coerceAtLeast(1L).toDouble()
                val line = Path()
                points.forEachIndexed { index, point ->
                    val x = ((point.time-first)/duration).toFloat()*size.width
                    val y = ((high-point.valueMinor)/(high-low)).toFloat()*size.height
                    if (index == 0) line.moveTo(x,y) else line.lineTo(x,y)
                }
                val area = Path().apply { addPath(line); lineTo(size.width,size.height); lineTo(0f,size.height); close() }
                val fill = Brush.verticalGradient(listOf(Orange.copy(alpha = .25f), Orange.copy(alpha = .015f)))
                onDrawBehind {
                    if (axes) {
                        repeat(5) { i ->
                            val y = size.height*i/4
                            val x = size.width*i/4
                            drawLine(DataRule.copy(alpha = .8f), Offset(0f,y), Offset(size.width,y), .7.dp.toPx())
                            drawLine(DataRule.copy(alpha = .65f), Offset(x,0f), Offset(x,size.height), .7.dp.toPx())
                        }
                    }
                    drawPath(area, fill)
                    drawPath(line, Orange, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }) {}
        }
        if (axes) Row(Modifier.fillMaxWidth().padding(start = 76.dp, top = 6.dp, bottom = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            repeat(5) { i ->
                val item = points[(points.lastIndex*i/4.0).roundToInt()]
                Text(date.format(Instant.ofEpochMilli(item.time).atZone(ZoneId.systemDefault())).removeSuffix("."),
                    color = SoftText, fontSize = 12.sp, lineHeight = 16.sp)
            }
        }
    }
}

@Composable
internal fun DataGlyph(kind: String, modifier: Modifier = Modifier.size(26.dp), tint: Color = StrongText) {
    val icon = when (kind) {
        "Портфель" -> Icons.Outlined.WorkOutline
        "Активы", "Фонды" -> Icons.Outlined.Layers
        "Операции" -> Icons.Outlined.SwapHoriz
        "Аналитика", "Акции" -> null
        "Ещё" -> Icons.Outlined.MoreHoriz
        "Облигации" -> Icons.Outlined.CreditCard
        "Купоны" -> Icons.Outlined.CalendarToday
        else -> null
    }
    if (icon != null) Icon(icon, null, tint = tint, modifier = modifier)
    else Canvas(modifier) {
        val u = size.width/28
        if (kind == "Акции" || kind == "Аналитика") {
            listOf(9f,17f,25f).forEachIndexed { index,height ->
                drawLine(tint, Offset((5f+index*9)*u,(27-height)*u), Offset((5f+index*9)*u,27*u),1.8f*u,StrokeCap.Round)
            }
            return@Canvas
        }
        val stroke = Stroke(1.5f*u, cap = StrokeCap.Round)
        listOf(17f,12f,7f).forEach { y -> drawOval(tint, Offset(3*u,y*u),Size(16*u,6*u),style = stroke) }
        drawLine(tint,Offset(3*u,10*u),Offset(3*u,20*u),1.5f*u)
        drawLine(tint,Offset(19*u,10*u),Offset(19*u,20*u),1.5f*u)
        listOf(10f,5f).forEach { y -> drawOval(tint,Offset(13*u,y*u),Size(12*u,5*u),style = stroke) }
    }
}
