package com.mothblank.notasegura.ui.screens.add_edit_payment

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mothblank.notasegura.domain.model.Payment
import com.mothblank.notasegura.domain.repository.PaymentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

data class AddEditPaymentUiState(
    val title: String = "",
    val amount: String = "",
    val dueDate: LocalDate? = null,
    val isPaid: Boolean = false,
    val isRecurring: Boolean = false,
    val isSaving: Boolean = false
)

class AddEditPaymentViewModel(
    private val repository: PaymentRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddEditPaymentUiState())
    val uiState = _uiState.asStateFlow()

    private val paymentId: String? = savedStateHandle["paymentId"]

    init {
        if (paymentId != null) {
            viewModelScope.launch {
                repository.getPaymentById(paymentId)?.let { payment ->
                    _uiState.update {
                        it.copy(
                            title = payment.title,
                            amount = payment.amount.toString(),
                            dueDate = payment.dueDate,
                            isPaid = payment.isPaid,
                            isRecurring = payment.isRecurring
                        )
                    }
                }
            }
        }
    }

    fun onTitleChange(newTitle: String) {
        _uiState.update { it.copy(title = newTitle) }
    }

    fun onAmountChange(newAmount: String) {
        // Only allow numbers and one decimal point
        if (newAmount.isEmpty() || newAmount.matches(Regex("""^\d*\.?\d*$"""))) {
            _uiState.update { it.copy(amount = newAmount) }
        }
    }

    fun onDueDateChange(newDate: LocalDate) {
        _uiState.update { it.copy(dueDate = newDate) }
    }

    fun onPaidChange(isPaid: Boolean) {
        _uiState.update { it.copy(isPaid = isPaid) }
    }

    fun onRecurringChange(isRecurring: Boolean) {
        _uiState.update { it.copy(isRecurring = isRecurring) }
    }

    fun formatDate(date: LocalDate?): String {
        return date?.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) ?: ""
    }

    fun savePayment() {
        val currentState = _uiState.value
        val amountValue = currentState.amount.toDoubleOrNull() ?: 0.0
        
        if (currentState.title.isBlank() || currentState.dueDate == null) {
            return
        }

        val paymentToSave = Payment(
            id = paymentId ?: UUID.randomUUID().toString(),
            title = currentState.title.trim(),
            amount = amountValue,
            dueDate = currentState.dueDate,
            isPaid = currentState.isPaid,
            isRecurring = currentState.isRecurring
        )

        viewModelScope.launch {
            repository.insertPayment(paymentToSave)
        }
    }
}
