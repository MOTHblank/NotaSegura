package com.mothblank.notasegura.ui.screens.payments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mothblank.notasegura.domain.model.Payment
import com.mothblank.notasegura.domain.repository.PaymentRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.asStateFlow

class PaymentsViewModel(
    private val repository: PaymentRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _showOnlyPending = MutableStateFlow(false)
    val showOnlyPending = _showOnlyPending.asStateFlow()

    val uiState: StateFlow<List<Payment>> =
        combine(repository.getAllPayments(), _searchQuery, _showOnlyPending) { payments, query, onlyPending ->
            payments.filter { payment ->
                val matchesQuery = payment.title.contains(query, ignoreCase = true)
                val matchesStatus = !onlyPending || !payment.isPaid
                matchesQuery && matchesStatus
            }
        }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = emptyList()
            )

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onToggleShowOnlyPending() {
        _showOnlyPending.value = !_showOnlyPending.value
    }

    fun deletePayment(payment: Payment) {
        viewModelScope.launch {
            repository.deletePayment(payment)
        }
    }

    fun togglePaidStatus(payment: Payment) {
        viewModelScope.launch {
            val newIsPaid = !payment.isPaid
            repository.insertPayment(payment.copy(isPaid = newIsPaid))
            
            // Lógica de Recorrência: Se foi marcado como PAGO e é RECORRENTE
            if (newIsPaid && payment.isRecurring) {
                val nextMonthDate = payment.dueDate.plusMonths(1)
                
                // Verifica se já não existe um lembrete para o próximo mês (evita duplicatas)
                val alreadyExists = repository.existsPayment(payment.title, nextMonthDate)
                
                if (!alreadyExists) {
                    val nextPayment = payment.copy(
                        id = java.util.UUID.randomUUID().toString(),
                        dueDate = nextMonthDate,
                        isPaid = false
                    )
                    repository.insertPayment(nextPayment)
                }
            }
        }
    }
}
