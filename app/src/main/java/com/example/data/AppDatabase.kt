package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [InventorySheet::class, InventoryItem::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sheetDao(): SheetDao
    abstract fun itemDao(): ItemDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // v1 -> v2: items get a persisted position. Existing rows keep the order they had
        // on screen (newest first), so nobody's list is shuffled by the update.
        internal val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE inventory_items ADD COLUMN orderIndex INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    """
                    UPDATE inventory_items SET orderIndex = (
                        SELECT COUNT(*) FROM inventory_items AS newer
                        WHERE newer.sheetId = inventory_items.sheetId
                          AND newer.id > inventory_items.id
                    )
                    """.trimIndent()
                )
            }
        }

        // v2 -> v3: sheets remember when they last changed. Existing sheets start from their
        // creation time, so no sheet claims to have been modified before it existed.
        // The old auto-created sheets were named "warehouse"; the default is "New sheet" now,
        // and those rows were never renamed by hand, so they are carried over.
        internal val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE inventory_sheets ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE inventory_sheets SET updatedAt = createdAt")
                db.execSQL("UPDATE inventory_sheets SET name = '$DEFAULT_SHEET_NAME' WHERE name = 'warehouse'")
            }
        }

        // v3 -> v4: items remember when they were added. Rows written before this column existed
        // only recorded their last change, which is the closest thing to a creation time that was
        // ever stored for them; an item that was counted once and never edited keeps it exactly.
        internal val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE inventory_items ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE inventory_items SET createdAt = updatedAt")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "scancounter_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
