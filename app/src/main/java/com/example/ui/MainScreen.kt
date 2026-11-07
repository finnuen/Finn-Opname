package com.example.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.DEFAULT_SHEET_NAME
import com.example.data.InventoryItem
import com.example.data.InventorySheet
import com.example.ui.theme.ControlShape
import com.example.ui.theme.QuantityStyle
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

private fun safeFileName(name: String): String =
    name.replace("[^a-zA-Z0-9_-]".toRegex(), "_").ifBlank { "sheet" }

@Composable
fun MainScreen(viewModel: InventoryViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val allSheets by viewModel.allSheets.collectAsStateWithLifecycle()
    val activeSheet by viewModel.activeSheet.collectAsStateWithLifecycle()
    val currentItems by viewModel.currentItems.collectAsStateWithLifecycle()

    val canUndo by viewModel.canUndo.collectAsStateWithLifecycle()
    val canRedo by viewModel.canRedo.collectAsStateWithLifecycle()
    val isDarkMode by viewModel.isDarkMode.collectAsStateWithLifecycle()

    val selectedItemForDetail by viewModel.selectedItemForDetail.collectAsStateWithLifecycle()
    val isScannerOpen by viewModel.isScannerOpen.collectAsStateWithLifecycle()
    val isManualAddOpen by viewModel.isManualAddOpen.collectAsStateWithLifecycle()
    val scanBannerMessage by viewModel.scanBannerMessage.collectAsStateWithLifecycle()

    var showNewSheetDialog by remember { mutableStateOf(false) }
    var newSheetNameInput by remember { mutableStateOf("") }

    var isEditingSheetTitleInline by remember { mutableStateOf(false) }
    var inlineSheetTitleInput by remember { mutableStateOf("") }

    val itemListState = rememberLazyListState()
    val reorderableItems = rememberReorderableList(
        source = currentItems,
        idOf = { it.id },
        listState = itemListState,
        onCommit = { orderedIds -> viewModel.reorderItems(orderedIds) }
    )

    // CSV Import Launcher
    val csvImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val content = stream.bufferedReader().use { it.readText() }
                    viewModel.importCsv(content)
                    Toast.makeText(context, "CSV imported", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Couldn't read CSV: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // CSV "Save to device": the system file picker lets the user choose where it goes,
    // and needs no storage permission on any Android version.
    var pendingCsv by remember { mutableStateOf<String?>(null) }
    val csvSaveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        val text = pendingCsv
        pendingCsv = null
        if (uri != null && text != null) {
            try {
                val stream = context.contentResolver.openOutputStream(uri, "wt")
                    ?: throw IllegalStateException("Couldn't open the file for writing")
                stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                Toast.makeText(context, "CSV saved", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Couldn't save CSV: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun saveCsvToDevice(sheet: InventorySheet) {
        viewModel.exportSheetCsv(sheet) { csv ->
            pendingCsv = csv
            csvSaveLauncher.launch("${safeFileName(sheet.name)}.csv")
        }
    }

    fun shareCsv(sheet: InventorySheet) {
        viewModel.exportSheetCsv(sheet) { csv ->
            try {
                val cacheFolder = File(context.cacheDir, "exports")
                if (!cacheFolder.exists()) cacheFolder.mkdirs()
                val file = File(cacheFolder, "${safeFileName(sheet.name)}.csv")
                FileOutputStream(file).use { it.write(csv.toByteArray(Charsets.UTF_8)) }

                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "${sheet.name} inventory")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Share ${sheet.name} CSV"))
            } catch (e: Exception) {
                Toast.makeText(context, "Couldn't share CSV: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            DrawerContent(
                sheets = allSheets,
                activeSheetId = activeSheet?.id,
                canUndo = canUndo,
                canRedo = canRedo,
                isDarkMode = isDarkMode,
                onSelectSheet = { sheet ->
                    viewModel.selectSheet(sheet)
                    scope.launch { drawerState.close() }
                },
                onRemoveSheet = { sheet -> viewModel.deleteSheet(sheet) },
                onDuplicateSheet = { sheet ->
                    viewModel.duplicateSheet(sheet)
                    scope.launch { drawerState.close() }
                },
                onRenameSheet = { sheet, newName -> viewModel.renameSheet(sheet, newName) },
                onSaveSheetCsv = { sheet -> saveCsvToDevice(sheet) },
                onShareSheetCsv = { sheet -> shareCsv(sheet) },
                onReorderSheets = { orderedIds -> viewModel.reorderSheets(orderedIds) },
                onUndo = { viewModel.undo() },
                onRedo = { viewModel.redo() },
                onNewSheet = {
                    newSheetNameInput = ""
                    showNewSheetDialog = true
                },
                onImportCsv = {
                    csvImportLauncher.launch(arrayOf("text/*", "text/comma-separated-values", "text/csv", "application/csv"))
                },
                onToggleDarkMode = { viewModel.toggleDarkMode() }
            )
        }
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Column(modifier = Modifier.statusBarsPadding()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { scope.launch { drawerState.open() } },
                                modifier = Modifier.testTag("hamburger_menu_button")
                            ) {
                                Icon(Icons.Default.Menu, contentDescription = "Open sheets")
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(ControlShape)
                                    .clickable {
                                        inlineSheetTitleInput = activeSheet?.name.orEmpty()
                                        isEditingSheetTitleInline = true
                                    }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                                    .testTag("top_sheet_title_clickable")
                            ) {
                                Text(
                                    text = activeSheet?.name ?: DEFAULT_SHEET_NAME,
                                    style = MaterialTheme.typography.titleLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Rename sheet",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            IconButton(
                                onClick = { activeSheet?.let { saveCsvToDevice(it) } },
                                modifier = Modifier.testTag("top_save_csv_button")
                            ) {
                                Icon(Icons.Default.SaveAlt, contentDescription = "Save CSV to device")
                            }
                            IconButton(
                                onClick = { activeSheet?.let { shareCsv(it) } },
                                modifier = Modifier.testTag("top_export_button")
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "Share CSV")
                            }
                        }
                    }
                }
            },
            bottomBar = {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.navigationBarsPadding()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                TextButton(
                                    onClick = { viewModel.undo() },
                                    enabled = canUndo,
                                    shape = ControlShape,
                                    modifier = Modifier.testTag("main_undo_button")
                                ) {
                                    Icon(Icons.Default.Undo, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Undo", style = MaterialTheme.typography.labelMedium)
                                }
                                TextButton(
                                    onClick = { viewModel.redo() },
                                    enabled = canRedo,
                                    shape = ControlShape,
                                    modifier = Modifier.testTag("main_redo_button")
                                ) {
                                    Icon(Icons.Default.Redo, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Redo", style = MaterialTheme.typography.labelMedium)
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.openManualAdd() },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(60.dp)
                                        .testTag("type_manually_button"),
                                    shape = ControlShape,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.background,
                                        contentColor = MaterialTheme.colorScheme.onBackground
                                    )
                                ) {
                                    Icon(Icons.Default.Keyboard, contentDescription = null, modifier = Modifier.size(22.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Type it in", style = MaterialTheme.typography.labelLarge)
                                }

                                Button(
                                    onClick = { viewModel.openScanner() },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(60.dp)
                                        .testTag("scan_camera_button"),
                                    shape = ControlShape,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.tertiary,
                                        contentColor = MaterialTheme.colorScheme.onTertiary
                                    )
                                ) {
                                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text("Scan", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (currentItems.isEmpty()) {
                    EmptySheetState(sheetName = activeSheet?.name ?: "this sheet")
                } else {
                    LedgerTotal(
                        itemCount = currentItems.size,
                        unitCount = currentItems.sumOf { it.quantity }
                    )

                    LazyColumn(
                        state = itemListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("inventory_items_list"),
                        contentPadding = PaddingValues(bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(reorderableItems.items, key = { it.id }) { item ->
                            InventoryItemRow(
                                item = item,
                                dragState = reorderableItems.drag,
                                onItemNameClick = { viewModel.openItemDetail(item) },
                                onDeleteClick = { viewModel.deleteItem(item) },
                                onIncrement = { viewModel.incrementQuantity(item) },
                                onDecrement = { viewModel.decrementQuantity(item) },
                                modifier = reorderableRow(reorderableItems.drag, item.id)
                            )
                        }
                    }
                }
            }
        }
    }

    // Detail Dialog / Bottom Sheet
    selectedItemForDetail?.let { item ->
        ItemDetailDialog(
            item = item,
            onClose = { viewModel.closeItemDetail() },
            onSaveDetails = { newName, newCode, newQty ->
                viewModel.updateItemDetails(item, newName, newCode, newQty)
            },
            onIncrement = { viewModel.incrementQuantity(it) },
            onDecrement = { viewModel.decrementQuantity(it) },
            onSetQuantity = { itm, qty -> viewModel.setQuantity(itm, qty) }
        )
    }

    // Manual Add Dialog
    if (isManualAddOpen) {
        ManualAddDialog(
            onDismiss = { viewModel.closeManualAdd() },
            onAddItem = { name, code, qty ->
                viewModel.addManualItem(name, code, qty)
            }
        )
    }

    // Fullscreen Camera Scanner
    if (isScannerOpen) {
        ScannerView(
            bannerMessage = scanBannerMessage,
            onBarcodeScanned = { code ->
                viewModel.onBarcodeScanned(code)
            },
            onClose = { viewModel.closeScanner() }
        )
    }

    // New Sheet Dialog
    if (showNewSheetDialog) {
        AlertDialog(
            onDismissRequest = { showNewSheetDialog = false },
            title = { Text("New sheet", style = MaterialTheme.typography.headlineSmall) },
            text = {
                OutlinedTextField(
                    value = newSheetNameInput,
                    onValueChange = { newSheetNameInput = it },
                    label = { Text("Sheet name (e.g. Aisle 4)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("new_sheet_name_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = newSheetNameInput.trim().ifBlank { DEFAULT_SHEET_NAME }
                        viewModel.createNewSheet(name)
                        showNewSheetDialog = false
                    },
                    shape = ControlShape,
                    modifier = Modifier.testTag("confirm_create_sheet_button")
                ) {
                    Text("Create sheet")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewSheetDialog = false }, shape = ControlShape) {
                    Text("Cancel")
                }
            }
        )
    }

    // Inline Edit Sheet Title Dialog
    if (isEditingSheetTitleInline) {
        AlertDialog(
            onDismissRequest = { isEditingSheetTitleInline = false },
            title = { Text("Rename sheet", style = MaterialTheme.typography.headlineSmall) },
            text = {
                OutlinedTextField(
                    value = inlineSheetTitleInput,
                    onValueChange = { inlineSheetTitleInput = it },
                    label = { Text("Sheet name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("inline_sheet_title_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        activeSheet?.let { sheet ->
                            viewModel.renameSheet(sheet, inlineSheetTitleInput)
                        }
                        isEditingSheetTitleInline = false
                    },
                    shape = ControlShape,
                    modifier = Modifier.testTag("confirm_inline_title_button")
                ) {
                    Text("Save name")
                }
            },
            dismissButton = {
                TextButton(onClick = { isEditingSheetTitleInline = false }, shape = ControlShape) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * "14 items ........ 212 units" with a dotted leader, like the total line on a count sheet.
 * Both sides read the same way: the count in the display face, its unit word beside it.
 */
@Composable
private fun LedgerTotal(itemCount: Int, unitCount: Int) {
    val leaderColor = MaterialTheme.colorScheme.outline
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 10.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            text = "$itemCount",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = " ${if (itemCount == 1) "item" else "items"}",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 2.dp)
        )
        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(14.dp)
                .padding(horizontal = 10.dp)
        ) {
            drawLine(
                color = leaderColor,
                start = Offset(0f, size.height - 3.dp.toPx()),
                end = Offset(size.width, size.height - 3.dp.toPx()),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.1f, 7.dp.toPx()))
            )
        }
        Text(
            text = "$unitCount",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = " ${if (unitCount == 1) "unit" else "units"}",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 2.dp)
        )
    }
}

