package com.ugocalc.app.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.ugocalc.app.data.local.prefs.AppTheme

val NunitoFamily = FontFamily.SansSerif
val EditorialSerifFamily = FontFamily.Serif

val LocalUseEditorialSerif = staticCompositionLocalOf { true }

/**
 * Restituisce lo stile tipografico editoriale (Serif caldo) per i titoli hero e i saluti,
 * rispettando eventuali preferenze di accessibilità se l'utente ha scelto un font personalizzato.
 */
@Composable
@ReadOnlyComposable
fun editorialDisplayTextStyle(
    base: TextStyle = MaterialTheme.typography.headlineMedium,
): TextStyle = if (LocalUseEditorialSerif.current) {
    base.copy(fontFamily = EditorialSerifFamily)
} else {
    base
}

/**
 * Famiglie di carattere configurabili in Accessibilità.
 */
enum class AppFontFamily(val label: String) {
    DEFAULT("Predefinito"),
    SANS_SERIF("Sans serif"),
    SERIF("Serif"),
    MONOSPACE("Monospace"),
    CURSIVE("Corsivo");

    val fontFamily: FontFamily? get() = when (this) {
        DEFAULT -> NunitoFamily
        SANS_SERIF -> FontFamily.SansSerif
        SERIF -> FontFamily.Serif
        MONOSPACE -> FontFamily.Monospace
        CURSIVE -> FontFamily.Cursive
    }

    /** Su alcuni dispositivi Monospace risulta simile a Sans serif: un peso marcato lo rende riconoscibile. */
    val fontWeight: FontWeight? get() = if (this == MONOSPACE) FontWeight.Bold else null
}

// Token storici mantenuti per compatibilità con i call-site esistenti
val UgoGreen = Color(0xFF15803D)
val UgoRed = Color(0xFFDC2626)
val UgoGrey = Color(0xFF71717A)

// Palette semantiche di base (Casa & Ristorante)
val WarmCream = Color(0xFFFAF9F6)
val WarmWhite = Color(0xFFFFFFFF)
val WarmSand = Color(0xFFECEBE4)
val WarmTerracotta = Color(0xFFEA580C)
val WarmAmber = Color(0xFFD97706)

val DarkCharcoal = Color(0xFF0E1113)
val DarkSurface = Color(0xFF0E1113)
val DarkSurfaceVariant = Color(0xFF1F272C)
val DarkSecondaryVibrant = Color(0xFFFB923C)

val RistoranteGold = Color(0xFFB45309)
val RistoranteWine = Color(0xFF9F1239)
val RistoranteCream = Color(0xFFFBF8F5)

// Spacing tokens
val SpaceXS = 4.dp
val SpaceS = 8.dp
val SpaceM = 16.dp
val SpaceL = 24.dp
val SpaceXL = 32.dp

