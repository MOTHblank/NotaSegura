package com.mothblank.notasegura.ui.screens.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mothblank.notasegura.domain.model.WarrantyItem
import android.content.Context
import com.mothblank.notasegura.domain.repository.WarrantyRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import com.mothblank.notasegura.util.FileStorageManager
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch // Importe o launch

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

class TimelineViewModel(
    private val repository: WarrantyRepository // Mude para private val
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow<String?>(null)
    val selectedCategory = _selectedCategory.asStateFlow()

    val uiState: StateFlow<List<WarrantyItem>> =
        combine(repository.getAllItems(), _searchQuery, _selectedCategory) { items, query, category ->
            items.filter { item ->
                val matchesQuery = item.name.contains(query, ignoreCase = true) || 
                                 item.category.contains(query, ignoreCase = true)
                val matchesCategory = category == null || item.category == category
                matchesQuery && matchesCategory
            }
        }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = emptyList()
            )

    val categories: StateFlow<List<String>> = 
        repository.getAllItems()
            .map { items -> items.map { it.category }.distinct().sorted() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onCategorySelected(category: String?) {
        _selectedCategory.value = category
    }

    fun deleteItem(context: Context, item: WarrantyItem) {
        viewModelScope.launch {
            item.imagePath?.let { path ->
                FileStorageManager.deleteImageFromInternalStorage(context, path)
            }
            repository.deleteItem(item)
        }
    }
}