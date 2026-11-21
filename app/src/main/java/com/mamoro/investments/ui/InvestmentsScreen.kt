@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.mamoro.investments.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.activity.compose.BackHandler
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
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
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mamoro.investments.BuildConfig
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
private val tabIcons = listOf(Icons.Rounded.BarChart, Icons.Outlined.Layers, Icons.Rounded.SwapHoriz, Icons.Rounded.PieChartOutline, Icons.Rounded.MoreHoriz)

@Composable
fun InvestmentsScreen(vm: InvestmentsViewModel) {
    val state by vm.dashboard.collectAsState()
    InvestmentDashboard(vm, state)
}

/** Shared by the real activity and debug-only visual captures; never seeds the database. */
@Composable
internal fun InvestmentDashboard(
    vm: InvestmentsViewModel, state: InvestmentsRepository.DashboardState?,
    initialPage: Int = 0, initiallyShowQuick: Boolean = false
) {
    val pager = rememberPagerState(initialPage = initialPage, pageCount = { 5 })
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    LaunchedEffect(pager.currentPage) { focus.clearFocus(force = true) }
    var quickActions by rememberSaveable { mutableStateOf(initiallyShowQuick) }
    val quickSheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    fun closeQuick(after: () -> Unit = {}) {
        scope.launch { quickSheet.hide(); quickActions = false; after() }
    }
    var form by remember { mutableStateOf<FormKind?>(null) }
    var walletAction by remember { mutableStateOf<String?>(null) }
    var chosenSecurity by remember { mutableStateOf<String?>(null) }
    var selectedDetail by remember { mutableStateOf<String?>(null) }
    var deleteItem by remember { mutableStateOf<Pair<String, String>?>(null) }
    BackHandler(enabled = pager.currentPage != 0 && !quickActions && form == null && selectedDetail == null) {
        scope.launch { pager.animateScrollToPage(0, animationSpec = tween(180)) }
    }
    val message by vm.message.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        val value = message ?: return@LaunchedEffect
        snackbar.showSnackbar(value)
        vm.clearMessage()
    }
