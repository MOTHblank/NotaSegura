package com.mothblank.notasegura.ui.screens.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mothblank.notasegura.data.storage.WarrantyDocumentStore
import com.mothblank.notasegura.domain.model.WarrantyItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TimelineViewModel(
    private val documentStore: WarrantyDocumentStore
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow<String?>(null)
    val selectedCategory = _selectedCategory.asStateFlow()

    val uiState: StateFlow<List<WarrantyItem>> =
        combine(documentStore.getAllItems(), _searchQuery, _selectedCategory) { items, query, category ->
            items.filter { item ->
                val matchesQuery =
                    item.name.contains(query, ignoreCase = true) ||
                        item.category.contains(query, ignoreCase = true)
                val matchesCategory = category == null || item.category == category
                matchesQuery && matchesCategory
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = emptyList()
        )

    val categories: StateFlow<List<String>> =
        documentStore.getAllItems()
            .map { items ->
                items.map { it.category }
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

    fun deleteItem(item: WarrantyItem) {
        viewModelScope.launch {
            documentStore.deleteItem(item)
        }
    }
}
