package com.mothblank.notasegura

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import com.mothblank.notasegura.data.storage.PurchaseDocumentStore
import com.mothblank.notasegura.domain.repository.PaymentRepository
import com.mothblank.notasegura.ui.screens.add_edit_item.AddEditItemViewModel
import com.mothblank.notasegura.ui.screens.add_edit_payment.AddEditPaymentViewModel
import com.mothblank.notasegura.ui.screens.payments.PaymentsViewModel
import com.mothblank.notasegura.ui.screens.purchase_details.PurchaseDetailsViewModel
import com.mothblank.notasegura.ui.screens.timeline.TimelineViewModel

@Suppress("UNCHECKED_CAST")
class ViewModelFactory(
    private val purchaseDocumentStore: PurchaseDocumentStore,
    private val paymentRepository: PaymentRepository
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(
        modelClass: Class<T>,
        extras: CreationExtras
    ): T {
        val savedStateHandle = extras.createSavedStateHandle()

        return when {
            modelClass.isAssignableFrom(TimelineViewModel::class.java) ->
                TimelineViewModel(purchaseDocumentStore, paymentRepository) as T

            modelClass.isAssignableFrom(AddEditItemViewModel::class.java) ->
                AddEditItemViewModel(purchaseDocumentStore, savedStateHandle) as T

            modelClass.isAssignableFrom(PurchaseDetailsViewModel::class.java) ->
                PurchaseDetailsViewModel(purchaseDocumentStore, savedStateHandle) as T

            modelClass.isAssignableFrom(PaymentsViewModel::class.java) ->
                PaymentsViewModel(paymentRepository) as T

            modelClass.isAssignableFrom(AddEditPaymentViewModel::class.java) ->
                AddEditPaymentViewModel(paymentRepository, savedStateHandle) as T

            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
