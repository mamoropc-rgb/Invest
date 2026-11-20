package com.mamoro.investments.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Investments 1.0.3: reference-2 premium glass tokens, Android 8.0+ compatible. */
val Orange = Color(0xFFFF993B)
val OrangeLight = Color(0xFFFFC082)
val Green = Color(0xFF81E59B)
val Red = Color(0xFFFF8089)
val Ink = Color(0xFF0C0D12)
val Frost = Color(0xFF2B2B33)
val SoftText = Color(0xFFC4C3CD)
val StrongText = Color(0xFFF9F7F8)

object GlassSizes {
    val phoneSidePadding = 16.dp
    val tabletSidePadding = 24.dp
    val maxContentWidth = 704.dp
    val maxNavigationWidth = 630.dp
    val cardCorner = 22.dp
    val controlCorner = 15.dp
    val cardPadding = 16.dp
    val compactCardPadding = 12.dp
    val sectionGap = 10.dp
    val navHeight = 76.dp
}

private val darkColors = darkColorScheme(
    primary = Orange,
    onPrimary = Color(0xFF251303),
    primaryContainer = Color(0xFF593419),
    onPrimaryContainer = Color(0xFFFFEBDB),
    secondary = OrangeLight,
    onSecondary = Color(0xFF251303),
    secondaryContainer = Color(0xFF4C3729),
    onSecondaryContainer = Color(0xFFFFE9DC),
    background = Ink,
    onBackground = StrongText,
    surface = Color(0xFF1D1F27),
    onSurface = StrongText,
    surfaceVariant = Color(0xFF34343C),
    onSurfaceVariant = SoftText,
    outline = Color(0xFF92909D),
    error = Red,
    onError = Color(0xFF220607)
)

private val type = Typography(
    titleLarge = androidx.compose.ui.text.TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    titleMedium = androidx.compose.ui.text.TextStyle(fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold),
    bodyMedium = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
)

@Composable
fun InvestmentsTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColors, typography = type) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = darkColors.background,
            contentColor = darkColors.onBackground,
            content = content
        )
    }
}

/**
 * Lightweight translucent glass: works back to Android 8 without real-time
 * RenderEffect blur. Gradients and bright upper edge mimic frosted layers.
 * A shared implementation prevents contrast/radius inconsistencies.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    elevated: Boolean = false,
    compact: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(GlassSizes.cardCorner)
    val pane = if (elevated) listOf(
        Color(0xE4543D32), Color(0xE632323A), Color(0xF01A1C26)
    ) else listOf(
        Color(0xDF49474E), Color(0xE52B2B34), Color(0xF01B1D26)
    )
    val rim = if (elevated) listOf(
        Color(0xC5FFF1E1), Color(0x3AF2E9E8), Color(0x9FFF9A40)
    ) else listOf(
        Color(0xB0FFFFFF), Color(0x2CFFFFFF), Color(0x77F4A66F)
    )
    Column(
        modifier = modifier
            .shadow(
                elevation = if (elevated) 13.dp else 5.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = .38f),
                spotColor = if (elevated) Orange.copy(alpha = .23f) else Color.Black
            )
            .clip(shape)
            .background(Brush.linearGradient(pane), shape)
            // Soft upper-left highlight, behind content; no real-time GPU blur.
            .background(Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = if (elevated) .105f else .075f), Color.Transparent),
                center = Offset.Zero, radius = 570f
            ), shape)
            // Thin top reflection; a second wide metallic rim completes the glass edge.
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = .032f), Color.Transparent, Color.Transparent)), shape)
            .border(1.dp, Brush.linearGradient(rim), shape)
            .padding(if (compact) GlassSizes.compactCardPadding else GlassSizes.cardPadding),
        content = content
    )
}

/** Background glows are ornamental; no simulated market-price time series. */
val OrangeBackground = Brush.verticalGradient(
    0.00f to Color(0xFF0B0D12),
    0.25f to Color(0xFF15131A),
    0.48f to Color(0xFF2D1C1C),
    0.72f to Color(0xFF17131A),
    1.00f to Color(0xFF0A0C11)
)

/** Reference-2 luminous amber CTA. Reused by portfolio and empty states. */
@Composable
fun GlassActionButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(GlassSizes.controlCorner)
    Row(
        modifier = modifier
            .heightIn(min = 52.dp)
            .shadow(10.dp, shape, spotColor = Orange.copy(alpha = .38f))
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFFFFC17B), Orange, Color(0xFFFA8526))), shape)
            .border(1.dp, Brush.verticalGradient(listOf(Color(0xFFFFE5BD), Color(0x88FFB76B))), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 17.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Rounded.Add, contentDescription = null, tint = Color(0xFF311A09), modifier = Modifier.size(21.dp))
        androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
        Text(label, color = Color(0xFF2E190C), fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}
