package com.mothblank.notasegura

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import com.mothblank.notasegura.domain.repository.PaymentRepository
import com.mothblank.notasegura.domain.repository.WarrantyRepository
import com.mothblank.notasegura.ui.screens.add_edit_item.AddEditItemViewModel
import com.mothblank.notasegura.ui.screens.timeline.TimelineViewModel
import com.mothblank.notasegura.ui.screens.payments.PaymentsViewModel
import com.mothblank.notasegura.ui.screens.add_edit_payment.AddEditPaymentViewModel

/**
 * A fábrica de ViewModel moderna que implementa diretamente ViewModelProvider.Factory.
 * Ela recebe as dependências que nós controlamos (os repositórios).
 */
@Suppress("UNCHECKED_CAST")
class ViewModelFactory(
    private val repository: WarrantyRepository,
    private val paymentRepository: PaymentRepository? = null
) : ViewModelProvider.Factory {

    /**
     * O novo método create que usa CreationExtras.
     * CreationExtras é um "pacote" de dados que o sistema fornece,
     * incluindo o SavedStateHandle.
     */
    override fun <T : ViewModel> create(
        modelClass: Class<T>,
        extras: CreationExtras
    ): T {
        // Obtém o SavedStateHandle a partir dos extras.
        // Esta é a maneira recomendada e moderna.
        val savedStateHandle = extras.createSavedStateHandle()

        return when {
            // Verifica se a classe pedida é a do TimelineViewModel
            modelClass.isAssignableFrom(TimelineViewModel::class.java) -> {
                TimelineViewModel(repository) as T
            }
            // Verifica se a classe pedida é a do AddEditItemViewModel
            modelClass.isAssignableFrom(AddEditItemViewModel::class.java) -> {
                AddEditItemViewModel(repository, savedStateHandle) as T
            }
            modelClass.isAssignableFrom(PaymentsViewModel::class.java) -> {
                PaymentsViewModel(paymentRepository!!) as T
            }
            modelClass.isAssignableFrom(AddEditPaymentViewModel::class.java) -> {
                AddEditPaymentViewModel(paymentRepository!!, savedStateHandle) as T
            }
            // Lança uma exceção se um ViewModel desconhecido for solicitado
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}