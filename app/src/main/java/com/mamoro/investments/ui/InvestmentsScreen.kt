@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.mamoro.investments.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mamoro.investments.data.IncomeEntity
import com.mamoro.investments.data.InvestmentsRepository
import com.mamoro.investments.data.SecurityEntity
import com.mamoro.investments.data.TradeEntity
import com.mamoro.investments.domain.MoneyMath
import com.mamoro.investments.domain.PortfolioMath
import com.mamoro.investments.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val labels = listOf("Портфель", "Активы", "Операции", "Аналитика", "Ещё")
private val tabIcons = listOf(Icons.Rounded.ShowChart, Icons.Rounded.Layers, Icons.Rounded.SwapHoriz, Icons.Rounded.PieChart, Icons.Rounded.MoreHoriz)

@Composable
fun InvestmentsScreen(vm: InvestmentsViewModel) {
    val snapshot by vm.dashboard.collectAsState()
    val state = snapshot
    val pager = rememberPagerState(pageCount = { 5 })
    val scope = rememberCoroutineScope()
    var quickActions by remember { mutableStateOf(false) }
    var form by remember { mutableStateOf<FormKind?>(null) }
    var chosenSecurity by remember { mutableStateOf<String?>(null) }
    var selectedDetail by remember { mutableStateOf<String?>(null) }
    var deleteItem by remember { mutableStateOf<Pair<String, String>?>(null) }
    val message by vm.message.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        val value = message ?: return@LaunchedEffect
        snackbar.showSnackbar(value)
        vm.clearMessage()
    }
    Box(Modifier.fillMaxSize().background(OrangeBackground)) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Ink.copy(alpha = .67f)))))
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            TopBar(onAdd = { quickActions = true })
            if (state == null) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Orange)
                }
            } else {
                HorizontalPager(state = pager, modifier = Modifier.weight(1f), beyondViewportPageCount = 1) { page ->
                    when (page) {
                        0 -> PortfolioPage(state, onCategory = { scope.launch { pager.animateScrollToPage(1) } },
                            onQuick = { quickActions = true }, onAddSecurity = { form = FormKind.NEW_SECURITY },
                            onSecurity = { selectedDetail = it })
                        1 -> AssetsPage(state, onAdd = { form = FormKind.NEW_SECURITY }, onSecurity = { selectedDetail = it })
                        2 -> OperationsPage(state, onDelete = { kind, id -> deleteItem = kind to id })
                        3 -> AnalyticsPage(state)
                        else -> SettingsPage(state)
                    }
                }
            }
            NavigationGlass(pager.currentPage, onSelect = { page -> scope.launch { pager.animateScrollToPage(page) } })
        }
        SnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 85.dp))
    }

    if (quickActions) {
        ModalBottomSheet(onDismissRequest = { quickActions = false }, containerColor = Color(0xEF202127), dragHandle = { BottomSheetDefaults.DragHandle(color = SoftText) }) {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Быстрые действия", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                ActionEntry("Купить бумагу", Icons.Rounded.AddCircleOutline) { form = FormKind.BUY; quickActions = false }
                ActionEntry("Продать", Icons.Rounded.RemoveCircleOutline) { form = FormKind.SELL; quickActions = false }
                ActionEntry("Добавить дивиденд", Icons.Rounded.Payments) { form = FormKind.DIVIDEND; quickActions = false }
                ActionEntry("Добавить купон", Icons.Rounded.ConfirmationNumber) { form = FormKind.COUPON; quickActions = false }
                ActionEntry("Добавить бумагу", Icons.Rounded.LibraryAdd) { form = FormKind.NEW_SECURITY; quickActions = false }
                Text("Пополнение и вывод денег выполняются в «Кошельке».", color = SoftText, fontSize = 12.sp)
            }
        }
    }

    if (form != null && state != null) {
        ModalBottomSheet(onDismissRequest = { form = null; chosenSecurity = null }, containerColor = Color(0xF5212228)) {
            InvestmentForm(form!!, state, chosenSecurity, onClose = { form = null; chosenSecurity = null }, vm = vm)
        }
    }

    if (selectedDetail != null && state != null) {
        val item = state.securities.firstOrNull { it.id == selectedDetail }
        if (item != null) {
            ModalBottomSheet(onDismissRequest = { selectedDetail = null }, containerColor = Color(0xFF1C1F25)) {
                AssetDetail(item, state, onTrade = { type ->
                    chosenSecurity = item.id
                    form = type
                    selectedDetail = null
                }, onPrice = { chosenSecurity = item.id; form = FormKind.PRICE; selectedDetail = null },
                    onArchive = { vm.archiveSecurity(item.id); selectedDetail = null })
            }
        }
    }

    if (deleteItem != null) {
        AlertDialog(
            onDismissRequest = { deleteItem = null },
            title = { Text("Удалить операцию?") },
            text = { Text("Удаление будет передано в «Кошелёк» после подключения синхронизации.") },
            confirmButton = {
                TextButton(onClick = {
                    deleteItem?.let { (kind, id) -> if (kind == "trade") vm.deleteTrade(id) else vm.deleteIncome(id) }
                    deleteItem = null
                }) { Text("Удалить", color = Red) }
            },
            dismissButton = { TextButton(onClick = { deleteItem = null }) { Text("Отмена") } }
        )
    }
}

