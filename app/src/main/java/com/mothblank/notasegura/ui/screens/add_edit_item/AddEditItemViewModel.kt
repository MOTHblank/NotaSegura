package com.mothblank.notasegura.ui.screens.add_edit_item

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.mothblank.notasegura.data.storage.WarrantyDocumentStore
import com.mothblank.notasegura.domain.model.WarrantyItem
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.UUID

data class AddEditUiState(
    val name: String = "",
    val category: String = "",
    val purchaseDate: LocalDate? = null,
    val expirationDate: LocalDate? = null,
    val imagePath: String? = null,
    val isSaving: Boolean = false,
    val errorMessage: String? = null
)

class AddEditItemViewModel(
    private val documentStore: WarrantyDocumentStore,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddEditUiState())
    val uiState = _uiState.asStateFlow()

    private val _saved = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val saved = _saved.asSharedFlow()

    private val itemId: String? = savedStateHandle["itemId"]
    private var originalImagePath: String? = null
    private var stagedImagePath: String? = null

    init {
        if (itemId != null) {
            viewModelScope.launch {
                documentStore.getItemById(itemId)?.let { item ->
                    originalImagePath = item.imagePath
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

    fun onNameChange(value: String) = _uiState.update {
        it.copy(name = value, errorMessage = null)
    }

    fun onCategoryChange(value: String) = _uiState.update {
        it.copy(category = value, errorMessage = null)
    }

    fun onPurchaseDateChange(value: LocalDate) = _uiState.update {
        it.copy(purchaseDate = value, errorMessage = null)
    }

    fun onExpirationDateChange(value: LocalDate) = _uiState.update {
        it.copy(expirationDate = value, errorMessage = null)
    }

    fun onImageSelected(context: Context, uri: Uri) {
        viewModelScope.launch {
            val newStagedPath = documentStore.stageImage(uri)
            if (newStagedPath == null) {
                _uiState.update { it.copy(errorMessage = "Não foi possível copiar a imagem selecionada.") }
                return@launch
            }

            documentStore.discardStagedImage(stagedImagePath)
            stagedImagePath = newStagedPath
            _uiState.update { it.copy(imagePath = newStagedPath, errorMessage = null) }
            processImageForOcr(context, uri)
        }
    }

    private fun processImageForOcr(context: Context, imageUri: Uri) {
        try {
            val image = InputImage.fromFilePath(context, imageUri)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            recognizer.process(image)
                .addOnSuccessListener { visionText -> parseReceiptDates(visionText.text) }
                .addOnCompleteListener { recognizer.close() }
        } catch (_: IOException) {
            // The attachment is still valid even if OCR cannot read it.
        }
    }

    private fun parseReceiptDates(text: String) {
        val dateRegex = """(\d{2}[/-]\d{2}[/-]\d{4})""".toRegex()
        val dates = dateRegex.findAll(text)
            .mapNotNull { match ->
                try {
                    LocalDate.parse(
                        match.value.replace("-", "/"),
                        DateTimeFormatter.ofPattern("dd/MM/yyyy")
                    )
                } catch (_: DateTimeParseException) {
                    null
                }
            }
            .filter { !it.isAfter(LocalDate.now().plusDays(1)) }
            .toList()

        val likelyPurchaseDate = dates.maxOrNull() ?: return
        _uiState.update { current ->
            if (current.purchaseDate == null) current.copy(purchaseDate = likelyPurchaseDate)
            else current
        }
    }

    fun formatDate(date: LocalDate?): String =
        date?.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) ?: ""

    fun saveItem() {
        val state = _uiState.value
        val purchaseDate = state.purchaseDate
        val expirationDate = state.expirationDate

        if (state.name.isBlank() || purchaseDate == null || expirationDate == null) return
        if (expirationDate.isBefore(purchaseDate)) {
            _uiState.update { it.copy(errorMessage = "O fim da garantia não pode ser anterior à compra.") }
            return
        }
        if (state.isSaving) return

        val item = WarrantyItem(
            id = itemId ?: UUID.randomUUID().toString(),
            name = state.name.trim(),
            category = state.category.trim(),
            purchaseDate = purchaseDate,
            expirationDate = expirationDate,
            imagePath = null
        )

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            try {
                val savedItem = documentStore.saveItem(
                    item = item,
                    stagedImagePath = stagedImagePath,
                    previousImagePath = originalImagePath
                )
                originalImagePath = savedItem.imagePath
                stagedImagePath = null
                _uiState.update {
                    it.copy(imagePath = savedItem.imagePath, isSaving = false)
                }
                _saved.emit(Unit)
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = error.message ?: "Não foi possível salvar o item."
                    )
                }
            }
        }
    }

    override fun onCleared() {
        documentStore.discardStagedImage(stagedImagePath)
        super.onCleared()
    }
}
