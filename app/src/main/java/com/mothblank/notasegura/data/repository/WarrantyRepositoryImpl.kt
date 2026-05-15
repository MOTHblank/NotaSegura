package com.mothblank.notasegura.data.repository

import com.mothblank.notasegura.data.local.WarrantyItemDao
import com.mothblank.notasegura.domain.model.WarrantyItem
import com.mothblank.notasegura.domain.repository.WarrantyRepository
import kotlinx.coroutines.flow.Flow

/**
 * Implementação concreta do repositório. Ele depende do DAO para
 * obter os dados do banco de dados local.
 */
class WarrantyRepositoryImpl(
    private val dao: WarrantyItemDao
) : WarrantyRepository {

    override fun getAllItems(): Flow<List<WarrantyItem>> {
        return dao.getAllItems()
    }

    override suspend fun insertItem(item: WarrantyItem) {
        dao.insertItem(item)
    }
    override suspend fun deleteItem(item: WarrantyItem) {
        dao.deleteItem(item)
    }
    override suspend fun getItemById(id: String): WarrantyItem? {
        return dao.getItemById(id)
    }
}