package com.example

import com.example.data.InventoryItem
import com.example.util.CsvHandler
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.TimeZone

class ExampleUnitTest {

    // Export writes fixed yyyy-MM-dd HH:mm, which is only a fixed string in a fixed zone.
    private lateinit var originalZone: TimeZone

    @Before
    fun useUtc() {
        originalZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun restoreZone() {
        TimeZone.setDefault(originalZone)
    }

    private val originalItems = listOf(
        InventoryItem(
            id = 1,
            sheetId = 1,
            name = "Canned potato",
            code = "123",
            quantity = 4,
            createdAt = 1_700_000_000_000L, // 2023-11-14 22:13 UTC
            updatedAt = 1_700_003_600_000L  // 2023-11-14 23:13 UTC
        ),
        InventoryItem(
            id = 2,
            sheetId = 1,
            name = "Soda Can, 330ml",
            code = "889900",
            quantity = 12,
            createdAt = 1_700_007_200_000L, // 2023-11-15 00:13 UTC
            updatedAt = 1_700_010_800_000L  // 2023-11-15 01:13 UTC
        )
    )

    @Test
    fun csv_exportAndImport_isConsistent() {
        val csvString = CsvHandler.exportToCsv(originalItems)
        val parsed = CsvHandler.parseCsv(csvString)

        assertEquals(2, parsed.size)
        assertEquals("Canned potato", parsed[0].name)
        assertEquals("123", parsed[0].code)
        assertEquals(4, parsed[0].quantity)

        assertEquals("Soda Can, 330ml", parsed[1].name)
        assertEquals("889900", parsed[1].code)
        assertEquals(12, parsed[1].quantity)
    }

    @Test
    fun csv_exportCarriesEachItemsOwnDates() {
        val lines = CsvHandler.exportToCsv(originalItems).lines().filter { it.isNotBlank() }

        assertEquals("Name,Code,Quantity,Created,Last modified", lines[0])
        assertEquals(3, lines.size)
        assertTrue(
            "first row dates: ${lines[1]}",
            lines[1].endsWith(",2023-11-14 22:13,2023-11-14 23:13")
        )
        assertTrue(
            "second row dates: ${lines[2]}",
            lines[2].endsWith(",2023-11-15 00:13,2023-11-15 01:13")
        )
    }
}
