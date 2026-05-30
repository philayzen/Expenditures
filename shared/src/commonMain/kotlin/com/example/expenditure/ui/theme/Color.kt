package com.example.expenditure.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Design tokens ported verbatim from the Python/Tailwind app's `:root` palette (static/css/style.css).
 * An earthy "moss green / cream / sand / charcoal" scheme; the brand identity of the original site.
 */
internal object Tokens {
    val Moss = Color(0xFF3D5A3E)
    val MossLight = Color(0xFF5A7F5B)
    val MossMid = Color(0xFF8AAB8B)
    val MossDark = Color(0xFF2A3E2B)
    val Cream = Color(0xFFF5F0E8)
    val Warm = Color(0xFFEDE5D5)
    val Sand = Color(0xFFC9B99A)
    val SandLight = Color(0xFFE2D5C0)
    val Charcoal = Color(0xFF1E2420)
    val Amber = Color(0xFFA07850)
    val White = Color(0xFFFFFFFF)

    /** Muted moss for secondary text/labels (`onSurfaceVariant`); softer than full Moss for body copy. */
    val MossMuted = Color(0xFF4C5A4D)
}

/**
 * Categorical palette for charts (doughnut slices, bar columns). Ported from the JS chart palette so
 * the desktop/Android charts read the same as the original web charts.
 */
val ChartPalette = listOf(
    Tokens.Moss, Tokens.MossLight, Tokens.Sand, Tokens.Charcoal,
    Tokens.MossMid, Tokens.Amber, Tokens.SandLight, Tokens.MossDark,
)
