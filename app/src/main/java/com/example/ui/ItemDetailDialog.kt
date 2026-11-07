package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.InventoryItem
import androidx.compose.ui.semantics.Role
import com.example.ui.theme.ControlShape
import com.example.ui.theme.QuantityStyle
import com.example.util.BarcodeUtil
import com.example.util.formatTimestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** How long typing must pause before the barcode and QR code are rebuilt. */
private const val CODE_RENDER_DEBOUNCE_MS = 1_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemDetailDialog(
    item: InventoryItem,
    onClose: () -> Unit,
    onSaveDetails: (newName: String, newCode: String, newQuantity: Int) -> Unit,
    onIncrement: (InventoryItem) -> Unit,
    onDecrement: (InventoryItem) -> Unit,
    onSetQuantity: (InventoryItem, Int) -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var currentName by remember(item.id) { mutableStateOf(item.name) }
    var currentCode by remember(item.id) { mutableStateOf(item.code) }
    var currentQty by remember(item.id) { mutableIntStateOf(item.quantity) }

    var isEditingQtyDirectly by remember { mutableStateOf(false) }
    var tempQtyInput by remember { mutableStateOf(item.quantity.toString()) }

    var barcodeBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Re-render barcodes when code changes. Both images carry the code only, never the name.
    // Opening the sheet renders at once; edits wait until typing stops, because rebuilding two
    // bitmaps on every keystroke is what made the Code field feel sluggish. The work itself runs
    // off the main thread.
    var hasRendered by remember(item.id) { mutableStateOf(false) }
    LaunchedEffect(currentCode) {
        if (hasRendered) delay(CODE_RENDER_DEBOUNCE_MS)
        val code = currentCode
        val rendered = withContext(Dispatchers.Default) {
            BarcodeUtil.generateBarcodeBitmap(code, width = 1200, height = 270) to
                BarcodeUtil.generateQrCodeBitmap(code, size = 600)
        }
        barcodeBitmap = rendered.first
        qrBitmap = rendered.second
        hasRendered = true
    }

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
        dragHandle = null,
        modifier = Modifier.testTag("item_detail_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                // Name: directly editable, with the same copy button as the code below.
                Text(
                    text = "Name",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = currentName,
                        onValueChange = { newName ->
                            currentName = newName
                            onSaveDetails(newName, currentCode, currentQty)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("detail_name_input"),
                        singleLine = true,
                        shape = ControlShape
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    OutlinedIconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Item name", currentName)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Copied to clipboard!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .size(52.dp)
                            .testTag("copy_name_button"),
                        shape = ControlShape
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy name to clipboard",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Text: Raw Code Field with Copy button on right side
                Text(
                    text = "Code",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = currentCode,
                        onValueChange = { newCode ->
                            currentCode = newCode
                            onSaveDetails(currentName, newCode, currentQty)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("detail_code_input"),
                        singleLine = true,
                        shape = ControlShape
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    OutlinedIconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Barcode Text", currentCode)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Copied to clipboard!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .size(52.dp)
                            .testTag("copy_text_button"),
                        shape = ControlShape
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy text to clipboard",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Quantity Stepper: < 4 >
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Quantity",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(ControlShape)
                                .border(1.5.dp, MaterialTheme.colorScheme.outline, ControlShape)
                                .clickable(role = Role.Button) {
                                    if (currentQty > 0) {
                                        val newQ = currentQty - 1
                                        currentQty = newQ
                                        onSetQuantity(item, newQ)
                                    }
                                }
                                .testTag("detail_decrement_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Decrease quantity")
                        }

                        Text(
                            text = currentQty.toString(),
                            style = QuantityStyle.copy(fontSize = 44.sp, lineHeight = 46.sp),
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .width(84.dp)
                                .clickable(onClickLabel = "Type a quantity") {
                                    tempQtyInput = currentQty.toString()
                                    isEditingQtyDirectly = true
                                }
                                .testTag("detail_quantity_display")
                        )

                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(ControlShape)
                                .background(MaterialTheme.colorScheme.tertiary)
                                .clickable(role = Role.Button) {
                                    val newQ = currentQty + 1
                                    currentQty = newQ
                                    onSetQuantity(item, newQ)
                                }
                                .testTag("detail_increment_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "Increase quantity",
                                tint = MaterialTheme.colorScheme.onTertiary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // The item's own timestamps, not the sheet's: when it was first added, and when it
                // last changed. Small and quiet so they don't compete with the code images.
                Text(
                    text = "Created: ${formatTimestamp(item.createdAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Last modified: ${formatTimestamp(item.updatedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Barcode Section
                SaveableCodeSection(
                    title = "Barcode",
                    bitmap = barcodeBitmap,
                    onShare = {
                        barcodeBitmap?.let { bmp ->
                            BarcodeUtil.shareBarcodeImage(
                                context, bmp, "Barcode for $currentName", currentCode
                            )
                        }
                    },
                    onCopyImage = {
                        barcodeBitmap?.let { bmp ->
                            BarcodeUtil.copyBarcodeImageToClipboard(
                                context, bmp, "Barcode for $currentName", currentCode
                            )
                        }
                    },
                    emptyMessage = "Code is incompatible with barcode",
                    isQr = false
                )

                Spacer(modifier = Modifier.height(14.dp))

                // QR Code Section
                SaveableCodeSection(
                    title = "QR code",
                    bitmap = qrBitmap,
                    onShare = {
                        qrBitmap?.let { bmp ->
                            BarcodeUtil.shareBarcodeImage(
                                context, bmp, "QR code for $currentName", currentCode
                            )
                        }
                    },
                    onCopyImage = {
                        qrBitmap?.let { bmp ->
                            BarcodeUtil.copyBarcodeImageToClipboard(
                                context, bmp, "QR code for $currentName", currentCode
                            )
                        }
                    },
                    emptyMessage = "Code is incompatible with QR code",
                    isQr = true
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Close Button
                Button(
                    onClick = onClose,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("detail_close_button"),
                    shape = ControlShape
                ) {
                    Text("Done", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }

    // Direct Quantity Edit Dialog
    if (isEditingQtyDirectly) {
        AlertDialog(
            onDismissRequest = { isEditingQtyDirectly = false },
            title = { Text("Set quantity", style = MaterialTheme.typography.headlineSmall) },
            text = {
                OutlinedTextField(
                    value = tempQtyInput,
                    onValueChange = { tempQtyInput = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Quantity") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("direct_quantity_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsed = tempQtyInput.toIntOrNull()?.coerceAtLeast(0) ?: currentQty
                        currentQty = parsed
                        onSetQuantity(item, parsed)
                        isEditingQtyDirectly = false
                    },
                    shape = ControlShape
                ) {
                    Text("Set quantity")
                }
            },
            dismissButton = {
                TextButton(onClick = { isEditingQtyDirectly = false }, shape = ControlShape) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SaveableCodeSection(
    title: String,
    bitmap: Bitmap?,
    onShare: () -> Unit,
    onCopyImage: () -> Unit,
    emptyMessage: String,
    isQr: Boolean
) {
    var showMenu by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("code_menu_${if (isQr) "qr" else "barcode"}_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Options for $title",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Share image") },
                        onClick = {
                            showMenu = false
                            onShare()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Copy image to clipboard") },
                        onClick = {
                            showMenu = false
                            onCopyImage()
                        }
                    )
                    // Not an action: saving to Photos is broken on this device, so this is the
                    // instruction that replaces it. Disabled so it can't be tapped like a button,
                    // but kept at the same text size as the actions above.
                    DropdownMenuItem(
                        text = { Text("Screenshot to save") },
                        onClick = {},
                        enabled = false
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 0.5.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                    shape = ControlShape
                ),
            color = Color.White,
            shape = ControlShape
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = title,
                        modifier = if (isQr) {
                            Modifier.size(132.dp)
                        } else {
                            Modifier
                                .fillMaxWidth()
                                .height(68.dp)
                        }
                    )
                } else {
                    Text(
                        text = emptyMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}