private val LightColors = lightColorScheme(
    primary = Color(0xFF15803D),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCFCE7),
    onPrimaryContainer = Color(0xFF052E16),
    secondary = Color(0xFFEA580C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFEDD5),
    onSecondaryContainer = Color(0xFF431407),
    tertiary = Color(0xFFD97706),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFEF3C7),
    onTertiaryContainer = Color(0xFF451A03),
    background = Color(0xFFF3F2EE),
    onBackground = Color(0xFF18181B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF18181B),
    surfaceVariant = Color(0xFFEFEEEA),
    onSurfaceVariant = Color(0xFF6B6A66),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F6F3),
    surfaceContainer = Color(0xFFEFEEEA),
    surfaceContainerHigh = Color(0xFFE7E5DF),
    surfaceContainerHighest = Color(0xFFDDDBD4),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFE7E5DF),
    surfaceTint = Color(0xFF15803D),
    outline = Color(0xFF8A8882),
    outlineVariant = Color(0xFFE8E6E0),
    error = Color(0xFFDC2626),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),
    inverseSurface = Color(0xFF18181B),
    inverseOnSurface = Color(0xFFFAF9F6),
    inversePrimary = Color(0xFF34D399),
    scrim = Color(0xFF090B0C),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF34D399),
    onPrimary = Color(0xFF022C22),
    primaryContainer = Color(0xFF064E3B),
    onPrimaryContainer = Color(0xFFA7F3D0),
    secondary = Color(0xFFFB923C),
    onSecondary = Color(0xFF431407),
    secondaryContainer = Color(0xFF7C2D12),
    onSecondaryContainer = Color(0xFFFFEDD5),
    tertiary = Color(0xFFFBBF24),
    onTertiary = Color(0xFF451A03),
    tertiaryContainer = Color(0xFF78350F),
    onTertiaryContainer = Color(0xFFFEF3C7),
    background = Color(0xFF0B0E10),
    onBackground = Color(0xFFF4F5F6),
    surface = Color(0xFF14181B),
    onSurface = Color(0xFFF4F5F6),
    surfaceVariant = Color(0xFF1E2428),
    onSurfaceVariant = Color(0xFF9AA4AF),
    surfaceContainerLowest = Color(0xFF14181B),
    surfaceContainerLow = Color(0xFF1B2024),
    surfaceContainer = Color(0xFF22282D),
    surfaceContainerHigh = Color(0xFF2B3238),
    surfaceContainerHighest = Color(0xFF343C43),
    surfaceBright = Color(0xFF343C43),
    surfaceDim = Color(0xFF0B0E10),
    surfaceTint = Color(0xFF34D399),
    outline = Color(0xFF64748B),
    outlineVariant = Color(0xFF273036),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFECACA),
    inverseSurface = Color(0xFFF4F5F6),
    inverseOnSurface = Color(0xFF14181B),
    inversePrimary = Color(0xFF15803D),
    scrim = Color(0xFF000000),
)

private val RistoranteLightColors = lightColorScheme(
    primary = Color(0xFF9F1239),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFE4E6),
    onPrimaryContainer = Color(0xFF4C0519),
    secondary = Color(0xFFB45309),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFEF3C7),
    onSecondaryContainer = Color(0xFF451A03),
    tertiary = Color(0xFF78350F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF5EBE0),
    onTertiaryContainer = Color(0xFF2E1505),
    background = Color(0xFFF5F0EB),
    onBackground = Color(0xFF1C1917),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C1917),
    surfaceVariant = Color(0xFFF0EAE3),
    onSurfaceVariant = Color(0xFF6B6560),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF9F6F2),
    surfaceContainer = Color(0xFFF0EAE3),
    surfaceContainerHigh = Color(0xFFE7DFD6),
    surfaceContainerHighest = Color(0xFFDFD4C8),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFE7DFD6),
    surfaceTint = Color(0xFF9F1239),
    outline = Color(0xFF8C847E),
    outlineVariant = Color(0xFFE9E2DA),
    error = Color(0xFFDC2626),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),
    inverseSurface = Color(0xFF1C1917),
    inverseOnSurface = Color(0xFFFBF8F5),
    inversePrimary = Color(0xFFFB7185),
    scrim = Color(0xFF130E10),
)