// Both 6.55-inch phones and 12.1-inch tablets use the same centered,
    // constrained design grid. Compose uses dp rather than raw device pixels.
    BoxWithConstraints(Modifier.fillMaxSize().premiumBackdrop()) {
        val isTablet = maxWidth >= 600.dp
        val maxPane = if (isTablet) GlassSizes.maxContentWidth else maxWidth
        val horizontalInset = if (isTablet) GlassSizes.tabletSidePadding else GlassSizes.phoneSidePadding
        val paneModifier = Modifier.widthIn(max = maxPane).fillMaxWidth()
        Column(
            Modifier.fillMaxSize().safeDrawingPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(paneModifier) {
                if (pager.currentPage == 2) HistoryTopBar(onBack = {
                    scope.launch { pager.animateScrollToPage(0, animationSpec = tween(180)) }
                }, horizontalInset = horizontalInset)
                else TopBar(
                onMenu = { scope.launch { pager.animateScrollToPage(4, animationSpec = tween(180)) } },
                onSearch = { scope.launch { pager.animateScrollToPage(1, animationSpec = tween(180)) } },
                onHistory = { scope.launch { pager.animateScrollToPage(2, animationSpec = tween(180)) } },
                horizontalInset = horizontalInset) }
            if (state == null) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Orange)
                }
            } else {
                HorizontalPager(
                    state = pager,
                    modifier = Modifier.weight(1f).then(paneModifier),
                    beyondViewportPageCount = 4
                ) { page ->
                    when (page) {
                        0 -> PortfolioPage(state,
                            onCategory = { scope.launch { pager.animateScrollToPage(1) } },
                            onQuick = { quickActions = true },
                            onAdd = { form = FormKind.NEW_SECURITY },
                            onSecurity = { selectedDetail = it }, horizontalInset = horizontalInset)
                        1 -> AssetsPage(state, active = pager.currentPage == 1, onAdd = { form = FormKind.NEW_SECURITY }, onSecurity = { selectedDetail = it }, horizontalInset = horizontalInset)
                        2 -> OperationsPage(state, onDelete = { kind, id -> deleteItem = kind to id }, horizontalInset = horizontalInset)
                        3 -> AnalyticsPage(state, horizontalInset = horizontalInset)
                        else -> SettingsPage(state, horizontalInset = horizontalInset)
                    }
                }
            }
            AnimatedVisibility(visible = pager.currentPage != 2,
                enter = androidx.compose.animation.fadeIn(tween(160)) + androidx.compose.animation.expandVertically(tween(160)),
                exit = androidx.compose.animation.fadeOut(tween(160)) + androidx.compose.animation.shrinkVertically(tween(160))) {
              Box(paneModifier) {
                NavigationGlass(pager.currentPage, onSelect = { page ->
                    scope.launch { pager.animateScrollToPage(page, animationSpec = tween(200)) }
                })
              }
            }
        }
        SnackbarHost(hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(bottom = 88.dp))
    }


    if (quickActions) {
        ModalBottomSheet(
            onDismissRequest = { quickActions = false },
            sheetState = quickSheet,
            sheetMaxWidth = GlassSizes.maxContentWidth,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            containerColor = Color.Transparent, tonalElevation = 0.dp,
            scrimColor = Color.Black.copy(alpha = .58f),
            dragHandle = null
        ) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(Color(0xFA202220)).glassSurface(corner = 28f, warm = true)
                .drawWithCache {
                    val shade = Brush.verticalGradient(0f to Color.Transparent,
                        .2f to Color(0x30100B08), .85f to Color(0x59100B08), 1f to Color(0x0D100B08))
                    val reflection = Path().apply {
                        moveTo(size.width, size.height * .18f); lineTo(size.width, size.height * .39f)
                        lineTo(0f, size.height * .91f); lineTo(0f, size.height * .72f); close()
                    }
                    val light = Brush.linearGradient(listOf(Color(0x0DFFFFFF), Color(0x04FFD6BA), Color.Transparent))
                    onDrawBehind { drawRect(shade); drawPath(reflection, light) }
                }.verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 82.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.fillMaxWidth().height(22.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.offset(y = 8.dp).size(width = 54.dp, height = 5.dp).clip(CircleShape)
                        .background(Brush.verticalGradient(listOf(Color(0xFFCFD0CD), Color(0xFF888B87)))))
                }
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Быстрые действия", color = StrongText, fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    IconButton(onClick = { closeQuick() }, modifier = Modifier.size(38.dp)
                        .clip(CircleShape).background(Color.White.copy(alpha = .10f))
                        .border(.7.dp, Color.White.copy(alpha = .30f), CircleShape)) {
                        Icon(Icons.Rounded.Close, "Закрыть меню", tint = StrongText, modifier = Modifier.size(24.dp))
                    }
                }
                ActionEntry("Купить бумагу", Icons.Outlined.Add) { closeQuick { form = FormKind.BUY } }
                ActionEntry("Продать", Icons.Outlined.RemoveCircleOutline) { closeQuick { form = FormKind.SELL } }
                ActionEntry("Добавить дивиденд", Icons.Outlined.Payments, "Дивиденды") { closeQuick { form = FormKind.DIVIDEND } }
                ActionEntry("Добавить купон", Icons.Outlined.ConfirmationNumber) { closeQuick { form = FormKind.COUPON } }
                ActionEntry("Пополнить счёт", Icons.Outlined.AccountBalanceWallet) { closeQuick { walletAction = "Пополнение счёта" } }
                ActionEntry("Вывести средства", Icons.Outlined.FileUpload) { closeQuick { walletAction = "Вывод средств" } }
            }
        }
    }

    if (walletAction != null) {
        AlertDialog(onDismissRequest = { walletAction = null },
            shape = RoundedCornerShape(GlassSizes.cardCorner), containerColor = Frost,
            title = { Text(walletAction.orEmpty(), color = StrongText) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Откройте «Кошелёк» и выберите брокерский счёт. Пополнение и вывод выполняются там.", color = SoftText)
                    if (state?.accounts?.isNotEmpty() == true) {
                        Text("Счета из Кошелька: " + state.accounts.joinToString { it.name }, color = StrongText)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { walletAction = null }) { Text("Понятно", color = Orange) } })
    }

    if (form != null && state != null) {
        ModalBottomSheet(onDismissRequest = { form = null; chosenSecurity = null }, containerColor = Color(0xFA202322)) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                InvestmentForm(form!!, state, chosenSecurity, onClose = { form = null; chosenSecurity = null }, vm = vm)
            }
        }
    }

    if (selectedDetail != null && state != null) {
        val item = state.securities.firstOrNull { it.id == selectedDetail }
        if (item != null) {
            ModalBottomSheet(onDismissRequest = { selectedDetail = null }, containerColor = Color(0xFF202322)) {
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

@Composable private fun TopBar(
    onMenu: () -> Unit, onSearch: () -> Unit, onHistory: () -> Unit,
    horizontalInset: androidx.compose.ui.unit.Dp
) {
    Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = horizontalInset - 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onMenu, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Rounded.Menu, "Меню действий", tint = StrongText, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(20.dp))
        Text("Инвестиции", color = StrongText, fontWeight = FontWeight.Medium,
            fontSize = 20.sp, modifier = Modifier.weight(1f).offset(y = 2.dp))
        IconButton(onClick = onSearch, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Rounded.Search, "Найти бумагу", tint = StrongText, modifier = Modifier.size(24.dp))
        }
        IconButton(onClick = onHistory, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Rounded.NotificationsNone, "События портфеля", tint = StrongText, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable private fun NavigationGlass(selected: Int, onSelect: (Int) -> Unit) {
    val shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
    val largeFont = androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.2f
    Box(Modifier.fillMaxWidth().padding(top = 6.dp), contentAlignment = Alignment.Center) {
        BoxWithConstraints(Modifier.widthIn(max = GlassSizes.maxNavigationWidth).fillMaxWidth()
            .shadow(12.dp, shape).clip(shape).glassSurface(corner = 24f)) {
            val cell = maxWidth / 5
            val indicator by animateDpAsState(cell * selected, tween(180), label = "navigation-position")
            Box(Modifier.offset { IntOffset(indicator.roundToPx(), 0) }.width(cell).height(68.dp)
                .background(Brush.radialGradient(listOf(Orange.copy(alpha = .19f), Color.Transparent))))
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 9.dp, bottom = 20.dp)) {
                labels.forEachIndexed { index, title ->
                    val tint by animateColorAsState(if (index == selected) Orange else StrongText,
                        tween(160), label = "navigation-tint")
                    val caption = if (largeFont) when (title) {
                        "Портфель" -> "Порт\u00adфель"
                        "Операции" -> "Опера\u00adции"
                        "Аналитика" -> "Анали\u00adтика"
                        else -> title
                    } else title
                    Column(Modifier.weight(1f).heightIn(min = if (largeFont) 76.dp else 58.dp)
                        .clip(RoundedCornerShape(15.dp)).glassClick { onSelect(index) }
                        .padding(top = if (largeFont) 5.dp else 1.dp, bottom = if (largeFont) 5.dp else 9.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                            if (index == 0) CategoryGlyph("Акции", tint)
                            else Icon(tabIcons[index], null, tint = tint, modifier = Modifier.size(24.dp))
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(caption, color = tint, fontSize = 10.sp, lineHeight = 12.sp,
                            modifier = Modifier.padding(horizontal = if (largeFont) 4.dp else 0.dp),
                            maxLines = if (largeFont) 2 else 1,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/** The visible pill stays compact; its touch target remains at least 48 dp. */
@Composable internal fun PremiumChip(selected: Boolean, title: String, onClick: () -> Unit) {
    val progress by animateFloatAsState(if (selected) 1f else 0f, tween(160), label = "filter-highlight")
    val shape = RoundedCornerShape(18.dp)
    Box(Modifier.heightIn(min = 48.dp).glassClick(onClick), contentAlignment = Alignment.Center) {
        Box(Modifier.heightIn(min = 34.dp).clip(shape).glassSurface(corner = 18f)
            .background(Brush.verticalGradient(listOf(OrangeLight.copy(alpha = progress), Orange.copy(alpha = progress))))
            .border(.8.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = .56f), OrangeLight.copy(alpha = .20f))), shape)
            .padding(horizontal = 16.dp, vertical = 7.dp)) {
            Text(title, color = androidx.compose.ui.graphics.lerp(StrongText, Color(0xFF201400), progress),
                fontSize = 12.sp, lineHeight = 18.sp, fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal)
        }
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
        contentPadding = PaddingValues(start = horizontalInset, end = horizontalInset, top = 10.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(GlassSizes.sectionGap)
    ) {
        item { PortfolioHero(summary) }
        item {
            CategoryRow("Акции", Icons.Rounded.BarChart, Orange,
                categoryValue(summary, "STOCK"), categoryPerformance(summary, "STOCK"), onCategory)
        }
        item {
            CategoryRow("Облигации", Icons.Rounded.Layers, Color(0xFFBE86DD),
                categoryValue(summary, "BOND"), categoryPerformance(summary, "BOND"), onCategory)
        }
        item {
            CategoryRow("Фонды", Icons.Rounded.PieChart, Color(0xFF25BBEF),
                categoryValue(summary, "FUND"), categoryPerformance(summary, "FUND"), onCategory)
        }
        item {
            CategoryRow("Дивиденды", Icons.Rounded.Payments, Green, summary.dividendsMinor,
                if (summary.dividendsMinor != 0L) "Получено" else "Нет выплат", onCategory)
        }
        item {
            CategoryRow("Купоны", Icons.Rounded.ConfirmationNumber, Orange, summary.couponsMinor,
                if (summary.couponsMinor != 0L) "Получено" else "Нет выплат", onCategory)
        }
        item {
            GlassActionButton("Быстрое действие", onClick = onQuick,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        }
        item { Spacer(Modifier.height(28.dp)) }
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
    val profit = summary.unrealizedMinor + summary.realizedMinor + summary.dividendsMinor + summary.couponsMinor
    Box(Modifier.fillMaxWidth().heightIn(min = 162.dp).padding(horizontal = 12.dp)) {
        Box(Modifier.align(Alignment.BottomEnd).width(145.dp).height(95.dp)
            .background(Brush.radialGradient(listOf(Orange.copy(alpha = .10f), Color.Transparent)))) {
            Text("История стоимости\nпока недоступна", color = SoftText.copy(alpha = .68f),
                fontSize = 10.sp, lineHeight = 14.sp, modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 15.dp))
        }
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Стоимость портфеля", color = StrongText, fontSize = 15.sp)
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Rounded.Info, "Рыночная стоимость по введённым ценам", tint = SoftText, modifier = Modifier.size(15.dp))
            }
            Spacer(Modifier.height(3.dp))
            AnimatedContent(targetState = summary.marketValueMinor, label = "portfolio-total", transitionSpec = {
                (androidx.compose.animation.fadeIn(tween(160)) +
                    androidx.compose.animation.slideInVertically(tween(180)) { it / 4 }) togetherWith
                    androidx.compose.animation.fadeOut(tween(100))
            }) { current ->
                Text(MoneyMath.formatMoney(current), color = StrongText,
                    fontSize = 36.sp, lineHeight = 43.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(signedMoney(summary.unrealizedMinor),
                    color = if (summary.unrealizedMinor >= 0L) Green else Red,
                    fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(" по позициям", color = StrongText, fontSize = 12.sp)
            }
            Spacer(Modifier.height(18.dp))
            Text("Прибыль", color = SoftText, fontSize = 15.sp)
            Text(signedMoney(profit), color = if (profit >= 0) Green else Red,
                fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }
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
    val shape = RoundedCornerShape(21.dp)
    val largeFont = androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.2f
    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).shadow(5.dp, shape)
        .clip(shape).glassSurface().glassClick(onClick)
        .drawWithCache {
            val localGlow = Brush.radialGradient(listOf(tint.copy(alpha = .10f), Color.Transparent),
                center = Offset(40.dp.toPx(), 32.dp.toPx()), radius = 100.dp.toPx())
            onDrawBehind { drawRect(localGlow) }
        }
        .padding(start = 18.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        GlassBadge(icon, tint, Modifier.size(44.dp), glyph = title)
        Spacer(Modifier.width(18.dp))
        if (largeFont) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, color = StrongText, fontSize = 16.sp, maxLines = 2)
                Text(MoneyMath.formatMoney(minor), color = StrongText, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text(supporting, color = if (supporting.startsWith("+")) Green
                    else if (supporting.startsWith("−") || supporting.startsWith("-")) Red else SoftText,
                    fontSize = 14.sp)
            }
        } else {
        Text(title, color = StrongText, fontWeight = FontWeight.Medium, fontSize = 16.sp,
            modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Column(horizontalAlignment = Alignment.End) {
            Text(MoneyMath.formatMoney(minor), color = StrongText, fontWeight = FontWeight.Medium, fontSize = 16.sp)
            Text(supporting, color = if (supporting.startsWith("+")) Green
                else if (supporting.startsWith("−") || supporting.startsWith("-")) Red else SoftText,
                fontSize = 14.sp, lineHeight = 19.sp)
        }
        }
        Spacer(Modifier.width(5.dp))
        Icon(Icons.Rounded.ChevronRight, null, tint = StrongText, modifier = Modifier.size(18.dp))
    }
}

@Composable private fun GlassBadge(
    icon: ImageVector, tint: Color, modifier: Modifier = Modifier.size(42.dp), glyph: String? = null,
    vivid: Boolean = false
) {
    Box(modifier.clip(CircleShape).background(Color.Black.copy(alpha = .16f))
        .background(Brush.radialGradient(listOf(tint.copy(alpha = if (vivid) .48f else .30f),
            tint.copy(alpha = if (vivid) .19f else .10f))))
        .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = .04f), Color.Transparent)))
        .border(.6.dp, tint.copy(alpha = .16f), CircleShape), contentAlignment = Alignment.Center) {
        if (glyph == null) Icon(icon, null, tint = tint, modifier = Modifier.size(if (vivid) 30.dp else 27.dp))
        else CategoryGlyph(glyph, tint)
    }
}

@Composable private fun CategoryGlyph(kind: String, tint: Color) {
    Canvas(Modifier.size(28.dp)) {
        val u = size.width / 28f
        val stroke = Stroke(1.8f * u, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
        when (kind) {
            "Акции" -> listOf(10f, 18f, 26f).forEachIndexed { index, h ->
                drawRoundRect(tint, Offset((3f + index * 9f) * u, (27f - h) * u),
                    Size(6f * u, h * u), androidx.compose.ui.geometry.CornerRadius(3f * u))
            }
            "Облигации" -> {
                val top = Path().apply {
                    moveTo(14*u, 3*u); lineTo(26*u, 9*u); lineTo(14*u, 15*u); lineTo(2*u, 9*u); close()
                }
                drawPath(top, tint, style = stroke)
                listOf(15f, 21f).forEach { y ->
                    val layer = Path().apply { moveTo(2*u, y*u); lineTo(14*u, (y+6)*u); lineTo(26*u, y*u) }
                    drawPath(layer, tint, style = stroke)
                }
            }
            "Фонды" -> {
                drawArc(tint, 0f, 270f, true, Offset(1*u,1*u), Size(26*u,26*u))
                drawArc(tint.copy(alpha = .58f), -90f, 90f, true, Offset(1*u,1*u), Size(26*u,26*u))
                drawLine(Color(0xFF174155), Offset(14*u, 1*u), Offset(14*u,14*u), 1.8f*u)
                drawLine(Color(0xFF174155), Offset(14*u,14*u), Offset(27*u,14*u), 1.8f*u)
            }
            "Дивиденды" -> {
                listOf(15f, 10f, 5f).forEach { y ->
                    drawOval(tint.copy(alpha = .12f), Offset(4*u,y*u), Size(20*u,9*u))
                    drawOval(tint, Offset(4*u,y*u), Size(20*u,9*u), style = stroke)
                }
                drawLine(tint, Offset(4*u,9*u), Offset(4*u,20*u), 1.8f*u)
                drawLine(tint, Offset(24*u,9*u), Offset(24*u,20*u), 1.8f*u)
            }
            else -> rotate(-22f) {
                val ticket = Path().apply {
                    moveTo(5*u,6*u); lineTo(23*u,6*u); quadraticBezierTo(26*u,6*u,26*u,9*u)
                    lineTo(26*u,11*u); quadraticBezierTo(21*u,14*u,26*u,17*u)
                    lineTo(26*u,20*u); quadraticBezierTo(26*u,23*u,23*u,23*u)
                    lineTo(5*u,23*u); quadraticBezierTo(2*u,23*u,2*u,20*u)
                    lineTo(2*u,17*u); quadraticBezierTo(7*u,14*u,2*u,11*u)
                    lineTo(2*u,9*u); quadraticBezierTo(2*u,6*u,5*u,6*u); close()
                }
                drawPath(ticket, tint, style = stroke)
            }
        }
    }
}

@Composable private fun AssetsPage(
    state: InvestmentsRepository.DashboardState, active: Boolean, onAdd: () -> Unit,
    onSecurity: (String) -> Unit, horizontalInset: androidx.compose.ui.unit.Dp
) {
    var kind by rememberSaveable { mutableStateOf("ALL") }
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = state.summary.positions.filter {
        val matchesKind = if (kind == "ARCHIVE") it.security.archived
        else !it.security.archived && (kind == "ALL" || it.security.kind == kind)
        matchesKind && (query.isBlank() || it.security.name.contains(query, ignoreCase = true) ||
            it.security.ticker.contains(query, ignoreCase = true))
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
            OutlinedTextField(query, { query = it }, enabled = active, placeholder = { Text("Название или тикер") },
                leadingIcon = { Icon(Icons.Rounded.Search, null, tint = SoftText) },
                singleLine = true, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth())
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
    val largeFont = androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.2f
    val iconTone = when (position.security.kind) {
        "BOND" -> Color(0xFFBE86DD)
        "FUND" -> Color(0xFF25BBEF)
        else -> Orange
    }
    GlassCard(Modifier.fillMaxWidth().glassClick(onClick), compact = true) {
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
                    fontSize = 14.sp, maxLines = if (largeFont) 2 else 1, overflow = TextOverflow.Ellipsis)
                Text("${position.security.ticker} · ${MoneyMath.formatQuantity(position.quantityMicros)} шт.",
                    fontSize = 11.sp, color = SoftText)
                if (largeFont) {
                    Spacer(Modifier.height(4.dp))
                    Text(MoneyMath.formatMoney(position.currentValueMinor), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(signedMoney(position.unrealizedMinor), fontSize = 12.sp,
                        color = if (position.unrealizedMinor >= 0L) Green else Red)
                }
            }
            if (!largeFont) {
              Spacer(Modifier.width(8.dp))
              Column(horizontalAlignment = Alignment.End) {
                Text(MoneyMath.formatMoney(position.currentValueMinor), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(signedMoney(position.unrealizedMinor), fontSize = 12.sp,
                    color = if (position.unrealizedMinor >= 0L) Green else Red)
              }
            }
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = SoftText, modifier = Modifier.size(17.dp))
        }
    }
}

private data class Operation(
    val id: String, val source: String, val kind: String, val title: String,
    val detail: String, val time: Long, val delta: Long
)

@Composable private fun HistoryTopBar(onBack: () -> Unit, horizontalInset: androidx.compose.ui.unit.Dp) {
    Box(Modifier.fillMaxWidth().height(56.dp)) {
        Text("История операций", color = StrongText, fontSize = 20.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.align(Alignment.Center).offset(y = 2.dp))
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)
            .padding(start = horizontalInset - 4.dp).size(48.dp)) {
            Icon(Icons.Rounded.ArrowBack, "Назад к портфелю", tint = StrongText, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable private fun OperationsPage(
    state: InvestmentsRepository.DashboardState, onDelete: (String, String) -> Unit,
    horizontalInset: androidx.compose.ui.unit.Dp
) {
    var filter by rememberSaveable { mutableStateOf("ALL") }
    val operations = remember(state.trades, state.income, state.securities) {
        val securities = state.securities.associateBy { it.id }
        (state.trades.filterNot { it.deleted }.map { trade ->
            val delta = MoneyMath.totalMinor(trade.unitPriceMinor, trade.quantityMicros)
            Operation(trade.id, "trade", trade.side, if (trade.side == "BUY") "Покупка" else "Продажа",
                "${securities[trade.securityId]?.name ?: "Бумага"} · ${MoneyMath.formatQuantity(trade.quantityMicros)} шт.",
                trade.happenedAt, if (trade.side == "BUY") -(delta + trade.feeMinor) else delta - trade.feeMinor)
        } + state.income.filterNot { it.deleted }.map { income ->
            Operation(income.id, "income", income.kind,
                if (income.kind == "DIVIDEND") "Выплата дивидендов" else "Выплата купона",
                securities[income.securityId]?.name ?: "Бумага", income.happenedAt, income.amountMinor)
        }).sortedWith(compareByDescending<Operation> { it.time }.thenByDescending { it.id })
    }
    val visible = remember(operations, filter) { operations.filter {
        filter == "ALL" || it.kind == filter || (filter == "INCOME" && it.source == "income")
    } }
    val zone = java.time.ZoneId.systemDefault()
    val today = java.time.LocalDate.now(zone)
    val groups = visible.groupBy { java.time.Instant.ofEpochMilli(it.time).atZone(zone).toLocalDate() }
    val dateFormat = remember { java.time.format.DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("ru-RU")) }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.forLanguageTag("ru-RU")) }
    LazyColumn(Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = horizontalInset, end = horizontalInset, top = 18.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf("ALL" to "Все", "BUY" to "Покупки", "SELL" to "Продажи", "INCOME" to "Выплаты").forEach { (code, text) ->
                    PremiumChip(selected = filter == code, title = text, onClick = { filter = code })
                }
            }
        }
        if (visible.isEmpty()) item { EmptyState("История пуста", "Добавьте покупку, продажу или выплату.") }
        items(groups.entries.toList(), key = { "date-${it.key}" }) { (date, list) ->
            Column(Modifier.fillMaxWidth().animateItem().padding(top = 5.dp, bottom = 8.dp)) {
                val prefix = when (date) { today -> "Сегодня, "; today.minusDays(1) -> "Вчера, "; else -> "" }
                Text(prefix + date.format(dateFormat) + if (date.year != today.year) " ${date.year}" else "",
                    color = StrongText, fontSize = 15.sp, modifier = Modifier.padding(start = 4.dp))
                Spacer(Modifier.height(10.dp))
                Column(Modifier.fillMaxWidth().animateContentSize(tween(180))
                    .shadow(5.dp, RoundedCornerShape(19.dp)).clip(RoundedCornerShape(19.dp))
                    .glassSurface(corner = 19f).padding(horizontal = 8.dp, vertical = 2.dp)) {
                    list.forEachIndexed { index, op ->
                        key(op.source, op.id) {
                            val appearance = remember { androidx.compose.animation.core.MutableTransitionState(false).apply { targetState = true } }
                            AnimatedVisibility(visibleState = appearance,
                                enter = androidx.compose.animation.fadeIn(tween(180)) + androidx.compose.animation.expandVertically(tween(180))) {
                                OperationRow(op, timeFormat) { onDelete(op.source, op.id) }
                            }
                        }
                        if (index != list.lastIndex) HorizontalDivider(Modifier.padding(start = 54.dp),
                            thickness = .6.dp, color = Color.White.copy(alpha = .16f))
                    }
                }
            }
        }
    }
}

