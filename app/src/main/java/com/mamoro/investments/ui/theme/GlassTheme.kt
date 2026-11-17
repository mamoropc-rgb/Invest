package com.mamoro.investments.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val Orange = Color(0xFFFF9A35)
val OrangeLight = Color(0xFFFFBC74)
val Green = Color(0xFF78E59A)
val Red = Color(0xFFFF777E)
val Ink = Color(0xFF0B0D11)
val Frost = Color(0xFF292B31)
val SoftText = Color(0xFFAEB0BA)

private val colors = darkColorScheme(
    primary = Orange,
    onPrimary = Color(0xFF1B1005),
    secondary = OrangeLight,
    background = Ink,
    surface = Color(0xFF17191F),
    onSurface = Color(0xFFF6F6F8),
    onBackground = Color.White,
    error = Red
)

@Composable
fun InvestmentsTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, content = content)
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    elevated: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(22.dp)
    val fill = if (elevated) listOf(Color(0x665B4A3C), Color(0xCC22252B), Color(0xE01B1D23))
               else listOf(Color(0xB93B3E46), Color(0xCF22252B), Color(0xDD1C1E24))
    Column(
        modifier = modifier
            .background(Brush.linearGradient(fill), shape)
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(listOf(Color(0x8AFFFFFF), Color(0x27FFFFFF), Orange.copy(alpha = if (elevated) .40f else .12f))),
                shape = shape
            )
            .padding(15.dp),
        content = content
    )
}

val OrangeBackground = Brush.radialGradient(
    colors = listOf(Color(0xFF432619), Color(0xFF1A171A), Ink),
    radius = 1180f
)