private val RistoranteDarkColors = darkColorScheme(
    primary = Color(0xFFFB7185),
    onPrimary = Color(0xFF4C0519),
    primaryContainer = Color(0xFF881337),
    onPrimaryContainer = Color(0xFFFFE4E6),
    secondary = Color(0xFFFBBF24),
    onSecondary = Color(0xFF451A03),
    secondaryContainer = Color(0xFF78350F),
    onSecondaryContainer = Color(0xFFFEF3C7),
    tertiary = Color(0xFFF59E0B),
    onTertiary = Color(0xFF451A03),
    tertiaryContainer = Color(0xFF5C280A),
    onTertiaryContainer = Color(0xFFFEF3C7),
    background = Color(0xFF0F0A0C),
    onBackground = Color(0xFFFAF5F6),
    surface = Color(0xFF181114),
    onSurface = Color(0xFFFAF5F6),
    surfaceVariant = Color(0xFF231A1E),
    onSurfaceVariant = Color(0xFFBBAFB4),
    surfaceContainerLowest = Color(0xFF181114),
    surfaceContainerLow = Color(0xFF21181C),
    surfaceContainer = Color(0xFF2A1F24),
    surfaceContainerHigh = Color(0xFF34272D),
    surfaceContainerHighest = Color(0xFF3F3037),
    surfaceBright = Color(0xFF3F3037),
    surfaceDim = Color(0xFF0F0A0C),
    surfaceTint = Color(0xFFFB7185),
    outline = Color(0xFF6E5E65),
    outlineVariant = Color(0xFF372A30),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFECACA),
    inverseSurface = Color(0xFFFAF5F6),
    inverseOnSurface = Color(0xFF1C1917),
    inversePrimary = Color(0xFF9F1239),
    scrim = Color(0xFF000000),
)

@Immutable
data class UgoSemanticPalette(
    val cho: Color,
    val onCho: Color,
    val choContainer: Color,
    val onChoContainer: Color,
    val fat: Color,
    val onFat: Color,
    val fatContainer: Color,
    val onFatContainer: Color,
    val protein: Color,
    val onProtein: Color,
    val proteinContainer: Color,
    val onProteinContainer: Color,
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
)

private val DefaultLightSemanticColors = UgoSemanticPalette(
    cho = Color(0xFF15803D),
    onCho = Color(0xFFFFFFFF),
    choContainer = Color(0xFFDCFCE7),
    onChoContainer = Color(0xFF052E16),
    fat = Color(0xFFEA580C),
    onFat = Color(0xFFFFFFFF),
    fatContainer = Color(0xFFFFEDD5),
    onFatContainer = Color(0xFF431407),
    protein = Color(0xFF2563EB),
    onProtein = Color(0xFFFFFFFF),
    proteinContainer = Color(0xFFDBEAFE),
    onProteinContainer = Color(0xFF1E3A8A),
    success = Color(0xFF16A34A),
    onSuccess = Color(0xFFFFFFFF),
    successContainer = Color(0xFFDCFCE7),
    onSuccessContainer = Color(0xFF052E16),
    warning = Color(0xFFD97706),
    onWarning = Color(0xFFFFFFFF),
    warningContainer = Color(0xFFFEF3C7),
    onWarningContainer = Color(0xFF451A03),
)

private val DefaultDarkSemanticColors = UgoSemanticPalette(
    cho = Color(0xFF34D399),
    onCho = Color(0xFF022C22),
    choContainer = Color(0xFF064E3B),
    onChoContainer = Color(0xFFA7F3D0),
    fat = Color(0xFFFB923C),
    onFat = Color(0xFF431407),
    fatContainer = Color(0xFF7C2D12),
    onFatContainer = Color(0xFFFFEDD5),
    protein = Color(0xFF60A5FA),
    onProtein = Color(0xFF0F172A),
    proteinContainer = Color(0xFF1E3A8A),
    onProteinContainer = Color(0xFFDBEAFE),
    success = Color(0xFF4ADE80),
    onSuccess = Color(0xFF052E16),
    successContainer = Color(0xFF064E3B),
    onSuccessContainer = Color(0xFFA7F3D0),
    warning = Color(0xFFFBBF24),
    onWarning = Color(0xFF451A03),
    warningContainer = Color(0xFF78350F),
    onWarningContainer = Color(0xFFFEF3C7),
)

val LocalUgoSemanticColors = staticCompositionLocalOf { DefaultLightSemanticColors }

/**
 * Palette semantica dedicata ai macronutrienti e agli stati di feedback.
 */
object UgoSemanticColors {
    val current: UgoSemanticPalette
        @Composable
        @ReadOnlyComposable
        get() = LocalUgoSemanticColors.current

