package com.eivoid.feature.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.eivoid.core.ai.Tier
import com.eivoid.designsystem.theme.VoidColors

/**
 * Tier markers use the ink/paper pair so every dot survives both schemes.
 * The dark scheme goes bright-to-dim; the light scheme goes dark-to-light.
 */
@Composable
fun Tier.markerColor(): Color {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (dark) {
        when (this) {
            Tier.RED_DWARF -> VoidColors.tierRed
            Tier.WHITE_DWARF -> VoidColors.tierWhite
            Tier.NEUTRON_STAR -> VoidColors.tierAmber
        }
    } else {
        when (this) {
            Tier.RED_DWARF -> VoidColors.ink
            Tier.WHITE_DWARF -> VoidColors.stone
            Tier.NEUTRON_STAR -> VoidColors.outlineLight
        }
    }
}
