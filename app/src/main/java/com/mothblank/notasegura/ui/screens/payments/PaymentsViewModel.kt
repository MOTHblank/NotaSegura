package com.mothblank.notasegura.ui.screens.payments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mothblank.notasegura.domain.model.Payment
import com.mothblank.notasegura.domain.model.RecurringPaymentPlan
import com.mothblank.notasegura.domain.repository.PaymentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class PaymentListTab { PENDING, RECURRING, HISTORY }

data class PaymentsOverview(
    val payments: List<Payment> = emptyList(),
    val plans: List<RecurringPaymentPlan> = emptyList()
) {
    val pending: List<Payment>
        get() = payments.filterNot { it.isPaid }.sortedBy { it.dueDate }
    val paid: List<Payment>
        get() = payments.filter { it.isPaid }.sortedWith(
            compareByDescending<Payment> { it.paidAt ?: it.dueDate }.thenBy { it.title }
        )
    val pendingTotal: Long get() = pending.sumOf { it.amountCents }
    val overdueCount: Int get() = pending.count { it.dueDate.isBefore(LocalDate.now()) }
}

class PaymentsViewModel(private val repository: PaymentRepository) : ViewModel() {
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _tab = MutableStateFlow(PaymentListTab.PENDING)
    val tab = _tab.asStateFlow()

    val uiState: StateFlow<PaymentsOverview> =
        combine(repository.getAllPayments(), repository.getRecurringPlans(), _searchQuery) {
                payments, plans, query ->
            val q = query.trim()
            PaymentsOverview(
                payments = payments.filter { it.title.contains(q, ignoreCase = true) },
                plans = plans.filter { it.title.contains(q, ignoreCase = true) }
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = PaymentsOverview()
        )

    init {
        viewModelScope.launch {
            repository.refreshOccurrences(LocalDate.now().plusMonths(1))
        }
    }

    fun selectTab(tab: PaymentListTab) { _tab.value = tab }
    fun onSearchQueryChange(query: String) { _searchQuery.value = query }

    fun togglePaidStatus(payment: Payment) {
        viewModelScope.launch {
            repository.setPaidStatus(payment, !payment.isPaid, LocalDate.now())
        }
    }
}
