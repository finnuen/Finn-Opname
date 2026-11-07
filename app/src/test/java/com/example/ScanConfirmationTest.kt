package com.example

import com.example.ui.StableBarcodeReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanConfirmationTest {

    private val accepted = mutableListOf<String>()
    private var clock = 0L
    private val reader = StableBarcodeReader(
        onConfirmed = { accepted += it },
        now = { clock }
    )

    /** One frame every [gapMs] until the code has been shown [times] frames. */
    private fun frames(code: String?, times: Int, gapMs: Long = 120L) {
        repeat(times) {
            clock += gapMs
            reader.onFrame(listOfNotNull(code))
        }
    }

    @Test
    fun `one or two frames are not enough`() {
        frames("5901234123457", times = 2)
        assertTrue(accepted.isEmpty())
    }

    @Test
    fun `three agreeing frames accept the code once`() {
        frames("5901234123457", times = 3)
        assertEquals(listOf("5901234123457"), accepted)
    }

    @Test
    fun `a shaky read that keeps changing is never accepted`() {
        frames("5901234123457", times = 1)
        frames("5901234123458", times = 1)
        frames("5901234123450", times = 1)
        frames("5901234123457", times = 1)
        assertTrue(accepted.isEmpty())
    }

    @Test
    fun `an empty frame resets the agreement`() {
        frames("FINN-0042", times = 2)
        frames(null, times = 1)
        frames("FINN-0042", times = 2)
        assertTrue(accepted.isEmpty())
    }

    @Test
    fun `agreement does not survive a long pause`() {
        frames("FINN-0042", times = 2)
        frames("FINN-0042", times = 1, gapMs = 2_000L)
        assertTrue(accepted.isEmpty())
    }

    @Test
    fun `the same code has to earn a fresh agreement`() {
        frames("FINN-0042", times = 3)
        frames("FINN-0042", times = 2)
        assertEquals(listOf("FINN-0042"), accepted)
    }
}
