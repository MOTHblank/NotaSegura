package com.mothblank.notasegura.data.storage

import android.content.Context
import android.net.Uri
import com.mothblank.notasegura.domain.model.WarrantyItem
import com.mothblank.notasegura.domain.repository.WarrantyRepository
import com.mothblank.notasegura.util.FileStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class WarrantyDocumentStore(
    private val context: Context,
    private val repository: WarrantyRepository
) {
    fun getAllItems(): Flow<List<WarrantyItem>> = repository.getAllItems()

    suspend fun getItemById(id: String): WarrantyItem? = repository.getItemById(id)

    suspend fun stageImage(uri: Uri): String? = withContext(Dispatchers.IO) {
        FileStorageManager.stageImage(context, uri)
    }

    fun discardStagedImage(path: String?) {
        FileStorageManager.deleteStagedImage(context, path)
    }

    suspend fun saveItem(
        item: WarrantyItem,
        stagedImagePath: String?,
        previousImagePath: String?
    ): WarrantyItem = withContext(Dispatchers.IO) {
        val committedPath = stagedImagePath?.let {
            FileStorageManager.commitStagedImage(context, it)
                ?: throw IllegalStateException("Não foi possível salvar a imagem do documento.")
        }

        val savedItem = item.copy(imagePath = committedPath ?: previousImagePath)
        try {
            repository.insertItem(savedItem)
        } catch (error: Throwable) {
            if (committedPath != null) {
                FileStorageManager.deleteManagedImage(context, committedPath)
            }
            throw error
        }

        if (committedPath != null && previousImagePath != null && previousImagePath != committedPath) {
            FileStorageManager.deleteManagedImage(context, previousImagePath)
        }

        savedItem
    }

    suspend fun deleteItem(item: WarrantyItem) = withContext(Dispatchers.IO) {
        repository.deleteItem(item)
        FileStorageManager.deleteManagedImage(context, item.imagePath)
    }
}
