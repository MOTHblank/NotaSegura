package com.mothblank.notasegura.data.repository

import com.mothblank.notasegura.data.local.WarrantyItemDao
import com.mothblank.notasegura.domain.model.WarrantyItem
import com.mothblank.notasegura.domain.repository.WarrantyRepository
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

class WarrantyRepositoryImpl(
    private val dao: WarrantyItemDao
) : WarrantyRepository {
    override fun getAllItems(): Flow<List<WarrantyItem>> = dao.getAllItems()

    override suspend fun insertItem(item: WarrantyItem) = dao.insertItem(item)

    override suspend fun deleteItem(item: WarrantyItem) = dao.deleteItem(item)

    override suspend fun getItemById(id: String): WarrantyItem? = dao.getItemById(id)

    override suspend fun getItemsExpiringBetween(
        startDate: LocalDate,
        endDate: LocalDate
    ): List<WarrantyItem> = dao.getItemsExpiringBetween(startDate, endDate)
}