@Composable private fun TopBar(onAdd: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(62.dp).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(38.dp)
                .shadow(8.dp, RoundedCornerShape(13.dp), spotColor = Orange.copy(alpha = .45f))
                .clip(RoundedCornerShape(13.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF63412B), Color(0xFF28232A))))
                .border(1.dp, Orange.copy(alpha = .48f), RoundedCornerShape(13.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.BarChart, contentDescription = null, tint = Orange)
        }
        Spacer(Modifier.width(11.dp))
        Text("Инвестиции", color = Color(0xFFF9F8FA), fontWeight = FontWeight.Bold, fontSize = 21.sp, modifier = Modifier.weight(1f))
        IconButton(onClick = onAdd) { Icon(Icons.Rounded.Add, contentDescription = "Быстрое действие", tint = Orange) }
    }
}

@Composable private fun NavigationGlass(selected: Int, onSelect: (Int) -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
        .shadow(12.dp, shape)
        .clip(shape)
        .background(Brush.verticalGradient(listOf(Color(0xF043424D), Color(0xEE24262F))))
        .border(1.dp, Brush.linearGradient(listOf(Color(0xAAFFFFFF), Color(0x44FFFFFF), Orange.copy(alpha = .26f))), shape)
        .padding(vertical = 9.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        labels.forEachIndexed { i, title ->
            Column(
                Modifier.weight(1f).clickable { onSelect(i) }.padding(vertical = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier.size(width = 36.dp, height = 28.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (i == selected) Orange.copy(alpha = .15f) else Color.Transparent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(tabIcons[i], contentDescription = title,
                        tint = if (i == selected) Orange else SoftText,
                        modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.height(3.dp))
                Text(title, color = if (i == selected) OrangeLight else SoftText, fontSize = 10.sp, maxLines = 1)
            }
        }
    }
}

@Composable private fun PortfolioPage(
    state: InvestmentsRepository.DashboardState,
    onCategory: () -> Unit,
    onQuick: () -> Unit,
    onAddSecurity: () -> Unit,
    onSecurity: (String) -> Unit
) {
    val summary = state.summary
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { PortfolioHero(summary) }
        item { Text("Активы", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        item { CategoryRow("Акции", Icons.Rounded.TrendingUp, Orange, categoryValue(summary, "STOCK"), onCategory) }
        item { CategoryRow("Облигации", Icons.Rounded.Layers, Color(0xFFC4A1FF), categoryValue(summary, "BOND"), onCategory) }
        item { CategoryRow("Фонды", Icons.Rounded.PieChart, Color(0xFF76C3FF), categoryValue(summary, "FUND"), onCategory) }
        item { CategoryRow("Дивиденды", Icons.Rounded.Payments, Green, summary.dividendsMinor, onCategory) }
        item { CategoryRow("Купоны", Icons.Rounded.ConfirmationNumber, OrangeLight, summary.couponsMinor, onCategory) }
        item {
            Button(
                onClick = onQuick,
                modifier = Modifier.fillMaxWidth().height(52.dp)
                    .shadow(8.dp, RoundedCornerShape(17.dp), spotColor = Orange.copy(alpha = .42f)),
                shape = RoundedCornerShape(17.dp)
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Быстрое действие", fontWeight = FontWeight.Bold)
            }
        }
        if (state.securities.isEmpty()) {
            item { EmptyState("Портфель пока пуст", "Добавьте первую бумагу, затем запишите покупку.",
                actionLabel = "Добавить первую бумагу", onAction = onAddSecurity) }
        } else {
            item { Text("Мои бумаги", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            items(summary.positions.filter { it.quantityMicros > 0 }.take(4), key = { it.security.id }) { position ->
                PositionRow(position) { onSecurity(position.security.id) }
            }
        }
    }
}

private fun categoryValue(summary: PortfolioMath.Summary, kind: String) = summary.positions
    .filter { it.security.kind == kind }.sumOf { it.currentValueMinor }

@Composable private fun PortfolioHero(summary: PortfolioMath.Summary) {
    GlassCard(Modifier.fillMaxWidth(), elevated = true) {
        Text("Стоимость портфеля", color = SoftText, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        AnimatedContent(targetState = summary.marketValueMinor, label = "portfolio-total") { current ->
            Text(MoneyMath.formatMoney(current), fontSize = 33.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.TrendingUp, null, tint = if (summary.unrealizedMinor >= 0) Green else Red, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(6.dp))
            Text("По открытым позициям: ${signedMoney(summary.unrealizedMinor)}", color = if (summary.unrealizedMinor >= 0) Green else Red, fontSize = 13.sp)
        }
        Spacer(Modifier.height(13.dp))
        HorizontalDivider(color = Color.White.copy(alpha = .14f))
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Metric("Результат продаж", signedMoney(summary.realizedMinor), Modifier.weight(1f))
            Metric("Купоны + дивиденды", MoneyMath.formatMoney(summary.couponsMinor + summary.dividendsMinor), Modifier.weight(1f))
        }
        Spacer(Modifier.height(11.dp))
        Text("Оценка по последним введённым ценам. Деньги учитываются в «Кошельке».", color = SoftText, fontSize = 11.sp, lineHeight = 15.sp)
    }
}

@Composable private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, color = SoftText, fontSize = 11.sp, maxLines = 2, lineHeight = 14.sp)
        Spacer(Modifier.height(4.dp))
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun signedMoney(minor: Long) = (if (minor >= 0) "+" else "−") + MoneyMath.formatMoney(kotlin.math.abs(minor))

@Composable private fun CategoryRow(title: String, icon: ImageVector, tint: Color, minor: Long, onClick: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(41.dp).clip(RoundedCornerShape(13.dp)).background(tint.copy(alpha = .15f)), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(23.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(title, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text(MoneyMath.formatMoney(minor), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(Modifier.width(3.dp))
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = SoftText)
        }
    }
}

@Composable private fun AssetsPage(
    state: InvestmentsRepository.DashboardState, onAdd: () -> Unit, onSecurity: (String) -> Unit
) {
    var kind by remember { mutableStateOf("ALL") }
    val filtered = state.summary.positions.filter { if (kind == "ARCHIVE") it.security.archived else !it.security.archived && (kind == "ALL" || it.security.kind == kind) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Мои бумаги", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onAdd) { Icon(Icons.Rounded.AddCircleOutline, contentDescription = "Добавить бумагу", tint = Orange) }
            }
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf("ALL" to "Все", "STOCK" to "Акции", "BOND" to "Облигации", "FUND" to "Фонды", "ARCHIVE" to "Архив").forEach { (code, text) ->
                    FilterChip(selected = kind == code, onClick = { kind = code }, label = { Text(text, fontSize = 12.sp) })
                }
            }
        }
        if (filtered.isEmpty()) item {
            EmptyState("Пока нет бумаг", "Добавьте первую акцию, облигацию или фонд.",
                actionLabel = "Добавить бумагу", onAction = onAdd)
        }
        items(filtered, key = { it.security.id }) { item -> PositionRow(item) { onSecurity(item.security.id) } }
    }
}

@Composable private fun PositionRow(position: PortfolioMath.Position, onClick: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(43.dp).clip(RoundedCornerShape(14.dp)).background(Orange.copy(alpha = .15f)), contentAlignment = Alignment.Center) {
                Text(position.security.ticker.take(2), color = Orange, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(position.security.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${position.security.ticker} · ${MoneyMath.formatQuantity(position.quantityMicros)} шт.", fontSize = 11.sp, color = SoftText)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(MoneyMath.formatMoney(position.currentValueMinor), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(signedMoney(position.unrealizedMinor), fontSize = 12.sp, color = if (position.unrealizedMinor >= 0L) Green else Red)
            }
        }
    }
}

