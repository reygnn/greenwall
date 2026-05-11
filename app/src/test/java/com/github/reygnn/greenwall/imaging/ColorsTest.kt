package com.github.reygnn.greenwall.imaging

import org.junit.Assert.assertEquals
import org.junit.Test

class ColorsTest {

    @Test
    fun `complement of pure green is pure magenta`() {
        assertEquals(0xFFFF00FF.toInt(), complementaryRgb(0xFF00FF00.toInt()))
    }

    @Test
    fun `complement of pure blue is pure yellow`() {
        assertEquals(0xFFFFFF00.toInt(), complementaryRgb(0xFF0000FF.toInt()))
    }

    @Test
    fun `complement of pure pink is dark teal`() {
        // pink 0xFFFF00FF → R=255, G=0, B=255 → R'=0, G'=255, B'=0 → 0xFF00FF00
        assertEquals(0xFF00FF00.toInt(), complementaryRgb(0xFFFF00FF.toInt()))
    }

    @Test
    fun `output alpha is always FF regardless of input alpha`() {
        // Transparent green in, opaque magenta out.
        assertEquals(0xFFFF00FF.toInt(), complementaryRgb(0x0000FF00))
    }

    @Test
    fun `complement of mid-gray is mid-gray`() {
        // Channels (128, 128, 128) → (127, 127, 127).
        assertEquals(0xFF7F7F7F.toInt(), complementaryRgb(0xFF808080.toInt()))
    }
}
