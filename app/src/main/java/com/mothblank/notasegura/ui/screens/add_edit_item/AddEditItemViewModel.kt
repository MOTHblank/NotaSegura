package com.mothblank.notasegura.ui.screens.add_edit_item

import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mothblank.notasegura.R
import com.mothblank.notasegura.data.ocr.ReceiptOcrEngine
import com.mothblank.notasegura.data.storage.PurchaseDocumentStore
import com.mothblank.notasegura.data.storage.StageAttachmentResult
import com.mothblank.notasegura.data.storage.StagedAttachment
import com.mothblank.notasegura.domain.model.Attachment
import com.mothblank.notasegura.domain.model.AttachmentType
import com.mothblank.notasegura.domain.model.Purchase
import com.mothblank.notasegura.domain.model.PurchaseWithAttachments
import com.mothblank.notasegura.util.CategoryNormalizer
import com.mothblank.notasegura.util.CurrencyUtils
import com.mothblank.notasegura.util.ReceiptOcrParser
import com.mothblank.notasegura.util.ReceiptOcrSuggestions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

data class PurchaseAttachmentUi(
    val id: String,
    val path: String,
    val mimeType: String,
    val displayName: String,
    val type: String,
    val isStaged: Boolean
) {
    val isImage: Boolean
        get() = mimeType.startsWith("image/")
}

data class AddEditUiState(
    val productName: String = "",
    val merchant: String = "",
    val purchaseValue: String = "",
    @StringRes val purchaseValueErrorRes: Int? = null,
    val category: String = "",
    val modelNumber: String = "",
    val serialNumber: String = "",
    val notes: String = "",
    val purchaseDate: LocalDate? = null,
    val warrantyEndDate: LocalDate? = null,
    val attachments: List<PurchaseAttachmentUi> = emptyList(),
    val ocrSuggestions: ReceiptOcrSuggestions? = null,
    val hasOcrAutofill: Boolean = false,
    val isAnalyzingDocument: Boolean = false,
    val isSaving: Boolean = false,
    @StringRes val errorMessageRes: Int? = null,
    @StringRes val infoMessageRes: Int? = null
)

private enum class OcrEditableField {
    MERCHANT,
    PURCHASE_VALUE,
    PURCHASE_DATE,
    MODEL_NUMBER,
    SERIAL_NUMBER
}

private data class AttachmentEditorSnapshot(
    val id: String,
    val displayName: String,
    val type: String
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
    val attachments: List<AttachmentEditorSnapshot>
)

