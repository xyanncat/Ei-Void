package com.eivoid.designsystem.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.eivoid.designsystem.R

/**
 * The Void mark: the chalk cat and its halo, framed as a stamped coin.
 * The artwork carries its own night-sky background, so it sits on both the
 * dark canvas and the paper surface without a second asset.
 */
@Composable
fun VoidLogo(
    modifier: Modifier = Modifier,
    size: Dp = 132.dp,
    contentDescription: String? = "Void logo",
) {
    Image(
        painter = painterResource(R.drawable.gatete_chill),
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                shape = CircleShape,
            ),
    )
}
