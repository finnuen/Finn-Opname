package com.example.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow

class InventoryRepository(
    private val database: AppDatabase,
    private val sheetDao: SheetDao,
    private val itemDao: ItemDao
) {
    val allSheets: Flow<List<InventorySheet>> = sheetDao.getAllSheets()

    fun getItemsForSheet(sheetId: Long): Flow<List<InventoryItem>> =
        itemDao.getItemsForSheet(sheetId)

    suspend fun getItemsForSheetSync(sheetId: Long): List<InventoryItem> =
        itemDao.getItemsForSheetSync(sheetId)

    suspend fun getSheetById(id: Long): InventorySheet? = sheetDao.getSheetById(id)

    suspend fun getSheetCount(): Int = sheetDao.getSheetCount()

    suspend fun insertSheet(name: String): Long {
        // MAX + 1 rather than COUNT: after a delete or a reorder, COUNT can collide with an existing index.
        val next = sheetDao.getMaxOrderIndex() + 1
        return sheetDao.insertSheet(InventorySheet(name = name, orderIndex = next))
    }

    suspend fun updateSheet(sheet: InventorySheet) = sheetDao.updateSheet(sheet)

    suspend fun deleteSheet(sheet: InventorySheet) = sheetDao.deleteSheet(sheet)

    suspend fun deleteSheetById(id: Long) = sheetDao.deleteSheetById(id)

    suspend fun duplicateSheet(sourceSheetId: Long, newName: String): Long {
        val newSheetId = insertSheet(newName)
        val items = itemDao.getItemsForSheetSync(sourceSheetId)
        // orderIndex is copied as-is, so the duplicate keeps the source's order. The copies are
        // new items in a new sheet, so they start their own history here.
        val now = System.currentTimeMillis()
        val duplicatedItems = items.map {
            it.copy(id = 0, sheetId = newSheetId, createdAt = now, updatedAt = now)
        }
        if (duplicatedItems.isNotEmpty()) {
            itemDao.insertItems(duplicatedItems)
        }
        return newSheetId
    }

    suspend fun reorderSheets(orderedIds: List<Long>) {
        database.withTransaction {
            orderedIds.forEachIndexed { index, id -> sheetDao.setSheetOrder(id, index) }
        }
    }

    suspend fun reorderItems(orderedIds: List<Long>) {
        database.withTransaction {
            orderedIds.forEachIndexed { index, id -> itemDao.setItemOrder(id, index) }
        }
    }

    suspend fun getItemByCode(sheetId: Long, code: String): InventoryItem? =
        itemDao.getItemByCode(sheetId, code)

    suspend fun getItemById(id: Long): InventoryItem? = itemDao.getItemById(id)

    /** Inserts exactly as given, keeping its orderIndex. Used when undo/redo restores an item in place. */
    suspend fun insertItem(item: InventoryItem): Long {
        val id = itemDao.insertItem(item)
        touchSheet(item.sheetId)
        return id
    }

    /** Inserts a brand-new item at the top of its sheet and returns it with its id and position. */
    suspend fun insertItemAtTop(item: InventoryItem): InventoryItem {
        val toInsert = item.copy(orderIndex = itemDao.getMinOrderIndex(item.sheetId) - 1)
        val inserted = toInsert.copy(id = itemDao.insertItem(toInsert))
        touchSheet(item.sheetId)
        return inserted
    }

    suspend fun getMinOrderIndex(sheetId: Long): Int = itemDao.getMinOrderIndex(sheetId)

    suspend fun insertItems(items: List<InventoryItem>): List<Long> {
        val ids = itemDao.insertItems(items)
        items.map { it.sheetId }.distinct().forEach { touchSheet(it) }
        return ids
    }

    suspend fun updateItem(item: InventoryItem) {
        itemDao.updateItem(item)
        touchSheet(item.sheetId)
    }

    suspend fun deleteItem(item: InventoryItem) {
        itemDao.deleteItem(item)
        touchSheet(item.sheetId)
    }

    suspend fun deleteItemById(id: Long) {
        val sheetId = itemDao.getItemById(id)?.sheetId
        itemDao.deleteItemById(id)
        sheetId?.let { touchSheet(it) }
    }

    /** Marks a sheet as changed now, so the drawer's "Last modified" line stays honest. */
    suspend fun touchSheet(sheetId: Long) = sheetDao.touchSheet(sheetId, System.currentTimeMillis())
}
