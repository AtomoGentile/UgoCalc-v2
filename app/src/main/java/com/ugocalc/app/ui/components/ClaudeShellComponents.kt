package com.ugocalc.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ugocalc.app.domain.model.AiEffort
import kotlin.math.roundToInt

/**
 * Shell editoriale stile Claude: cornice esterna calda (`background`) con top bar flottante,
 * e foglio interno pulito (`surface`) con grandi angoli arrotondati superiori.
 */
@Composable
fun EditorialCanvasShell(
    topBar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHost: @Composable () -> Unit = {},
    overlayContent: @Composable BoxScope.() -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    val isDark = isDarkSurfaceTheme()
    val sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            topBar()

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = sheetShape,
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(
                    width = 0.75.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.55f else 0.70f),
                ),
                shadowElevation = if (isDark) 0.dp else 1.dp,
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    content = content,
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 84.dp),
        ) {
            snackbarHost()
        }

        overlayContent()
    }
}

/**
 * Shell editoriale stile Claude per schermate secondarie e flussi guidati:
 * cornice calda con pulsante indietro circolare + titolo pulito + azioni a destra,
 * foglio interno arrotondato bianco/scuro elevato e bottom bar opzionale.
 */
@Composable
fun EditorialDetailScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    val isDark = isDarkSurfaceTheme()
    val sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            EditorialTopBar(
                leading = {
                    if (onBack != null) {
                        FloatingCircleIconButton(
                            icon = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Indietro",
                            onClick = onBack,
                            size = 40.dp,
                            iconSize = 19.dp,
                        )
                    }
                },
                center = {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 52.dp),
                    )
                },
                trailing = trailing,
            )

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = sheetShape,
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(
                    width = 0.75.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.55f else 0.70f),
                ),
                shadowElevation = if (isDark) 0.dp else 1.dp,
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        content = content,
                    )
                    bottomBar()
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
        ) {
            snackbarHost()
        }
    }
}

/**
 * Header compatto sulla fascia calda esterna con slot sinistro, centrale (es. indicatore modalità) e destro.
 */
@Composable
fun EditorialTopBar(
    modifier: Modifier = Modifier,
    leading: @Composable () -> Unit = {},
    center: @Composable BoxScope.() -> Unit = {},
    trailing: @Composable () -> Unit = {},
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .defaultMinSize(minHeight = 42.dp),
    ) {
        Box(modifier = Modifier.align(Alignment.CenterStart)) {
            leading()
        }
        Box(modifier = Modifier.align(Alignment.Center)) {
            center()
        }
        Box(modifier = Modifier.align(Alignment.CenterEnd)) {
            trailing()
        }
    }
}

/**
 * Pillola informativa o cliccabile coerente per la fascia superiore (versione app, conteggio alimenti, ecc.).
 */
