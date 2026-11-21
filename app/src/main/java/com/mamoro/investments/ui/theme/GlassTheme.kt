package com.mamoro.investments.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Variant 3: restrained dark surfaces and a strict numerical hierarchy.
val Orange = Color(0xFFFFAD32)
val OrangeLight = Color(0xFFFFC265)
val Green = Color(0xFF58ED62)
val Red = Color(0xFFFF6267)
val Ink = Color(0xFF060B0E)
val Frost = Color(0xFF11181D)
val SoftText = Color(0xFFAEB4B8)
val StrongText = Color(0xFFF4F6F7)
val DataRule = Color(0xFF272E33)

object GlassSizes {
    val phoneSidePadding = 14.dp
    val tabletSidePadding = 18.dp
    val maxContentWidth = 560.dp
    val maxNavigationWidth = 560.dp
    val cardCorner = 14.dp
    val controlCorner = 11.dp
    val cardPadding = 16.dp
    val compactCardPadding = 12.dp
    val sectionGap = 12.dp
    val navHeight = 64.dp
}

private val darkColors = darkColorScheme(
    primary = Orange, onPrimary = Color(0xFF171208),
    primaryContainer = Color(0xFF463217), onPrimaryContainer = StrongText,
    secondary = OrangeLight, onSecondary = Ink,
    secondaryContainer = Color(0xFF242B30), onSecondaryContainer = StrongText,
    background = Ink, onBackground = StrongText,
    surface = Frost, onSurface = StrongText,
    surfaceVariant = Color(0xFF172027), onSurfaceVariant = SoftText,
    outline = Color(0xFF55616A), error = Red, onError = Ink
)
private val type = Typography(
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 21.sp, letterSpacing = 0.sp, fontFeatureSettings = "tnum"),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.sp, fontFeatureSettings = "tnum"),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.sp, fontFeatureSettings = "tnum"),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.sp),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.sp),
    titleLarge = TextStyle(fontSize = 23.sp, lineHeight = 29.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.sp),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp)
)

@Composable
fun InvestmentsTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColors, typography = type) {
        Surface(Modifier.fillMaxSize(), color = Ink, contentColor = StrongText, content = content)
    }
}

/** Source-compatible surface primitive for all existing screens and forms. */
fun Modifier.glassSurface(corner: Float = 14f, warm: Boolean = false): Modifier = drawWithCache {
    val radius = CornerRadius(minOf(corner, 14f).dp.toPx())
    val fill = Brush.linearGradient(listOf(Color(0xFF10171C), Color(0xFF0C1216)),
        Offset.Zero, Offset(size.width, size.height))
    val edge = Brush.verticalGradient(listOf(Color(0xFF293136), Color(0xFF1C252B)))
    onDrawBehind {
        drawRoundRect(fill, cornerRadius = radius)
        val half = .5.dp.toPx()
        drawRoundRect(edge, Offset(half, half),
            Size((size.width-half*2).coerceAtLeast(0f), (size.height-half*2).coerceAtLeast(0f)),
            radius, style = Stroke(.8.dp.toPx()))
    }
}

@Composable
fun GlassCard(modifier: Modifier = Modifier, elevated: Boolean = false, compact: Boolean = false,
    content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.clip(RoundedCornerShape(GlassSizes.cardCorner)).glassSurface()
        .padding(if (compact) GlassSizes.compactCardPadding else GlassSizes.cardPadding), content = content)
}

fun Modifier.premiumBackdrop(): Modifier = background(Ink)
val OrangeBackground = Brush.verticalGradient(listOf(Ink, Ink))

@Composable
fun Modifier.glassClick(onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .987f else 1f, tween(100), label = "data-press")
    return graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = source, indication = null, onClick = onClick)
}

@Composable
fun GlassActionButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.Bolt, enabled: Boolean = true) {
    Row(modifier.alpha(if (enabled) 1f else .48f).heightIn(min = 48.dp)
        .clip(RoundedCornerShape(GlassSizes.controlCorner))
        .background(Brush.verticalGradient(listOf(Color(0xFFFFB940), Color(0xFFFFA323))))
        .then(if (enabled) Modifier.glassClick(onClick) else Modifier)
        .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Icon(icon, null, tint = Color(0xFF16160F), modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = Color(0xFF16160F), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    }
}
