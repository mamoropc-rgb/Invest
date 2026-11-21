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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
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
    val phoneSidePadding = 14.dp
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
            0f to Color(0xED2B2E30), .28f to Color(0xE126292B),
            .72f to Color(0xEB191C1F), 1f to Color(0xE32C2F32)
        )
        val topLight = Brush.radialGradient(
            if (warm) listOf(Color(0x6BFFE0C8), Color(0x18FFE0C8), Color.Transparent)
            else listOf(Color(0x70FFFFFF), Color(0x15FFFFFF), Color.Transparent),
            Offset(w * .08f, -minOf(h * .35f, 24.dp.toPx())), w * .35f
        )
        val amber = Brush.radialGradient(
            listOf(Color(0xFFF0B68F).copy(alpha = if (warm) .20f else .07f), Color.Transparent),
            Offset(w * .98f, h * 1.25f), w * .44f
        )
        val reflectionCenter = Offset(w * .80f, h * .18f)
        val reflectionRadius = w * .28f
        val warmReflection = Brush.radialGradient(
            0f to Color(0xFFE6915F).copy(alpha = .48f),
            .45f to Color(0xFFE6915F).copy(alpha = .25f), 1f to Color.Transparent,
            center = reflectionCenter, radius = reflectionRadius
        )
        val rim = Brush.linearGradient(
            listOf(Color(0xC9FFFFFF), Color(0x58E9EBEF), Color(0x18FFFFFF), Color(0x6B9D9992)),
            Offset.Zero, Offset(w * .7f, h)
        )
        val topEdge = Brush.horizontalGradient(
            listOf(Color.Transparent, Color(0xAFFFFFF4), Color(0x20FFFFFF), Color.Transparent)
        )
        val sheen = Brush.verticalGradient(
            0f to Color(0x2FFFFFFF), .045f to Color(0x0CFFFFFF), .22f to Color.Transparent,
            .8f to Color.Transparent, 1f to Color(0x0FFFFFFF)
        )
        onDrawBehind {
            drawRoundRect(base, cornerRadius = r)
            drawRoundRect(topLight, cornerRadius = r)
            drawRoundRect(amber, cornerRadius = r)
            if (warm) scale(1f, .35f, pivot = reflectionCenter) {
                drawCircle(warmReflection, reflectionRadius, reflectionCenter)
            }
            drawRoundRect(sheen, cornerRadius = r)
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

/** Soft diagonal lighting, with no hard-edged stripe or presentation phone frame. */
fun Modifier.premiumBackdrop(): Modifier = drawWithCache {
    val w = size.width
    val h = size.height
    val base = Brush.linearGradient(
        listOf(Color(0xFF1D1F21), Color(0xFF111315), Color(0xFF191B1D), Color(0xFF141618)),
        Offset.Zero, Offset(w, h)
    )
    val light = Brush.linearGradient(
        0f to Color.Transparent, .23f to Color.Transparent, .30f to Color(0x12FFFFFF),
        .40f to Color(0x08FFFFFF), .48f to Color.Transparent, 1f to Color.Transparent,
        start = Offset.Zero, end = Offset(w, h * .35f)
    )
    val bottomGlow = Brush.radialGradient(listOf(Color(0x6AB56B36), Color(0x177B421D), Color.Transparent),
        Offset(w * 1.10f, h * .95f), w * .88f)
    val upperGlow = Brush.radialGradient(listOf(Color(0x1AFFFFFF), Color.Transparent),
        Offset(w * .65f, 0f), w * .85f)
    val middleGlow = Brush.radialGradient(listOf(Color(0x1788512D), Color.Transparent),
        Offset(w * .1f, h * .32f), w * .75f)
    onDrawBehind {
        drawRect(base); drawRect(light); drawRect(bottomGlow); drawRect(upperGlow); drawRect(middleGlow)
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
        modifier.alpha(if (enabled) 1f else .48f).heightIn(min = 56.dp)
            .drawWithCache {
                val middle = Offset(size.width / 2f, size.height / 2f)
                val radius = size.width * .62f
                val bloom = Brush.radialGradient(listOf(Orange.copy(alpha = .85f), Orange.copy(alpha = .28f), Color.Transparent), middle, radius)
                onDrawBehind { scale(1f, .30f, pivot = middle) { drawCircle(bloom, radius, middle) } }
            }
            .shadow(14.dp, shape, ambientColor = Orange.copy(alpha = .25f), spotColor = Orange.copy(alpha = .5f))
            .clip(shape)
            .background(Brush.verticalGradient(
                0f to Color(0xFFFFDA91), .22f to Color(0xFFFFAB34),
                .56f to Color(0xFFFF9100), .84f to Color(0xFFFF9200), 1f to Color(0xFFFFC346)
            ))
            .drawWithCache {
                val edge = Brush.verticalGradient(listOf(Color(0xFFFFF4D9), Color(0x35FFFFFF), Color(0xFFFFD37B)))
                onDrawBehind {
                    drawRoundRect(edge, cornerRadius = CornerRadius(21.dp.toPx()), style = Stroke(1.5.dp.toPx()))
                    val glint = Offset(size.width * .10f, size.height * .06f)
                    scale(1f, .12f, pivot = glint) {
                        drawCircle(Brush.radialGradient(listOf(Color.White, Color.Transparent), glint, size.width * .65f),
                            size.width * .65f, glint)
                    }
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