    val cho: Color @Composable @ReadOnlyComposable get() = LocalUgoSemanticColors.current.cho
    val onCho: Color @Composable @ReadOnlyComposable get() = LocalUgoSemanticColors.current.onCho
    val choContainer: Color @Composable @ReadOnlyComposable get() = LocalUgoSemanticColors.current.choContainer
    val onChoContainer: Color @Composable @ReadOnlyComposable get() = LocalUgoSemanticColors.current.onChoContainer

    val fat: Color @Composable @ReadOnlyComposable get() = LocalUgoSemanticColors.current.fat
    val onFat: Color @Composable @ReadOnlyComposable get() = LocalUgoSemanticColors.current.onFat
    val fatContainer: Color @Composable @ReadOnlyComposable get() = LocalUgoSemanticColors.current.fatContainer
    val onFatContainer: Color @Composable @ReadOnlyComposable get() = LocalUgoSemanticColors.current.onFatContainer

    val protein: Color @Composable @ReadOnlyComposable get() = LocalUgoSemanticColors.current.protein
    val onProtein: Color @Composable @ReadOnlyComposable get() = LocalUgoSemanticColors.current.onProtein
    val proteinContainer: Color @Composable @ReadOnlyComposable get() = LocalUgoSemanticColors.current.proteinContainer
    val onProteinContainer: Color @Composable @ReadOnlyComposable get() = LocalUgoSemanticColors.current.onProteinContainer

    val success: Color @Composable @ReadOnlyComposable get() = LocalUgoSemanticColors.current.success
    val onSuccess: Color @Composable @ReadOnlyComposable get() = LocalUgoSemanticColors.current.onSuccess
    val successContainer: Color @Composable @ReadOnlyComposable get() = LocalUgoSemanticColors.current.successContainer
    val onSuccessContainer: Color @Composable @ReadOnlyComposable get() = LocalUgoSemanticColors.current.onSuccessContainer

    val warning: Color @Composable @ReadOnlyComposable get() = LocalUgoSemanticColors.current.warning
    val onWarning: Color @Composable @ReadOnlyComposable get() = LocalUgoSemanticColors.current.onWarning
    val warningContainer: Color @Composable @ReadOnlyComposable get() = LocalUgoSemanticColors.current.warningContainer
    val onWarningContainer: Color @Composable @ReadOnlyComposable get() = LocalUgoSemanticColors.current.onWarningContainer
}

val UgoTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = NunitoFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
        lineHeight = 46.sp,
        letterSpacing = (-0.5).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = NunitoFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.25).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = NunitoFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = 0.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = NunitoFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.2).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = NunitoFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = NunitoFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = NunitoFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 25.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = NunitoFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.1.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = NunitoFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = NunitoFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = NunitoFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.2.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = NunitoFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.3.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = NunitoFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = NunitoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = NunitoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.4.sp,
    ),
)

val UgoShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Colori del tema *senza* personalizzazione, per seminare i cursori e per il reset. */
fun defaultThemeColors(useDark: Boolean, isRistorante: Boolean = false): ColorScheme = when {
    isRistorante && useDark -> RistoranteDarkColors
    isRistorante -> RistoranteLightColors
    useDark -> DarkColors
    else -> LightColors
}

/** Testo/icona leggibile sopra [color]. */
private fun contrastingOn(color: Color): Color =
    if (color.luminance() > 0.45f) Color(0xFF18181B) else Color(0xFFFAF9F6)

private fun containerColors(base: Color, useDark: Boolean): Pair<Color, Color> {
    val container = if (useDark) lerp(base, Color(0xFF090B0C), 0.55f) else lerp(base, Color.White, 0.72f)
    val onContainer = if (container.luminance() > 0.45f) {
        lerp(base, Color(0xFF050505), 0.65f)
    } else {
        lerp(base, Color.White, 0.82f)
    }
    return container to onContainer
}

/**
 * Applica personalizzazione colori ricalcolando tutti i ruoli derivati inclusi
 * surfaceContainerLowest..Highest, surfaceVariant, onSurfaceVariant e outlineVariant,
 * così da non far mai trapelare toni M3 predefiniti (lilla/viola).
 */
