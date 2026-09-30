package com.mothblank.notasegura.ui.screens.payment_details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mothblank.notasegura.domain.model.Payment
import com.mothblank.notasegura.domain.repository.PaymentRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class PaymentDetailsUiState(
    val isLoading: Boolean = true,
    val payment: Payment? = null
)

class PaymentDetailsViewModel(
    private val repository: PaymentRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val paymentId: String = checkNotNull(savedStateHandle["paymentId"])

    val uiState = repository.getAllPayments()
        .map { payments ->
            PaymentDetailsUiState(
                isLoading = false,
                payment = payments.firstOrNull { it.id == paymentId }
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = PaymentDetailsUiState()
        )

    private val _deleted = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val deleted = _deleted.asSharedFlow()

    fun togglePaidStatus() {
        val payment = uiState.value.payment ?: return
        viewModelScope.launch {
            repository.setPaidStatus(
                payment = payment,
                isPaid = !payment.isPaid,
                date = LocalDate.now()
            )
        }
    }

    fun deletePayment() {
        val payment = uiState.value.payment ?: return
        viewModelScope.launch {
            repository.deletePayment(payment)
            _deleted.emit(Unit)
        }
    }
}
