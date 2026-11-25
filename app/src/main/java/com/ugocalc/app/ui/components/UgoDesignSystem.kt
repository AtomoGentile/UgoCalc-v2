package com.ugocalc.app.ui.components

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ugocalc.app.domain.glycemic.GlycemicCurveCalculator
import com.ugocalc.app.domain.glycemic.GlycemicLoadLevel
import com.ugocalc.app.domain.model.AiModel
import com.ugocalc.app.domain.model.CalculatedFoodLine
import com.ugocalc.app.ui.theme.UgoSemanticColors
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun isDarkSurfaceTheme(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.45f

@Composable
fun UgoCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = MaterialTheme.shapes.large,
    containerColor: Color = if (isDarkSurfaceTheme()) {
        MaterialTheme.colorScheme.surfaceContainerLow
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    },
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.50f),
    elevation: Dp = 0.dp,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val border = BorderStroke(1.dp, borderColor)
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            color = containerColor,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = border,
            shadowElevation = elevation,
        ) {
            Column(modifier = Modifier.padding(contentPadding), content = content)
        }
    } else {
        Surface(
            modifier = modifier,
            shape = shape,
            color = containerColor,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = border,
            shadowElevation = elevation,
        ) {
            Column(modifier = Modifier.padding(contentPadding), content = content)
        }
    }
}

@Composable
fun UgoSectionHeader(
    title: String,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (!actionLabel.isNullOrBlank() && onAction != null) {
            TextButton(onClick = onAction) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Formatta un valore di macronutrienti in grammi in modo compatto e coerente in tutta l'app:
 * elimina gli zeri decimali superflui (es. `21,0` -> `21g`, `19,9` -> `19,9g`, `124,3` -> `124g`).
 */
fun formatCompactGrams(value: Double): String {
    if (!value.isFinite() || value <= 0.0) return "0g"
    if (value >= 100.0) return "${value.roundToInt()}g"
    val roundedTenths = (value * 10.0).roundToInt()
    return if (roundedTenths % 10 == 0) {
        "${roundedTenths / 10}g"
    } else {
        "${"%.1f".format(Locale.ITALIAN, roundedTenths / 10.0)}g"
    }
}

/** Normalizza i titoli dei pasti per la visualizzazione (iniziale maiuscola e fallback pulito). */
fun formatMealDisplayTitle(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return "Pasto"
    return trimmed.replaceFirstChar { ch ->
        if (ch.isLowerCase()) ch.titlecase(Locale.ITALIAN) else ch.toString()
    }
}

@Composable
fun MacroPill(
    label: String,
    value: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Surface(
        modifier = modifier,
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(50),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (compact) 7.dp else 10.dp,
                vertical = if (compact) 3.dp else 4.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 4.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.85f),
                maxLines = 1,
                softWrap = false,
            )
            Text(
                text = value,
                style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = contentColor,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
fun MacroMetricTile(
    label: String,
    value: String,
    unit: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = accentColor.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.28f)),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(accentColor),
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (unit.isNotBlank()) {
                    Text(
                        text = unit,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 3.dp),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

enum class BannerTone { INFO, SUCCESS, WARNING, ERROR }

@Composable
fun StatusBanner(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tone: BannerTone,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val (container, accent, contentColor) = when (tone) {
        BannerTone.INFO -> Triple(
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.onSurface,
        )
        BannerTone.SUCCESS -> Triple(
            UgoSemanticColors.successContainer.copy(alpha = 0.72f),
            UgoSemanticColors.success,
            UgoSemanticColors.onSuccessContainer,
        )
        BannerTone.WARNING -> Triple(
            UgoSemanticColors.warningContainer.copy(alpha = 0.75f),
            UgoSemanticColors.warning,
            UgoSemanticColors.onWarningContainer,
        )
        BannerTone.ERROR -> Triple(
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.78f),
            MaterialTheme.colorScheme.error,
            MaterialTheme.colorScheme.onErrorContainer,
        )
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = container,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.30f)),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.85f),
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            if (!actionLabel.isNullOrBlank() && onAction != null) {
                TextButton(
                    onClick = onAction,
                    colors = ButtonDefaults.textButtonColors(contentColor = accent),
                ) {
                    Text(
                        text = actionLabel,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
fun UgoConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    dismissLabel: String = "Annulla",
    isDestructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shape = RoundedCornerShape(12.dp),
                colors = if (isDestructive) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    )
                } else {
                    ButtonDefaults.buttonColors()
                },
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissLabel)
            }
        },
    )
}

