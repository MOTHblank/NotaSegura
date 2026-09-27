package com.mothblank.notasegura.ui.screens.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mothblank.notasegura.data.storage.PurchaseDocumentStore
import com.mothblank.notasegura.domain.model.PurchaseWithAttachments
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TimelineViewModel(
    private val documentStore: PurchaseDocumentStore
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow<String?>(null)
    val selectedCategory = _selectedCategory.asStateFlow()

    private val allPurchases = documentStore.getAllPurchases()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = emptyList()
        )

    val uiState: StateFlow<List<PurchaseWithAttachments>> =
        combine(allPurchases, _searchQuery, _selectedCategory) { items, query, category ->
            val normalizedQuery = query.trim()
            items.filter { item ->
                val purchase = item.purchase
                val matchesQuery = normalizedQuery.isBlank() ||
                    purchase.productName.contains(normalizedQuery, ignoreCase = true) ||
                    purchase.merchant.orEmpty().contains(normalizedQuery, ignoreCase = true) ||
                    purchase.category.contains(normalizedQuery, ignoreCase = true) ||
                    purchase.modelNumber.orEmpty().contains(normalizedQuery, ignoreCase = true) ||
                    purchase.serialNumber.orEmpty().contains(normalizedQuery, ignoreCase = true) ||
                    purchase.notes.contains(normalizedQuery, ignoreCase = true) ||
                    item.attachments.any { attachment ->
                        attachment.ocrText.orEmpty().contains(normalizedQuery, ignoreCase = true)
                    }

                val matchesCategory = category == null || purchase.category == category
                matchesQuery && matchesCategory
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = emptyList()
        )

    val categories: StateFlow<List<String>> =
        allPurchases
            .map { items ->
                items.map { it.purchase.category }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .sorted()
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000L),
                emptyList()
            )

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onCategorySelected(category: String?) {
        _selectedCategory.value = category
    }

    fun deleteItem(item: PurchaseWithAttachments) {
        viewModelScope.launch {
            documentStore.deletePurchase(item)
        }
    }
}
