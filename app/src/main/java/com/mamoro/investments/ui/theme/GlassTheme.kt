package com.mamoro.investments.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Investments 1.0.2: accessible graphite + amber design tokens. */
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
    val cardCorner = 22.dp
    val controlCorner = 15.dp
    val cardPadding = 15.dp
    val sectionGap = 10.dp
    val navHeight = 78.dp
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
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(GlassSizes.cardCorner)
    val pane = if (elevated) listOf(
        Color(0xD94A3833), Color(0xE333333D), Color(0xF21A1D26)
    ) else listOf(
        Color(0xDA42414A), Color(0xE52A2B34), Color(0xF21B1D26)
    )
    val rim = if (elevated) listOf(
        Color(0xB0FFF2E6), Color(0x38FFF2EB), Color(0x99F79C4C)
    ) else listOf(
        Color(0x93FFFFFF), Color(0x25FFFFFF), Color(0x65DD935C)
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
            .border(1.dp, Brush.linearGradient(rim), shape)
            .padding(GlassSizes.cardPadding),
        content = content
    )
}

/** Background glows are ornamental; no simulated market-price time series. */
val OrangeBackground = Brush.verticalGradient(
        0.00f to Color(0xFF0B0D12),
        0.24f to Color(0xFF15131B),
        0.46f to Color(0xFF261A1B),
        0.72f to Color(0xFF121118),
        1.00f to Color(0xFF0B0C10)
)
