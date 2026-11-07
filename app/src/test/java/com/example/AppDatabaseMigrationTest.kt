package com.example

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppDatabaseMigrationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dbName = "migration_test.db"
    private val donorName = "schema_donor.db"

    @After
    fun cleanUp() {
        context.deleteDatabase(dbName)
        context.deleteDatabase(donorName)
    }

    @Test
    fun `v2 to v4 adds timestamps and renames the old default sheet`() {
        // The v2 schema is the current one minus the columns the later migrations add, taken from
        // Room itself so a hand-written table definition can't drift from what the app created.
        writeVersionTwoDatabase()

        // Opening through Room runs the migrations and validates the result against the entities.
        val migrated = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4)
            .allowMainThreadQueries()
            .build()

        val sheets = runBlocking { migrated.sheetDao().getAllSheets().first() }
        assertEquals(listOf("New sheet", "Aisle 4"), sheets.map { it.name })
        // A sheet that was never touched since it was made keeps its creation time.
        assertEquals(1000L, sheets[0].updatedAt)
        assertEquals(2000L, sheets[1].updatedAt)

        val item = runBlocking { migrated.itemDao().getItemsForSheetSync(2) }.single()
        assertEquals("Bolt", item.name)
        assertEquals(3, item.quantity)
        // The item's only recorded time was its last change, so that is what it starts from.
        assertEquals(1500L, item.createdAt)
        assertEquals(1500L, item.updatedAt)
        migrated.close()
    }

    @Test
    fun `v3 to v4 gives every item a creation time`() {
        val path = context.getDatabasePath(dbName)
        path.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
            schemaAt(3).forEach { db.execSQL(it) }
            db.execSQL(
                "INSERT INTO inventory_sheets (id, name, createdAt, updatedAt, orderIndex) " +
                    "VALUES (1, 'New sheet', 1000, 4000, 0)"
            )
            db.execSQL(
                "INSERT INTO inventory_items (id, sheetId, name, code, quantity, updatedAt, orderIndex) " +
                    "VALUES (1, 1, 'Bolt', '5901234123457', 3, 7500, 0)"
            )
            db.execSQL(
                "INSERT INTO inventory_items (id, sheetId, name, code, quantity, updatedAt, orderIndex) " +
                    "VALUES (2, 1, 'Nut', '5901234123458', 1, 8000, 1)"
            )
            db.version = 3
        }

        val migrated = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_3_4)
            .allowMainThreadQueries()
            .build()

        val items = runBlocking { migrated.itemDao().getItemsForSheetSync(1) }
        assertEquals(listOf(7500L, 8000L), items.map { it.createdAt })
        assertEquals(listOf(7500L, 8000L), items.map { it.updatedAt })
        // The sheet's own timestamps are untouched by this migration.
        val sheet = runBlocking { migrated.sheetDao().getSheetById(1) }!!
        assertEquals(1000L, sheet.createdAt)
        assertEquals(4000L, sheet.updatedAt)
        migrated.close()
    }

    private fun writeVersionTwoDatabase() {
        context.deleteDatabase(dbName)
        val path = context.getDatabasePath(dbName)
        path.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
            schemaAt(2).forEach { db.execSQL(it) }
            db.execSQL(
                "INSERT INTO inventory_sheets (id, name, createdAt, orderIndex) VALUES (1, 'warehouse', 1000, 0)"
            )
            db.execSQL(
                "INSERT INTO inventory_sheets (id, name, createdAt, orderIndex) VALUES (2, 'Aisle 4', 2000, 1)"
            )
            db.execSQL(
                "INSERT INTO inventory_items (id, sheetId, name, code, quantity, updatedAt, orderIndex) " +
                    "VALUES (1, 2, 'Bolt', '5901234123457', 3, 1500, 0)"
            )
            db.version = 2
        }
    }

    /** DDL for the schema as it stood at [version], with the later migrations' columns removed. */
    private fun schemaAt(version: Int): List<String> {
        context.deleteDatabase(donorName)
        val donor = Room.databaseBuilder(context, AppDatabase::class.java, donorName)
            .allowMainThreadQueries()
            .build()
        val statements = mutableListOf<String>()
        donor.openHelper.writableDatabase.query(
            "SELECT sql FROM sqlite_master WHERE sql IS NOT NULL " +
                "AND name NOT LIKE 'room_%' AND name NOT LIKE 'sqlite_%' " +
                "AND name != 'android_metadata'"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                statements += cursor.getString(0)
            }
        }
        donor.close()

        return statements.map { sql ->
            var result = sql
            if (version < 4) result = withoutColumn(result, "inventory_items", "createdAt")
            if (version < 3) result = withoutColumn(result, "inventory_sheets", "updatedAt")
            result
        }
    }

    private fun withoutColumn(ddl: String, table: String, column: String): String {
        // Match the table being created, not a table merely referenced by a foreign key.
        if (!Regex("^CREATE TABLE (IF NOT EXISTS )?`$table`").containsMatchIn(ddl)) return ddl
        return ddl.replace(Regex(",\\s*`$column`[^,)]*"), "")
    }
}
