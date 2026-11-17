package com.mamoro.investments.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mamoro.investments.data.InvestmentDatabase
import com.mamoro.investments.data.InvestmentsRepository
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class InvestmentsViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = InvestmentsRepository(application, InvestmentDatabase.get(application))
    val dashboard = repo.dashboard.map { it as InvestmentsRepository.DashboardState? }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    fun clearMessage() { _message.value = null }

    private fun runAction(success: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }.fold(
                onSuccess = { _message.value = success },
                onFailure = { _message.value = it.message ?: "Не удалось выполнить действие" }
            )
        }
    }

    fun addSecurity(name: String, ticker: String, kind: String, priceMinor: Long) =
        runAction("Бумага добавлена") { repo.createSecurity(name, ticker, kind, priceMinor) }

    fun updatePrice(id: String, priceMinor: Long) =
        runAction("Цена обновлена") { repo.updatePrice(id, priceMinor) }

    fun archiveSecurity(id: String) =
        runAction("Статус бумаги изменён") { repo.archiveSecurity(id) }

    fun addTrade(securityId: String, side: String, quantityMicros: Long, priceMinor: Long,
                 feeMinor: Long, walletAccountId: String) =
        runAction("Сделка сохранена") {
            repo.createTrade(securityId, side, quantityMicros, priceMinor, feeMinor, walletAccountId)
        }

    fun addIncome(securityId: String, kind: String, amountMinor: Long,
                  note: String, walletAccountId: String) =
        runAction("Доход сохранён") {
            repo.createIncome(securityId, kind, amountMinor, note, walletAccountId)
        }

    fun deleteTrade(id: String) = runAction("Сделка удалена") { repo.deleteTrade(id) }
    fun deleteIncome(id: String) = runAction("Доход удалён") { repo.deleteIncome(id) }
}
