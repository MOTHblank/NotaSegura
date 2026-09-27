package com.mothblank.notasegura.ui.screens.payments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mothblank.notasegura.domain.model.Payment
import com.mothblank.notasegura.domain.repository.PaymentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

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
            started = SharingStarted.WhileSubscribed(5_000L),
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
            repository.setPaidStatus(
                payment = payment,
                isPaid = !payment.isPaid,
                date = LocalDate.now()
            )
        }
    }
}
