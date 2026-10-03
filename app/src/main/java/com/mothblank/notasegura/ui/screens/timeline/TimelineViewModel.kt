package com.mothblank.notasegura.ui.screens.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mothblank.notasegura.data.storage.PurchaseDocumentStore
import com.mothblank.notasegura.domain.model.Payment
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

data class WarrantyAttentionItem(
    val purchaseId: String,
    val productName: String,
    val warrantyEndDate: LocalDate
)

data class PaymentAttentionItem(
    val paymentId: String,
    val title: String,
    val amountCents: Long,
    val dueDate: LocalDate,
    val isOverdue: Boolean
)

data class AttentionDashboardState(
    val warrantiesExpiringSoon: List<WarrantyAttentionItem> = emptyList(),
    val paymentsRequiringAttention: List<PaymentAttentionItem> = emptyList()
) {
    val overduePayments: Int
        get() = paymentsRequiringAttention.count { it.isOverdue }

    val paymentsDueThisWeek: Int
        get() = paymentsRequiringAttention.size - overduePayments

    val isEmpty: Boolean
        get() = warrantiesExpiringSoon.isEmpty() && paymentsRequiringAttention.isEmpty()
}

internal fun buildAttentionDashboard(
    purchases: List<PurchaseWithAttachments>,
    payments: List<Payment>,
    today: LocalDate
): AttentionDashboardState {
    val warrantyLimit = today.plusDays(30)
    val paymentLimit = today.plusDays(7)

    val warranties = purchases
        .mapNotNull { item ->
            val endDate = item.purchase.warrantyEndDate ?: return@mapNotNull null
            if (endDate.isBefore(today) || endDate.isAfter(warrantyLimit)) {
                return@mapNotNull null
            }

            WarrantyAttentionItem(
                purchaseId = item.purchase.id,
                productName = item.purchase.productName,
                warrantyEndDate = endDate
            )
        }
        .sortedWith(
            compareBy<WarrantyAttentionItem> { it.warrantyEndDate }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.productName }
        )

    val pendingPayments = payments
        .asSequence()
        .filter { payment ->
            !payment.isPaid && !payment.dueDate.isAfter(paymentLimit)
        }
        .sortedWith(
            compareBy<Payment> { it.dueDate }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
        )
        .map { payment ->
            PaymentAttentionItem(
                paymentId = payment.id,
                title = payment.title,
                amountCents = payment.amountCents,
                dueDate = payment.dueDate,
                isOverdue = payment.dueDate.isBefore(today)
            )
        }
        .toList()

    return AttentionDashboardState(
        warrantiesExpiringSoon = warranties,
        paymentsRequiringAttention = pendingPayments
    )
}

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
                    CategoryNormalizer.key(purchase.category).contains(
                        CategoryNormalizer.key(normalizedQuery)
                    ) ||
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

    val attentionDashboard: StateFlow<AttentionDashboardState> =
        combine(allPurchases, paymentRepository.getAllPayments()) { purchases, payments ->
            buildAttentionDashboard(
                purchases = purchases,
                payments = payments,
                today = LocalDate.now()
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = AttentionDashboardState()
        )

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onCategorySelected(category: String?) {
        _selectedCategory.value = category
    }
}