@Composable
private fun EmptySheetState(sheetName: String) {
    val dashColor = MaterialTheme.colorScheme.outline
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // An empty label slot waiting for its first code.
        Box(
            modifier = Modifier
                .size(112.dp)
                .drawBehind {
                    drawRoundRect(
                        color = dashColor,
                        cornerRadius = CornerRadius(6.dp.toPx()),
                        style = Stroke(
                            width = 2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
                        )
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.QrCodeScanner,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(46.dp)
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "No items in $sheetName yet",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Scan a barcode or type one in. Every scan of the same code adds one to its count.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun InventoryItemRow(
    item: InventoryItem,
    dragState: DragDropState,
    onItemNameClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    modifier: Modifier = Modifier
) {
    val canDecrement = item.quantity > 1

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("item_row_${item.id}"),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 76.dp)
                .padding(start = 4.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onDeleteClick,
                modifier = Modifier
                    .size(40.dp)
                    .testTag("delete_item_button_${item.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Delete ${item.name}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.small)
                    .clickable(onClickLabel = "Open details") { onItemNameClick() }
                    .padding(horizontal = 6.dp, vertical = 8.dp)
                    .testTag("item_name_clickable_${item.id}")
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.code != item.name) {
                    Text(
                        text = item.code,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // − [ 12 ] +   The + is yellow because it is the button pressed most.
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(ControlShape)
                    .border(
                        1.5.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = if (canDecrement) 1f else 0.4f),
                        ControlShape
                    )
                    .clickable(enabled = canDecrement, role = Role.Button, onClick = onDecrement)
                    .testTag("item_decrement_${item.id}"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = "Decrease ${item.name}",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (canDecrement) 1f else 0.35f),
                    modifier = Modifier.size(20.dp)
                )
            }

            Text(
                text = item.quantity.toString(),
                style = QuantityStyle,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier
                    .widthIn(min = 48.dp)
                    .padding(horizontal = 4.dp)
                    .testTag("item_qty_text_${item.id}")
            )

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(ControlShape)
                    .background(MaterialTheme.colorScheme.tertiary)
                    .clickable(role = Role.Button, onClick = onIncrement)
                    .testTag("item_increment_${item.id}"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Increase ${item.name}",
                    tint = MaterialTheme.colorScheme.onTertiary,
                    modifier = Modifier.size(22.dp)
                )
            }

            // Grab here to reorder. Only this area starts a drag, so scrolling and taps elsewhere are unaffected.
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(64.dp)
                    .dragHandle(dragState, item.id)
                    .testTag("item_drag_handle_${item.id}"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.DragHandle,
                    contentDescription = "Drag to reorder ${item.name}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