private fun ColorScheme.withCustomColors(
    useDark: Boolean,
    primaryColor: Color?,
    secondaryColor: Color?,
    tertiaryColor: Color?,
    backgroundColor: Color?,
    errorColor: Color?,
): ColorScheme {
    val primary = primaryColor ?: this.primary
    val onPrimary = if (primaryColor != null) contrastingOn(primary) else this.onPrimary
    val (primaryContainer, onPrimaryContainer) = if (primaryColor != null) {
        containerColors(primary, useDark)
    } else {
        this.primaryContainer to this.onPrimaryContainer
    }

    val secondary = secondaryColor ?: this.secondary
    val onSecondary = if (secondaryColor != null) contrastingOn(secondary) else this.onSecondary
    val (secondaryContainer, onSecondaryContainer) = if (secondaryColor != null) {
        containerColors(secondary, useDark)
    } else {
        this.secondaryContainer to this.onSecondaryContainer
    }

    val tertiary = tertiaryColor ?: this.tertiary
    val onTertiary = if (tertiaryColor != null) contrastingOn(tertiary) else this.onTertiary
    val (tertiaryContainer, onTertiaryContainer) = if (tertiaryColor != null) {
        containerColors(tertiary, useDark)
    } else {
        this.tertiaryContainer to this.onTertiaryContainer
    }

    val error = errorColor ?: this.error
    val onError = if (errorColor != null) contrastingOn(error) else this.onError
    val (errorContainer, onErrorContainer) = if (errorColor != null) {
        containerColors(error, useDark)
    } else {
        this.errorContainer to this.onErrorContainer
    }

    val background = backgroundColor ?: this.background
    val surface = backgroundColor ?: this.surface
    val bgIsDark = if (backgroundColor != null) background.luminance() < 0.45f else useDark

    val onBg = if (backgroundColor != null) contrastingOn(background) else this.onBackground
    val onSurf = if (backgroundColor != null) contrastingOn(surface) else this.onSurface

    val surfaceContainerLowest = if (backgroundColor != null) {
        if (bgIsDark) lerp(background, Color.Black, 0.35f) else lerp(background, Color.White, 0.72f)
    } else {
        this.surfaceContainerLowest
    }
    val surfaceContainerLow = if (backgroundColor != null) {
        if (bgIsDark) lerp(background, Color.White, 0.04f) else lerp(background, Color.Black, 0.03f)
    } else {
        this.surfaceContainerLow
    }
    val surfaceContainer = if (backgroundColor != null) {
        if (bgIsDark) lerp(background, Color.White, 0.08f) else lerp(background, Color.Black, 0.06f)
    } else {
        this.surfaceContainer
    }
    val surfaceContainerHigh = if (backgroundColor != null) {
        if (bgIsDark) lerp(background, Color.White, 0.12f) else lerp(background, Color.Black, 0.10f)
    } else {
        this.surfaceContainerHigh
    }
    val surfaceContainerHighest = if (backgroundColor != null) {
        if (bgIsDark) lerp(background, Color.White, 0.17f) else lerp(background, Color.Black, 0.14f)
    } else {
        this.surfaceContainerHighest
    }
    val surfaceVariant = if (backgroundColor != null) surfaceContainer else this.surfaceVariant
    val onSurfaceVariant = if (backgroundColor != null) lerp(onBg, background, 0.32f) else this.onSurfaceVariant
    val outline = if (backgroundColor != null) lerp(onBg, background, 0.52f) else this.outline
    val outlineVariant = if (backgroundColor != null) lerp(background, onBg, 0.18f) else this.outlineVariant
    val surfaceBright = if (backgroundColor != null) {
        if (bgIsDark) surfaceContainerHighest else surfaceContainerLowest
    } else {
        this.surfaceBright
    }
    val surfaceDim = if (backgroundColor != null) {
        if (bgIsDark) surfaceContainerLowest else surfaceContainerHigh
    } else {
        this.surfaceDim
    }
    val inversePrimary = if (primaryColor != null) {
        if (useDark) lerp(primary, Color.Black, 0.25f) else lerp(primary, Color.White, 0.35f)
    } else {
        this.inversePrimary
    }

    return copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        tertiary = tertiary,
        onTertiary = onTertiary,
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = onTertiaryContainer,
        error = error,
        onError = onError,
        errorContainer = errorContainer,
        onErrorContainer = onErrorContainer,
        background = background,
        onBackground = onBg,
        surface = surface,
        onSurface = onSurf,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariant,
        surfaceContainerLowest = surfaceContainerLowest,
        surfaceContainerLow = surfaceContainerLow,
        surfaceContainer = surfaceContainer,
        surfaceContainerHigh = surfaceContainerHigh,
        surfaceContainerHighest = surfaceContainerHighest,
        surfaceBright = surfaceBright,
        surfaceDim = surfaceDim,
        surfaceTint = primary,
        outline = outline,
        outlineVariant = outlineVariant,
        inversePrimary = inversePrimary,
    )
}

