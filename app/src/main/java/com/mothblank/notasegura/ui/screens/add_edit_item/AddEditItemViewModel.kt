package com.mothblank.notasegura.ui.screens.add_edit_item

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.mothblank.notasegura.domain.model.WarrantyItem
import com.mothblank.notasegura.domain.repository.WarrantyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.UUID

import com.mothblank.notasegura.util.FileStorageManager

data class AddEditUiState(
    val name: String = "",
    val category: String = "",
    val purchaseDate: LocalDate? = null,
    val expirationDate: LocalDate? = null,
    val imagePath: String? = null,
    val isSaving: Boolean = false
)

class AddEditItemViewModel(
    private val repository: WarrantyRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddEditUiState())
    val uiState = _uiState.asStateFlow()

    private val itemId: String? = savedStateHandle["itemId"]

    init {
        if (itemId != null) {
            viewModelScope.launch {
                repository.getItemById(itemId)?.let { item ->
                    _uiState.update {
                        it.copy(
                            name = item.name,
                            category = item.category,
                            purchaseDate = item.purchaseDate,
                            expirationDate = item.expirationDate,
                            imagePath = item.imagePath
                        )
                    }
                }
            }
        }
    }

    fun onNameChange(newName: String) {
        _uiState.update { it.copy(name = newName) }
    }

    fun onCategoryChange(newCategory: String) {
        _uiState.update { it.copy(category = newCategory) }
    }

    fun onPurchaseDateChange(newDate: LocalDate) {
        _uiState.update { it.copy(purchaseDate = newDate) }
    }

    fun onExpirationDateChange(newDate: LocalDate) {
        _uiState.update { it.copy(expirationDate = newDate) }
    }

    fun onImageSelected(context: Context, uri: Uri) {
        viewModelScope.launch {
            val savedPath = FileStorageManager.saveImageToInternalStorage(context, uri)
            _uiState.update { it.copy(imagePath = savedPath) }
            processImageForOcr(context, uri)
        }
    }

    fun processImageForOcr(context: Context, imageUri: Uri) {
        try {
            val image = InputImage.fromFilePath(context, imageUri)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    parseTextAndFillForm(visionText.text)
                }
                .addOnFailureListener { e -> e.printStackTrace() }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun parseTextAndFillForm(text: String) {
        val dateRegex = """(\d{2}[/-]\d{2}[/-]\d{4})""".toRegex()
        val foundDates = dateRegex.findAll(text)
            .map { it.value.replace("-", "/") }
            .toList()

        val localDates = foundDates.mapNotNull { dateString ->
            try {
                LocalDate.parse(dateString, DateTimeFormatter.ofPattern("dd/MM/yyyy"))
            } catch (e: DateTimeParseException) {
                null
            }
        }.sorted()

        // Tenta extrair o nome da loja (geralmente nas primeiras linhas)
        val lines = text.lines().filter { it.isNotBlank() }
        val possibleStoreName = lines.take(3).firstOrNull { it.length > 3 && !it.any { char -> char.isDigit() } }

        _uiState.update { currentState ->
            currentState.copy(
                name = possibleStoreName ?: currentState.name,
                purchaseDate = localDates.getOrNull(0) ?: currentState.purchaseDate,
                expirationDate = localDates.lastOrNull() ?: currentState.expirationDate
            )
        }
    }

    fun formatDate(date: LocalDate?): String {
        return date?.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) ?: ""
    }

    fun saveItem() {
        val currentState = _uiState.value
        if (currentState.name.isBlank() || currentState.purchaseDate == null || currentState.expirationDate == null) {
            return
        }

        val itemToSave = WarrantyItem(
            id = itemId ?: UUID.randomUUID().toString(),
            name = currentState.name.trim(),
            category = currentState.category.trim(),
            purchaseDate = currentState.purchaseDate,
            expirationDate = currentState.expirationDate,
            imagePath = currentState.imagePath
        )

        viewModelScope.launch {
            repository.insertItem(itemToSave)
        }
    }
}