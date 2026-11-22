package com.mamoro.investments.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mamoro.investments.data.*
import com.mamoro.investments.domain.MoneyMath
import com.mamoro.investments.ui.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable internal fun BondAssetSection(controller: BondController, asset: SecurityEntity,
    onFind: () -> Unit, onCalendar: () -> Unit, onSettlements: () -> Unit) {
    val state by controller.state.collectAsState()
    val refreshing by controller.refreshing.collectAsState()
    val cached=state.references.firstOrNull { it.securityId == asset.id }
    if (cached == null) {
        GlassCard(Modifier.fillMaxWidth()) {
            Text("Расписание облигации",fontWeight=FontWeight.SemiBold,fontSize=17.sp,lineHeight=23.sp)
            Text("Найдите выпуск по названию, тикеру или ISIN. Приложение загрузит сведения об облигации и даты купонов.",color=SoftText,fontSize=13.sp,lineHeight=18.sp,modifier=Modifier.padding(vertical=10.dp))
            GlassActionButton("Найти в Т‑Инвестициях",onFind,Modifier.fillMaxWidth(),icon=Icons.Outlined.Search)
        }
    } else {
        BondMetadata(cached.reference)
        GlassCard(Modifier.fillMaxWidth(),compact=true) {
            Text("Расписание сохранено",fontWeight=FontWeight.SemiBold,fontSize=16.sp,lineHeight=22.sp)
            Text("${bondDate(cached.fromDay)} — ${bondDate(cached.throughDay)}",color=SoftText,fontSize=12.sp,lineHeight=17.sp)
            Text("Загружено: " + Instant.ofEpochMilli(cached.reference.loadedAt).atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")),color=SoftText,fontSize=11.sp,lineHeight=16.sp)
            if (cached.reference.perpetual || cached.reference.maturityDay == null) Text("Для бессрочных выпусков горизонт загрузки — 5 лет. Будущие необъявленные купоны уточняются при обновлении.",color=SoftText,fontSize=12.sp,lineHeight=17.sp,modifier=Modifier.padding(top=8.dp))
            Spacer(Modifier.height(12.dp))
            GlassActionButton("Календарь выплат",onCalendar,Modifier.fillMaxWidth(),icon=Icons.Outlined.CalendarMonth)
            TextButton(onClick={ controller.refresh(asset.id) },enabled=asset.id !in refreshing) {
                Text(if (asset.id in refreshing) "Обновление…" else "Обновить из Т‑Инвестиций")
            }
        }
    }
    TextButton(onClick=onSettlements,modifier=Modifier.fillMaxWidth()) { Text("Даты расчётов по сделкам") }
}

@Composable internal fun BondSettlementSheet(controller: BondController, dashboard: InvestmentsRepository.DashboardState,
    securityId: String, onClose: () -> Unit) {
    val state by controller.state.collectAsState()
    val dates=state.tradeDates.associateBy { it.tradeId }
    val trades=dashboard.trades.filter { it.securityId == securityId && !it.deleted }.sortedByDescending { it.happenedAt }
    val scope=rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf<String?>(null) }
    Column(Modifier.widthIn(max=720.dp).fillMaxWidth().heightIn(max=760.dp).verticalScroll(rememberScrollState())
        .padding(horizontal=18.dp).padding(bottom=32.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Text("Даты расчётов",fontSize=23.sp,lineHeight=29.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
            IconButton(onClick=onClose) { Icon(Icons.Outlined.Close,"Закрыть даты расчётов") }
        }
        Text("Укажите даты исполнения из отчёта брокера. По ним определяется количество для купона. Если дата неизвестна, используется дата сделки и прогноз помечается как оценка.",color=SoftText,fontSize=13.sp,lineHeight=18.sp)
        if (trades.isEmpty()) Text("Сделок с этой облигацией пока нет",color=SoftText)
        trades.forEach { trade ->
            val execution=dates[trade.id]?.executionDay ?: Instant.ofEpochMilli(trade.happenedAt).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
            GlassCard(Modifier.fillMaxWidth(),compact=true) {
                Text("${if (trade.side == "BUY") "Покупка" else "Продажа"} · ${MoneyMath.formatQuantity(trade.quantityMicros)} шт.",fontWeight=FontWeight.SemiBold)
                Text("Сделка: ${bondDate(execution)}",color=SoftText,fontSize=12.sp,lineHeight=17.sp)
                Spacer(Modifier.height(10.dp))
                if (saving == trade.id) LinearProgressIndicator(color=Orange,modifier=Modifier.fillMaxWidth())
                else BondDateField("Дата расчётов",dates[trade.id]?.settlementDay,{ day ->
                    if (saving == null) {
                        saving=trade.id; error=null
                        scope.launch {
                            try { controller.settlement(trade.id,day) }
                            catch (e: CancellationException) { throw e }
                            catch (e: Exception) { error=e.message ?: "Не удалось сохранить дату" }
                            finally { saving=null }
                        }
                    }
                },minDay=execution)
            }
        }
        error?.let { Text(it,color=Red) }
    }
}