@Composable
fun ModernModelCard(
    model: AiModel,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = isDarkSurfaceTheme()
    val containerColor = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (isDark) 0.35f else 0.48f)
        isDark -> MaterialTheme.colorScheme.surfaceContainerLow
        else -> MaterialTheme.colorScheme.surfaceContainerLowest
    }
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
    }
    val borderWidth = if (isSelected) 2.dp else 1.dp

    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 148.dp),
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(borderWidth, borderColor),
        shadowElevation = if (isSelected) 3.dp else 1.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                // Badge bianco arrotondato: mantiene leggibili loghi scuri anche in Dark mode
                Surface(
                    modifier = Modifier.size(56.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White,
                    border = BorderStroke(0.5.dp, Color.Black.copy(alpha = 0.08f)),
                    shadowElevation = 1.dp,
                ) {
                    ModelLogoBox(
                        logoRes = model.logoRes,
                        displayName = model.displayName,
                        subtitle = model.subtitle,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = model.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                if (model.subtitle.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        shape = RoundedCornerShape(50),
                    ) {
                        Text(
                            text = model.subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            }

            if (isSelected) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Selezionato",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(20.dp),
                )
            }
        }
    }
}

@Composable
fun BrandFilterRow(
    brands: List<String>,
    selectedBrand: String?,
    counts: Map<String, Int>,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val totalCount = counts.values.sum()
    val allLabel = if (counts.isNotEmpty()) "Tutti ($totalCount)" else "Tutti"

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 2.dp),
    ) {
        item(key = "__all__") {
            val isSelected = selectedBrand == null
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(null) },
                modifier = Modifier.widthIn(min = 56.dp),
                leadingIcon = if (isSelected) {
                    {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize),
                        )
                    }
                } else null,
                label = {
                    Text(
                        text = allLabel,
                        maxLines = 1,
                        softWrap = false,
                    )
                },
            )
        }
        items(brands, key = { it }) { brand ->
            val cleanBrand = brand.trim()
            val count = counts[brand] ?: counts[cleanBrand]
            val label = if (count != null) "$cleanBrand ($count)" else cleanBrand
            val isSelected = selectedBrand == brand || (selectedBrand != null && selectedBrand.trim() == cleanBrand)

            FilterChip(
                selected = isSelected,
                onClick = { onSelect(brand) },
                modifier = Modifier.widthIn(min = 56.dp),
                leadingIcon = if (isSelected) {
                    {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize),
                        )
                    }
                } else null,
                label = {
                    Text(
                        text = label,
                        maxLines = 1,
                        softWrap = false,
                    )
                },
            )
        }
    }
}

/**
 * Numero animato con `animateFloatAsState` per calorie e grammi di macronutrienti.
 */
