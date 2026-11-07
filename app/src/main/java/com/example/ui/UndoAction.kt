package com.example.ui

import com.example.data.InventoryItem

sealed interface UndoAction {
    data class QuantityChange(
        val itemId: Long,
        val previousQty: Int,
        val newQty: Int
    ) : UndoAction

    data class ItemAdded(
        val item: InventoryItem
    ) : UndoAction

    data class ItemDeleted(
        val item: InventoryItem
    ) : UndoAction

    data class ItemEdited(
        val oldItem: InventoryItem,
        val newItem: InventoryItem
    ) : UndoAction

    data class BatchAdded(
        val sheetId: Long,
        val items: List<InventoryItem>
    ) : UndoAction
}
