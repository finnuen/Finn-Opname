package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.DEFAULT_SHEET_NAME
import com.example.data.InventoryItem
import com.example.data.InventoryRepository
import com.example.data.InventorySheet
import com.example.data.SettingsStore
import com.example.util.CsvHandler
import com.example.util.FeedbackUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class InventoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: InventoryRepository
    private val feedbackUtil: FeedbackUtil = FeedbackUtil(application)
    private val settings = SettingsStore(application)

    val allSheets: StateFlow<List<InventorySheet>>

    private val _activeSheetId = MutableStateFlow<Long?>(null)
    val activeSheetId: StateFlow<Long?> = _activeSheetId.asStateFlow()

    private val _activeSheet = MutableStateFlow<InventorySheet?>(null)
    val activeSheet: StateFlow<InventorySheet?> = _activeSheet.asStateFlow()

    val currentItems: StateFlow<List<InventoryItem>>

    // Undo / Redo
    private val undoStack = mutableListOf<UndoAction>()
    private val redoStack = mutableListOf<UndoAction>()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    // Dark Mode, restored from the last launch.
    private val _isDarkMode = MutableStateFlow(settings.isDarkMode)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    // UI Dialog States
    private val _selectedItemForDetail = MutableStateFlow<InventoryItem?>(null)
    val selectedItemForDetail: StateFlow<InventoryItem?> = _selectedItemForDetail.asStateFlow()

    private val _isScannerOpen = MutableStateFlow(false)
    val isScannerOpen: StateFlow<Boolean> = _isScannerOpen.asStateFlow()

    private val _isManualAddOpen = MutableStateFlow(false)
    val isManualAddOpen: StateFlow<Boolean> = _isManualAddOpen.asStateFlow()

    private val _scanBannerMessage = MutableStateFlow<String?>(null)
    val scanBannerMessage: StateFlow<String?> = _scanBannerMessage.asStateFlow()

    // Scanning debounce
    private var lastScanAt = 0L

    private companion object {
        /** Hard ceiling on accepted scans: one per second, whichever code it is. */
        const val SCAN_INTERVAL_MS = 1000L
    }

    init {
        val db = AppDatabase.getDatabase(application)
        repository = InventoryRepository(db, db.sheetDao(), db.itemDao())

        allSheets = repository.allSheets.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

        @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
        currentItems = _activeSheetId.flatMapLatest { sheetId ->
            if (sheetId != null) {
                repository.getItemsForSheet(sheetId)
            } else {
                flowOf(emptyList())
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        // Initialize default sheet if empty. Collect the database flow, not the UI StateFlow:
        // the StateFlow starts with an empty list before the database answers, and treating that
        // placeholder as "no sheets" added a fresh sheet on every launch.
        viewModelScope.launch {
            repository.allSheets.collect { sheets ->
                if (sheets.isEmpty()) {
                    val defaultId = repository.insertSheet(DEFAULT_SHEET_NAME)
                    _activeSheetId.value = defaultId
                } else if (_activeSheetId.value == null || sheets.none { it.id == _activeSheetId.value }) {
                    _activeSheetId.value = sheets.first().id
                }
                _activeSheet.value = sheets.find { it.id == _activeSheetId.value }
            }
        }
    }

    fun toggleDarkMode() {
        val enabled = !_isDarkMode.value
        _isDarkMode.value = enabled
        settings.isDarkMode = enabled
    }

    fun selectSheet(sheet: InventorySheet) {
        _activeSheetId.value = sheet.id
        _activeSheet.value = sheet
        undoStack.clear()
        redoStack.clear()
        updateUndoRedoStates()
    }

    fun createNewSheet(name: String) {
        val trimmed = name.trim().ifBlank { DEFAULT_SHEET_NAME }
        viewModelScope.launch {
            val id = repository.insertSheet(trimmed)
            val created = repository.getSheetById(id)
            if (created != null) {
                selectSheet(created)
            }
        }
    }

    fun renameSheet(sheet: InventorySheet, newName: String) {
        val trimmed = newName.trim().ifBlank { sheet.name }
        viewModelScope.launch {
            val updated = sheet.copy(name = trimmed, updatedAt = System.currentTimeMillis())
            repository.updateSheet(updated)
            if (_activeSheetId.value == sheet.id) {
                _activeSheet.value = updated
            }
        }
    }

    fun duplicateSheet(sheet: InventorySheet) {
        viewModelScope.launch {
            val newId = repository.duplicateSheet(sheet.id, "${sheet.name} (Copy)")
            val created = repository.getSheetById(newId)
            if (created != null) {
                selectSheet(created)
            }
        }
    }

    fun deleteSheet(sheet: InventorySheet) {
        // The observer above already selects another sheet, and creates a fresh sheet
        // when this was the last one. Creating one here as well left two empty sheets behind.
        viewModelScope.launch {
            repository.deleteSheet(sheet)
            undoStack.clear()
            redoStack.clear()
            updateUndoRedoStates()
        }
    }

    fun openItemDetail(item: InventoryItem) {
        _selectedItemForDetail.value = item
    }

    fun closeItemDetail() {
        _selectedItemForDetail.value = null
    }

    fun openScanner() {
        _isScannerOpen.value = true
        _scanBannerMessage.value = null
    }

    fun closeScanner() {
        _isScannerOpen.value = false
        _scanBannerMessage.value = null
    }

    fun openManualAdd() {
        _isManualAddOpen.value = true
    }

    fun closeManualAdd() {
        _isManualAddOpen.value = false
    }

    fun onBarcodeScanned(code: String) {
        val currentSheetId = _activeSheetId.value ?: return
        val now = System.currentTimeMillis()
        // One accepted scan per second, whatever the code is. The camera already requires several
        // agreeing frames before calling this, but that is a confidence check, not a rate limit:
        // a stack of labels can still confirm several codes in a row.
        if (now - lastScanAt < SCAN_INTERVAL_MS) return
        lastScanAt = now

        feedbackUtil.playScanFeedback()

        viewModelScope.launch {
            val existingItem = repository.getItemByCode(currentSheetId, code)
            if (existingItem != null) {
                val oldQty = existingItem.quantity
                val newQty = oldQty + 1
                val updated = existingItem.copy(quantity = newQty, updatedAt = now)
                repository.updateItem(updated)
                pushUndo(UndoAction.QuantityChange(existingItem.id, oldQty, newQty))
                _scanBannerMessage.value = "Counted '${existingItem.name}': $newQty"
            } else {
                val newItem = InventoryItem(
                    sheetId = currentSheetId,
                    name = code,
                    code = code,
                    quantity = 1,
                    updatedAt = now
                )
                val inserted = repository.insertItemAtTop(newItem)
                pushUndo(UndoAction.ItemAdded(inserted))
                _scanBannerMessage.value = "Added new '$code' (Qty: 1)"
            }
        }
    }

    fun incrementQuantity(item: InventoryItem) {
        val oldQty = item.quantity
        val newQty = oldQty + 1
        val updated = item.copy(quantity = newQty, updatedAt = System.currentTimeMillis())
        viewModelScope.launch {
            repository.updateItem(updated)
            pushUndo(UndoAction.QuantityChange(item.id, oldQty, newQty))
            // Update detail modal if open
            if (_selectedItemForDetail.value?.id == item.id) {
                _selectedItemForDetail.value = updated
            }
        }
    }

    fun decrementQuantity(item: InventoryItem) {
        val oldQty = item.quantity
        if (oldQty <= 1) return // Keep at least 1, user can tap X to delete
        val newQty = oldQty - 1
        val updated = item.copy(quantity = newQty, updatedAt = System.currentTimeMillis())
        viewModelScope.launch {
            repository.updateItem(updated)
            pushUndo(UndoAction.QuantityChange(item.id, oldQty, newQty))
            // Update detail modal if open
            if (_selectedItemForDetail.value?.id == item.id) {
                _selectedItemForDetail.value = updated
            }
        }
    }

    fun setQuantity(item: InventoryItem, newQty: Int) {
        val sanitized = newQty.coerceAtLeast(0)
        val oldQty = item.quantity
        if (sanitized == oldQty) return
        val updated = item.copy(quantity = sanitized, updatedAt = System.currentTimeMillis())
        viewModelScope.launch {
            repository.updateItem(updated)
            pushUndo(UndoAction.QuantityChange(item.id, oldQty, sanitized))
            if (_selectedItemForDetail.value?.id == item.id) {
                _selectedItemForDetail.value = updated
            }
        }
    }

    fun deleteItem(item: InventoryItem) {
        viewModelScope.launch {
            repository.deleteItem(item)
            pushUndo(UndoAction.ItemDeleted(item))
            if (_selectedItemForDetail.value?.id == item.id) {
                _selectedItemForDetail.value = null
            }
        }
    }

    fun updateItemDetails(item: InventoryItem, newName: String, newCode: String, newQty: Int) {
        val updated = item.copy(
            name = newName.trim().ifBlank { item.name },
            code = newCode.trim().ifBlank { item.code },
            quantity = newQty.coerceAtLeast(0),
            updatedAt = System.currentTimeMillis()
        )
        viewModelScope.launch {
            repository.updateItem(updated)
            pushUndo(UndoAction.ItemEdited(item, updated))
            _selectedItemForDetail.value = updated
        }
    }

    fun addManualItem(name: String, code: String, quantity: Int) {
        val sheetId = _activeSheetId.value ?: return
        val finalCode = code.trim().ifBlank { name.trim() }
        val finalName = name.trim().ifBlank { finalCode }
        val finalQty = quantity.coerceAtLeast(1)

        viewModelScope.launch {
            val existing = repository.getItemByCode(sheetId, finalCode)
            if (existing != null) {
                val oldQty = existing.quantity
                val newQty = oldQty + finalQty
                repository.updateItem(existing.copy(quantity = newQty, updatedAt = System.currentTimeMillis()))
                pushUndo(UndoAction.QuantityChange(existing.id, oldQty, newQty))
            } else {
                val item = InventoryItem(
                    sheetId = sheetId,
                    name = finalName,
                    code = finalCode,
                    quantity = finalQty,
                    updatedAt = System.currentTimeMillis()
                )
                val inserted = repository.insertItemAtTop(item)
                pushUndo(UndoAction.ItemAdded(inserted))
            }
            closeManualAdd()
        }
    }

    fun importCsv(csvContent: String, sheetName: String? = null) {
        viewModelScope.launch {
            val parsed = withContext(Dispatchers.Default) {
                CsvHandler.parseCsv(csvContent)
            }
            if (parsed.isEmpty()) return@launch

            val targetSheetId = if (sheetName != null) {
                repository.insertSheet(sheetName)
            } else {
                _activeSheetId.value ?: repository.insertSheet(DEFAULT_SHEET_NAME)
            }

            val firstIndex = repository.getMinOrderIndex(targetSheetId) - parsed.size
            val itemsToInsert = parsed.mapIndexed { index, it ->
                InventoryItem(
                    sheetId = targetSheetId,
                    name = it.name,
                    code = it.code,
                    quantity = it.quantity,
                    updatedAt = System.currentTimeMillis(),
                    orderIndex = firstIndex + index
                )
            }

            val insertedIds = repository.insertItems(itemsToInsert)
            val itemsWithIds = itemsToInsert.mapIndexed { idx, it ->
                it.copy(id = insertedIds.getOrElse(idx) { 0L })
            }

            val targetSheet = repository.getSheetById(targetSheetId)
            if (targetSheet != null) {
                selectSheet(targetSheet)
            }

            pushUndo(UndoAction.BatchAdded(targetSheetId, itemsWithIds))
        }
    }

    /** Builds the CSV for the given sheet (not necessarily the active one) and hands it back on the main thread. */
    fun exportSheetCsv(sheet: InventorySheet, onReady: (String) -> Unit) {
        viewModelScope.launch {
            val items = repository.getItemsForSheetSync(sheet.id)
            onReady(CsvHandler.exportToCsv(items))
        }
    }

    fun reorderSheets(orderedIds: List<Long>) {
        viewModelScope.launch { repository.reorderSheets(orderedIds) }
    }

    fun reorderItems(orderedIds: List<Long>) {
        viewModelScope.launch { repository.reorderItems(orderedIds) }
    }

    // Undo / Redo engine
    private fun pushUndo(action: UndoAction) {
        undoStack.add(action)
        redoStack.clear()
        updateUndoRedoStates()
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        val action = undoStack.removeAt(undoStack.lastIndex)
        viewModelScope.launch {
            when (action) {
                is UndoAction.QuantityChange -> {
                    val item = repository.getItemById(action.itemId)
                    if (item != null) {
                        repository.updateItem(item.copy(quantity = action.previousQty))
                        if (_selectedItemForDetail.value?.id == item.id) {
                            _selectedItemForDetail.value = item.copy(quantity = action.previousQty)
                        }
                    }
                }
                is UndoAction.ItemAdded -> {
                    repository.deleteItem(action.item)
                    if (_selectedItemForDetail.value?.id == action.item.id) {
                        _selectedItemForDetail.value = null
                    }
                }
                is UndoAction.ItemDeleted -> {
                    val restoredId = repository.insertItem(action.item.copy(id = 0))
                    // adjust action so redo knows the new ID if re-deleted
                }
                is UndoAction.ItemEdited -> {
                    repository.updateItem(action.oldItem)
                    if (_selectedItemForDetail.value?.id == action.oldItem.id) {
                        _selectedItemForDetail.value = action.oldItem
                    }
                }
                is UndoAction.BatchAdded -> {
                    for (item in action.items) {
                        repository.deleteItemById(item.id)
                    }
                }
            }
            redoStack.add(action)
            updateUndoRedoStates()
        }
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val action = redoStack.removeAt(redoStack.lastIndex)
        viewModelScope.launch {
            when (action) {
                is UndoAction.QuantityChange -> {
                    val item = repository.getItemById(action.itemId)
                    if (item != null) {
                        repository.updateItem(item.copy(quantity = action.newQty))
                        if (_selectedItemForDetail.value?.id == item.id) {
                            _selectedItemForDetail.value = item.copy(quantity = action.newQty)
                        }
                    }
                }
                is UndoAction.ItemAdded -> {
                    val id = repository.insertItem(action.item.copy(id = 0))
                }
                is UndoAction.ItemDeleted -> {
                    repository.deleteItem(action.item)
                }
                is UndoAction.ItemEdited -> {
                    repository.updateItem(action.newItem)
                    if (_selectedItemForDetail.value?.id == action.newItem.id) {
                        _selectedItemForDetail.value = action.newItem
                    }
                }
                is UndoAction.BatchAdded -> {
                    val reinserted = action.items.map { it.copy(id = 0) }
                    repository.insertItems(reinserted)
                }
            }
            undoStack.add(action)
            updateUndoRedoStates()
        }
    }

    private fun updateUndoRedoStates() {
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }

    override fun onCleared() {
        super.onCleared()
        feedbackUtil.release()
    }
}
