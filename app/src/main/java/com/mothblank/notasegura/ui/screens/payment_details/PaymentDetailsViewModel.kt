package com.mothblank.notasegura.ui.screens.payment_details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mothblank.notasegura.domain.model.Payment
import com.mothblank.notasegura.domain.model.RecurringPaymentPlan
import com.mothblank.notasegura.domain.repository.PaymentRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class PaymentDetailsUiState(
    val isLoading: Boolean = true,
    val payment: Payment? = null,
    val plan: RecurringPaymentPlan? = null,
    val series: List<Payment> = emptyList()
)

class PaymentDetailsViewModel(
    private val repository: PaymentRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val paymentId: String = checkNotNull(savedStateHandle["paymentId"])

    val uiState = combine(
        repository.getAllPayments(), repository.getRecurringPlans()
    ) { payments, plans ->
        val payment = payments.firstOrNull { it.id == paymentId }
        PaymentDetailsUiState(
            isLoading = false,
            payment = payment,
            plan = payment?.seriesId?.let { id -> plans.firstOrNull { it.id == id } },
            series = payment?.seriesId?.let { id ->
                payments.filter { it.seriesId == id }.sortedByDescending { it.dueDate }
            }.orEmpty()
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = PaymentDetailsUiState()
    )

    private val _deleted = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val deleted = _deleted.asSharedFlow()

    init { viewModelScope.launch { repository.refreshOccurrences(LocalDate.now().plusMonths(1)) } }

    fun togglePaidStatus() {
        val payment = uiState.value.payment ?: return
        viewModelScope.launch {
            repository.setPaidStatus(payment, !payment.isPaid, LocalDate.now())
        }
    }

    fun deletePayment(includeFuture: Boolean = false) {
        val payment = uiState.value.payment ?: return
        viewModelScope.launch {
            repository.removePayment(payment, includeFuture)
            _deleted.emit(Unit)
        }
    }
}
