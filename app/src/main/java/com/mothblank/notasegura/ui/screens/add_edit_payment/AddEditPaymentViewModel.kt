package com.mothblank.notasegura.ui.screens.add_edit_payment

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mothblank.notasegura.domain.model.Payment
import com.mothblank.notasegura.domain.repository.PaymentRepository
import com.mothblank.notasegura.util.CurrencyUtils
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

data class AddEditPaymentUiState(
    val title: String = "",
    val amount: String = "",
    val amountError: String? = null,
    val dueDate: LocalDate? = null,
    val isPaid: Boolean = false,
    val isRecurring: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null
)

class AddEditPaymentViewModel(
    private val repository: PaymentRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddEditPaymentUiState())
    val uiState = _uiState.asStateFlow()

    private val _saved = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val saved = _saved.asSharedFlow()

    private val paymentId: String? = savedStateHandle["paymentId"]
    private var originalPayment: Payment? = null

    init {
        if (paymentId != null) {
            viewModelScope.launch {
                repository.getPaymentById(paymentId)?.let { payment ->
                    originalPayment = payment
                    _uiState.update {
                        it.copy(
                            title = payment.title,
                            amount = CurrencyUtils.centsToEditable(payment.amountCents),
                            dueDate = payment.dueDate,
                            isPaid = payment.isPaid,
                            isRecurring = payment.recurrenceMonths != null
                        )
                    }
                }
            }
        }
    }

    fun onTitleChange(value: String) = _uiState.update {
        it.copy(title = value, errorMessage = null)
    }

    fun onAmountChange(value: String) {
        if (CurrencyUtils.isValidEditableAmount(value)) {
            _uiState.update { it.copy(amount = value, amountError = null, errorMessage = null) }
        }
    }

    fun onDueDateChange(value: LocalDate) = _uiState.update {
        it.copy(dueDate = value, errorMessage = null)
    }

    fun onPaidChange(value: Boolean) = _uiState.update { it.copy(isPaid = value) }

    fun onRecurringChange(value: Boolean) = _uiState.update { it.copy(isRecurring = value) }

    fun formatDate(date: LocalDate?): String =
        date?.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) ?: ""

    fun savePayment() {
        val state = _uiState.value
        val dueDate = state.dueDate ?: return
        if (state.title.isBlank() || state.isSaving) return

        val amountCents = CurrencyUtils.parseToCents(state.amount)
        if (amountCents == null) {
            _uiState.update { it.copy(amountError = "Valor inválido") }
            return
        }

        val id = paymentId ?: UUID.randomUUID().toString()
        val existing = originalPayment
        val dueDateChanged = existing?.dueDate != null && existing.dueDate != dueDate
        val recurrenceMonths = if (state.isRecurring) 1 else null
        val recurrenceAnchorDay = if (state.isRecurring) {
            if (dueDateChanged) dueDate.dayOfMonth
            else existing?.recurrenceAnchorDay ?: dueDate.dayOfMonth
        } else {
            null
        }
        val seriesId = if (state.isRecurring) existing?.seriesId ?: id else null
        val paidAt = when {
            !state.isPaid -> null
            existing?.isPaid == true -> existing.paidAt
            else -> LocalDate.now()
        }

        val payment = Payment(
            id = id,
            title = state.title.trim(),
            amountCents = amountCents,
            dueDate = dueDate,
            isPaid = state.isPaid,
            paidAt = paidAt,
            recurrenceMonths = recurrenceMonths,
            recurrenceAnchorDay = recurrenceAnchorDay,
            seriesId = seriesId,
            generatedFromId = null
        )

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            try {
                if (payment.isPaid && payment.recurrenceMonths != null) {
                    repository.setPaidStatus(
                        payment = payment,
                        isPaid = true,
                        date = payment.paidAt ?: LocalDate.now()
                    )
                } else {
                    repository.insertPayment(payment)
                }
                originalPayment = payment
                _uiState.update { it.copy(isSaving = false) }
                _saved.emit(Unit)
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = error.message ?: "Não foi possível salvar o pagamento."
                    )
                }
            }
        }
    }
}
