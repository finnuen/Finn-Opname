package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SheetDao {
    @Query("SELECT * FROM inventory_sheets ORDER BY orderIndex ASC, id ASC")
    fun getAllSheets(): Flow<List<InventorySheet>>

    @Query("SELECT * FROM inventory_sheets WHERE id = :id LIMIT 1")
    suspend fun getSheetById(id: Long): InventorySheet?

    @Query("SELECT COUNT(*) FROM inventory_sheets")
    suspend fun getSheetCount(): Int

    @Query("SELECT COALESCE(MAX(orderIndex), -1) FROM inventory_sheets")
    suspend fun getMaxOrderIndex(): Int

    @Query("UPDATE inventory_sheets SET orderIndex = :orderIndex WHERE id = :id")
    suspend fun setSheetOrder(id: Long, orderIndex: Int)

    @Query("UPDATE inventory_sheets SET updatedAt = :timestamp WHERE id = :id")
    suspend fun touchSheet(id: Long, timestamp: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSheet(sheet: InventorySheet): Long

    @Update
    suspend fun updateSheet(sheet: InventorySheet)

    @Delete
    suspend fun deleteSheet(sheet: InventorySheet)

    @Query("DELETE FROM inventory_sheets WHERE id = :id")
    suspend fun deleteSheetById(id: Long)
}

@Dao
interface ItemDao {
    @Query("SELECT * FROM inventory_items WHERE sheetId = :sheetId ORDER BY orderIndex ASC, id DESC")
    fun getItemsForSheet(sheetId: Long): Flow<List<InventoryItem>>

    @Query("SELECT * FROM inventory_items WHERE sheetId = :sheetId ORDER BY orderIndex ASC, id DESC")
    suspend fun getItemsForSheetSync(sheetId: Long): List<InventoryItem>

    @Query("SELECT COALESCE(MIN(orderIndex), 0) FROM inventory_items WHERE sheetId = :sheetId")
    suspend fun getMinOrderIndex(sheetId: Long): Int

    @Query("UPDATE inventory_items SET orderIndex = :orderIndex WHERE id = :id")
    suspend fun setItemOrder(id: Long, orderIndex: Int)

    @Query("SELECT * FROM inventory_items WHERE sheetId = :sheetId AND code = :code LIMIT 1")
    suspend fun getItemByCode(sheetId: Long, code: String): InventoryItem?

    @Query("SELECT * FROM inventory_items WHERE id = :id LIMIT 1")
    suspend fun getItemById(id: Long): InventoryItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: InventoryItem): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<InventoryItem>): List<Long>

    @Update
    suspend fun updateItem(item: InventoryItem)

    @Delete
    suspend fun deleteItem(item: InventoryItem)

    @Query("DELETE FROM inventory_items WHERE id = :id")
    suspend fun deleteItemById(id: Long)

    @Query("DELETE FROM inventory_items WHERE sheetId = :sheetId")
    suspend fun deleteAllForSheet(sheetId: Long)
}
