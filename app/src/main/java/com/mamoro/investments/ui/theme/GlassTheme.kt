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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Orange = Color(0xFFFF9D00)
val OrangeLight = Color(0xFFFFC374)
val Green = Color(0xFF8CEB42)
val Red = Color(0xFFFF5547)
val Ink = Color(0xFF111313)
val Frost = Color(0xFF303231)
val SoftText = Color(0xFFBFC0BD)
val StrongText = Color(0xFFF5F5F1)

object GlassSizes {
    val phoneSidePadding = 16.dp
    val tabletSidePadding = 24.dp
    val maxContentWidth = 480.dp
    val maxNavigationWidth = 480.dp
    val cardCorner = 21.dp
    val controlCorner = 21.dp
    val cardPadding = 16.dp
    val compactCardPadding = 10.dp
    val sectionGap = 8.dp
    val navHeight = 72.dp
}

private val darkColors = darkColorScheme(
    primary = Orange, onPrimary = Color(0xFF211300),
    primaryContainer = Color(0xFF594025), onPrimaryContainer = StrongText,
    secondary = OrangeLight, onSecondary = Ink,
    secondaryContainer = Color(0xFF40372D), onSecondaryContainer = StrongText,
    background = Ink, onBackground = StrongText,
    surface = Color(0xFF242625), onSurface = StrongText,
    surfaceVariant = Frost, onSurfaceVariant = SoftText,
    outline = Color(0xFF868782), error = Red, onError = Ink
)

private val type = Typography(
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 20.sp, letterSpacing = 0.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = 0.sp, fontWeight = FontWeight.Medium),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.sp),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
)

@Composable
fun InvestmentsTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColors, typography = type) {
        Surface(Modifier.fillMaxSize(), color = Ink, contentColor = StrongText, content = content)
    }
}

/** Cached, resolution-independent optics. No bitmap of the reference and no live blur. */
fun Modifier.glassSurface(corner: Float = 21f, warm: Boolean = false): Modifier =
    drawWithCache {
        val r = CornerRadius(corner.dp.toPx())
        val w = size.width
        val h = size.height
        val base = Brush.verticalGradient(
            0f to Color(0xD35C5C58), .38f to Color(0xE0292C2B),
            .78f to Color(0xEE202322), 1f to Color(0xD747423B)
        )
        val topLight = Brush.radialGradient(
            listOf(Color(0x53FFFFFF), Color(0x0EFFFFFF), Color.Transparent),
            Offset(w * .08f, -h * .3f), w * .57f
        )
        val amber = Brush.radialGradient(
            listOf(OrangeLight.copy(alpha = if (warm) .27f else .10f), Color.Transparent),
            Offset(w * .95f, h * 1.25f), w * .5f
        )
        val rim = Brush.linearGradient(
            listOf(Color(0xC5F9E8D6), Color(0x52F0F0E8), Color(0x19FFFFFF), Color(0x8CCBB59C)),
            Offset.Zero, Offset(w * .7f, h)
        )
        val topEdge = Brush.horizontalGradient(
            listOf(Color.Transparent, Color(0xAFFFFFF4), Color(0x20FFFFFF), Color.Transparent)
        )
        onDrawBehind {
            drawRoundRect(base, cornerRadius = r)
            drawRoundRect(topLight, cornerRadius = r)
            drawRoundRect(amber, cornerRadius = r)
            // Wide dim bevel, then the brighter outside edge and the upper reflection.
            val inset = 1.8.dp.toPx()
            drawRoundRect(Color.White.copy(alpha = .035f), Offset(inset, inset),
                Size((w - inset * 2).coerceAtLeast(0f), (h - inset * 2).coerceAtLeast(0f)),
                CornerRadius((r.x - inset).coerceAtLeast(0f)), style = Stroke(2.6.dp.toPx()))
            val half = .5.dp.toPx()
            drawRoundRect(rim, Offset(half, half), Size(w - half * 2, h - half * 2),
                r, style = Stroke(.8.dp.toPx()))
            drawLine(topEdge, Offset(r.x, half), Offset(w - r.x, half), .8.dp.toPx(), StrokeCap.Round)
        }
    }

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    elevated: Boolean = false,
    compact: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(GlassSizes.cardCorner)
    Column(
        modifier.shadow(if (elevated) 12.dp else 5.dp, shape,
            ambientColor = Color.Black.copy(alpha = .6f),
            spotColor = if (elevated) Orange.copy(alpha = .18f) else Color.Black)
            .clip(shape).glassSurface(warm = elevated)
            .padding(if (compact) GlassSizes.compactCardPadding else GlassSizes.cardPadding),
        content = content
    )
}