class AddEditItemViewModel(
    private val documentStore: PurchaseDocumentStore,
    private val receiptOcrEngine: ReceiptOcrEngine,
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

    val categoryOptions: StateFlow<List<String>> =
        documentStore.getAllPurchases()
            .map { items ->
                CategoryNormalizer.distinctDisplay(items.map { it.purchase.category })
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000L),
                initialValue = emptyList()
            )

    val categorySuggestions: StateFlow<List<String>> =
        combine(categoryOptions, _uiState) { categories, state ->
            val queryKey = CategoryNormalizer.key(state.category)
            if (queryKey.isBlank()) {
                emptyList()
            } else {
                categories
                    .filter {
                        val categoryKey = CategoryNormalizer.key(it)
                        categoryKey != queryKey && categoryKey.contains(queryKey)
                    }
                    .take(5)
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = emptyList()
        )

    private var original: PurchaseWithAttachments? = null
    private val stagedAttachments = linkedMapOf<String, StagedAttachment>()
    private val removedAttachments = linkedMapOf<String, Attachment>()
    private val userEditedOcrFields = mutableSetOf<OcrEditableField>()
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
                                    displayName = attachment.displayName.orEmpty(),
                                    type = AttachmentType.normalize(attachment.type),
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

    fun onMerchantChange(value: String) {
        userEditedOcrFields += OcrEditableField.MERCHANT
        updateText { copy(merchant = value) }
    }

    fun onCategoryChange(value: String) = updateText { copy(category = value) }

    fun onCategorySuggestionSelected(value: String) {
        updateText { copy(category = value) }
    }

    fun onModelNumberChange(value: String) {
        userEditedOcrFields += OcrEditableField.MODEL_NUMBER
        updateText { copy(modelNumber = value) }
    }

    fun onSerialNumberChange(value: String) {
        userEditedOcrFields += OcrEditableField.SERIAL_NUMBER
        updateText { copy(serialNumber = value) }
    }

    fun onNotesChange(value: String) = updateText { copy(notes = value) }

    fun onPurchaseValueChange(value: String) {
        userEditedOcrFields += OcrEditableField.PURCHASE_VALUE
        if (CurrencyUtils.isValidEditableAmount(value)) {
            _uiState.update {
                it.copy(
                    purchaseValue = value,
                    purchaseValueErrorRes = null,
                    errorMessageRes = null
                )
            }
        }
    }

    fun onPurchaseDateChange(value: LocalDate) {
        userEditedOcrFields += OcrEditableField.PURCHASE_DATE
        _uiState.update { it.copy(purchaseDate = value, errorMessageRes = null) }
    }

    fun onWarrantyEndDateChange(value: LocalDate?) {
        _uiState.update { it.copy(warrantyEndDate = value, errorMessageRes = null) }
    }

    fun onAttachmentSelected(uri: Uri) {
        viewModelScope.launch {
            when (val result = documentStore.stageAttachment(uri)) {
                is StageAttachmentResult.Success -> {
                    val staged = result.attachment
                    val checksum = staged.stagedFile.sha256
                    val duplicatesExisting = original
                        ?.attachments
                        .orEmpty()
                        .any { attachment ->
                            attachment.id !in removedAttachments &&
                                attachment.sha256 != null &&
                                attachment.sha256 == checksum
                        }
                    val duplicatesStaged = stagedAttachments.values.any { attachment ->
                        attachment.stagedFile.sha256 == checksum
                    }

                    if (duplicatesExisting || duplicatesStaged) {
                        documentStore.discardStagedAttachment(staged)
                        _uiState.update {
                            it.copy(
                                errorMessageRes = null,
                                infoMessageRes = R.string.attachment_duplicate_ignored
                            )
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
                                displayName = staged.displayName,
                                type = staged.type,
                                isStaged = true
                            ),
                            errorMessageRes = null,
                            infoMessageRes = null
                        )
                    }

                    if (staged.stagedFile.mimeType.startsWith("image/")) {
                        processImageForOcr(staged.stagedFile.path, staged.stagedFile.id)
                    }
                }
                StageAttachmentResult.UnsupportedType -> showAttachmentError(
                    R.string.error_document_unsupported
                )
                StageAttachmentResult.TooLarge -> showAttachmentError(
                    R.string.error_document_too_large
                )
                StageAttachmentResult.Empty -> showAttachmentError(
                    R.string.error_document_empty
                )
                StageAttachmentResult.InvalidContent -> showAttachmentError(
                    R.string.error_document_invalid
                )
                StageAttachmentResult.ReadError -> showAttachmentError(
                    R.string.error_document_copy
                )
            }
        }
    }

    private fun showAttachmentError(@StringRes messageRes: Int) {
        _uiState.update {
            it.copy(
                errorMessageRes = messageRes,
                infoMessageRes = null
            )
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

    fun updateAttachmentMetadata(
        id: String,
        displayName: String,
        type: String
    ) {
        val normalizedType = AttachmentType.normalize(type)
        val normalizedName = displayName.trim()

        stagedAttachments[id]?.let { staged ->
            stagedAttachments[id] = staged.copy(
                type = normalizedType,
                displayName = normalizedName
            )
        }

        _uiState.update { state ->
            state.copy(
                attachments = state.attachments.map { attachment ->
                    if (attachment.id == id) {
                        attachment.copy(
                            displayName = normalizedName,
                            type = normalizedType
                        )
                    } else {
                        attachment
                    }
                }
            )
        }
    }

    private suspend fun processImageForOcr(imagePath: String, attachmentId: String) {
        pendingOcrCount += 1
        _uiState.update { it.copy(isAnalyzingDocument = true) }

        try {
            val text = receiptOcrEngine.recognize(File(imagePath))
            val staged = stagedAttachments[attachmentId] ?: return

            stagedAttachments[attachmentId] = staged.copy(ocrText = text)
            val parsed = ReceiptOcrParser.parse(text)
            _uiState.update { state ->
                applyOcrResult(state, parsed)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            if (attachmentId in stagedAttachments) {
                _uiState.update {
                    it.copy(
                        infoMessageRes = R.string.ocr_read_failed,
                        errorMessageRes = null
                    )
                }
            }
        } finally {
            finishOcr()
        }
    }

    private fun finishOcr() {
        pendingOcrCount = (pendingOcrCount - 1).coerceAtLeast(0)
        _uiState.update { it.copy(isAnalyzingDocument = pendingOcrCount > 0) }
    }

    fun applyMerchantSuggestion() {
        val suggestion = _uiState.value.ocrSuggestions?.merchant ?: return
        userEditedOcrFields += OcrEditableField.MERCHANT
        _uiState.update {
            it.copy(
                merchant = suggestion,
                ocrSuggestions = it.ocrSuggestions?.copy(merchant = null)
            )
        }
    }

    fun applyPurchaseDateSuggestion() {
        val suggestion = _uiState.value.ocrSuggestions?.purchaseDate ?: return
        userEditedOcrFields += OcrEditableField.PURCHASE_DATE
        _uiState.update {
            it.copy(
                purchaseDate = suggestion,
                ocrSuggestions = it.ocrSuggestions?.copy(purchaseDate = null)
            )
        }
    }

    fun applyPurchaseValueSuggestion() {
        val suggestion = _uiState.value.ocrSuggestions?.purchaseValueCents ?: return
        userEditedOcrFields += OcrEditableField.PURCHASE_VALUE
        _uiState.update {
            it.copy(
                purchaseValue = CurrencyUtils.centsToEditable(suggestion),
                ocrSuggestions = it.ocrSuggestions?.copy(purchaseValueCents = null)
            )
        }
    }

    fun applyModelSuggestion() {
        val suggestion = _uiState.value.ocrSuggestions?.modelNumber ?: return
        userEditedOcrFields += OcrEditableField.MODEL_NUMBER
        _uiState.update {
            it.copy(
                modelNumber = suggestion,
                ocrSuggestions = it.ocrSuggestions?.copy(modelNumber = null)
            )
        }
    }

    fun applySerialSuggestion() {
        val suggestion = _uiState.value.ocrSuggestions?.serialNumber ?: return
        userEditedOcrFields += OcrEditableField.SERIAL_NUMBER
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
                it.copy(errorMessageRes = R.string.error_warranty_before_purchase)
            }
            return
        }

        val purchaseValueCents = if (state.purchaseValue.isBlank()) {
            null
        } else {
            CurrencyUtils.parseToCents(state.purchaseValue)
        }

        if (state.purchaseValue.isNotBlank() && purchaseValueCents == null) {
            _uiState.update { it.copy(purchaseValueErrorRes = R.string.error_invalid_value) }
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
            category = CategoryNormalizer.canonicalDisplay(
                state.category,
                categoryOptions.value
            ),
            modelNumber = state.modelNumber.trim().ifBlank { null },
            serialNumber = state.serialNumber.trim().ifBlank { null },
            notes = state.notes.trim(),
            createdAt = previous?.createdAt?.takeIf { it > 0L } ?: now,
            updatedAt = now
        )

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessageRes = null) }
            try {
                val updatedAttachments = state.attachments
                    .asSequence()
                    .filterNot { it.isStaged }
                    .mapNotNull { uiAttachment ->
                        original?.attachments
                            ?.firstOrNull { it.id == uiAttachment.id }
                            ?.copy(
                                displayName = uiAttachment.displayName.trim().ifBlank { null },
                                type = AttachmentType.normalize(uiAttachment.type)
                            )
                    }
                    .toList()

                documentStore.savePurchase(
                    purchase = purchase,
                    stagedAttachments = stagedAttachments.values.toList(),
                    updatedAttachments = updatedAttachments,
                    removedAttachments = removedAttachments.values.toList()
                )
                stagedAttachments.clear()
                removedAttachments.clear()
                _uiState.update { it.copy(isSaving = false) }
                originalSnapshot.value = snapshotOf(_uiState.value)
                _saved.emit(Unit)
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessageRes = R.string.error_save_purchase
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
        _uiState.update { it.transform().copy(errorMessageRes = null) }
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
        attachments = state.attachments.map { attachment ->
            AttachmentEditorSnapshot(
                id = attachment.id,
                displayName = attachment.displayName,
                type = attachment.type
            )
        }
    )

    private fun applyOcrResult(
        state: AddEditUiState,
        parsed: ReceiptOcrSuggestions
    ): AddEditUiState {
        val merged = mergeSuggestions(state.ocrSuggestions, parsed)

        val autoFillMerchant =
            state.merchant.isBlank() &&
                OcrEditableField.MERCHANT !in userEditedOcrFields &&
                merged.merchant != null
        val autoFillValue =
            state.purchaseValue.isBlank() &&
                OcrEditableField.PURCHASE_VALUE !in userEditedOcrFields &&
                merged.purchaseValueCents != null
        val autoFillDate =
            state.purchaseDate == null &&
                OcrEditableField.PURCHASE_DATE !in userEditedOcrFields &&
                merged.purchaseDate != null
        val autoFillModel =
            state.modelNumber.isBlank() &&
                OcrEditableField.MODEL_NUMBER !in userEditedOcrFields &&
                merged.modelNumber != null
        val autoFillSerial =
            state.serialNumber.isBlank() &&
                OcrEditableField.SERIAL_NUMBER !in userEditedOcrFields &&
                merged.serialNumber != null

        val merchant = if (autoFillMerchant) merged.merchant.orEmpty() else state.merchant
        val purchaseValue = if (autoFillValue) {
            CurrencyUtils.centsToEditable(checkNotNull(merged.purchaseValueCents))
        } else {
            state.purchaseValue
        }
        val purchaseDate = if (autoFillDate) merged.purchaseDate else state.purchaseDate
        val modelNumber = if (autoFillModel) merged.modelNumber.orEmpty() else state.modelNumber
        val serialNumber = if (autoFillSerial) merged.serialNumber.orEmpty() else state.serialNumber

        val remainingSuggestions = merged.copy(
            merchant = merged.merchant
                ?.takeUnless { autoFillMerchant || sameTextValue(state.merchant, it) },
            purchaseDate = merged.purchaseDate
                ?.takeUnless { autoFillDate || state.purchaseDate == it },
            purchaseValueCents = merged.purchaseValueCents
                ?.takeUnless {
                    autoFillValue ||
                        CurrencyUtils.parseToCents(state.purchaseValue) == it
                },
            modelNumber = merged.modelNumber
                ?.takeUnless { autoFillModel || sameTextValue(state.modelNumber, it) },
            serialNumber = merged.serialNumber
                ?.takeUnless { autoFillSerial || sameTextValue(state.serialNumber, it) }
        )

        val didAutoFill =
            autoFillMerchant || autoFillValue || autoFillDate || autoFillModel || autoFillSerial

        return state.copy(
            merchant = merchant,
            purchaseValue = purchaseValue,
            purchaseDate = purchaseDate,
            modelNumber = modelNumber,
            serialNumber = serialNumber,
            ocrSuggestions = remainingSuggestions,
            hasOcrAutofill = state.hasOcrAutofill || didAutoFill,
            purchaseValueErrorRes = if (autoFillValue) null else state.purchaseValueErrorRes,
            errorMessageRes = null
        )
    }

    private fun sameTextValue(current: String, suggestion: String): Boolean =
        current.trim().equals(suggestion.trim(), ignoreCase = true)

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
