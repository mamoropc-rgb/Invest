@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.mamoro.investments.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mamoro.investments.data.*
import com.mamoro.investments.domain.BondCalendar
import com.mamoro.investments.domain.MoneyMath
import com.mamoro.investments.ui.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable internal fun BondCalendarSheet(controller: BondController, dashboard: InvestmentsRepository.DashboardState,
    securityId: String?, onClose: () -> Unit) {
    val bonds by controller.state.collectAsState()
    val payments by controller.payments.collectAsState()
    var confirming by remember { mutableStateOf<BondCalendar.Payment?>(null) }
    BondCalendarContent(dashboard,bonds,payments,securityId,onClose,{ confirming=it })
    confirming?.let { payment -> CouponReceiptDialog(controller,dashboard,bonds,payment) { confirming=null } }
}

@Composable internal fun BondCalendarContent(dashboard: InvestmentsRepository.DashboardState, bonds: BondState,
    payments: List<BondCalendar.Payment>, securityId: String?, onClose: () -> Unit,
    onConfirm: (BondCalendar.Payment) -> Unit, initialFilter: Int = 0, today: Long = LocalDate.now().toEpochDay()) {
    var filter by rememberSaveable(securityId) { mutableIntStateOf(initialFilter) }
    val names = remember(dashboard.securities) { dashboard.securities.associateBy { it.id } }
    val own = remember(payments,securityId) { payments.filter { securityId == null || it.securityId == securityId } }
    val received = remember(dashboard.income,securityId) { dashboard.income.filter {
        !it.deleted && it.kind == "COUPON" && (securityId == null || it.securityId == securityId) }.sortedByDescending { it.happenedAt } }
    val upcoming = own.filter { it.received == null && it.sourceActive && it.quantityMicros > 0 }
    val displayed = if (filter == 0) upcoming else own.filter { it.sourceActive || it.received != null }
    val linked = bonds.references.filter { securityId == null || it.securityId == securityId }
    LazyColumn(Modifier.widthIn(max=720.dp).fillMaxWidth().heightIn(max=820.dp).testTag("bond-calendar-list"),
        contentPadding=PaddingValues(start=18.dp,end=18.dp,bottom=32.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Календарь купонов",fontWeight=FontWeight.Bold,fontSize=23.sp,lineHeight=29.sp)
                    Text(securityId?.let { names[it]?.name } ?: "Облигации портфеля",color=SoftText,fontSize=13.sp,lineHeight=18.sp)
                }
                IconButton(onClick=onClose) { Icon(Icons.Outlined.Close,"Закрыть календарь") }
            }
        }
        item { CouponMonthChart(upcoming,today) }
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                listOf("Ожидаемые","Полученные","Расписание").forEachIndexed { index,title ->
                    PremiumChip(filter == index,title) { filter=index }
                }
            }
        }
        item { Text(when (filter) {
            0 -> "Прогноз до налогов по количеству на дату фиксации. Прошедшая дата выплаты сама не создаёт доход."
            1 -> "Фактические операции купонного дохода, включая ранее введённые вручную."
            else -> "Расписание эмитента. Количество может быть нулевым, если на дату фиксации бумаги не было в портфеле."
        },color=SoftText,fontSize=12.sp,lineHeight=17.sp) }
        if (filter == 1) {
            if (received.isEmpty()) item { CalendarEmpty("Полученных купонов пока нет","Подтвердите выплату, когда деньги поступят, или добавьте купон вручную.") }
            items(received,key={ "income/${it.id}" }) { income ->
                GlassCard(Modifier.fillMaxWidth(),compact=true) {
                    Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Outlined.Payments,null,tint=Green,modifier=Modifier.size(24.dp))
                        Column(Modifier.weight(1f)) {
                            Text(names[income.securityId]?.name ?: "Купон",fontWeight=FontWeight.SemiBold)
                            Text(bondDate(Instant.ofEpochMilli(income.happenedAt).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()),color=SoftText,fontSize=12.sp,lineHeight=17.sp)
                            if (income.note.isNotBlank()) Text(income.note,color=SoftText,fontSize=12.sp,lineHeight=17.sp)
                        }
                        Text(MoneyMath.formatMoney(income.amountMinor),color=Green,fontWeight=FontWeight.SemiBold)
                    }
                }
            }
        } else {
            if (displayed.isEmpty()) item { CalendarEmpty(if (linked.isEmpty()) "Подключите облигацию" else "Нет выплат для показа",
                if (linked.isEmpty()) "Найдите выпуск в Т‑Инвестициях при добавлении бумаги или подключите расписание из карточки актива."
                else "Прогноз появится после покупки с учётом дат расчётов. Полный график доступен во вкладке «Расписание».") }
            items(displayed,key={ "coupon/${it.securityId}/${it.coupon.eventKey}" }) { payment ->
                CouponPaymentRow(payment,names[payment.securityId]?.name ?: "Облигация",today,onConfirm)
            }
        }
        if (linked.isNotEmpty()) item {
            val oldest=linked.minOf { it.reference.loadedAt }
            Text("Источник: Т‑Инвестиции · Последняя загрузка: " +
                Instant.ofEpochMilli(oldest).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")) +
                ". Обновление доступно в карточке облигации.",color=SoftText,fontSize=11.sp,lineHeight=16.sp)
        }
    }
}

