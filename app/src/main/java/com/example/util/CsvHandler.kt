package com.example.util

import com.example.data.InventoryItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ParsedItem(
    val name: String,
    val code: String,
    val quantity: Int
)

object CsvHandler {

    /**
     * Writes one row per item, ending with that item's own creation and last-change times. Fixed
     * yyyy-MM-dd HH:mm rather than the app's locale format, so the file reads the same way wherever
     * it is opened. The importer ignores the two extra columns.
     */
    fun exportToCsv(items: List<InventoryItem>): String {
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        val sb = StringBuilder()
        sb.append("Name,Code,Quantity,Created,Last modified\n")
        for (item in items) {
            sb.append(escapeCsvField(item.name)).append(",")
            sb.append(escapeCsvField(item.code)).append(",")
            sb.append(item.quantity).append(",")
            sb.append(format.format(Date(item.createdAt))).append(",")
            sb.append(format.format(Date(item.updatedAt))).append("\n")
        }
        return sb.toString()
    }

    private fun escapeCsvField(field: String): String {
        return if (field.contains(",") || field.contains("\"") || field.contains("\n") || field.contains("\r")) {
            "\"" + field.replace("\"", "\"\"") + "\""
        } else {
            field
        }
    }

    fun parseCsv(content: String): List<ParsedItem> {
        val lines = content.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()

        // Check if first line is a header
        val firstLine = lines.first()
        val firstRowTokens = parseCsvLine(firstLine)

        var nameCol = 0
        var codeCol = 1
        var qtyCol = 2
        var startIndex = 0

        val lowerFirstRow = firstRowTokens.map { it.trim().lowercase() }
        val hasHeader = lowerFirstRow.any { it in listOf("name", "item", "title", "code", "barcode", "qr", "quantity", "qty", "count") }

        if (hasHeader) {
            startIndex = 1
            lowerFirstRow.forEachIndexed { index, token ->
                when {
                    token.contains("name") || token.contains("item") || token.contains("title") -> nameCol = index
                    token.contains("code") || token.contains("barcode") || token.contains("qr") -> codeCol = index
                    token.contains("qty") || token.contains("quantity") || token.contains("count") -> qtyCol = index
                }
            }
        }

        val result = mutableListOf<ParsedItem>()
        for (i in startIndex until lines.size) {
            val tokens = parseCsvLine(lines[i])
            if (tokens.isEmpty()) continue

            val rawName = tokens.getOrNull(nameCol)?.trim().orEmpty()
            val rawCode = tokens.getOrNull(codeCol)?.trim().orEmpty()
            val rawQty = tokens.getOrNull(qtyCol)?.trim()

            val name = rawName.ifBlank { rawCode.ifBlank { "Item ${result.size + 1}" } }
            val code = rawCode.ifBlank { rawName.ifBlank { "ITEM_${result.size + 1}" } }
            val quantity = rawQty?.toIntOrNull()?.coerceAtLeast(1) ?: 1

            result.add(ParsedItem(name = name, code = code, quantity = quantity))
        }

        return result
    }

    private fun parseCsvLine(line: String): List<String> {
        val tokens = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '\"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '\"') {
                        sb.append('\"')
                        i++ // skip escaped quote
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                c == ',' && !inQuotes -> {
                    tokens.add(sb.toString())
                    sb.clear()
                }
                c == ';' && !inQuotes && !line.contains(',') -> {
                    tokens.add(sb.toString())
                    sb.clear()
                }
                else -> {
                    sb.append(c)
                }
            }
            i++
        }
        tokens.add(sb.toString())
        return tokens
    }
}
