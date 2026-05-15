package com.mothblank.notasegura.domain.repository

import com.mothblank.notasegura.domain.model.WarrantyItem
import kotlinx.coroutines.flow.Flow


interface WarrantyRepository {

    fun getAllItems(): Flow<List<WarrantyItem>>

    suspend fun insertItem(item: WarrantyItem)
    suspend fun deleteItem(item: WarrantyItem)
    suspend fun getItemById(id: String): WarrantyItem?
}