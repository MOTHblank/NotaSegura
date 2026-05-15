package com.mothblank.notasegura.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(tableName = "warranty_items") // Anotação que define esta classe como uma tabela
data class WarrantyItem(
    @PrimaryKey // Anotação que define este campo como a chave primária
    val id: String, // Não vamos mais gerar com UUID aqui, passaremos no momento da criação
    val name: String,
    val purchaseDate: LocalDate,
    val expirationDate: LocalDate,
    val category: String,
    val imagePath: String? = null
)