private data class Operation(val id: String, val source: String, val title: String, val detail: String, val time: Long, val delta: Long)

@Composable private fun OperationsPage(state: InvestmentsRepository.DashboardState, onDelete: (String, String) -> Unit) {
    var filter by remember { mutableStateOf("ALL") }
    val tickerMap = state.securities.associateBy { it.id }
    val operations = remember(state.trades, state.income, state.securities) {
        (state.trades.map { trade ->
            val delta = MoneyMath.totalMinor(trade.unitPriceMinor, trade.quantityMicros)
            Operation(trade.id, "trade", if (trade.side == "BUY") "Покупка" else "Продажа",
                "${tickerMap[trade.securityId]?.ticker ?: "Бумага"} · ${MoneyMath.formatQuantity(trade.quantityMicros)} шт.",
                trade.happenedAt, if (trade.side == "BUY") -(delta + trade.feeMinor) else delta - trade.feeMinor)
        } + state.income.map { income ->
            Operation(income.id, "income", if (income.kind == "DIVIDEND") "Дивиденд" else "Купон",
                tickerMap[income.securityId]?.ticker ?: "Бумага", income.happenedAt, income.amountMinor)
        }).sortedWith(compareByDescending<Operation> { it.time }.thenByDescending { it.id })
    }
    val visible = operations.filter { filter == "ALL" || (filter == "TRADES" && it.source == "trade") ||
        (filter == "INCOME" && it.source == "income") }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        item { Text("История операций", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("ALL" to "Все", "TRADES" to "Сделки", "INCOME" to "Выплаты").forEach { (code, text) ->
                    FilterChip(selected = filter == code, onClick = { filter = code }, label = { Text(text) })
                }
            }
        }
        if (visible.isEmpty()) item { EmptyState("История пуста", "Добавьте покупку, продажу или выплату.") }
        items(visible, key = { "${it.source}:${it.id}" }) { op ->
            GlassCard(Modifier.fillMaxWidth().clickable { onDelete(op.source, op.id) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val positive = op.delta >= 0
                    Box(Modifier.size(38.dp).clip(CircleShape).background(if (positive) Green.copy(alpha = .13f) else Orange.copy(alpha = .17f)), contentAlignment = Alignment.Center) {
                        Icon(if (positive) Icons.Rounded.SouthWest else Icons.Rounded.NorthEast, null,
                            tint = if (positive) Green else Orange, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text(op.title, fontWeight = FontWeight.Medium)
                        Text(op.detail, fontSize = 12.sp, color = SoftText)
                        Text(SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.forLanguageTag("ru-RU")).format(Date(op.time)), fontSize = 10.sp, color = SoftText)
                    }
                    Text(signedMoney(op.delta), color = if (positive) Green else Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Icon(Icons.Rounded.MoreVert, contentDescription = "Удалить запись", tint = SoftText, modifier = Modifier.size(18.dp))
                }
            }
        }
        item { Text("Нажмите на операцию для удаления с подтверждением.", color = SoftText, fontSize = 11.sp) }
    }
}