@Composable
fun AnimatedMetricValue(
    targetValue: Float,
    modifier: Modifier = Modifier,
    decimals: Int = 1,
    prefix: String = "",
    suffix: String = "",
    style: TextStyle = MaterialTheme.typography.headlineSmall,
    fontWeight: FontWeight = FontWeight.Bold,
    color: Color = MaterialTheme.colorScheme.onSurface,
    maxLines: Int = 1,
) {
    val safeTarget = if (targetValue.isFinite()) targetValue.coerceAtLeast(0f) else 0f
    val animatedValue by animateFloatAsState(
        targetValue = safeTarget,
        animationSpec = tween(durationMillis = 460, easing = FastOutSlowInEasing),
        label = "animated_metric_value",
    )
    val formattedNumber = if (decimals <= 0) {
        animatedValue.roundToInt().toString()
    } else {
        "%.${decimals}f".format(Locale.ITALIAN, animatedValue)
    }
    Text(
        text = "$prefix$formattedNumber$suffix",
        modifier = modifier,
        style = style,
        fontWeight = fontWeight,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * Tile metrica con valore numerico animato per le schermate di riepilogo e anteprima live.
 */
@Composable
fun AnimatedMacroMetricTile(
    label: String,
    targetValue: Float,
    unit: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    decimals: Int = 1,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = accentColor.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.28f)),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(accentColor),
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                AnimatedMetricValue(
                    targetValue = targetValue,
                    decimals = decimals,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                )
                if (unit.isNotBlank()) {
                    Text(
                        text = unit,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 3.dp),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * Anello di avanzamento segmentato per kcal e distribuzione P/C/G su palette Emerald & Graphite.
 */
@Composable
fun MacroProgressRing(
    choG: Double,
    grassiG: Double,
    estimatedKcal: Int,
    modifier: Modifier = Modifier,
    proteineG: Double = 0.0,
    dailyKcalTarget: Int = 2000,
    size: Dp = 96.dp,
    strokeWidth: Dp = 10.dp,
) {
    val safeCho = choG.coerceAtLeast(0.0)
    val safeFat = grassiG.coerceAtLeast(0.0)
    val safeProt = proteineG.coerceAtLeast(0.0)

    val choEnergy = safeCho * 4.0
    val fatEnergy = safeFat * 9.0
    val protEnergy = safeProt * 4.0
    val macroEnergySum = choEnergy + fatEnergy + protEnergy

    val targetFill = if (estimatedKcal <= 0) {
        0f
    } else {
        (estimatedKcal.toFloat() / dailyKcalTarget.coerceAtLeast(1)).coerceIn(0.08f, 1f)
    }
    val animatedFill by animateFloatAsState(
        targetValue = targetFill,
        animationSpec = tween(durationMillis = 620, easing = FastOutSlowInEasing),
        label = "macro_ring_fill",
    )

    val choFraction = if (macroEnergySum > 0.0) (choEnergy / macroEnergySum).toFloat() else 0f
    val fatFraction = if (macroEnergySum > 0.0) (fatEnergy / macroEnergySum).toFloat() else 0f
    val protFraction = if (macroEnergySum > 0.0) (protEnergy / macroEnergySum).toFloat() else 0f

    val isDark = isDarkSurfaceTheme()
    val trackColor = if (isDark) {
        MaterialTheme.colorScheme.surfaceContainerHighest
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val choColor = UgoSemanticColors.cho
    val fatColor = UgoSemanticColors.fat
    val protColor = UgoSemanticColors.protein

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = strokeWidth.toPx()
            val inset = strokePx / 2f + 2.dp.toPx()
            val arcSize = Size(this.size.width - inset * 2f, this.size.height - inset * 2f)
            val topLeft = Offset(inset, inset)

            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
            )

            if (animatedFill > 0.005f && macroEnergySum > 0.0) {
                val totalSweep = (animatedFill * 360f).coerceAtLeast(6f)
                val segments = buildList {
                    if (choFraction > 0.01f) add(choFraction to choColor)
                    if (fatFraction > 0.01f) add(fatFraction to fatColor)
                    if (protFraction > 0.01f) add(protFraction to protColor)
                }
                val activeGap = if (segments.size > 1 && totalSweep > 24f) 4f else 0f
                val cap = if (segments.size == 1) StrokeCap.Round else StrokeCap.Butt

                var currentStart = -90f
                segments.forEach { (fraction, color) ->
                    val rawSweep = totalSweep * fraction
                    val drawSweep = (rawSweep - activeGap).coerceAtLeast(2f)
                    drawArc(
                        color = color,
                        startAngle = currentStart + activeGap / 2f,
                        sweepAngle = drawSweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokePx, cap = cap),
                    )
                    currentStart += rawSweep
                }
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedMetricValue(
                targetValue = estimatedKcal.toFloat(),
                decimals = 0,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "kcal",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Formatta un riepilogo testuale pulito in italiano del pasto per la condivisione rapida.
 */
fun formatMealShareSummary(
    title: String,
    totalChoG: Double,
    totalGrassiG: Double,
    lines: List<CalculatedFoodLine>,
    totalProteineG: Double? = null,
    estimatedKcal: Int = (totalChoG * 4.0 + totalGrassiG * 9.0 + (totalProteineG ?: 0.0) * 4.0).roundToInt(),
    glycemicLoad: Double? = null,
    sessionModeLabel: String? = null,
    dateLabel: String? = null,
): String = buildString {
    val cleanTitle = title.trim().ifBlank { "Pasto UgoCalc" }
    appendLine(cleanTitle)
    val metaParts = listOfNotNull(
        dateLabel?.takeIf { it.isNotBlank() },
        sessionModeLabel?.takeIf { it.isNotBlank() },
    )
    if (metaParts.isNotEmpty()) {
        appendLine(metaParts.joinToString(" · "))
    }
    appendLine()
    appendLine("Riepilogo nutrizionale:")
    appendLine("• Energia stimata: ~$estimatedKcal kcal")
    appendLine("• Carboidrati (CHO): ${formatShareNumber(totalChoG)} g")
    if (totalProteineG != null && totalProteineG > 0.0) {
        appendLine("• Proteine: ${formatShareNumber(totalProteineG)} g")
    }
    appendLine("• Grassi: ${formatShareNumber(totalGrassiG)} g")
    if (glycemicLoad != null && glycemicLoad > 0.0) {
        val levelLabel = when (GlycemicCurveCalculator.classifyGlycemicLoad(glycemicLoad)) {
            GlycemicLoadLevel.BASSO -> "Basso"
            GlycemicLoadLevel.MEDIO -> "Medio"
            GlycemicLoadLevel.ALTO -> "Alto"
        }
        appendLine("• Carico Glicemico: ${"%.1f".format(Locale.ITALIAN, glycemicLoad)} ($levelLabel)")
    }
    if (lines.isNotEmpty()) {
        appendLine()
        appendLine("Alimenti (${lines.size}):")
        lines.forEach { line ->
            val weightText = if (line.recognizedFood.isTrascurabile) {
                "trascurabile"
            } else {
                "${line.recognizedFood.pesoStimatoG.roundToInt()} g"
            }
            if (line.isExcluded) {
                appendLine("• ${line.recognizedFood.nome} ($weightText) — escluso")
            } else {
                val protPart = if (line.proteineContributionG > 0.0) {
                    " · Prot ${formatShareNumber(line.proteineContributionG)} g"
                } else {
                    ""
                }
                appendLine(
                    "• ${line.recognizedFood.nome} ($weightText) — CHO ${formatShareNumber(line.choContributionG)} g$protPart · Grassi ${formatShareNumber(line.grassiContributionG)} g"
                )
            }
        }
    }
    appendLine()
    append("Calcolato con UgoCalc")
}

private fun formatShareNumber(value: Double): String = when {
    value <= 0.0 -> "0"
    value >= 100.0 -> "%.0f".format(Locale.ITALIAN, value)
    else -> "%.1f".format(Locale.ITALIAN, value)
}

/**
 * Apre il foglio di condivisione di sistema con [text] e titolo [title].
 */
fun shareText(context: Context, title: String, text: String) {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    val chooser = Intent.createChooser(sendIntent, title).apply {
        if (context !is Activity) {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
    context.startActivity(chooser)
}