/** Same diagonal lighting as the screen interiors, without the presentation's phone frame. */
fun Modifier.premiumBackdrop(): Modifier = drawWithCache {
    val w = size.width
    val h = size.height
    val base = Brush.linearGradient(
        listOf(Color(0xFF242625), Color(0xFF111313), Color(0xFF191B1A), Color(0xFF101212)),
        Offset.Zero, Offset(w, h)
    )
    val reflection = Path().apply {
        moveTo(w * .83f, 0f); lineTo(w, 0f); lineTo(w, h * .08f)
        lineTo(0f, h * .67f); lineTo(0f, h * .51f); close()
    }
    val light = Brush.linearGradient(listOf(Color(0x0DFFFFFF), Color(0x02FFFFFF), Color.Transparent),
        Offset(w, 0f), Offset(w * .15f, h * .6f))
    val bottomGlow = Brush.radialGradient(listOf(Color(0x4C8E481A), Color(0x0D7B421D), Color.Transparent),
        Offset(w * 1.13f, h * .96f), w * .9f)
    val upperGlow = Brush.radialGradient(listOf(Color(0x0AFFFFFF), Color.Transparent),
        Offset(w * .65f, 0f), w * .85f)
    onDrawBehind {
        drawRect(base); drawPath(reflection, light); drawRect(bottomGlow); drawRect(upperGlow)
    }
}

// Kept for non-screen consumers. Screen lighting uses the size-aware cached backdrop.
val OrangeBackground = Brush.verticalGradient(listOf(Color(0xFF242625), Ink, Color(0xFF191B1A)))

@Composable
fun Modifier.glassClick(onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .982f else 1f, tween(110), label = "glass-press")
    return graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = source, indication = null, onClick = onClick)
}

@Composable
fun GlassActionButton(
    label: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.Add, enabled: Boolean = true
) {
    val shape = RoundedCornerShape(GlassSizes.controlCorner)
    Row(
        modifier.heightIn(min = 56.dp)
            .shadow(14.dp, shape, ambientColor = Orange.copy(alpha = .25f), spotColor = Orange.copy(alpha = .5f))
            .clip(shape)
            .background(Brush.verticalGradient(
                0f to Color(0xFFFFDE93), .22f to Color(0xFFFFAF40),
                .56f to Color(0xFFFF9100), .84f to Color(0xFFFF9200), 1f to Color(0xFFFFBA30)
            ))
            .drawWithCache {
                val edge = Brush.verticalGradient(listOf(Color(0xFFFFE4AD), Color(0x35FFFFFF), OrangeLight))
                onDrawBehind {
                    drawRoundRect(edge, cornerRadius = CornerRadius(21.dp.toPx()), style = Stroke(1.5.dp.toPx()))
                    drawRoundRect(Brush.radialGradient(listOf(Color(0xA0FFFFFF), Color(0x16FFFFFF), Color.Transparent),
                        Offset(size.width * .12f, 0f), size.width * .5f), cornerRadius = CornerRadius(21.dp.toPx()))
                }
            }
            .then(if (enabled) Modifier.glassClick(onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = Color(0xFF211400), modifier = Modifier.size(27.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, color = Color(0xFF211400), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    }
}