@Composable private fun AnalyticsPage(state: InvestmentsRepository.DashboardState) {
    val summary = state.summary
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Аналитика", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        item { GlassCard(Modifier.fillMaxWidth()) {
            Text("Структура портфеля", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val entries = listOf(categoryValue(summary,"STOCK"), categoryValue(summary,"BOND"), categoryValue(summary,"FUND"))
                Donut(entries, Modifier.size(132.dp))
                Spacer(Modifier.width(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Breakdown("Акции", entries[0], Orange)
                    Breakdown("Облигации", entries[1], Color(0xFFC4A1FF))
                    Breakdown("Фонды", entries[2], Color(0xFF76C3FF))
                }
            }
        } }
        item { GlassCard(Modifier.fillMaxWidth()) {
            Text("Доходность", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            Metric("Нереализованная прибыль/убыток", signedMoney(summary.unrealizedMinor))
            Spacer(Modifier.height(12.dp))
            Metric("Результат закрытых сделок", signedMoney(summary.realizedMinor))
            Spacer(Modifier.height(12.dp))
            Metric("Дивиденды", MoneyMath.formatMoney(summary.dividendsMinor))
            Spacer(Modifier.height(12.dp))
            Metric("Купоны", MoneyMath.formatMoney(summary.couponsMinor))
        } }
        item { GlassCard(Modifier.fillMaxWidth()) {
            Text("Оценка бумаг", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text("Рыночные цены пока вводятся вручную. Исторические графики и ежедневная доходность появятся после подключения проверенного источника котировок.",
                color = SoftText, fontSize = 12.sp, lineHeight = 18.sp)
        } }
    }
}

