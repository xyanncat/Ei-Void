package com.eivoid.designsystem.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class ColorTest {
    @Test
    fun `primary token is chalk`() =
        assertEquals(Color(0xFFF2F2EF), VoidColors.primary)

    @Test
    fun `secondary token is graphite`() =
        assertEquals(Color(0xFFB8B8B4), VoidColors.secondary)

    @Test
    fun `accent token is stone`() =
        assertEquals(Color(0xFF6E6E6A), VoidColors.accent)
}
