@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.mamoro.investments.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import com.mamoro.investments.data.*
import com.mamoro.investments.domain.MoneyMath
import com.mamoro.investments.ui.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

internal fun bondDate(day: Long?): String = day?.let { LocalDate.ofEpochDay(it).format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) } ?: "Не указана"
internal fun bondDecimal(value: String?, currency: String): String = value?.let {
    runCatching { BigDecimal(it).stripTrailingZeros().toPlainString().replace('.',',') }.getOrDefault(it) + " " + if (currency == "RUB") "₽" else currency
} ?: "Не объявлено"

@Composable internal fun BondDateField(label: String, day: Long?, onChange: (Long?) -> Unit,
    optional: Boolean = true, minDay: Long? = null, maxDay: Long? = null) {
    var choosing by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().glassSurface().glassClick { choosing = true }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.CalendarMonth,null,tint=Orange,modifier=Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label,color=SoftText,fontSize=12.sp,lineHeight=17.sp)
            Text(if (day == null) "Не указана" else bondDate(day),fontSize=15.sp,lineHeight=21.sp)
        }
        Icon(Icons.Outlined.Edit,"Выбрать: $label",tint=SoftText,modifier=Modifier.size(18.dp))
    }
    if (choosing) {
        val picker = rememberDatePickerState(initialSelectedDateMillis=day?.times(86400000L),
            selectableDates=object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val candidate = Math.floorDiv(utcTimeMillis,86400000L)
                    return (minDay == null || candidate >= minDay) && (maxDay == null || candidate <= maxDay)
                }
            })
        DatePickerDialog(onDismissRequest={ choosing=false }, confirmButton={
            TextButton(onClick={ picker.selectedDateMillis?.let { onChange(Math.floorDiv(it,86400000L)) }; choosing=false },
                enabled=picker.selectedDateMillis != null) { Text("Выбрать") }
        }, dismissButton={ Row {
            if (optional) TextButton(onClick={ onChange(null); choosing=false }) { Text("Неизвестна") }
            TextButton(onClick={ choosing=false }) { Text("Отмена") }
        } }) { DatePicker(state=picker,title={ Text(label,Modifier.padding(start=24.dp,top=16.dp),fontSize=16.sp,lineHeight=22.sp) }) }
    }
}

@Composable internal fun BondMetadata(reference: BondReference) {
    GlassCard(Modifier.fillMaxWidth(),compact=true) {
        Text(reference.name,fontWeight=FontWeight.SemiBold,fontSize=17.sp,lineHeight=23.sp)
        Text("${reference.ticker} · ${reference.isin}",color=SoftText,fontSize=12.sp,lineHeight=17.sp)
        Spacer(Modifier.height(10.dp))
        BondDataLine("Дата выпуска",bondDate(reference.issueDay))
        BondDataLine("Размещение",bondDate(reference.placementDay))
        BondDataLine("Погашение",if (reference.perpetual) "Бессрочная" else bondDate(reference.maturityDay))
        BondDataLine("Номинал",bondDecimal(reference.nominal,reference.currency))
        BondDataLine("НКД по справочнику",bondDecimal(reference.accruedInterest,reference.currency))
        BondDataLine("Купонов в год",reference.couponsPerYear.takeIf { it > 0 }?.toString() ?: "Не указано")
        BondDataLine("Тип купона",if (reference.floating) "Плавающий" else "По расписанию")
        if (reference.amortizing) Text("Предусмотрена амортизация номинала",color=OrangeLight,fontSize=12.sp,lineHeight=17.sp)
    }
}
@Composable internal fun BondDataLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical=4.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(label,color=SoftText,fontSize=12.sp,lineHeight=17.sp,modifier=Modifier.weight(1f))
        Text(value,fontSize=13.sp,lineHeight=18.sp,modifier=Modifier.weight(1f),fontWeight=FontWeight.Medium)
    }
}

