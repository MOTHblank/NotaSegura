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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.delay
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

enum class WarrantyFilter { ALL, ACTIVE, EXPIRING_SOON, EXPIRED, NONE }
enum class AttachmentFilter { ALL, WITH_DOCUMENTS, WITHOUT_DOCUMENTS }
enum class PurchasePeriod { ALL, LAST_30_DAYS, THIS_YEAR }

private data class TimelineFilters(
    val query: String,
    val category: String?,
    val warranty: WarrantyFilter,
    val attachments: AttachmentFilter,
    val period: PurchasePeriod
)

class TimelineViewModel(
    private val documentStore: PurchaseDocumentStore,
    private val paymentRepository: PaymentRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow<String?>(null)
    val selectedCategory = _selectedCategory.asStateFlow()

    private val _warrantyFilter = MutableStateFlow(WarrantyFilter.ALL)
    val warrantyFilter = _warrantyFilter.asStateFlow()
    private val _attachmentFilter = MutableStateFlow(AttachmentFilter.ALL)
    val attachmentFilter = _attachmentFilter.asStateFlow()
    private val _purchasePeriod = MutableStateFlow(PurchasePeriod.ALL)
    val purchasePeriod = _purchasePeriod.asStateFlow()

    private val allPurchases = documentStore.getAllPurchases()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = emptyList()
        )

    // Database flows need not emit at midnight. Keeping the current date as a
    // reactive input makes warranty/date filters and deadline labels roll over
    // correctly while the app remains open.
    private val currentDate = flow {
        while (true) {
            emit(LocalDate.now())
            delay(60_000L)
        }
    }.distinctUntilChanged()

    private val filters = combine(
        _searchQuery,
        _selectedCategory,
        _warrantyFilter,
        _attachmentFilter,
        _purchasePeriod
    ) { query, category, warranty, attachments, period ->
        TimelineFilters(
            query = query.trim(),
            category = category,
            warranty = warranty,
            attachments = attachments,
            period = period
        )
    }

    val uiState: StateFlow<List<PurchaseWithAttachments>> =
        combine(allPurchases, filters, currentDate) { items, filters, today ->
            items.filter { item ->
                val purchase = item.purchase
                val matchesQuery = filters.query.isBlank() ||
                    purchase.productName.contains(filters.query, ignoreCase = true) ||
                    purchase.merchant.orEmpty().contains(filters.query, ignoreCase = true) ||
                    CategoryNormalizer.key(purchase.category).contains(CategoryNormalizer.key(filters.query)) ||
                    purchase.modelNumber.orEmpty().contains(filters.query, ignoreCase = true) ||
                    purchase.serialNumber.orEmpty().contains(filters.query, ignoreCase = true) ||
                    purchase.notes.contains(filters.query, ignoreCase = true) ||
                    item.attachments.any { it.ocrText.orEmpty().contains(filters.query, ignoreCase = true) }
                val matchesCategory = filters.category == null ||
                    CategoryNormalizer.key(purchase.category) == CategoryNormalizer.key(filters.category)
                val end = purchase.warrantyEndDate
                val matchesWarranty = when (filters.warranty) {
                    WarrantyFilter.ALL -> true
                    WarrantyFilter.ACTIVE -> end != null && !end.isBefore(today)
                    WarrantyFilter.EXPIRING_SOON ->
                        end != null && !end.isBefore(today) && !end.isAfter(today.plusDays(30))
                    WarrantyFilter.EXPIRED -> end != null && end.isBefore(today)
                    WarrantyFilter.NONE -> end == null
                }
                val matchesAttachments = when (filters.attachments) {
                    AttachmentFilter.ALL -> true
                    AttachmentFilter.WITH_DOCUMENTS -> item.attachments.isNotEmpty()
                    AttachmentFilter.WITHOUT_DOCUMENTS -> item.attachments.isEmpty()
                }
                val matchesPeriod = when (filters.period) {
                    PurchasePeriod.ALL -> true
                    PurchasePeriod.LAST_30_DAYS ->
                        !purchase.purchaseDate.isBefore(today.minusDays(30)) &&
                            !purchase.purchaseDate.isAfter(today)
                    PurchasePeriod.THIS_YEAR -> purchase.purchaseDate.year == today.year
                }
                matchesQuery && matchesCategory && matchesWarranty &&
                    matchesAttachments && matchesPeriod
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
        combine(allPurchases, paymentRepository.getAllPayments(), currentDate) {
                purchases, payments, today ->
            buildAttentionDashboard(
                purchases = purchases,
                payments = payments,
                today = today
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = AttentionDashboardState()
        )

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onWarrantyFilterSelected(filter: WarrantyFilter) { _warrantyFilter.value = filter }
    fun onAttachmentFilterSelected(filter: AttachmentFilter) { _attachmentFilter.value = filter }
    fun onPurchasePeriodSelected(period: PurchasePeriod) { _purchasePeriod.value = period }

    fun clearFilters() {
        _searchQuery.value = ""
        _selectedCategory.value = null
        _warrantyFilter.value = WarrantyFilter.ALL
        _attachmentFilter.value = AttachmentFilter.ALL
        _purchasePeriod.value = PurchasePeriod.ALL
    }

    fun onCategorySelected(category: String?) {
        _selectedCategory.value = category
    }
}