@Composable private fun OperationRow(op: Operation, timeFormat: SimpleDateFormat, onClick: () -> Unit) {
    val largeFont = androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.2f
    val tint = when (op.kind) { "SELL" -> Red; "COUPON" -> Orange; else -> Green }
    val icon = when (op.kind) {
        "BUY" -> Icons.Rounded.ArrowDownward
        "SELL" -> Icons.Rounded.ArrowUpward
        "DIVIDEND" -> Icons.Rounded.Percent
        else -> Icons.Outlined.ConfirmationNumber
    }
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).glassClick(onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        GlassBadge(icon, tint, Modifier.size(42.dp), vivid = true)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(op.title, color = StrongText, fontSize = 16.sp, fontWeight = FontWeight.Normal,
                maxLines = if (largeFont) 2 else 1, overflow = TextOverflow.Ellipsis)
            Text(op.detail, color = StrongText.copy(alpha = .86f), fontSize = 13.sp, lineHeight = 18.sp,
                maxLines = if (largeFont) 2 else 1, overflow = TextOverflow.Ellipsis)
            if (largeFont) Text(signedMoney(op.delta), color = if (op.delta >= 0) Green else StrongText,
                fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.width(6.dp))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            if (!largeFont) Text(signedMoney(op.delta), color = if (op.delta >= 0) Green else StrongText,
                fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(timeFormat.format(Date(op.time)), color = StrongText.copy(alpha = .86f), fontSize = 13.sp)
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
                        Breakdown("Облигации", types[1], Color(0xFFBE86DD))
                        Breakdown("Фонды", types[2], Color(0xFF25BBEF))
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
                        Breakdown("Купоны", summary.couponsMinor, Color(0xFFBE86DD))
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
    val palette = listOf(Orange, Color(0xFFBE86DD), Color(0xFF25BBEF))
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
        item { Text("Инвестиции ${BuildConfig.VERSION_NAME} · Android 8.0+", color = SoftText, fontSize = 12.sp,
            modifier = Modifier.padding(top = 8.dp)) }
    }
}