private fun buildSemanticPalette(
    colors: ColorScheme,
    useDark: Boolean,
    isRistorante: Boolean,
): UgoSemanticPalette {
    val base = if (useDark) DefaultDarkSemanticColors else DefaultLightSemanticColors
    return if (isRistorante) {
        base.copy(
            cho = if (useDark) Color(0xFF2DD4BF) else Color(0xFF0F766E),
            onCho = if (useDark) Color(0xFF042F2E) else Color(0xFFFFFFFF),
            choContainer = if (useDark) Color(0xFF115E59) else Color(0xFFCCFBF1),
            onChoContainer = if (useDark) Color(0xFFCCFBF1) else Color(0xFF134E4A),
            fat = colors.secondary,
            onFat = colors.onSecondary,
            fatContainer = colors.secondaryContainer,
            onFatContainer = colors.onSecondaryContainer,
        )
    } else {
        base.copy(
            cho = colors.primary,
            onCho = colors.onPrimary,
            choContainer = colors.primaryContainer,
            onChoContainer = colors.onPrimaryContainer,
            fat = colors.secondary,
            onFat = colors.onSecondary,
            fatContainer = colors.secondaryContainer,
            onFatContainer = colors.onSecondaryContainer,
        )
    }
}

@Composable
fun UgoCalcTheme(
    appTheme: AppTheme = AppTheme.SYSTEM,
    isRistorante: Boolean = false,
    textScale: Float = 1.0f,
    primaryColor: Color? = null,
    secondaryColor: Color? = null,
    tertiaryColor: Color? = null,
    backgroundColor: Color? = null,
    errorColor: Color? = null,
    fontFamily: FontFamily? = null,
    fontWeight: FontWeight? = null,
    fontColor: Color? = null,
    content: @Composable () -> Unit,
) {
    val useDark = when (appTheme) {
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
        AppTheme.SYSTEM -> isSystemInDarkTheme()
    }

    val targetColors = remember(
        useDark,
        isRistorante,
        primaryColor,
        secondaryColor,
        tertiaryColor,
        backgroundColor,
        errorColor,
    ) {
        defaultThemeColors(useDark, isRistorante).withCustomColors(
            useDark = useDark,
            primaryColor = primaryColor,
            secondaryColor = secondaryColor,
            tertiaryColor = tertiaryColor,
            backgroundColor = backgroundColor,
            errorColor = errorColor,
        )
    }

    // Transizione cromatica fluida (es. cambio Casa ↔ Ristorante o Chiaro ↔ Scuro)
    val themeAnimSpec = tween<Color>(durationMillis = 280)
    val animatedPrimary by animateColorAsState(targetColors.primary, themeAnimSpec, label = "theme_primary")
    val animatedOnPrimary by animateColorAsState(targetColors.onPrimary, themeAnimSpec, label = "theme_on_primary")
    val animatedPrimaryContainer by animateColorAsState(targetColors.primaryContainer, themeAnimSpec, label = "theme_primary_container")
    val animatedOnPrimaryContainer by animateColorAsState(targetColors.onPrimaryContainer, themeAnimSpec, label = "theme_on_primary_container")
    val animatedSecondary by animateColorAsState(targetColors.secondary, themeAnimSpec, label = "theme_secondary")
    val animatedBackground by animateColorAsState(targetColors.background, themeAnimSpec, label = "theme_bg")
    val animatedSurface by animateColorAsState(targetColors.surface, themeAnimSpec, label = "theme_surface")
    val animatedSurfaceLow by animateColorAsState(targetColors.surfaceContainerLow, themeAnimSpec, label = "theme_surface_low")
    val animatedSurfaceContainer by animateColorAsState(targetColors.surfaceContainer, themeAnimSpec, label = "theme_surface_container")
    val animatedSurfaceHigh by animateColorAsState(targetColors.surfaceContainerHigh, themeAnimSpec, label = "theme_surface_high")
    val animatedOutlineVariant by animateColorAsState(targetColors.outlineVariant, themeAnimSpec, label = "theme_outline_variant")

    val colors = targetColors.copy(
        primary = animatedPrimary,
        onPrimary = animatedOnPrimary,
        primaryContainer = animatedPrimaryContainer,
        onPrimaryContainer = animatedOnPrimaryContainer,
        secondary = animatedSecondary,
        background = animatedBackground,
        surface = animatedSurface,
        surfaceContainerLow = animatedSurfaceLow,
        surfaceContainer = animatedSurfaceContainer,
        surfaceContainerHigh = animatedSurfaceHigh,
        outlineVariant = animatedOutlineVariant,
    )

    val semanticColors = remember(colors, useDark, isRistorante) {
        buildSemanticPalette(colors, useDark, isRistorante)
    }

    val scaledTypography = remember(textScale, fontFamily, fontWeight, fontColor) {
        fun scaled(unit: TextUnit): TextUnit =
            if (unit.isSpecified) (unit.value * textScale).sp else unit

        fun style(base: TextStyle): TextStyle = base.copy(
            fontSize = scaled(base.fontSize),
            lineHeight = scaled(base.lineHeight),
            fontFamily = fontFamily ?: base.fontFamily,
            fontWeight = fontWeight ?: base.fontWeight,
            color = fontColor ?: base.color,
        )

        UgoTypography.copy(
            displayLarge = style(UgoTypography.displayLarge),
            displayMedium = style(UgoTypography.displayMedium),
            displaySmall = style(UgoTypography.displaySmall),
            headlineLarge = style(UgoTypography.headlineLarge),
            headlineMedium = style(UgoTypography.headlineMedium),
            headlineSmall = style(UgoTypography.headlineSmall),
            titleLarge = style(UgoTypography.titleLarge),
            titleMedium = style(UgoTypography.titleMedium),
            titleSmall = style(UgoTypography.titleSmall),
            bodyLarge = style(UgoTypography.bodyLarge),
            bodyMedium = style(UgoTypography.bodyMedium),
            bodySmall = style(UgoTypography.bodySmall),
            labelLarge = style(UgoTypography.labelLarge),
            labelMedium = style(UgoTypography.labelMedium),
            labelSmall = style(UgoTypography.labelSmall),
        )
    }

    val useEditorialSerif = fontFamily == null || fontFamily == NunitoFamily
    CompositionLocalProvider(
        LocalUgoSemanticColors provides semanticColors,
        LocalUseEditorialSerif provides useEditorialSerif,
    ) {
        MaterialTheme(
            colorScheme = colors,
            typography = scaledTypography,
            shapes = UgoShapes,
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
                contentColor = fontColor ?: contentColorFor(MaterialTheme.colorScheme.background),
            ) {
                content()
            }
        }
    }
}