@Composable private fun Donut(values: List<Long>, modifier: Modifier) {
    val palette = listOf(Orange, Color(0xFFC4A1FF), Color(0xFF76C3FF))
    Canvas(modifier) {
        val total = values.sum().toFloat().coerceAtLeast(1f)
        var start = -90f
        val stroke = 17.dp.toPx()
        if (values.all { it == 0L }) {
            drawArc(Color.White.copy(alpha = .14f), 0f, 360f, false, style = Stroke(stroke, cap = StrokeCap.Round))
        } else values.forEachIndexed { index, value ->
            val sweep = 360f * (value / total)
            if (sweep > .1f) drawArc(palette[index], start, sweep, false, style = Stroke(stroke, cap = StrokeCap.Butt))
            start += sweep
        }
    }
}

@Composable private fun Breakdown(label: String, value: Long, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(7.dp))
        Column {
            Text(label, color = SoftText, fontSize = 11.sp)
            Text(MoneyMath.formatMoney(value), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable private fun SettingsPage(state: InvestmentsRepository.DashboardState) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        item { Text("Настройки и связь", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        item { GlassCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Sync, null, tint = Orange)
                Spacer(Modifier.width(9.dp))
                Text("Связь с Кошельком", fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(12.dp))
            Text("Счета из Кошелька: ${state.accounts.size}", fontSize = 13.sp)
            Text("Событий без подтверждения: ${state.pendingEvents}", fontSize = 13.sp, color = if (state.pendingEvents > 0) OrangeLight else Green)
            Spacer(Modifier.height(9.dp))
            Text("Двусторонняя синхронизация заработает после установки адаптера в walletfree и настройки доверенной подписи. До этого операции хранятся локально и не меняют баланс Кошелька.", color = SoftText, fontSize = 12.sp, lineHeight = 18.sp)
        } }
        item { GlassCard(Modifier.fillMaxWidth()) {
            Text("Подключённые брокерские счета", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            if (state.accounts.isEmpty()) Text("Нет счетов. Их создаёт и передаёт Кошелёк.", color = SoftText, fontSize = 12.sp)
            state.accounts.forEach { account ->
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(account.name, fontSize = 13.sp)
                    Text(MoneyMath.formatMoney(account.balanceMinor), fontSize = 13.sp)
                }
            }
        } }
        item { GlassCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Lock, null, tint = Green)
                Spacer(Modifier.width(9.dp))
                Text("Конфиденциальность", fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
            Text("Данные хранятся в локальной базе. Разрешение для межпрограммного обмена ограничено цифровой подписью. Интернет не требуется.", color = SoftText, fontSize = 12.sp, lineHeight = 18.sp)
        } }
        item { Text("Инвестиции 1.0.1 · Android 8.0+", color = SoftText, fontSize = 12.sp) }
    }
}

