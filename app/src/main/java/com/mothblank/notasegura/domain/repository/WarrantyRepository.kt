package com.mothblank.notasegura.domain.repository

import com.mothblank.notasegura.domain.model.WarrantyItem
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface WarrantyRepository {
    fun getAllItems(): Flow<List<WarrantyItem>>
    suspend fun insertItem(item: WarrantyItem)
    suspend fun deleteItem(item: WarrantyItem)
    suspend fun getItemById(id: String): WarrantyItem?
    suspend fun getItemsExpiringBetween(startDate: LocalDate, endDate: LocalDate): List<WarrantyItem>
}
