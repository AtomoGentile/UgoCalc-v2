package com.ugocalc.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.ugocalc.app.data.local.prefs.AppTheme

/**
 * Famiglie di carattere (§ richiesta utente "colore, carattere se riesci"): solo font di sistema
 * (`FontFamily.SansSerif`/`Serif`/`Monospace`/`Cursive`), niente file di font da scaricare o
 * impacchettare — coprono comunque stili molto diversi tra loro.
 */
enum class AppFontFamily(val label: String) {
    DEFAULT("Predefinito"),
    SANS_SERIF("Sans serif"),
    SERIF("Serif"),
    MONOSPACE("Monospace"),
    CURSIVE("Corsivo");

    val fontFamily: FontFamily? get() = when (this) {
        DEFAULT -> null
        SANS_SERIF -> FontFamily.SansSerif
        SERIF -> FontFamily.Serif
        MONOSPACE -> FontFamily.Monospace
        CURSIVE -> FontFamily.Cursive
    }

    /** Su alcuni dispositivi Monospace risulta quasi identico a Sans serif (§ segnalazione utente): un peso marcato lo rende subito riconoscibile anche lì. */
    val fontWeight: FontWeight? get() = if (this == MONOSPACE) FontWeight.Bold else null
}

val UgoGreen = Color(0xFF2E7D32)
val UgoRed = Color(0xFFC62828)
val UgoGrey = Color(0xFF9E9E9E)
/** Colore grassi nel grafico a torta CHO/grassi (§ schermata finale), distinto dal verde primario (CHO). */
val UgoAmber = Color(0xFFF57C00)

private val LightColors = lightColorScheme(
    primary = UgoGreen,
    secondary = Color(0xFF66BB6A),
    tertiary = Color(0xFFEF6C00),
    error = UgoRed,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF81C784),
    secondary = Color(0xFF4CAF50),
    tertiary = Color(0xFFFFB74D),
    error = Color(0xFFEF9A9A),
)

/** Colori del tema *senza* personalizzazione (§ "Predefinito" nel selettore colori), per seminare i cursori e per il reset. */
fun defaultThemeColors(useDark: Boolean) = if (useDark) DarkColors else LightColors

/** Testo/icona leggibile sopra [color]: bianco su colori scuri, nero su colori chiari. */
private fun contrastingOn(color: Color): Color = if (color.luminance() > 0.5f) Color.Black else Color.White

/**
 * Tono "container" (§ Material 3: superficie tenue abbinata a [base]) e il suo testo leggibile.
 * Niente libreria Material Color Utilities: un blend semplice verso bianco/nero è sufficiente per
 * restare visivamente coerenti con qualunque colore scelto dall'utente, senza il calcolo HCT completo.
 */
private fun containerColors(base: Color, useDark: Boolean): Pair<Color, Color> {
    // Blend più marcato del "90/10" da manuale Material 3: a quell'intensità il tono risultava
    // troppo tenue e le Impostazioni sembravano "senza colore" (§ segnalazione utente).
    val container = if (useDark) lerp(base, Color.Black, 0.5f) else lerp(base, Color.White, 0.55f)
    val onContainer = if (useDark) lerp(base, Color.White, 0.8f) else lerp(base, Color.Black, 0.75f)
    return container to onContainer
}