@Composable private fun ActionEntry(
    title: String, icon: ImageVector, glyph: String? = null, action: () -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    Row(Modifier.fillMaxWidth().heightIn(min = 50.dp).shadow(5.dp, shape)
        .clip(shape).glassSurface(corner = 18f, warm = true).glassClick(action)
        .padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        GlassBadge(icon, Orange, Modifier.size(36.dp), glyph)
        Spacer(Modifier.width(14.dp))
        Text(title, color = StrongText, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium,
            fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.width(6.dp))
        Icon(Icons.Rounded.ChevronRight, null, tint = StrongText, modifier = Modifier.size(20.dp))
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
            GlassActionButton("Купить", { onTrade(FormKind.BUY) }, Modifier.weight(1f))
            GlassActionButton("Продать", { onTrade(FormKind.SELL) }, Modifier.weight(1f), icon = Icons.Outlined.Remove)
        }
        ActionEntry("Изменить цену", Icons.Outlined.Edit, action = onPrice)
        TextButton(onClick = onArchive, modifier = Modifier.fillMaxWidth()) { Text(if (asset.archived) "Вернуть из архива" else "Архивировать бумагу") }
    }
}

private fun kindLabel(kind: String) = when (kind) { "STOCK" -> "Акция"; "BOND" -> "Облигация"; else -> "Фонд" }