@Composable internal fun BondConnectionCard(controller: BondController) {
    val state by controller.connection.collectAsState()
    var edit by remember { mutableStateOf(false) }
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Icon(Icons.Outlined.Link,null,tint=Orange)
            Spacer(Modifier.width(10.dp))
            Text("Т‑Инвестиции",fontWeight=FontWeight.SemiBold,fontSize=17.sp,lineHeight=23.sp)
        }
        Spacer(Modifier.height(8.dp))
        Text(if (state.saved) "Токен сохранён на устройстве" else "Справочник облигаций и расписание купонов",color=SoftText,fontSize=13.sp,lineHeight=18.sp)
        Text("Для подключения нужен токен «Только чтение». Данные о ваших сделках вводятся в приложении.",
            color=SoftText,fontSize=12.sp,lineHeight=17.sp,modifier=Modifier.padding(vertical=8.dp))
        GlassActionButton(if (state.saved) "Заменить токен" else "Подключить",{ edit=true },Modifier.fillMaxWidth(),icon=Icons.Outlined.Link)
        if (state.saved) Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            TextButton(onClick=controller::testConnection,enabled=!state.testing) { Text(if (state.testing) "Проверка…" else "Проверить связь") }
            TextButton(onClick=controller::forgetToken) { Text("Удалить токен",color=SoftText) }
        }
        state.message?.let { Text(it,color=SoftText,fontSize=12.sp,lineHeight=17.sp,modifier=Modifier.padding(top=8.dp)) }
    }
    if (edit) BondTokenDialog(controller) { edit=false }
}

@Composable internal fun BondTokenDialog(controller: BondController, onClose: () -> Unit) {
    var value by remember { mutableStateOf("") } // Never saved in instance state or screenshots.
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(onDismissRequest={ if (!saving) onClose() },containerColor=Frost,
        properties=DialogProperties(securePolicy=SecureFlagPolicy.SecureOn),
        title={ Text("Токен Т‑Инвестиций") },text={ Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("Создайте токен «Только чтение» в настройках API Т‑Инвестиций и вставьте его здесь. Токен шифруется ключом Android Keystore.",color=SoftText,fontSize=13.sp,lineHeight=18.sp)
            OutlinedTextField(value=value,onValueChange={ value=it; error=null },label={ Text("Токен API") },
                visualTransformation=PasswordVisualTransformation(),singleLine=true,modifier=Modifier.fillMaxWidth(),
                keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password,autoCorrectEnabled=false),colors=premiumFieldColors())
            error?.let { Text(it,color=Red,fontSize=12.sp,lineHeight=17.sp) }
        } },confirmButton={ TextButton(enabled=value.isNotBlank() && !saving,onClick={
            saving=true
            scope.launch {
                try { controller.saveToken(value); value=""; onClose() }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { error=e.message ?: "Не удалось сохранить токен" }
                finally { saving=false }
            }
        }) { Text(if (saving) "Сохранение…" else "Сохранить") } },dismissButton={ TextButton(onClick=onClose,enabled=!saving) { Text("Отмена") } })
}

@Composable internal fun BondSearchSheet(controller: BondController, localAsset: SecurityEntity?, onClose: () -> Unit, onSaved: (String) -> Unit) {
    val browser by controller.browser.collectAsState()
    val connection by controller.connection.collectAsState()
    var tokenDialog by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    BondSearchContent(browser,connection.saved,localAsset,controller::search,
        { controller.select(it,localAsset?.id) },controller::backToResults,{ tokenDialog=true },onClose,
        onSave={ price ->
            val download=browser.selected ?: return@BondSearchContent
            saving=true; error=null
            scope.launch {
                try { onSaved(controller.accept(download,localAsset?.id,price)) }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { error=e.message ?: "Не удалось добавить облигацию" }
                finally { saving=false }
            }
        },saving=saving,saveError=error)
    if (tokenDialog) BondTokenDialog(controller) { tokenDialog=false }
}

