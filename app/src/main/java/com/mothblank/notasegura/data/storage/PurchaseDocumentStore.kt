package com.mothblank.notasegura.data.storage

import android.content.Context
import android.net.Uri
import com.mothblank.notasegura.domain.model.Attachment
import com.mothblank.notasegura.domain.model.AttachmentType
import com.mothblank.notasegura.domain.model.Purchase
import com.mothblank.notasegura.domain.model.PurchaseWithAttachments
import com.mothblank.notasegura.domain.repository.PurchaseRepository
import com.mothblank.notasegura.util.FileStorageManager
import com.mothblank.notasegura.util.StageFileResult
import com.mothblank.notasegura.util.StagedFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

data class StagedAttachment(
    val stagedFile: StagedFile,
    val type: String = AttachmentType.RECEIPT,
    val ocrText: String? = null
)

sealed interface StageAttachmentResult {
    data class Success(val attachment: StagedAttachment) : StageAttachmentResult
    data object UnsupportedType : StageAttachmentResult
    data object TooLarge : StageAttachmentResult
    data object Empty : StageAttachmentResult
    data object InvalidContent : StageAttachmentResult
    data object ReadError : StageAttachmentResult
}

class PurchaseDocumentStore(
    private val context: Context,
    private val repository: PurchaseRepository
) {
    fun getAllPurchases(): Flow<List<PurchaseWithAttachments>> =
        repository.getAllPurchases()

    suspend fun getPurchaseById(id: String): PurchaseWithAttachments? =
        repository.getPurchaseById(id)

    suspend fun stageAttachment(uri: Uri): StageAttachmentResult = withContext(Dispatchers.IO) {
        when (val result = FileStorageManager.stageUri(context, uri)) {
            is StageFileResult.Success ->
                StageAttachmentResult.Success(StagedAttachment(result.file))
            StageFileResult.UnsupportedType -> StageAttachmentResult.UnsupportedType
            StageFileResult.TooLarge -> StageAttachmentResult.TooLarge
            StageFileResult.Empty -> StageAttachmentResult.Empty
            StageFileResult.InvalidContent -> StageAttachmentResult.InvalidContent
            StageFileResult.ReadError -> StageAttachmentResult.ReadError
        }
    }

    fun discardStagedAttachment(staged: StagedAttachment?) {
        FileStorageManager.deleteStagedFile(context, staged?.stagedFile?.path)
    }

    suspend fun savePurchase(
        purchase: Purchase,
        stagedAttachments: List<StagedAttachment>,
        removedAttachments: List<Attachment>
    ) = withContext(Dispatchers.IO) {
        val committed = mutableListOf<Pair<StagedAttachment, String>>()
        var databaseSaved = false

        try {
            stagedAttachments.forEach { staged ->
                val managed = FileStorageManager.commitStagedFile(context, staged.stagedFile)
                    ?: throw IllegalStateException("Não foi possível salvar um dos documentos anexados.")
                committed += staged to managed.path
            }

            val newAttachments = committed.map { (staged, managedPath) ->
                Attachment(
                    id = staged.stagedFile.id,
                    purchaseId = purchase.id,
                    mimeType = staged.stagedFile.mimeType,
                    type = staged.type,
                    filePath = managedPath,
                    displayName = staged.stagedFile.displayName,
                    sha256 = staged.stagedFile.sha256,
                    ocrText = staged.ocrText,
                    createdAt = System.currentTimeMillis()
                )
            }

            repository.savePurchase(
                purchase = purchase,
                newAttachments = newAttachments,
                attachmentIdsToDelete = removedAttachments.map { it.id }
            )
            databaseSaved = true

            stagedAttachments.forEach { staged ->
                FileStorageManager.deleteStagedFile(context, staged.stagedFile.path)
            }

            removedAttachments.forEach {
                FileStorageManager.deleteManagedFile(context, it.filePath)
            }
        } catch (error: Throwable) {
            if (!databaseSaved) {
                committed.forEach { (_, managedPath) ->
                    FileStorageManager.deleteManagedFile(context, managedPath)
                }
            }
            throw error
        }
    }

    suspend fun deletePurchase(item: PurchaseWithAttachments) = withContext(Dispatchers.IO) {
        repository.deletePurchaseById(item.purchase.id)
        item.attachments.forEach { attachment ->
            FileStorageManager.deleteManagedFile(context, attachment.filePath)
        }
    }
}
