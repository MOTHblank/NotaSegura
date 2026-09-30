package com.mothblank.notasegura.ui.screens.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mothblank.notasegura.data.storage.PurchaseDocumentStore
import com.mothblank.notasegura.domain.model.PurchaseWithAttachments
import com.mothblank.notasegura.domain.repository.PaymentRepository
import com.mothblank.notasegura.util.CategoryNormalizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class AttentionSummary(
    val warrantiesExpiringSoon: Int = 0,
    val overduePayments: Int = 0,
    val paymentsDueThisWeek: Int = 0
)

class TimelineViewModel(
    private val documentStore: PurchaseDocumentStore,
    private val paymentRepository: PaymentRepository
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

                val matchesCategory = category == null ||
                    CategoryNormalizer.key(purchase.category) == CategoryNormalizer.key(category)
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
                CategoryNormalizer.distinctDisplay(items.map { it.purchase.category })
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000L),
                emptyList()
            )

    val attentionSummary: StateFlow<AttentionSummary> =
        combine(allPurchases, paymentRepository.getAllPayments()) { purchases, payments ->
            val today = LocalDate.now()
            val warrantyLimit = today.plusDays(30)
            val paymentLimit = today.plusDays(7)

            AttentionSummary(
                warrantiesExpiringSoon = purchases.count { item ->
                    item.purchase.warrantyEndDate?.let { date ->
                        !date.isBefore(today) && !date.isAfter(warrantyLimit)
                    } == true
                },
                overduePayments = payments.count { payment ->
                    !payment.isPaid && payment.dueDate.isBefore(today)
                },
                paymentsDueThisWeek = payments.count { payment ->
                    !payment.isPaid &&
                        !payment.dueDate.isBefore(today) &&
                        !payment.dueDate.isAfter(paymentLimit)
                }
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = AttentionSummary()
        )

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onCategorySelected(category: String?) {
        _selectedCategory.value = category
    }

}
