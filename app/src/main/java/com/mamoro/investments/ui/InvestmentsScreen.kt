@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.mamoro.investments.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.draw.alpha
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
// Both 6.55-inch phones and 12.1-inch tablets use the same centered,
    // constrained design grid. Compose uses dp rather than raw device pixels.
    BoxWithConstraints(Modifier.fillMaxSize().background(OrangeBackground)) {
        val isTablet = maxWidth >= 600.dp
        val maxPane = if (isTablet) GlassSizes.maxContentWidth else maxWidth
        val horizontalInset = if (isTablet) GlassSizes.tabletSidePadding else GlassSizes.phoneSidePadding
        val paneModifier = Modifier.widthIn(max = maxPane).fillMaxWidth()
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.radialGradient(
                    listOf(Orange.copy(alpha = .16f), Orange.copy(alpha = .045f), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(230f, 1020f),
                    radius = 1180f
                )
            )
        )
        Column(
            Modifier.fillMaxSize().safeDrawingPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(paneModifier) { TopBar(onAdd = { quickActions = true }, horizontalInset = horizontalInset) }
            if (state == null) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Orange)
                }
            } else {
                HorizontalPager(
                    state = pager,
                    modifier = Modifier.weight(1f).then(paneModifier),
                    beyondViewportPageCount = 1
                ) { page ->
                    when (page) {
                        0 -> PortfolioPage(state,
                            onCategory = { scope.launch { pager.animateScrollToPage(1) } },
                            onQuick = { quickActions = true },
                            onAdd = { form = FormKind.NEW_SECURITY },
                            onSecurity = { selectedDetail = it }, horizontalInset = horizontalInset)
                        1 -> AssetsPage(state, onAdd = { form = FormKind.NEW_SECURITY }, onSecurity = { selectedDetail = it }, horizontalInset = horizontalInset)
                        2 -> OperationsPage(state, onDelete = { kind, id -> deleteItem = kind to id }, horizontalInset = horizontalInset)
                        3 -> AnalyticsPage(state, horizontalInset = horizontalInset)
                        else -> SettingsPage(state, horizontalInset = horizontalInset)
                    }
                }
            }
            Box(paneModifier) {
                NavigationGlass(pager.currentPage, onSelect = { page ->
                    scope.launch { pager.animateScrollToPage(page) }
                })
            }
        }
        SnackbarHost(hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(bottom = 88.dp))
    }


    if (quickActions) {
        ModalBottomSheet(
            onDismissRequest = { quickActions = false },
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            containerColor = Color(0xF123222A),
            dragHandle = { BottomSheetDefaults.DragHandle(color = OrangeLight.copy(alpha = .72f)) }
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                Column(
                    Modifier.widthIn(max = GlassSizes.maxContentWidth).fillMaxWidth()
                        .padding(start = 18.dp, end = 18.dp, bottom = 30.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Быстрые действия", color = StrongText,
                                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("Операции с бумагами за несколько касаний", color = SoftText, fontSize = 12.sp)
                        }
                        IconButton(onClick = { quickActions = false }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Закрыть меню", tint = StrongText)
                        }
                    }
                    Spacer(Modifier.height(3.dp))
                    ActionEntry("Купить бумагу", Icons.Rounded.AddCircleOutline) { form = FormKind.BUY; quickActions = false }
                    ActionEntry("Продать", Icons.Rounded.RemoveCircleOutline) { form = FormKind.SELL; quickActions = false }
                    ActionEntry("Добавить дивиденд", Icons.Rounded.Payments) { form = FormKind.DIVIDEND; quickActions = false }
                    ActionEntry("Добавить купон", Icons.Rounded.ConfirmationNumber) { form = FormKind.COUPON; quickActions = false }
                    ActionEntry("Добавить бумагу", Icons.Rounded.LibraryAdd) { form = FormKind.NEW_SECURITY; quickActions = false }
                    Text("Пополнение и вывод денежных средств выполняются в «Кошельке».",
                        color = SoftText, fontSize = 12.sp, lineHeight = 17.sp,
                        modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp))
                }
            }
        }
    }

    if (form != null && state != null) {
        ModalBottomSheet(onDismissRequest = { form = null; chosenSecurity = null }, containerColor = Color(0xF521222C)) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                InvestmentForm(form!!, state, chosenSecurity, onClose = { form = null; chosenSecurity = null }, vm = vm)
            }
        }
    }

    if (selectedDetail != null && state != null) {
        val item = state.securities.firstOrNull { it.id == selectedDetail }
        if (item != null) {
            ModalBottomSheet(onDismissRequest = { selectedDetail = null }, containerColor = Color(0xFF1E2028)) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                AssetDetail(item, state, onTrade = { type ->
                    chosenSecurity = item.id
                    form = type
                    selectedDetail = null
                }, onPrice = { chosenSecurity = item.id; form = FormKind.PRICE; selectedDetail = null },
                    onArchive = { vm.archiveSecurity(item.id); selectedDetail = null })
                }
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

@Composable private fun TopBar(onAdd: () -> Unit, horizontalInset: androidx.compose.ui.unit.Dp) {
    Row(
        Modifier.fillMaxWidth().height(66.dp).padding(horizontal = horizontalInset),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val shape = RoundedCornerShape(14.dp)
        Box(
            Modifier.size(42.dp).shadow(7.dp, shape)
                .clip(shape)
                .background(Brush.linearGradient(listOf(Color(0xFF513526), Color(0xFF211D22))))
                .border(1.dp, Brush.linearGradient(listOf(OrangeLight.copy(alpha = .82f), Color.White.copy(alpha = .12f))), shape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.BarChart, contentDescription = null, tint = OrangeLight, modifier = Modifier.size(23.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text("Инвестиции", color = StrongText, fontWeight = FontWeight.Bold,
            fontSize = 21.sp, modifier = Modifier.weight(1f))
        val actionShape = RoundedCornerShape(14.dp)
        Box(Modifier.size(43.dp).clip(actionShape)
            .background(Brush.verticalGradient(listOf(Color(0x4D6E4935), Color(0x3325232A))), actionShape)
            .border(1.dp, OrangeLight.copy(alpha = .25f), actionShape),
            contentAlignment = Alignment.Center) {
            IconButton(onClick = onAdd, modifier = Modifier.size(43.dp)) {
                Icon(Icons.Rounded.Add, contentDescription = "Быстрые действия",
                    tint = OrangeLight, modifier = Modifier.size(25.dp))
            }
        }
    }
}

@Composable private fun NavigationGlass(selected: Int, onSelect: (Int) -> Unit) {
    val shape = RoundedCornerShape(23.dp)
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
    Row(
        Modifier.widthIn(max = GlassSizes.maxNavigationWidth).fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 5.dp, bottom = 6.dp)
            .shadow(14.dp, shape, ambientColor = Color.Black, spotColor = Orange.copy(alpha = .22f))
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xF15A5158), Color(0xF02C2B35), Color(0xFC1C1E26))))
            .background(Brush.radialGradient(listOf(Color.White.copy(alpha = .09f), Color.Transparent),
                center = androidx.compose.ui.geometry.Offset.Zero, radius = 500f), shape)
            .border(1.dp, Brush.linearGradient(listOf(Color(0xCBFFFFFF), Color(0x2FFFFFFF), Orange.copy(alpha = .47f))), shape)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        labels.forEachIndexed { i, title ->
            val tint by animateColorAsState(if (i == selected) OrangeLight else SoftText, label = "nav-color-$i")
            Column(
                Modifier.weight(1f).heightIn(min = 56.dp).clip(RoundedCornerShape(14.dp))
                    .clickable { onSelect(i) }
                    .padding(vertical = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                val selectedGlow by animateFloatAsState(if (i == selected) .23f else 0f,
                    animationSpec = tween(240), label = "nav-glow-$i")
                Box(
                    Modifier.size(34.dp).clip(RoundedCornerShape(12.dp))
                        .background(Orange.copy(alpha = selectedGlow))
                        .border(1.dp, OrangeLight.copy(alpha = selectedGlow * .7f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(tabIcons[i], contentDescription = title, tint = tint, modifier = Modifier.size(21.dp))
                }
                Text(title, color = tint, fontSize = 10.sp, maxLines = 1,
                    fontWeight = if (i == selected) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
    }
}

/** Orange pill with a polished glass edge, used consistently by both filters. */
@Composable private fun PremiumChip(selected: Boolean, title: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    val background = if (selected)
        Brush.verticalGradient(listOf(Color(0xFFFFB568), Color(0xFFFF902A)))
    else Brush.verticalGradient(listOf(Color(0xA43B3B44), Color(0xD221232B)))
    Box(
        Modifier.heightIn(min = 48.dp).clip(shape).background(background)
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = .07f), Color.Transparent)), shape)
            .border(1.dp, if (selected) OrangeLight else Color.White.copy(alpha = .30f), shape)
            .clickable(onClick = onClick).padding(horizontal = 15.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(title, color = if (selected) Color(0xFF291507) else StrongText,
            fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
    }
}

@Composable private fun PageHeading(title: String, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = StrongText, modifier = Modifier.weight(1f))
        if (action != null) action()
    }
}

@Composable private fun PortfolioPage(
    state: InvestmentsRepository.DashboardState,
    onCategory: () -> Unit,
    onQuick: () -> Unit,
    onAdd: () -> Unit,
    onSecurity: (String) -> Unit,
    horizontalInset: androidx.compose.ui.unit.Dp
) {
    val summary = state.summary
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = horizontalInset, end = horizontalInset, top = 8.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(GlassSizes.sectionGap)
    ) {
        item { PortfolioHero(summary) }
        item { Spacer(Modifier.height(2.dp)) }
        item {
            CategoryRow("Акции", Icons.Rounded.TrendingUp, Orange,
                categoryValue(summary, "STOCK"), categoryPerformance(summary, "STOCK"), onCategory)
        }
        item {
            CategoryRow("Облигации", Icons.Rounded.Layers, Color(0xFFC4A1FF),
                categoryValue(summary, "BOND"), categoryPerformance(summary, "BOND"), onCategory)
        }
        item {
            CategoryRow("Фонды", Icons.Rounded.PieChart, Color(0xFF76C3FF),
                categoryValue(summary, "FUND"), categoryPerformance(summary, "FUND"), onCategory)
        }
        item {
            CategoryRow("Дивиденды", Icons.Rounded.Payments, Green, summary.dividendsMinor,
                if (summary.dividendsMinor != 0L) "Получено" else "Нет выплат", onCategory)
        }
        item {
            CategoryRow("Купоны", Icons.Rounded.ConfirmationNumber, OrangeLight, summary.couponsMinor,
                if (summary.couponsMinor != 0L) "Получено" else "Нет выплат", onCategory)
        }
        item {
            GlassActionButton("Быстрое действие", onClick = onQuick, modifier = Modifier.fillMaxWidth())
        }
        if (state.securities.isEmpty()) {
            item {
                EmptyState("Портфель пока пуст", "Добавьте первую бумагу, затем запишите покупку.",
                    actionLabel = "Добавить первую бумагу", onAction = onAdd)
            }
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

private fun categoryPerformance(summary: PortfolioMath.Summary, kind: String): String {
    val positions = summary.positions.filter { it.security.kind == kind && it.quantityMicros > 0 }
    val basis = positions.sumOf { it.costMinor }
    if (basis <= 0L) return "Нет позиций"
    val gain = positions.sumOf { it.unrealizedMinor }
    return String.format(Locale.forLanguageTag("ru-RU"), "%+.1f%%",
        gain.toDouble() * 100.0 / basis.toDouble())
}

@Composable private fun PortfolioHero(summary: PortfolioMath.Summary) {
    GlassCard(Modifier.fillMaxWidth(), elevated = true) {
        Box(Modifier.fillMaxWidth()) {
            // Ambient spot behind the total; deliberately not a price chart.
            Box(Modifier.align(Alignment.CenterEnd).size(128.dp)
                .background(Brush.radialGradient(listOf(Orange.copy(alpha = .16f),
                    Orange.copy(alpha = .035f), Color.Transparent)), CircleShape))
            Column {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Стоимость портфеля", color = SoftText, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Icon(Icons.Rounded.ShowChart, contentDescription = "Рыночная стоимость",
                        tint = OrangeLight, modifier = Modifier.size(21.dp))
                }
                Spacer(Modifier.height(5.dp))
                AnimatedContent(targetState = summary.marketValueMinor, label = "portfolio-total", transitionSpec = {
                    androidx.compose.animation.fadeIn(tween(240)) togetherWith androidx.compose.animation.fadeOut(tween(130))
                }) { current ->
                    Text(MoneyMath.formatMoney(current), color = StrongText,
                        fontSize = 35.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(7.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (summary.unrealizedMinor >= 0) Icons.Rounded.TrendingUp else Icons.Rounded.TrendingDown,
                        contentDescription = null, tint = if (summary.unrealizedMinor >= 0) Green else Red,
                        modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Открытые позиции: ${signedMoney(summary.unrealizedMinor)}",
                        color = if (summary.unrealizedMinor >= 0) Green else Red,
                        fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = Color.White.copy(alpha = .13f))
        Spacer(Modifier.height(11.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Metric("Результат продаж", signedMoney(summary.realizedMinor), Modifier.weight(1f))
            Metric("Купоны + дивиденды", MoneyMath.formatMoney(summary.couponsMinor + summary.dividendsMinor), Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Text("Цены бумаг введены вручную. Денежные счета — в «Кошельке».",
            color = SoftText, fontSize = 11.sp, lineHeight = 15.sp)
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

@Composable private fun CategoryRow(
    title: String, icon: ImageVector, tint: Color, minor: Long, supporting: String, onClick: () -> Unit
) {
    GlassCard(Modifier.fillMaxWidth().clickable(onClick = onClick), compact = true) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val shape = RoundedCornerShape(13.dp)
            Box(
                Modifier.size(42.dp).clip(shape)
                    .background(Brush.linearGradient(listOf(tint.copy(alpha = .28f), tint.copy(alpha = .10f))))
                    .border(1.dp, tint.copy(alpha = .16f), shape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(23.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                modifier = Modifier.weight(1f), maxLines = 1)
            Column(horizontalAlignment = Alignment.End) {
                Text(MoneyMath.formatMoney(minor), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(supporting, color = if (supporting.startsWith("+")) Green
                    else if (supporting.startsWith("−") || supporting.startsWith("-")) Red else SoftText,
                    fontSize = 11.sp)
            }
            Spacer(Modifier.width(5.dp))
            Icon(Icons.Rounded.ChevronRight, contentDescription = null,
                tint = SoftText, modifier = Modifier.size(19.dp))
        }
    }
}

@Composable private fun AssetsPage(
    state: InvestmentsRepository.DashboardState, onAdd: () -> Unit,
    onSecurity: (String) -> Unit, horizontalInset: androidx.compose.ui.unit.Dp
) {
    var kind by remember { mutableStateOf("ALL") }
    val filtered = state.summary.positions.filter {
        if (kind == "ARCHIVE") it.security.archived
        else !it.security.archived && (kind == "ALL" || it.security.kind == kind)
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = horizontalInset, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(GlassSizes.sectionGap)
    ) {
        item {
            PageHeading("Мои бумаги") {
                IconButton(onClick = onAdd, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.AddCircleOutline, contentDescription = "Добавить бумагу",
                        tint = OrangeLight, modifier = Modifier.size(26.dp))
                }
            }
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf("ALL" to "Все", "STOCK" to "Акции", "BOND" to "Облигации",
                    "FUND" to "Фонды", "ARCHIVE" to "Архив").forEach { (code, text) ->
                    PremiumChip(selected = kind == code, title = text, onClick = { kind = code })
                }
            }
        }
        if (filtered.isEmpty()) {
            item {
                EmptyState(
                    if (kind == "ARCHIVE") "Архив пуст" else "Пока нет бумаг",
                    if (kind == "ARCHIVE") "Архивированные бумаги появятся здесь."
                    else "Добавьте первую акцию, облигацию или фонд.",
                    actionLabel = if (kind == "ARCHIVE") null else "Добавить бумагу",
                    onAction = if (kind == "ARCHIVE") null else onAdd
                )
            }
        }
        items(filtered, key = { it.security.id }) { position ->
            PositionRow(position) { onSecurity(position.security.id) }
        }
    }
}

@Composable private fun PositionRow(position: PortfolioMath.Position, onClick: () -> Unit) {
    val iconTone = when (position.security.kind) {
        "BOND" -> Color(0xFFC4A1FF)
        "FUND" -> Color(0xFF76C3FF)
        else -> Orange
    }
    GlassCard(Modifier.fillMaxWidth().clickable(onClick = onClick), compact = true) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(43.dp).clip(RoundedCornerShape(13.dp))
                    .background(iconTone.copy(alpha = .19f)),
                contentAlignment = Alignment.Center
            ) {
                Text(position.security.ticker.take(2).uppercase(Locale.ROOT), color = iconTone,
                    fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(position.security.name, fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${position.security.ticker} · ${MoneyMath.formatQuantity(position.quantityMicros)} шт.",
                    fontSize = 11.sp, color = SoftText)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(MoneyMath.formatMoney(position.currentValueMinor), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(signedMoney(position.unrealizedMinor), fontSize = 12.sp,
                    color = if (position.unrealizedMinor >= 0L) Green else Red)
            }
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = SoftText, modifier = Modifier.size(17.dp))
        }
    }
}

private data class Operation(val id: String, val source: String, val title: String, val detail: String, val time: Long, val delta: Long)

@Composable private fun OperationsPage(
    state: InvestmentsRepository.DashboardState, onDelete: (String, String) -> Unit,
    horizontalInset: androidx.compose.ui.unit.Dp
) {
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
    val visible = operations.filter {
        filter == "ALL" || (filter == "BUY" && it.title == "Покупка") ||
            (filter == "SELL" && it.title == "Продажа") ||
            (filter == "INCOME" && it.source == "income")
    }
    val dateFormat = remember { SimpleDateFormat("dd MMMM yyyy", Locale.forLanguageTag("ru-RU")) }
    val groups = visible.groupBy { dateFormat.format(Date(it.time)) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = horizontalInset, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(GlassSizes.sectionGap)
    ) {
        item { PageHeading("История операций") }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf("ALL" to "Все", "BUY" to "Покупки", "SELL" to "Продажи", "INCOME" to "Выплаты").forEach { (code, text) ->
                    PremiumChip(selected = filter == code, title = text, onClick = { filter = code })
                }
            }
        }
        if (visible.isEmpty()) {
            item { EmptyState("История пуста", "Добавьте покупку, продажу или выплату.") }
        } else {
            groups.forEach { (date, list) ->
                item(key = "date-$date") {
                    Text(date, color = SoftText, fontSize = 12.sp, modifier = Modifier.padding(top = 7.dp))
                }
                items(list, key = { "${it.source}:${it.id}" }) { op ->
                    GlassCard(Modifier.fillMaxWidth().clickable { onDelete(op.source, op.id) }, compact = true) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            val positive = op.delta >= 0
                            val isCoupon = op.title == "Купон"
                            val color = if (isCoupon) OrangeLight else if (positive) Green else Red
                            Box(
                                Modifier.size(40.dp).clip(RoundedCornerShape(13.dp))
                                    .background(color.copy(alpha = .17f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(if (isCoupon) Icons.Rounded.ConfirmationNumber
                                    else if (positive) Icons.Rounded.SouthWest else Icons.Rounded.NorthEast,
                                    null, tint = color, modifier = Modifier.size(21.dp))
                            }
                            Spacer(Modifier.width(11.dp))
                            Column(Modifier.weight(1f)) {
                                Text(op.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text(op.detail, fontSize = 11.sp, color = SoftText, maxLines = 1)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(signedMoney(op.delta), color = if (positive) Green else StrongText,
                                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text(SimpleDateFormat("HH:mm", Locale.forLanguageTag("ru-RU")).format(Date(op.time)),
                                    color = SoftText, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }
        if (visible.isNotEmpty()) {
            item { Text("Нажмите на операцию, чтобы удалить её с подтверждением.",
                color = SoftText, fontSize = 11.sp) }
        }
    }
}

@Composable private fun AnalyticsPage(
    state: InvestmentsRepository.DashboardState, horizontalInset: androidx.compose.ui.unit.Dp
) {
    val summary = state.summary
    val types = listOf(categoryValue(summary, "STOCK"), categoryValue(summary, "BOND"), categoryValue(summary, "FUND"))
    val payments = listOf(summary.dividendsMinor, summary.couponsMinor)
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = horizontalInset, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(GlassSizes.sectionGap)
    ) {
        item { PageHeading("Аналитика") }
        item {
            GlassCard(Modifier.fillMaxWidth(), elevated = true) {
                Text("Структура портфеля", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Donut(types, Modifier.size(122.dp))
                    Spacer(Modifier.width(18.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(11.dp), modifier = Modifier.weight(1f)) {
                        Breakdown("Акции", types[0], Orange)
                        Breakdown("Облигации", types[1], Color(0xFFC4A1FF))
                        Breakdown("Фонды", types[2], Color(0xFF76C3FF))
                    }
                }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("Доходность", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.height(11.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Metric("Открытые позиции", signedMoney(summary.unrealizedMinor), Modifier.weight(1f))
                    Metric("Закрытые сделки", signedMoney(summary.realizedMinor), Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = Color.White.copy(alpha = .12f))
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Metric("Дивиденды", MoneyMath.formatMoney(summary.dividendsMinor), Modifier.weight(1f))
                    Metric("Купоны", MoneyMath.formatMoney(summary.couponsMinor), Modifier.weight(1f))
                }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("Доходы по категориям", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Donut(payments, Modifier.size(104.dp))
                    Spacer(Modifier.width(17.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Breakdown("Дивиденды", summary.dividendsMinor, Orange)
                        Breakdown("Купоны", summary.couponsMinor, Color(0xFFC4A1FF))
                    }
                }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Info, contentDescription = null, tint = OrangeLight, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Оценка бумаг", fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(8.dp))
                Text("Рыночные цены пока вводятся вручную. Исторические графики и ежедневная доходность появятся после подключения проверенного источника котировок.",
                    color = SoftText, fontSize = 12.sp, lineHeight = 18.sp)
            }
        }
    }
}

@Composable private fun Donut(values: List<Long>, modifier: Modifier) {
    val palette = listOf(Orange, Color(0xFFC4A1FF), Color(0xFF76C3FF))
    val totalMinor = values.sum()
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(8.dp)) {
            val total = totalMinor.toFloat().coerceAtLeast(1f)
            var start = -90f
            val stroke = 15.dp.toPx()
            if (totalMinor <= 0L) {
                drawArc(Color.White.copy(alpha = .16f), 0f, 360f, false,
                    style = Stroke(stroke, cap = StrokeCap.Round))
            } else values.forEachIndexed { index, value ->
                val sweep = 360f * (value / total)
                if (sweep > .1f) drawArc(palette[index], start + 1f, (sweep - 2f).coerceAtLeast(.1f),
                    false, style = Stroke(stroke, cap = StrokeCap.Butt))
                start += sweep
            }
        }
        Text(if (totalMinor <= 0L) "0 ₽" else MoneyMath.formatMoney(totalMinor),
            color = StrongText, fontSize = if (totalMinor >= 1000000000L) 10.sp else 12.sp,
            maxLines = 1, fontWeight = FontWeight.SemiBold)
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

@Composable private fun SettingsPage(
    state: InvestmentsRepository.DashboardState, horizontalInset: androidx.compose.ui.unit.Dp
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = horizontalInset, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(GlassSizes.sectionGap)
    ) {
        item { PageHeading("Настройки и связь") }
        item {
            GlassCard(Modifier.fillMaxWidth(), elevated = true) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Sync, null, tint = OrangeLight, modifier = Modifier.size(23.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Связь с Кошельком", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }
                Spacer(Modifier.height(14.dp))
                Text("Счета из Кошелька: ${state.accounts.size}", fontSize = 13.sp)
                Spacer(Modifier.height(5.dp))
                Text("Событий без подтверждения: ${state.pendingEvents}", fontSize = 13.sp,
                    color = if (state.pendingEvents > 0) OrangeLight else Green)
                Spacer(Modifier.height(12.dp))
                Text("Двусторонняя синхронизация заработает после установки адаптера в walletfree и настройки доверенной подписи. До этого операции хранятся локально и не меняют баланс Кошелька.",
                    color = SoftText, fontSize = 12.sp, lineHeight = 18.sp)
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("Подключённые брокерские счета", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.height(10.dp))
                if (state.accounts.isEmpty()) Text("Нет счетов. Их создаёт и передаёт Кошелёк.", color = SoftText, fontSize = 12.sp)
                state.accounts.forEach { account ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(account.name, fontSize = 13.sp)
                        Text(MoneyMath.formatMoney(account.balanceMinor), fontSize = 13.sp)
                    }
                }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Lock, null, tint = Green, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Конфиденциальность", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }
                Spacer(Modifier.height(9.dp))
                Text("Данные хранятся в локальной базе. Межпрограммный обмен ограничен цифровой подписью. Интернет не требуется.",
                    color = SoftText, fontSize = 12.sp, lineHeight = 18.sp)
            }
        }
        item { Text("Инвестиции 1.0.3 · Android 8.0+", color = SoftText, fontSize = 12.sp,
            modifier = Modifier.padding(top = 8.dp)) }
    }
}

@Composable private fun ActionEntry(title: String, icon: ImageVector, action: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth().clickable(onClick = action), elevated = true, compact = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val shape = RoundedCornerShape(12.dp)
            Box(Modifier.size(38.dp).clip(shape)
                .background(Brush.linearGradient(listOf(Orange.copy(alpha = .31f), Orange.copy(alpha = .10f))))
                .border(1.dp, Orange.copy(alpha = .13f), shape), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = OrangeLight, modifier = Modifier.size(21.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(title, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Icon(Icons.Rounded.ChevronRight, null, tint = SoftText, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable private fun EmptyState(
    title: String, subtitle: String,
    actionLabel: String? = null, onAction: (() -> Unit)? = null
) {
    GlassCard(Modifier.fillMaxWidth(), elevated = true) {
        Box(
            Modifier.size(41.dp).clip(RoundedCornerShape(13.dp)).background(Orange.copy(alpha = .19f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Inventory2, contentDescription = null, tint = OrangeLight)
        }
        Spacer(Modifier.height(10.dp))
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Spacer(Modifier.height(5.dp))
        Text(subtitle, color = SoftText, fontSize = 12.sp, lineHeight = 17.sp)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(12.dp))
            GlassActionButton(actionLabel, onClick = onAction)
        }
    }
}

@Composable private fun AssetDetail(
    asset: SecurityEntity, state: InvestmentsRepository.DashboardState,
    onTrade: (FormKind) -> Unit, onPrice: () -> Unit, onArchive: () -> Unit
) {
    val holding = state.summary.positions.first { it.security.id == asset.id }
    Column(Modifier.widthIn(max = GlassSizes.maxContentWidth).fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 36.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
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