@Composable
fun FloatingTopBarPill(
    text: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    highlight: Boolean = false,
) {
    val isDark = isDarkSurfaceTheme()
    val containerColor = when {
        highlight -> MaterialTheme.colorScheme.primaryContainer
        isDark -> MaterialTheme.colorScheme.surfaceContainerLow
        else -> MaterialTheme.colorScheme.surfaceContainerLowest
    }
    val contentColor = if (highlight) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val borderColor = if (highlight) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
    }

    if (onClick != null) {
        val interaction = remember { MutableInteractionSource() }
        Surface(
            onClick = onClick,
            interactionSource = interaction,
            modifier = modifier
                .height(36.dp)
                .animatedPressScale(interaction, pressedScale = 0.94f),
            shape = RoundedCornerShape(50),
            color = containerColor,
            contentColor = contentColor,
            border = BorderStroke(0.75.dp, borderColor),
            shadowElevation = if (isDark) 0.dp else 1.5.dp,
        ) {
            Box(
                modifier = Modifier.padding(horizontal = 13.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (highlight) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    } else {
        Surface(
            modifier = modifier.height(36.dp),
            shape = RoundedCornerShape(50),
            color = containerColor,
            contentColor = contentColor,
            border = BorderStroke(0.75.dp, borderColor),
            shadowElevation = if (isDark) 0.dp else 1.5.dp,
        ) {
            Box(
                modifier = Modifier.padding(horizontal = 13.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (highlight) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Animazione tattile di pressione (spring bounce) riutilizzabile su pulsanti, card e pillole.
 */
@Composable
fun Modifier.animatedPressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.94f,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "press_scale",
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Pulsante circolare flottante stile Claude (bianco caldo con bordo sottile, ombra leggera e bounce tattile).
 */
@Composable
fun FloatingCircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
    iconSize: Dp = 20.dp,
    containerColor: Color = if (isDarkSurfaceTheme()) {
        MaterialTheme.colorScheme.surfaceContainerLow
    } else {
        MaterialTheme.colorScheme.surfaceContainerLowest
    },
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
    elevation: Dp = if (isDarkSurfaceTheme()) 0.dp else 2.dp,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = modifier
            .size(size)
            .animatedPressScale(interactionSource, pressedScale = 0.90f),
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(0.75.dp, borderColor),
        shadowElevation = elevation,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = contentColor,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}

/**
 * Pillola doppia flottante in alto a destra (es. Ricerca + Toggle Casa/Ristorante o Filtri),
 * separata da una hairline verticale discreta.
 */
@Composable
fun FloatingSplitPillActions(
    leftIcon: ImageVector,
    leftDescription: String,
    onLeftClick: () -> Unit,
    rightIcon: ImageVector,
    rightDescription: String,
    onRightClick: () -> Unit,
    modifier: Modifier = Modifier,
    leftActive: Boolean = false,
    rightActive: Boolean = false,
    activeColor: Color = MaterialTheme.colorScheme.primary,
) {
    val isDark = isDarkSurfaceTheme()
    val containerColor = if (isDark) {
        MaterialTheme.colorScheme.surfaceContainerLow
    } else {
        MaterialTheme.colorScheme.surfaceContainerLowest
    }
    val leftTint by animateColorAsState(
        targetValue = if (leftActive) activeColor else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(160),
        label = "split_left_tint",
    )
    val rightTint by animateColorAsState(
        targetValue = if (rightActive) activeColor else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(160),
        label = "split_right_tint",
    )

    Surface(
        modifier = modifier.height(42.dp),
        shape = RoundedCornerShape(50),
        color = containerColor,
        border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
        shadowElevation = if (isDark) 0.dp else 2.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(width = 44.dp, height = 42.dp)
                    .clickable(onClick = onLeftClick)
                    .semantics {
                        role = Role.Button
                        contentDescription = leftDescription
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = leftIcon,
                    contentDescription = leftDescription,
                    tint = leftTint,
                    modifier = Modifier.size(20.dp),
                )
            }
            Box(
                modifier = Modifier
                    .width(0.75.dp)
                    .height(20.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
            )
            Box(
                modifier = Modifier
                    .size(width = 44.dp, height = 42.dp)
                    .clickable(onClick = onRightClick)
                    .semantics {
                        role = Role.Button
                        contentDescription = rightDescription
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = rightIcon,
                    contentDescription = rightDescription,
                    tint = rightTint,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/**
 * Titolo sezione discreto in stile editoriale (`In evidenza`, `Recenti`, `Oggi`).
 */
@Composable
fun EditorialSectionLabel(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!actionLabel.isNullOrBlank() && onAction != null) {
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onAction)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

/**
 * Riga pulita senza card pesanti (stile liste Projects / Code / Search di Claude),
 * con icona opzionale, titolo + sottotitolo/contenuto e metadati a destra.
 */
@Composable
fun FramelessListRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    showDivider: Boolean = true,
    dividerStartPadding: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 13.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (leading != null) {
                leading()
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
                content = content,
            )
            if (trailing != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    content = trailing,
                )
            }
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = dividerStartPadding),
                thickness = 0.75.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
            )
        }
    }
}

/**
 * Barra di ricerca a pillola con pulsante indietro circolare, ispirata alla ricerca live di Claude.
 */
@Composable
fun EditorialSearchBarRow(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    trailingAction: (@Composable () -> Unit)? = null,
) {
    val isDark = isDarkSurfaceTheme()
    val pillBg = if (isDark) {
        MaterialTheme.colorScheme.surfaceContainerLow
    } else {
        MaterialTheme.colorScheme.surfaceContainerLowest
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (onBack != null) {
            FloatingCircleIconButton(
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Indietro",
                onClick = onBack,
            )
        }

        Surface(
            modifier = Modifier
                .weight(1f)
                .height(44.dp),
            shape = RoundedCornerShape(50),
            color = pillBg,
            border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
            shadowElevation = if (isDark) 0.dp else 1.5.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(19.dp),
                )
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (query.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (query.isNotEmpty()) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Cancella ricerca",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .clickable { onQueryChange("") }
                            .padding(1.dp),
                    )
                }
            }
        }

        if (trailingAction != null) {
            trailingAction()
        }
    }
}

/**
 * Evidenzia in grassetto tutte le occorrenze case-insensitive di [query] in [text],
 * nello stile della ricerca live di Claude.
 */
fun buildHighlightedMatchText(
    text: String,
    query: String,
    highlightColor: Color,
    baseColor: Color,
): AnnotatedString {
    val cleanQuery = query.trim()
    if (cleanQuery.isEmpty()) {
        return buildAnnotatedString {
            withStyle(SpanStyle(color = baseColor, fontWeight = FontWeight.Normal)) {
                append(text)
            }
        }
    }
    val lowerText = text.lowercase()
    val lowerQuery = cleanQuery.lowercase()
    return buildAnnotatedString {
        var cursor = 0
        while (cursor < text.length) {
            val matchIndex = lowerText.indexOf(lowerQuery, startIndex = cursor)
            if (matchIndex < 0) {
                withStyle(SpanStyle(color = baseColor, fontWeight = FontWeight.Normal)) {
                    append(text.substring(cursor))
                }
                break
            }
            if (matchIndex > cursor) {
                withStyle(SpanStyle(color = baseColor, fontWeight = FontWeight.Normal)) {
                    append(text.substring(cursor, matchIndex))
                }
            }
            val matchEnd = (matchIndex + lowerQuery.length).coerceAtMost(text.length)
            withStyle(SpanStyle(color = highlightColor, fontWeight = FontWeight.Bold)) {
                append(text.substring(matchIndex, matchEnd))
            }
            cursor = matchEnd
        }
    }
}

/**
 * Indicatore inline a puntini e stanghetta verticale (`· | · · ·`) per l'effort AI
 * dentro la riga del selettore modello.
 */
@Composable
fun EffortDotsIndicator(
    efforts: List<AiEffort>,
    selectedEffort: AiEffort,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.onSurface,
    inactiveColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
) {
    if (efforts.isEmpty()) return
    val activeIdx = efforts.indexOf(selectedEffort).coerceAtLeast(0)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        efforts.forEachIndexed { index, _ ->
            if (index == activeIdx) {
                Box(
                    modifier = Modifier
                        .width(2.2.dp)
                        .height(12.dp)
                        .clip(RoundedCornerShape(50))
                        .background(activeColor),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(3.5.dp)
                        .clip(CircleShape)
                        .background(inactiveColor),
                )
            }
        }
    }
}

