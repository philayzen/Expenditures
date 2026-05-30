package com.example.expenditure.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily

/**
 * Material3 light scheme mapping the [Tokens] palette onto color roles. The original web design is a
 * single light/warm theme (no dark mode), so [ExpenditureTheme] always applies this regardless of the
 * platform's dark-mode setting — the earthy palette *is* the brand.
 */
private val ExpenditureLightColors = lightColorScheme(
    primary = Tokens.Moss,
    onPrimary = Tokens.Cream,
    primaryContainer = Tokens.MossMid,
    onPrimaryContainer = Tokens.MossDark,
    secondary = Tokens.Amber,
    onSecondary = Tokens.Cream,
    secondaryContainer = Tokens.SandLight,
    onSecondaryContainer = Tokens.Charcoal,
    tertiary = Tokens.MossLight,
    onTertiary = Tokens.Cream,
    tertiaryContainer = Tokens.MossMid,
    onTertiaryContainer = Tokens.MossDark,
    background = Tokens.Cream,
    onBackground = Tokens.Charcoal,
    surface = Tokens.Cream,
    onSurface = Tokens.Charcoal,
    surfaceVariant = Tokens.Warm,
    onSurfaceVariant = Tokens.MossMuted,
    outline = Tokens.Sand,
    outlineVariant = Tokens.SandLight,
    inverseSurface = Tokens.Charcoal,
    inverseOnSurface = Tokens.Cream,
    inversePrimary = Tokens.MossMid,
    surfaceTint = Tokens.Moss,
)

/**
 * Echoes the web split of "Sora for headings, DM Mono for data" without bundling font binaries: data
 * rows and numeric cells lean on the built-in monospace family, while titles keep the default sans.
 */
private val ExpenditureTypography: Typography
    @Composable get() {
        val base = MaterialTheme.typography
        fun TextStyle.mono() = copy(fontFamily = FontFamily.Monospace)
        return base.copy(
            bodyMedium = base.bodyMedium.mono(),
            bodySmall = base.bodySmall.mono(),
            labelMedium = base.labelMedium.mono(),
            labelSmall = base.labelSmall.mono(),
        )
    }

@Composable
fun ExpenditureTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ExpenditureLightColors,
        typography = ExpenditureTypography,
        content = content,
    )
}
