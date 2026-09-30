package com.mothblank.notasegura.ui.screens.purchase_details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mothblank.notasegura.data.storage.PurchaseDocumentStore
import com.mothblank.notasegura.domain.model.PurchaseWithAttachments
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PurchaseDetailsUiState(
    val isLoading: Boolean = true,
    val item: PurchaseWithAttachments? = null
)

class PurchaseDetailsViewModel(
    private val documentStore: PurchaseDocumentStore,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val itemId: String = checkNotNull(savedStateHandle["itemId"])

    val uiState = documentStore.getAllPurchases()
        .map { purchases ->
            PurchaseDetailsUiState(
                isLoading = false,
                item = purchases.firstOrNull { it.purchase.id == itemId }
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = PurchaseDetailsUiState()
        )

    private val _deleted = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val deleted = _deleted.asSharedFlow()

    fun deletePurchase() {
        val item = uiState.value.item ?: return
        viewModelScope.launch {
            documentStore.deletePurchase(item)
            _deleted.emit(Unit)
        }
    }
}