@Composable private fun CalendarEmpty(title: String, description: String) {
    GlassCard(Modifier.fillMaxWidth()) {
        Icon(Icons.Outlined.EventNote,null,tint=Orange)
        Text(title,fontSize=17.sp,lineHeight=23.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(top=10.dp,bottom=6.dp))
        Text(description,color=SoftText,fontSize=13.sp,lineHeight=18.sp)
    }
}

@Composable private fun CouponMonthChart(payments: List<BondCalendar.Payment>, today: Long) {
    val start=YearMonth.from(LocalDate.ofEpochDay(today))
    val months=remember(start) { (0L..11L).map { start.plusMonths(it) } }
    val rows=payments.filter { it.coupon.payDay >= today && YearMonth.from(LocalDate.ofEpochDay(it.coupon.payDay)) in months }
    val buckets=months.map { month -> rows.filter { YearMonth.from(LocalDate.ofEpochDay(it.coupon.payDay)) == month }.sumOf { it.expectedMinor ?: 0 } }
    val total=buckets.sum()
    val unknown=rows.count { it.expectedMinor == null }
    GlassCard(Modifier.fillMaxWidth()) {
        Text("Прогноз на 12 месяцев",color=SoftText,fontSize=13.sp,lineHeight=18.sp)
        Text(if (total == 0L && unknown > 0) "Сумма уточняется" else MoneyMath.formatMoney(total),fontSize=28.sp,lineHeight=36.sp,fontWeight=FontWeight.Bold,
            modifier=Modifier.fillMaxWidth().testTag("coupon-forecast-total"))
        Text("До налогов · по сохранённому расписанию",color=SoftText,fontSize=11.sp,lineHeight=16.sp)
        Spacer(Modifier.height(16.dp))
        Canvas(Modifier.fillMaxWidth().height(88.dp).semantics { contentDescription="Прогноз купонов по месяцам" }) {
            val cell=size.width/12
            val maximum=(buckets.maxOrNull() ?: 0).coerceAtLeast(1)
            buckets.forEachIndexed { index,value ->
                val height=if (value > 0) (size.height-4.dp.toPx())*value.toFloat()/maximum else 2.dp.toPx()
                drawRoundRect(if (value > 0) Orange else DataRule,Offset(cell*index+cell*.20f,size.height-height),
                    Size(cell*.60f,height),CornerRadius(3.dp.toPx()))
            }
        }
        Row(Modifier.fillMaxWidth()) { months.forEach { month ->
            Text(month.format(DateTimeFormatter.ofPattern("LLL",Locale.forLanguageTag("ru"))).take(3),
                Modifier.weight(1f),color=SoftText,fontSize=9.sp,lineHeight=13.sp)
        } }
        if (unknown > 0) Text("Ещё $unknown выплат: размер купона пока не объявлен",color=OrangeLight,fontSize=12.sp,lineHeight=17.sp,modifier=Modifier.padding(top=10.dp))
        if (rows.any { it.estimated }) Text("Есть оценки: уточните даты фиксации и расчётов сделок",color=OrangeLight,fontSize=12.sp,lineHeight=17.sp,modifier=Modifier.padding(top=8.dp))
    }
}

