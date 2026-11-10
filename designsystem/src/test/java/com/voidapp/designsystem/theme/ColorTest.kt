package com.voidapp.designsystem.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class ColorTest {
    @Test
    fun `primary token is deep indigo`() =
        assertEquals(Color(0xFF4F46E5), VoidColors.primary)

    @Test
    fun `secondary token is warm amber`() =
        assertEquals(Color(0xFFF59E0B), VoidColors.secondary)

    @Test
    fun `accent token is electric coral`() =
        assertEquals(Color(0xFFFF5A5F), VoidColors.accent)
}