@Composable private fun ActionEntry(title: String, icon: ImageVector, action: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth().clickable(onClick = action)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Orange)
            Spacer(Modifier.width(13.dp))
            Text(title, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
            Icon(Icons.Rounded.ChevronRight, null, tint = SoftText)
        }
    }
}

@Composable private fun EmptyState(
    title: String, subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    GlassCard(Modifier.fillMaxWidth(), elevated = true) {
        Box(
            Modifier.size(42.dp).clip(RoundedCornerShape(14.dp))
                .background(Orange.copy(alpha = .17f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Inventory2, contentDescription = null, tint = OrangeLight)
        }
        Spacer(Modifier.height(12.dp))
        Text(title, color = Color(0xFFF9F8FA), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Spacer(Modifier.height(5.dp))
        Text(subtitle, color = SoftText, fontSize = 12.sp, lineHeight = 17.sp)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(12.dp))
            Button(onClick = onAction, shape = RoundedCornerShape(13.dp)) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(actionLabel, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable private fun AssetDetail(
    asset: SecurityEntity, state: InvestmentsRepository.DashboardState,
    onTrade: (FormKind) -> Unit, onPrice: () -> Unit, onArchive: () -> Unit
) {
    val holding = state.summary.positions.first { it.security.id == asset.id }
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 36.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        Text(asset.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("${asset.ticker} · ${kindLabel(asset.kind)}", color = SoftText)
        GlassCard(Modifier.fillMaxWidth()) {
            Text("Текущая цена (вводится вручную)", color = SoftText, fontSize = 12.sp)
            Text(MoneyMath.formatMoney(asset.lastPriceMinor), fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Metric("В портфеле", "${MoneyMath.formatQuantity(holding.quantityMicros)} шт.")
            Spacer(Modifier.height(6.dp))
            Metric("Стоимость позиции", MoneyMath.formatMoney(holding.currentValueMinor))
            Spacer(Modifier.height(6.dp))
            Metric("Нереализованный результат", signedMoney(holding.unrealizedMinor))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onTrade(FormKind.BUY) }, modifier = Modifier.weight(1f)) { Text("Купить") }
            OutlinedButton(onClick = { onTrade(FormKind.SELL) }, modifier = Modifier.weight(1f)) { Text("Продать") }
        }
        OutlinedButton(onClick = onPrice, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Edit, null); Spacer(Modifier.width(7.dp)); Text("Изменить цену") }
        TextButton(onClick = onArchive, modifier = Modifier.fillMaxWidth()) { Text(if (asset.archived) "Вернуть из архива" else "Архивировать бумагу") }
    }
}

private fun kindLabel(kind: String) = when (kind) { "STOCK" -> "Акция"; "BOND" -> "Облигация"; else -> "Фонд" }
