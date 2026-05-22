package com.app.cade.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

val PrimaryBlue = Color(0xFF2563EB)
val PrimaryCyan = Color(0xFF06B6D4)
val PrimaryDark = Color(0xFF1E3A8A)

// Fundo Premium (Quase preto absoluto para telas OLED)
val BackgroundDark = Color(0xFF0A0A0A)
val SurfaceDark = Color(0xFF171717)

// Textos
val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFFA1A1AA)

// Outros
val DangerRed = Color(0xFFEF4444)
val SuccessGreen = Color(0xFF10B981)

// Efeitos Visuais (Vidro / Glassmorphism)
val GlassWhite = Color(0x0DFFFFFF) // Translúcido
val GlassBorder = Color(0x1AFFFFFF) // Borda para o vidro

// Gradiente Principal da Logo (Azul para Ciano)
val BrandGradient = Brush.linearGradient(
    colors = listOf(PrimaryBlue, PrimaryCyan),
    start = Offset(0f, 0f),
    end = Offset(100f, 100f) // Será dimensionado pelo componente
)
val BrandGradientHorizontal = Brush.horizontalGradient(
    colors = listOf(PrimaryBlue, PrimaryCyan)
)
