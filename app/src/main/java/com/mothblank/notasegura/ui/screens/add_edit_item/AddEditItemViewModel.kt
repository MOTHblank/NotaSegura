package com.mothblank.notasegura.ui.screens.add_edit_item

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.mothblank.notasegura.data.storage.PurchaseDocumentStore
import com.mothblank.notasegura.data.storage.StagedAttachment
import com.mothblank.notasegura.domain.model.Attachment
import com.mothblank.notasegura.domain.model.Purchase
import com.mothblank.notasegura.domain.model.PurchaseWithAttachments
import com.mothblank.notasegura.util.CurrencyUtils
import com.mothblank.notasegura.util.ReceiptOcrParser
import com.mothblank.notasegura.util.ReceiptOcrSuggestions
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

data class PurchaseAttachmentUi(
    val id: String,
    val path: String,
    val mimeType: String,
    val displayName: String,
    val isStaged: Boolean
) {
    val isImage: Boolean
        get() = mimeType.startsWith("image/")
}

data class AddEditUiState(
    val productName: String = "",
    val merchant: String = "",
    val purchaseValue: String = "",
    val purchaseValueError: String? = null,
    val category: String = "",
    val modelNumber: String = "",
    val serialNumber: String = "",
    val notes: String = "",
    val purchaseDate: LocalDate? = null,
    val warrantyEndDate: LocalDate? = null,
    val attachments: List<PurchaseAttachmentUi> = emptyList(),
    val ocrSuggestions: ReceiptOcrSuggestions? = null,
    val isAnalyzingDocument: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null
)

private data class PurchaseEditorSnapshot(
    val productName: String,
    val merchant: String,
    val purchaseValue: String,
    val category: String,
    val modelNumber: String,
    val serialNumber: String,
    val notes: String,
    val purchaseDate: LocalDate?,
    val warrantyEndDate: LocalDate?,
    val attachmentIds: List<String>
)

