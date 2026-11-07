package com.example

import android.graphics.Bitmap
import com.example.util.BarcodeUtil
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BarcodeUtilTest {

    private fun decode(bitmap: Bitmap): String {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val binary = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(width, height, pixels)))
        return MultiFormatReader().decode(binary).text
    }

    @Test
    fun `barcode carries the code and nothing else`() {
        val code = "5901234123457"
        val bitmap = BarcodeUtil.generateBarcodeBitmap(code, width = 1200, height = 360)
        assertNotNull(bitmap)
        assertEquals(code, decode(bitmap!!))
    }

    @Test
    fun `qr carries the code and nothing else`() {
        val code = "FINN-0042"
        val bitmap = BarcodeUtil.generateQrCodeBitmap(code, size = 600)
        assertNotNull(bitmap)
        assertEquals(code, decode(bitmap!!))
    }

    @Test
    fun `code that code128 cannot hold has no barcode`() {
        assertNull(BarcodeUtil.generateBarcodeBitmap("\uD83D\uDE42"))
    }

    @Test
    fun `qr still holds text outside ascii`() {
        val text = "caf\u00e9"
        val bitmap = BarcodeUtil.generateQrCodeBitmap(text, size = 600)
        assertNotNull(bitmap)
        assertEquals(text, decode(bitmap!!))
    }
}
