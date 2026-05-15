package com.mothblank.notasegura.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mothblank.notasegura.domain.model.WarrantyItem
import kotlinx.coroutines.flow.Flow

@Dao
interface WarrantyItemDao {

    // Insere um novo item. Se já existir um com o mesmo ID, ele será substituído.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: WarrantyItem)

    // Busca todos os itens da tabela, ordenados pela data de expiração.
    // O retorno é um Flow, o que significa que a UI será notificada
    // automaticamente sobre qualquer mudança no banco de dados.
    @Query("SELECT * FROM warranty_items ORDER BY expirationDate ASC")
    fun getAllItems(): Flow<List<WarrantyItem>>

    @Query("SELECT * FROM warranty_items WHERE id = :id")
    suspend fun getItemById(id: String): WarrantyItem?

    @Delete
    suspend fun deleteItem(item: WarrantyItem)
}