class AddEditItemViewModel(
    private val documentStore: PurchaseDocumentStore,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddEditUiState())
    val uiState = _uiState.asStateFlow()

    private val _saved = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val saved = _saved.asSharedFlow()

    private val itemId: String? = savedStateHandle["itemId"]
    private val originalSnapshot = MutableStateFlow<PurchaseEditorSnapshot?>(
        if (itemId == null) snapshotOf(_uiState.value) else null
    )
    val isDirty: StateFlow<Boolean> = combine(_uiState, originalSnapshot) { state, baseline ->
        baseline != null && snapshotOf(state) != baseline
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = false
    )

    private var original: PurchaseWithAttachments? = null
    private val stagedAttachments = linkedMapOf<String, StagedAttachment>()
    private val removedAttachments = linkedMapOf<String, Attachment>()
    private var pendingOcrCount = 0

    init {
        if (itemId != null) {
            viewModelScope.launch {
                documentStore.getPurchaseById(itemId)?.let { item ->
                    original = item
                    _uiState.update {
                        it.copy(
                            productName = item.purchase.productName,
                            merchant = item.purchase.merchant.orEmpty(),
                            purchaseValue = item.purchase.purchaseValueCents
                                ?.let { CurrencyUtils.centsToEditable(it) }
                                .orEmpty(),
                            category = item.purchase.category,
                            modelNumber = item.purchase.modelNumber.orEmpty(),
                            serialNumber = item.purchase.serialNumber.orEmpty(),
                            notes = item.purchase.notes,
                            purchaseDate = item.purchase.purchaseDate,
                            warrantyEndDate = item.purchase.warrantyEndDate,
                            attachments = item.attachments.map { attachment ->
                                PurchaseAttachmentUi(
                                    id = attachment.id,
                                    path = attachment.filePath,
                                    mimeType = attachment.mimeType,
                                    displayName = attachment.displayName ?: "Documento",
                                    isStaged = false
                                )
                            }
                        )
                    }
                    originalSnapshot.value = snapshotOf(_uiState.value)
                }
            }
        }
    }

    fun onProductNameChange(value: String) = updateText { copy(productName = value) }
    fun onMerchantChange(value: String) = updateText { copy(merchant = value) }
    fun onCategoryChange(value: String) = updateText { copy(category = value) }
    fun onModelNumberChange(value: String) = updateText { copy(modelNumber = value) }
    fun onSerialNumberChange(value: String) = updateText { copy(serialNumber = value) }
    fun onNotesChange(value: String) = updateText { copy(notes = value) }

    fun onPurchaseValueChange(value: String) {
        if (CurrencyUtils.isValidEditableAmount(value)) {
            _uiState.update {
                it.copy(
                    purchaseValue = value,
                    purchaseValueError = null,
                    errorMessage = null
                )
            }
        }
    }

    fun onPurchaseDateChange(value: LocalDate) {
        _uiState.update { it.copy(purchaseDate = value, errorMessage = null) }
    }

    fun onWarrantyEndDateChange(value: LocalDate?) {
        _uiState.update { it.copy(warrantyEndDate = value, errorMessage = null) }
    }

    fun onAttachmentSelected(context: Context, uri: Uri) {
        viewModelScope.launch {
            val staged = documentStore.stageAttachment(uri)
            if (staged == null) {
                _uiState.update {
                    it.copy(errorMessage = "Não foi possível copiar o documento selecionado.")
                }
                return@launch
            }

            stagedAttachments[staged.stagedFile.id] = staged
            _uiState.update {
                it.copy(
                    attachments = it.attachments + PurchaseAttachmentUi(
                        id = staged.stagedFile.id,
                        path = staged.stagedFile.path,
                        mimeType = staged.stagedFile.mimeType,
                        displayName = staged.stagedFile.displayName,
                        isStaged = true
                    ),
                    errorMessage = null
                )
            }

            if (staged.stagedFile.mimeType.startsWith("image/")) {
                processImageForOcr(context, uri, staged.stagedFile.id)
            }
        }
    }

    fun removeAttachment(id: String) {
        stagedAttachments.remove(id)?.let { staged ->
            documentStore.discardStagedAttachment(staged)
        }

        original?.attachments?.firstOrNull { it.id == id }?.let { existing ->
            removedAttachments[id] = existing
        }

        _uiState.update {
            it.copy(attachments = it.attachments.filterNot { attachment -> attachment.id == id })
        }
    }

    private fun processImageForOcr(context: Context, imageUri: Uri, attachmentId: String) {
        pendingOcrCount += 1
        _uiState.update { it.copy(isAnalyzingDocument = true) }

        try {
            val image = InputImage.fromFilePath(context, imageUri)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    stagedAttachments[attachmentId]?.let { staged ->
                        stagedAttachments[attachmentId] = staged.copy(ocrText = visionText.text)
                    }
                    val parsed = ReceiptOcrParser.parse(visionText.text)
                    _uiState.update { state ->
                        state.copy(
                            ocrSuggestions = mergeSuggestions(state.ocrSuggestions, parsed)
                        )
                    }
                }
                .addOnCompleteListener {
                    recognizer.close()
                    finishOcr()
                }
        } catch (_: IOException) {
            finishOcr()
        }
    }

    private fun finishOcr() {
        pendingOcrCount = (pendingOcrCount - 1).coerceAtLeast(0)
        _uiState.update { it.copy(isAnalyzingDocument = pendingOcrCount > 0) }
    }

    fun applyMerchantSuggestion() {
        val suggestion = _uiState.value.ocrSuggestions?.merchant ?: return
        _uiState.update {
            it.copy(
                merchant = suggestion,
                ocrSuggestions = it.ocrSuggestions?.copy(merchant = null)
            )
        }
    }

    fun applyPurchaseDateSuggestion() {
        val suggestion = _uiState.value.ocrSuggestions?.purchaseDate ?: return
        _uiState.update {
            it.copy(
                purchaseDate = suggestion,
                ocrSuggestions = it.ocrSuggestions?.copy(purchaseDate = null)
            )
        }
    }

    fun applyPurchaseValueSuggestion() {
        val suggestion = _uiState.value.ocrSuggestions?.purchaseValueCents ?: return
        _uiState.update {
            it.copy(
                purchaseValue = CurrencyUtils.centsToEditable(suggestion),
                ocrSuggestions = it.ocrSuggestions?.copy(purchaseValueCents = null)
            )
        }
    }

    fun applyModelSuggestion() {
        val suggestion = _uiState.value.ocrSuggestions?.modelNumber ?: return
        _uiState.update {
            it.copy(
                modelNumber = suggestion,
                ocrSuggestions = it.ocrSuggestions?.copy(modelNumber = null)
            )
        }
    }

    fun applySerialSuggestion() {
        val suggestion = _uiState.value.ocrSuggestions?.serialNumber ?: return
        _uiState.update {
            it.copy(
                serialNumber = suggestion,
                ocrSuggestions = it.ocrSuggestions?.copy(serialNumber = null)
            )
        }
    }

    fun formatDate(date: LocalDate?): String =
        date?.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) ?: ""

    fun saveItem() {
        val state = _uiState.value
        val purchaseDate = state.purchaseDate ?: return
        if (state.productName.isBlank() || state.isSaving || state.isAnalyzingDocument) return

        if (state.warrantyEndDate?.isBefore(purchaseDate) == true) {
            _uiState.update {
                it.copy(errorMessage = "O fim da garantia não pode ser anterior à compra.")
            }
            return
        }

        val purchaseValueCents = if (state.purchaseValue.isBlank()) {
            null
        } else {
            CurrencyUtils.parseToCents(state.purchaseValue)
        }

        if (state.purchaseValue.isNotBlank() && purchaseValueCents == null) {
            _uiState.update { it.copy(purchaseValueError = "Valor inválido") }
            return
        }

        val now = System.currentTimeMillis()
        val previous = original?.purchase
        val purchase = Purchase(
            id = itemId ?: UUID.randomUUID().toString(),
            productName = state.productName.trim(),
            merchant = state.merchant.trim().ifBlank { null },
            purchaseValueCents = purchaseValueCents,
            purchaseDate = purchaseDate,
            warrantyEndDate = state.warrantyEndDate,
            category = state.category.trim(),
            modelNumber = state.modelNumber.trim().ifBlank { null },
            serialNumber = state.serialNumber.trim().ifBlank { null },
            notes = state.notes.trim(),
            createdAt = previous?.createdAt?.takeIf { it > 0L } ?: now,
            updatedAt = now
        )

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            try {
                documentStore.savePurchase(
                    purchase = purchase,
                    stagedAttachments = stagedAttachments.values.toList(),
                    removedAttachments = removedAttachments.values.toList()
                )
                stagedAttachments.clear()
                removedAttachments.clear()
                _uiState.update { it.copy(isSaving = false) }
                originalSnapshot.value = snapshotOf(_uiState.value)
                _saved.emit(Unit)
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = error.message ?: "Não foi possível salvar a compra."
                    )
                }
            }
        }
    }

    override fun onCleared() {
        stagedAttachments.values.forEach(documentStore::discardStagedAttachment)
        super.onCleared()
    }

    private fun updateText(transform: AddEditUiState.() -> AddEditUiState) {
        _uiState.update { it.transform().copy(errorMessage = null) }
    }

    private fun snapshotOf(state: AddEditUiState) = PurchaseEditorSnapshot(
        productName = state.productName,
        merchant = state.merchant,
        purchaseValue = state.purchaseValue,
        category = state.category,
        modelNumber = state.modelNumber,
        serialNumber = state.serialNumber,
        notes = state.notes,
        purchaseDate = state.purchaseDate,
        warrantyEndDate = state.warrantyEndDate,
        attachmentIds = state.attachments.map { it.id }
    )

    private fun mergeSuggestions(
        current: ReceiptOcrSuggestions?,
        next: ReceiptOcrSuggestions
    ): ReceiptOcrSuggestions {
        if (current == null) return next
        return ReceiptOcrSuggestions(
            merchant = current.merchant ?: next.merchant,
            purchaseDate = current.purchaseDate ?: next.purchaseDate,
            purchaseValueCents = current.purchaseValueCents ?: next.purchaseValueCents,
            modelNumber = current.modelNumber ?: next.modelNumber,
            serialNumber = current.serialNumber ?: next.serialNumber,
            rawText = current.rawText + "\n" + next.rawText
        )
    }
}