@Composable private fun CouponPaymentRow(payment: BondCalendar.Payment, name: String, today: Long, onConfirm: (BondCalendar.Payment) -> Unit) {
    val coupon=payment.coupon
    GlassCard(Modifier.fillMaxWidth(),compact=true) {
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically) {
            Icon(Icons.Outlined.CalendarMonth,null,tint=Orange,modifier=Modifier.size(25.dp))
            Column(Modifier.weight(1f)) {
                Text(name,fontWeight=FontWeight.SemiBold,fontSize=15.sp,lineHeight=21.sp)
                Text("${bondDate(coupon.payDay)} · Купон №${coupon.number}",color=SoftText,fontSize=12.sp,lineHeight=17.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text("${MoneyMath.formatQuantity(payment.quantityMicros)} шт.",fontSize=13.sp,lineHeight=18.sp)
                Text("Фиксация: ${bondDate(coupon.fixDay)}",color=SoftText,fontSize=11.sp,lineHeight=16.sp)
            }
            Text(payment.received?.let { MoneyMath.formatMoney(it.amountMinor) }
                ?: payment.expectedMinor?.let(MoneyMath::formatMoney) ?: "Не объявлено",
                color=if (payment.received != null) Green else StrongText,fontWeight=FontWeight.SemiBold,fontSize=17.sp,lineHeight=23.sp)
        }
        Text(when {
            payment.received != null -> "Получено · фактическая сумма"
            payment.estimated -> "Оценка · не все даты известны"
            coupon.payDay < today -> "Ожидает подтверждения поступления"
            else -> "Ожидается · до налогов"
        },color=if (payment.received != null) Green else if (payment.estimated) OrangeLight else SoftText,fontSize=11.sp,lineHeight=16.sp,
            modifier=Modifier.padding(top=8.dp))
        Text("На одну облигацию: ${bondDecimal(coupon.amountPerBond,coupon.currency)}",color=SoftText,fontSize=11.sp,lineHeight=16.sp,
            modifier=Modifier.padding(top=4.dp))
        if (payment.received == null && payment.quantityMicros > 0 && coupon.payDay <= today && payment.sourceActive) {
            TextButton(onClick={ onConfirm(payment) },contentPadding=PaddingValues(horizontal=0.dp)) { Text("Подтвердить получение") }
        }
    }
}

@Composable private fun CouponReceiptDialog(controller: BondController, dashboard: InvestmentsRepository.DashboardState,
    bonds: BondState, payment: BondCalendar.Payment, onClose: () -> Unit) {
    var amount by remember { mutableStateOf(payment.expectedMinor?.let { BigDecimal.valueOf(it,2).toPlainString() }.orEmpty()) }
    var day by remember { mutableStateOf<Long?>(LocalDate.now().toEpochDay()) }
    val rubleAccounts=dashboard.accounts.filter { it.currency.equals("RUB",ignoreCase=true) }
    var account by remember { mutableStateOf(rubleAccounts.firstOrNull()?.accountId.orEmpty()) }
    var existingId by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope=rememberCoroutineScope()
    val linked=bonds.receipts.map { it.incomeId }.toSet()
    val existing=dashboard.income.filter { it.securityId == payment.securityId && it.kind == "COUPON" && !it.deleted && it.id !in linked }
    AlertDialog(onDismissRequest={ if (!saving) onClose() },containerColor=Frost,title={ Text("Получение купона") },text={
        Column(Modifier.heightIn(max=520.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text("Купон №${payment.coupon.number} · ${bondDate(payment.coupon.payDay)}",color=SoftText,fontSize=13.sp,lineHeight=18.sp)
            if (existing.isNotEmpty()) SelectionField("Связать ранее введённую выплату",
                listOf("" to "Создать новую операцию") + existing.map { it.id to
                    "${bondDate(Instant.ofEpochMilli(it.happenedAt).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay())} · ${MoneyMath.formatMoney(it.amountMinor)} · ${it.note}" },existingId,{ existingId=it })
            if (existingId.isEmpty()) {
                Text("Укажите фактически полученную сумму после удержаний. Она станет операцией дохода.",color=SoftText,fontSize=12.sp,lineHeight=17.sp)
                AmountField(amount,{ amount=it },"Получено, ₽")
                BondDateField("Дата получения",day,{ day=it },optional=false,maxDay=LocalDate.now().toEpochDay())
                if (rubleAccounts.isNotEmpty()) SelectionField("Рублёвый счёт из Кошелька",listOf("" to "Сохранить локально") + rubleAccounts.map { it.accountId to it.name },account,{ account=it })
                else Text("Рублёвый счёт не подключён. Операция сохранится локально, передача в Кошелёк отложена.",color=SoftText,fontSize=12.sp,lineHeight=17.sp)
            } else Text("Новая операция дохода не создаётся. Расписание свяжется с выбранной выплатой.",color=SoftText,fontSize=12.sp,lineHeight=17.sp)
            error?.let { Text(it,color=Red,fontSize=12.sp,lineHeight=17.sp) }
        }
    },confirmButton={ TextButton(enabled=!saving,onClick={
        saving=true; error=null
        scope.launch {
            try {
                if (existingId.isNotEmpty()) controller.linkIncome(payment,existingId)
                else controller.confirm(payment,MoneyMath.parseMoney(amount),account,requireNotNull(day))
                onClose()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error=e.message ?: "Не удалось подтвердить выплату" }
            finally { saving=false }
        }
    }) { Text(if (saving) "Сохранение…" else "Сохранить") } },dismissButton={ TextButton(onClick=onClose,enabled=!saving) { Text("Отмена") } })
}
