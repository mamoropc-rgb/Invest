@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.mamoro.investments.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mamoro.investments.data.InvestmentsRepository
import com.mamoro.investments.domain.MoneyMath
import com.mamoro.investments.ui.theme.SoftText
import com.mamoro.investments.ui.theme.Orange
import com.mamoro.investments.ui.theme.OrangeLight
import com.mamoro.investments.ui.theme.GlassSizes
import androidx.compose.ui.graphics.Color

internal enum class FormKind { NEW_SECURITY, BUY, SELL, DIVIDEND, COUPON, PRICE }

@Composable
internal fun InvestmentForm(
    kind: FormKind, state: InvestmentsRepository.DashboardState,
    preselected: String?, onClose: () -> Unit, vm: InvestmentsViewModel
) {
    var name by remember(kind) { mutableStateOf("") }
    var ticker by remember(kind) { mutableStateOf("") }
    var securityKind by remember(kind) { mutableStateOf("STOCK") }
    var quantity by remember(kind) { mutableStateOf("") }
    var amount by remember(kind) { mutableStateOf("") }
    var fee by remember(kind) { mutableStateOf("0") }
    var note by remember(kind) { mutableStateOf("") }
    val available = state.securities.filterNot { it.archived }
    var securityId by remember(kind, preselected) { mutableStateOf(preselected ?: available.firstOrNull()?.id.orEmpty()) }
    var accountId by remember(kind) { mutableStateOf(state.accounts.firstOrNull()?.accountId.orEmpty()) }
    var error by remember(kind) { mutableStateOf<String?>(null) }

    val title = when (kind) {
        FormKind.NEW_SECURITY -> "Новая бумага"
        FormKind.BUY -> "Покупка бумаги"
        FormKind.SELL -> "Продажа бумаги"
        FormKind.DIVIDEND -> "Добавить дивиденд"
        FormKind.COUPON -> "Добавить купон"
        FormKind.PRICE -> "Изменить цену"
    }
    val selected = available.firstOrNull { it.id == securityId }

    Column(Modifier.widthIn(max = GlassSizes.maxContentWidth).fillMaxWidth().heightIn(max = 650.dp).verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp).padding(bottom = 35.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        when (kind) {
            FormKind.NEW_SECURITY -> {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Название") },
                    placeholder = { Text("ОФЗ 26242") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = premiumFieldColors())
                OutlinedTextField(value = ticker, onValueChange = { ticker = it }, label = { Text("Тикер / обозначение") },
                    placeholder = { Text("SU26242") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = premiumFieldColors())
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf("STOCK" to "Акция", "BOND" to "Облигация", "FUND" to "Фонд").forEach { (value, label) ->
                        FilterChip(selected = securityKind == value, onClick = { securityKind = value }, label = { Text(label) },
                            shape = RoundedCornerShape(12.dp),
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Orange.copy(alpha = .30f), selectedLabelColor = OrangeLight))
                    }
                }
                AmountField(amount, { amount = it }, "Текущая цена за бумагу, ₽")
            }
            FormKind.PRICE -> {
                Text("${selected?.name ?: "Бумага"} · ${selected?.ticker.orEmpty()}", color = SoftText)
                AmountField(amount, { amount = it }, "Новая цена за бумагу, ₽")
            }
            else -> {
                if (available.isEmpty()) {
                    Text("Сначала добавьте бумагу во вкладке «Активы».", color = MaterialTheme.colorScheme.error)
                } else {
                    SelectionField("Бумага", available.map { it.id to "${it.ticker} · ${it.name}" }, securityId, { securityId = it })
                    if (kind == FormKind.BUY || kind == FormKind.SELL) {
                        if (kind == FormKind.SELL) {
                            val held = state.summary.positions.firstOrNull { it.security.id == securityId }?.quantityMicros ?: 0
                            Text("Доступно: ${MoneyMath.formatQuantity(held)} шт.", color = SoftText)
                        }
                        OutlinedTextField(value = quantity, onValueChange = { quantity = it }, label = { Text("Количество, шт.") },
                            placeholder = { Text("10") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = premiumFieldColors())
                        AmountField(amount, { amount = it }, "Цена за бумагу, ₽")
                        AmountField(fee, { fee = it }, "Комиссия, ₽")
                    } else {
                        AmountField(amount, { amount = it }, "Сумма выплаты, ₽")
                        OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Примечание (необязательно)") },
                            modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(14.dp), colors = premiumFieldColors())
                    }
                    if (state.accounts.isNotEmpty()) {
                        SelectionField("Брокерский счёт из Кошелька", state.accounts.map { it.accountId to it.name }, accountId, { accountId = it })
                    } else {
                        Text("Брокерский счёт пока не подключён. Операция сохранится локально, передача денег в Кошелёк отложена.",
                            style = MaterialTheme.typography.bodySmall, color = SoftText)
                    }
                }
            }
        }
        if (error != null) Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
        Button(
            onClick = {
                val result = runCatching {
                    when (kind) {
                        FormKind.NEW_SECURITY -> {
                            require(name.isNotBlank() && ticker.isNotBlank()) { "Введите название и тикер" }
                            vm.addSecurity(name, ticker, securityKind, MoneyMath.parseMoney(amount))
                        }
                        FormKind.PRICE -> vm.updatePrice(requireNotNull(selected).id, MoneyMath.parseMoney(amount))
                        FormKind.BUY, FormKind.SELL -> {
                            val id = requireNotNull(selected) { "Выберите бумагу" }.id
                            vm.addTrade(id, if (kind == FormKind.BUY) "BUY" else "SELL",
                                MoneyMath.parseQuantity(quantity), MoneyMath.parseMoney(amount), MoneyMath.parseMoney(fee), accountId)
                        }
                        FormKind.DIVIDEND, FormKind.COUPON -> {
                            val id = requireNotNull(selected) { "Выберите бумагу" }.id
                            vm.addIncome(id, if (kind == FormKind.DIVIDEND) "DIVIDEND" else "COUPON",
                                MoneyMath.parseMoney(amount), note, accountId)
                        }
                    }
                }
                result.fold(onSuccess = { onClose() }, onFailure = { error = it.message ?: "Проверьте данные" })
            },
            enabled = (kind == FormKind.NEW_SECURITY || kind == FormKind.PRICE || available.isNotEmpty()),
            modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Rounded.Check, contentDescription = null)
            Spacer(Modifier.width(9.dp))
            Text("Сохранить", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable private fun AmountField(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) },
        placeholder = { Text("0,00") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp), colors = premiumFieldColors(),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal))
}

@Composable private fun SelectionField(
    label: String, options: List<Pair<String, String>>, selected: String, onChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = options.firstOrNull { it.first == selected }?.second.orEmpty(),
            onValueChange = {}, readOnly = true, label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            shape = RoundedCornerShape(14.dp), colors = premiumFieldColors()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, text) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { onChange(value); expanded = false })
            }
        }
    }
}

@Composable private fun premiumFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color(0xFFF9F7F8), unfocusedTextColor = Color(0xFFF9F7F8),
    focusedBorderColor = OrangeLight, unfocusedBorderColor = Color.White.copy(alpha = .30f),
    focusedContainerColor = Color(0x882C2B34), unfocusedContainerColor = Color(0x772B2A33)
)
