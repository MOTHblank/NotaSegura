package com.mothblank.notasegura.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mothblank.notasegura.domain.model.WarrantyItem
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface WarrantyItemDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: WarrantyItem)

    @Query("SELECT * FROM warranty_items ORDER BY expirationDate ASC")
    fun getAllItems(): Flow<List<WarrantyItem>>

    @Query("SELECT * FROM warranty_items WHERE id = :id")
    suspend fun getItemById(id: String): WarrantyItem?

    @Query(
        """
        SELECT * FROM warranty_items
        WHERE expirationDate BETWEEN :startDate AND :endDate
        ORDER BY expirationDate ASC
        """
    )
    suspend fun getItemsExpiringBetween(
        startDate: LocalDate,
        endDate: LocalDate
    ): List<WarrantyItem>

    @Delete
    suspend fun deleteItem(item: WarrantyItem)
}
