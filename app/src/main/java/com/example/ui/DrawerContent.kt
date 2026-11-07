package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.InventorySheet
import com.example.ui.theme.ControlShape
import com.example.util.formatTimestamp

@Composable
fun DrawerContent(
    sheets: List<InventorySheet>,
    activeSheetId: Long?,
    canUndo: Boolean,
    canRedo: Boolean,
    isDarkMode: Boolean,
    onSelectSheet: (InventorySheet) -> Unit,
    onRemoveSheet: (InventorySheet) -> Unit,
    onDuplicateSheet: (InventorySheet) -> Unit,
    onRenameSheet: (InventorySheet, String) -> Unit,
    onSaveSheetCsv: (InventorySheet) -> Unit,
    onShareSheetCsv: (InventorySheet) -> Unit,
    onReorderSheets: (List<Long>) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onNewSheet: () -> Unit,
    onImportCsv: () -> Unit,
    onToggleDarkMode: () -> Unit
) {
    var sheetToRename by remember { mutableStateOf<InventorySheet?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var sheetToDelete by remember { mutableStateOf<InventorySheet?>(null) }

    val sheetListState = rememberLazyListState()
    val reorderableSheets = rememberReorderableList(
        source = sheets,
        idOf = { it.id },
        listState = sheetListState,
        onCommit = onReorderSheets
    )

    Surface(
        modifier = Modifier
            .fillMaxHeight()
            .width(310.dp)
            .testTag("inventory_drawer_content"),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Text(
                text = "Sheets",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 14.dp)
            )

            LazyColumn(
                state = sheetListState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(reorderableSheets.items, key = { it.id }) { sheet ->
                    val isActive = sheet.id == activeSheetId
                    var menuExpanded by remember { mutableStateOf(false) }

                    Surface(
                        modifier = reorderableRow(reorderableSheets.drag, sheet.id).fillMaxWidth(),
                        color = if (isActive) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(84.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Yellow edge marks the sheet you are in.
                            Box(
                                modifier = Modifier
                                    .width(5.dp)
                                    .fillMaxHeight()
                                    .background(
                                        if (isActive) MaterialTheme.colorScheme.tertiary
                                        else androidx.compose.ui.graphics.Color.Transparent
                                    )
                            )

                            Box(
                                modifier = Modifier
                                    .width(44.dp)
                                    .fillMaxHeight()
                                    .dragHandle(reorderableSheets.drag, sheet.id)
                                    .testTag("sheet_drag_handle_${sheet.id}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DragHandle,
                                    contentDescription = "Drag to reorder ${sheet.name}",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable(role = Role.Button) { onSelectSheet(sheet) },
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Column {
                                    Text(
                                        text = sheet.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.testTag("drawer_sheet_name_${sheet.id}")
                                    )
                                    Text(
                                        text = "Created: ${formatTimestamp(sheet.createdAt)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Last modified: ${formatTimestamp(sheet.updatedAt)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Box {
                                IconButton(
                                    onClick = { menuExpanded = true },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("sheet_menu_button_${sheet.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "Options for ${sheet.name}",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                DropdownMenu(
                                    expanded = menuExpanded,
                                    onDismissRequest = { menuExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Rename") },
                                        onClick = {
                                            menuExpanded = false
                                            renameInput = sheet.name
                                            sheetToRename = sheet
                                        },
                                        modifier = Modifier.testTag("menu_rename_${sheet.id}")
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Duplicate") },
                                        onClick = {
                                            menuExpanded = false
                                            onDuplicateSheet(sheet)
                                        },
                                        modifier = Modifier.testTag("menu_duplicate_${sheet.id}")
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Save CSV to device") },
                                        onClick = {
                                            menuExpanded = false
                                            onSaveSheetCsv(sheet)
                                        },
                                        modifier = Modifier.testTag("menu_save_csv_${sheet.id}")
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Share CSV") },
                                        onClick = {
                                            menuExpanded = false
                                            onShareSheetCsv(sheet)
                                        },
                                        modifier = Modifier.testTag("menu_export_${sheet.id}")
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Remove", color = MaterialTheme.colorScheme.error) },
                                        onClick = {
                                            menuExpanded = false
                                            sheetToDelete = sheet
                                        },
                                        modifier = Modifier.testTag("menu_remove_${sheet.id}")
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onNewSheet,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("drawer_new_sheet_button"),
                        shape = ControlShape
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("New sheet", style = MaterialTheme.typography.labelMedium)
                    }

                    OutlinedButton(
                        onClick = onImportCsv,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("drawer_import_csv_button"),
                        shape = ControlShape
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Import CSV", style = MaterialTheme.typography.labelMedium)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = onUndo,
                        enabled = canUndo,
                        shape = ControlShape,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("drawer_undo_button")
                    ) {
                        Icon(Icons.Default.Undo, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Undo", style = MaterialTheme.typography.labelMedium)
                    }
                    TextButton(
                        onClick = onRedo,
                        enabled = canRedo,
                        shape = ControlShape,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("drawer_redo_button")
                    ) {
                        Icon(Icons.Default.Redo, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Redo", style = MaterialTheme.typography.labelMedium)
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 6.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Switch, onClick = onToggleDarkMode)
                        .padding(horizontal = 4.dp, vertical = 6.dp)
                        .testTag("drawer_dark_mode_button"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.DarkMode,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Dark mode",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(checked = isDarkMode, onCheckedChange = { onToggleDarkMode() })
                }
            }
        }
    }

    // Rename Sheet Dialog
    sheetToRename?.let { sheet ->
        AlertDialog(
            onDismissRequest = { sheetToRename = null },
            title = { Text("Rename sheet", style = MaterialTheme.typography.headlineSmall) },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    label = { Text("Sheet name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rename_sheet_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val sanitized = renameInput.trim().ifBlank { sheet.name }
                        onRenameSheet(sheet, sanitized)
                        sheetToRename = null
                    },
                    shape = ControlShape,
                    modifier = Modifier.testTag("confirm_rename_sheet_button")
                ) {
                    Text("Save name")
                }
            },
            dismissButton = {
                TextButton(onClick = { sheetToRename = null }, shape = ControlShape) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Confirmation Dialog
    sheetToDelete?.let { sheet ->
        AlertDialog(
            onDismissRequest = { sheetToDelete = null },
            title = { Text("Remove ${sheet.name}?", style = MaterialTheme.typography.headlineSmall) },
            text = { Text("This deletes the sheet and every item in it. You can't undo this.") },
            confirmButton = {
                Button(
                    onClick = {
                        onRemoveSheet(sheet)
                        sheetToDelete = null
                    },
                    shape = ControlShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Remove sheet")
                }
            },
            dismissButton = {
                TextButton(onClick = { sheetToDelete = null }, shape = ControlShape) {
                    Text("Keep it")
                }
            }
        )
    }
}
