package com.example.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import java.io.File
import java.io.FileOutputStream

object BarcodeUtil {

    /**
     * Encodes [content] exactly as given. Returns null when the format cannot represent it
     * (for example non-ASCII text in CODE_128), so the caller can tell the user instead of
     * showing a barcode of some altered string.
     */
    fun generateBarcodeBitmap(
        content: String,
        format: BarcodeFormat = BarcodeFormat.CODE_128,
        width: Int = 600,
        height: Int = 200
    ): Bitmap? {
        if (content.isBlank()) return null
        return try {
            val hints = mapOf(
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.MARGIN to if (format == BarcodeFormat.QR_CODE) 2 else 10
            )
            val writer = MultiFormatWriter()
            val bitMatrix: BitMatrix = writer.encode(content, format, width, height, hints)
            val matrixWidth = bitMatrix.width
            val matrixHeight = bitMatrix.height
            val bitmap = Bitmap.createBitmap(matrixWidth, matrixHeight, Bitmap.Config.ARGB_8888)
            for (x in 0 until matrixWidth) {
                for (y in 0 until matrixHeight) {
                    bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
                }
            }
            bitmap
        } catch (_: Exception) {
            null
        }
    }

    fun generateQrCodeBitmap(
        content: String,
        size: Int = 500
    ): Bitmap? {
        return generateBarcodeBitmap(content, BarcodeFormat.QR_CODE, size, size)
    }

    fun shareBarcodeImage(context: Context, bitmap: Bitmap, title: String, filename: String) {
        try {
            val uri = cacheImageUri(context, bitmap, filename)

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_TEXT, "$title\nData: $filename")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(Intent.createChooser(shareIntent, "Share $title"))
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "Failed to share image: ${e.localizedMessage ?: e.javaClass.simpleName}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * Puts the PNG on the clipboard so it can be pasted into a chat, a note or anywhere else that
     * accepts a pasted picture. The clip points at a FileProvider URI, which Android hands to the
     * pasting app with read access.
     */
    fun copyBarcodeImageToClipboard(
        context: Context,
        bitmap: Bitmap,
        label: String,
        filename: String
    ) {
        try {
            val uri = cacheImageUri(context, bitmap, filename)
            val clip = ClipData.newUri(context.contentResolver, label, uri)
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(clip)
            Toast.makeText(
                context,
                "Image copied. Paste it into a chat or note.",
                Toast.LENGTH_SHORT
            ).show()
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "Couldn't copy image: ${e.localizedMessage ?: e.javaClass.simpleName}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /** Writes the PNG into the shared cache folder and returns the URI other apps can read. */
    private fun cacheImageUri(context: Context, bitmap: Bitmap, filename: String): Uri {
        if (bitmap.isRecycled) throw IllegalStateException("The image is no longer in memory")
        val imagesFolder = File(context.cacheDir, "images")
        if (!imagesFolder.exists()) {
            imagesFolder.mkdirs()
        }
        val sanitizedName = filename.replace("[^a-zA-Z0-9_-]".toRegex(), "_").ifBlank { "code" }
        val file = File(imagesFolder, "$sanitizedName.png")
        FileOutputStream(file).use { out ->
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                throw IllegalStateException("PNG encoding failed")
            }
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
}
