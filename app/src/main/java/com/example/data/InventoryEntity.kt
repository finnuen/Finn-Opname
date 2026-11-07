package com.example.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Name given to the first sheet, and to the replacement made after the last one is removed. */
const val DEFAULT_SHEET_NAME = "New sheet"

@Entity(tableName = "inventory_sheets")
data class InventorySheet(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    /** Last time the sheet itself or any item in it changed. Shown in the drawer. */
    // The SQL default exists only so the v2 -> v3 ALTER TABLE matches the schema Room generates;
    // every insert carries a real timestamp.
    @ColumnInfo(defaultValue = "0")
    val updatedAt: Long = System.currentTimeMillis(),
    val orderIndex: Int = 0
)

@Entity(
    tableName = "inventory_items",
    foreignKeys = [
        ForeignKey(
            entity = InventorySheet::class,
            parentColumns = ["id"],
            childColumns = ["sheetId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("sheetId"),
        Index("code")
    ]
)
data class InventoryItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sheetId: Long,
    val name: String,
    val code: String,
    val quantity: Int = 1,
    /** When the item was first added to the sheet. */
    // The SQL default exists only so the v3 -> v4 ALTER TABLE matches the schema Room generates;
    // every insert carries a real timestamp.
    @ColumnInfo(defaultValue = "0")
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    // Position inside the sheet, lowest first. New items get (min - 1) so they land on top.
    val orderIndex: Int = 0
)