@Composable internal fun BondSearchContent(browser: BondBrowserState, connected: Boolean, localAsset: SecurityEntity?,
    onSearch: (String) -> Unit, onSelect: (String) -> Unit, onBack: () -> Unit, onConnect: () -> Unit,
    onClose: () -> Unit, onSave: (Long) -> Unit, saving: Boolean = false, saveError: String? = null) {
    var query by rememberSaveable { mutableStateOf(localAsset?.ticker.orEmpty()) }
    var price by remember(browser.selected?.reference?.uid) { mutableStateOf("") }
    var inputError by remember { mutableStateOf<String?>(null) }
    Column(Modifier.widthIn(max=720.dp).fillMaxWidth().heightIn(max=740.dp).imePadding()
        .verticalScroll(rememberScrollState()).padding(horizontal=18.dp).padding(bottom=28.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Text("Облигации",fontWeight=FontWeight.Bold,fontSize=23.sp,lineHeight=29.sp,modifier=Modifier.weight(1f))
            IconButton(onClick=onClose,enabled=!saving) { Icon(Icons.Outlined.Close,"Закрыть поиск") }
        }
        Text(if (localAsset == null) "Поиск в Т‑Инвестициях" else "Подключить: ${localAsset.name}",color=SoftText,fontSize=13.sp,lineHeight=18.sp)
        if (!connected) {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("Подключите справочник",fontWeight=FontWeight.SemiBold)
                Text("Добавьте токен «Только чтение», чтобы найти выпуск и загрузить даты выплат.",color=SoftText,fontSize=13.sp,lineHeight=18.sp,modifier=Modifier.padding(vertical=10.dp))
                GlassActionButton("Добавить токен",onConnect,Modifier.fillMaxWidth(),icon=Icons.Outlined.Link)
            }
        } else if (browser.selected == null) {
            OutlinedTextField(query,{ query=it },label={ Text("Название, тикер или ISIN") },singleLine=true,
                modifier=Modifier.fillMaxWidth(),colors=premiumFieldColors(),shape=RoundedCornerShape(GlassSizes.controlCorner))
            GlassActionButton("Найти",{ onSearch(query) },Modifier.fillMaxWidth(),icon=Icons.Outlined.Search,
                enabled=!browser.busy && query.trim().length in 2..160)
            if (browser.searched && browser.results.isEmpty() && browser.error == null) Text("Ничего не найдено. Уточните тикер или ISIN выпуска.",color=SoftText)
            browser.results.forEach { result ->
                GlassCard(Modifier.fillMaxWidth().glassClick { if (!browser.busy) onSelect(result.uid) },compact=true) {
                    Text(result.name,fontWeight=FontWeight.SemiBold,fontSize=16.sp,lineHeight=22.sp)
                    Text("${result.ticker} · ${result.isin}",color=SoftText,fontSize=12.sp,lineHeight=17.sp)
                }
            }
        } else {
            val selected = browser.selected
            TextButton(onClick=onBack,enabled=!saving) { Text("‹ Результаты поиска") }
            BondMetadata(selected.reference)
            Text("Купонов в расписании: ${selected.coupons.size}\nПериод: ${bondDate(selected.fromDay)} — ${bondDate(selected.throughDay)}",color=SoftText,fontSize=12.sp,lineHeight=17.sp)
            Text("НКД — справочная величина на дату загрузки. Купонный доход и погашение номинала учитываются отдельно. Котировки вводятся вручную.",color=SoftText,fontSize=12.sp,lineHeight=17.sp)
            if (localAsset == null) AmountField(price,{ price=it },"Текущая цена за облигацию, ₽")
            if (selected.reference.currency != "RUB") Text("Сейчас учёт поддерживает только рублёвые облигации.",color=OrangeLight,fontSize=13.sp,lineHeight=18.sp)
            GlassActionButton(if (saving) "Сохранение…" else if (localAsset == null) "Добавить облигацию" else "Подключить расписание",{
                try { val parsed=if (localAsset == null) MoneyMath.parseMoney(price) else localAsset.lastPriceMinor
                    require(parsed > 0 || localAsset != null) { "Введите текущую цену" }; inputError=null; onSave(parsed)
                } catch (e: Exception) { inputError=e.message ?: "Проверьте цену" }
            },Modifier.fillMaxWidth(),icon=Icons.Outlined.Check,enabled=!saving && selected.reference.currency == "RUB")
        }
        if (browser.busy) Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(Modifier.size(20.dp),color=Orange,strokeWidth=2.dp)
            Text("Загружаем данные и расписание…",color=SoftText,fontSize=13.sp,lineHeight=18.sp)
        }
        (saveError ?: inputError ?: browser.error)?.let { Text(it,color=Red,fontSize=13.sp,lineHeight=18.sp) }
        Text("Источник: Т‑Инвестиции · Справочник бумаг",color=SoftText,fontSize=11.sp,lineHeight=16.sp)
    }
}