/**
 * Slider tattile a pillola per l'Effort AI ispirato al selettore Effort di Claude:
 * traccia arrotondata spessa con tacche discrete, riempimento scuro animato con maniglia verticale,
 * supporto sia a tap diretto che a trascinamento continuo con feedback aptico.
 */
@Composable
fun TactilePillEffortSlider(
    efforts: List<AiEffort>,
    selectedEffort: AiEffort,
    onSelectEffort: (AiEffort) -> Unit,
    modifier: Modifier = Modifier,
    showHeaderLabel: Boolean = true,
    showDescription: Boolean = true,
    labelForEffort: (AiEffort) -> String = { it.label },
    descriptionForEffort: (AiEffort) -> String = { it.description },
) {
    if (efforts.isEmpty()) return
    val haptic = LocalHapticFeedback.current
    val activeIndex = efforts.indexOf(selectedEffort).let { idx ->
        if (idx >= 0) idx else when (selectedEffort) {
            AiEffort.OFF, AiEffort.LOW -> 0
            AiEffort.HIGH, AiEffort.MAX -> efforts.lastIndex
            AiEffort.MEDIUM -> efforts.size / 2
        }
    }
    val currentEffort = efforts[activeIndex]

    var trackWidthPx by remember { mutableFloatStateOf(1f) }
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    val stepCount = efforts.size
    val targetFraction = if (stepCount <= 1) 1f else activeIndex.toFloat() / (stepCount - 1).toFloat()
    val animatedFraction by animateFloatAsState(
        targetValue = if (isDragging) dragFraction else targetFraction,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "effort_pill_fraction",
    )

    val trackColor = MaterialTheme.colorScheme.surfaceContainer
    val fillColor = MaterialTheme.colorScheme.inverseSurface
    val onFillColor = MaterialTheme.colorScheme.inverseOnSurface
    val dotInactiveColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)

    fun indexForOffsetX(x: Float): Int {
        if (stepCount <= 1 || trackWidthPx <= 0f) return 0
        val pad = trackWidthPx * 0.10f
        val usable = (trackWidthPx - pad * 2f).coerceAtLeast(1f)
        val normalized = ((x - pad) / usable).coerceIn(0f, 1f)
        return (normalized * (stepCount - 1)).roundToInt().coerceIn(0, stepCount - 1)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (showHeaderLabel) {
            AnimatedContent(
                targetState = labelForEffort(currentEffort),
                transitionSpec = {
                    (fadeIn(tween(150)) + slideInVertically(tween(150)) { -it / 3 }) togetherWith
                        (fadeOut(tween(110)) + slideOutVertically(tween(110)) { it / 3 })
                },
                label = "effort_header_anim",
            ) { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .clip(RoundedCornerShape(50))
                .onSizeChanged { trackWidthPx = it.width.toFloat().coerceAtLeast(1f) }
                .pointerInput(efforts, activeIndex) {
                    detectTapGestures { offset ->
                        val nextIdx = indexForOffsetX(offset.x)
                        if (nextIdx != activeIndex) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelectEffort(efforts[nextIdx])
                        }
                    }
                }
                .pointerInput(efforts) {
                    var lastIdx = activeIndex
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            val pad = trackWidthPx * 0.10f
                            val usable = (trackWidthPx - pad * 2f).coerceAtLeast(1f)
                            dragFraction = ((offset.x - pad) / usable).coerceIn(0f, 1f)
                            val nextIdx = indexForOffsetX(offset.x)
                            lastIdx = nextIdx
                            if (nextIdx != activeIndex) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelectEffort(efforts[nextIdx])
                            }
                        },
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            val pad = trackWidthPx * 0.10f
                            val usable = (trackWidthPx - pad * 2f).coerceAtLeast(1f)
                            dragFraction = ((change.position.x - pad) / usable).coerceIn(0f, 1f)
                            val nextIdx = indexForOffsetX(change.position.x)
                            if (nextIdx != lastIdx) {
                                lastIdx = nextIdx
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelectEffort(efforts[nextIdx])
                            }
                        },
                    )
                },
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val h = size.height
                val w = size.width
                val radius = h / 2f
                val horizontalInset = radius * 0.92f
                val usableWidth = (w - horizontalInset * 2f).coerceAtLeast(1f)

                // Traccia completa a pillola
                drawRoundRect(
                    color = trackColor,
                    topLeft = Offset.Zero,
                    size = size,
                    cornerRadius = CornerRadius(radius, radius),
                )

                // Posizione centro del livello corrente
                val activeCenterX = if (stepCount <= 1) {
                    w - horizontalInset
                } else {
                    horizontalInset + usableWidth * animatedFraction.coerceIn(0f, 1f)
                }
                val fillRight = (activeCenterX + radius * 0.78f).coerceIn(h, w)

                // Pillola attiva scura
                drawRoundRect(
                    color = fillColor,
                    topLeft = Offset.Zero,
                    size = Size(fillRight, h),
                    cornerRadius = CornerRadius(radius, radius),
                )

                // Tacche circolari discrete
                val dotRadius = 2.6.dp.toPx()
                for (i in 0 until stepCount) {
                    val frac = if (stepCount <= 1) 1f else i.toFloat() / (stepCount - 1).toFloat()
                    val dotX = horizontalInset + usableWidth * frac
                    val isUnderFill = dotX < fillRight - radius * 0.52f
                    val isNearHandle = kotlin.math.abs(dotX - (fillRight - radius * 0.42f)) < 10.dp.toPx()
                    if (!isNearHandle) {
                        drawCircle(
                            color = if (isUnderFill) onFillColor.copy(alpha = 0.55f) else dotInactiveColor,
                            radius = dotRadius,
                            center = Offset(dotX, h / 2f),
                        )
                    }
                }

                // Stanghetta verticale pulita sulla punta destra della pillola attiva
                val barWidth = 2.6.dp.toPx()
                val barHeight = 20.dp.toPx()
                val barX = (fillRight - radius * 0.48f - barWidth / 2f).coerceIn(radius * 0.5f, w - radius * 0.45f)
                drawRoundRect(
                    color = onFillColor,
                    topLeft = Offset(barX, (h - barHeight) / 2f),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
                )
            }
        }

        // Etichette discrete sottostanti per immediata leggibilità
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            efforts.forEachIndexed { idx, option ->
                val isCurrent = idx == activeIndex
                Text(
                    text = labelForEffort(option),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    color = if (isCurrent) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            if (idx != activeIndex) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelectEffort(option)
                            }
                        }
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
        }

        if (showDescription) {
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = descriptionForEffort(currentEffort),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
    }
}
