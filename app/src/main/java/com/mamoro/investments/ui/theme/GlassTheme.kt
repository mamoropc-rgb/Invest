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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val Orange = Color(0xFFFF9A35)
val OrangeLight = Color(0xFFFFBC74)
val Green = Color(0xFF78E59A)
val Red = Color(0xFFFF777E)
val Ink = Color(0xFF0B0D11)
val Frost = Color(0xFF292B31)
val SoftText = Color(0xFFC5C7D0)

// Explicitly set all common dark-theme text colors. A plain Box does not
// automatically supply MaterialTheme.onSurface/onBackground to Text.
private val colors = darkColorScheme(
    primary = Orange,
    onPrimary = Color(0xFF201001),
    primaryContainer = Color(0xFF503018),
    onPrimaryContainer = Color(0xFFFFE9D0),
    secondary = OrangeLight,
    onSecondary = Color(0xFF201001),
    secondaryContainer = Color(0xFF49372C),
    onSecondaryContainer = Color(0xFFFFE8D5),
    background = Ink,
    onBackground = Color(0xFFF7F6F8),
    surface = Color(0xFF1C1E25),
    onSurface = Color(0xFFF7F6F8),
    surfaceVariant = Color(0xFF303139),
    onSurfaceVariant = Color(0xFFD3D2DB),
    outline = Color(0xFF83818B),
    error = Red,
    onError = Color(0xFF1D0708)
)

@Composable
fun InvestmentsTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors) {
        // Fixes dark/black text in every screen, including LazyColumn and
        // sheet content: Surface provides a high-contrast LocalContentColor.
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = colors.background,
            contentColor = colors.onBackground,
            content = content
        )
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    elevated: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(22.dp)
    val fill = if (elevated) {
        listOf(Color(0xE0403634), Color(0xE022252D), Color(0xF0181B22))
    } else {
        listOf(Color(0xDC3B3D46), Color(0xE024262E), Color(0xF01B1D24))
    }
    val edge = if (elevated) {
        listOf(Color(0xA8FFF1E5), Color(0x4DFFFFFF), Color(0x80FF9A35))
    } else {
        listOf(Color(0x88FFFFFF), Color(0x30FFFFFF), Color(0x55FF9A35))
    }
    Column(
        modifier = modifier
            // Layered translucent gradients provide the frosted-glass look
            // across Android 8.0+ without expensive runtime blur effects.
            .shadow(
                elevation = if (elevated) 15.dp else 6.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = .42f),
                spotColor = if (elevated) Orange.copy(alpha = .22f) else Color.Black
            )
            .background(Brush.linearGradient(fill), shape)
            .border(1.dp, Brush.linearGradient(edge), shape)
            .padding(15.dp),
        content = content
    )
}

val OrangeBackground = Brush.radialGradient(
    colors = listOf(Color(0xFF4B2819), Color(0xFF1B171D), Ink),
    radius = 1320f
)