/**
 * Applica personalizzazione (§ richiesta utente "personalizzazione colori") a uno schema base,
 * ricalcolando *tutti* i ruoli derivati (container, on-color) invece di sovrascrivere solo
 * primary/secondary/tertiary lasciando il resto alla tavolozza viola/rosa generica di Material 3
 * — la causa della "stella bianca" e del menu "senza colore" segnalati.
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
    val secondary = secondaryColor ?: this.secondary
    val tertiary = tertiaryColor ?: this.tertiary
    val error = errorColor ?: this.error
    val (primaryContainer, onPrimaryContainer) = containerColors(primary, useDark)
    val (secondaryContainer, onSecondaryContainer) = containerColors(secondary, useDark)
    val (tertiaryContainer, onTertiaryContainer) = containerColors(tertiary, useDark)
    val (errorContainer, onErrorContainer) = containerColors(error, useDark)
    val background = backgroundColor ?: this.background
    val surface = backgroundColor ?: this.surface

    return copy(
        primary = primary,
        onPrimary = contrastingOn(primary),
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = secondary,
        onSecondary = contrastingOn(secondary),
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        tertiary = tertiary,
        onTertiary = contrastingOn(tertiary),
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = onTertiaryContainer,
        // Il rosso resta "intenso" (colore pieno, non un tono container tenue): è l'avviso più
        // urgente dell'app, es. il pulsante Analizza quando sei offline (§ richiesta utente).
        error = error,
        onError = contrastingOn(error),
        errorContainer = errorContainer,
        onErrorContainer = onErrorContainer,
        background = background,
        onBackground = contrastingOn(background),
        surface = surface,
        onSurface = contrastingOn(surface),
        surfaceTint = primary,
    )
}

/**
 * [textScale] implementa "Accessibilità: dimensione testo" (§2.2): moltiplica tutte le dimensioni
 * tipografiche di base, senza dover duplicare l'intera Typography per ogni livello.
 *
 * [primaryColor]/[secondaryColor]/[tertiaryColor]/[backgroundColor] (§ richiesta utente,
 * "personalizzazione colori"): se valorizzati sovrascrivono il colore corrispondente e tutti i
 * ruoli derivati (container, on-color), altrimenti restano quelli del tema chiaro/scuro predefinito.
 *
 * [fontFamily]/[fontColor] (§ richiesta utente, "colore, carattere se riesci"): [fontColor] passa
 * dal `contentColor` della Surface radice, così arriva a qualunque `Text` che non specifichi già
 * un colore proprio, senza dover toccare ogni schermata una per una.
 */
@Composable
fun UgoCalcTheme(
    appTheme: AppTheme = AppTheme.SYSTEM,
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
    val baseColors = if (useDark) DarkColors else LightColors
    val colors = baseColors.withCustomColors(useDark, primaryColor, secondaryColor, tertiaryColor, backgroundColor, errorColor)
    val baseTypography = MaterialTheme.typography
    fun scaled(size: androidx.compose.ui.unit.TextUnit) = (size.value * textScale).sp
    // Il colore va "cotto" dentro ogni TextStyle, non solo passato come contentColor della
    // Surface radice: qualunque Card/Button/Surface annidato (usatissimi in questa app) reimposta
    // il proprio contentColor in base al proprio sfondo, sovrascrivendo quello ereditato dall'alto
    // — un TextStyle.color esplicito invece vince sempre, indipendentemente da dove si trova il Text.
    fun style(base: androidx.compose.ui.text.TextStyle, size: androidx.compose.ui.unit.TextUnit) = base.copy(
        fontSize = scaled(size),
        fontFamily = fontFamily ?: base.fontFamily,
        fontWeight = fontWeight ?: base.fontWeight,
        color = fontColor ?: base.color,
    )
    val scaledTypography = baseTypography.copy(
        bodyLarge = style(baseTypography.bodyLarge, baseTypography.bodyLarge.fontSize),
        bodyMedium = style(baseTypography.bodyMedium, baseTypography.bodyMedium.fontSize),
        bodySmall = style(baseTypography.bodySmall, baseTypography.bodySmall.fontSize),
        titleLarge = style(baseTypography.titleLarge, baseTypography.titleLarge.fontSize),
        titleMedium = style(baseTypography.titleMedium, baseTypography.titleMedium.fontSize),
        labelLarge = style(baseTypography.labelLarge, baseTypography.labelLarge.fontSize),
    )

    MaterialTheme(
        colorScheme = colors,
        typography = scaledTypography,
    ) {
        // Sfondo opaco a piena schermata: senza, durante le transizioni fadeIn/fadeOut tra
        // schermate (vedi UgoCalcNavHost) si intravede per un istante il windowBackground
        // dell'Activity invece del tema Compose corrente (flash bianco/chiaro).
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            contentColor = fontColor ?: contentColorFor(MaterialTheme.colorScheme.background),
        ) {
            content()
        }
    }
}
