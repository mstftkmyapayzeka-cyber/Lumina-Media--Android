package com.lumina.player.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.lumina.player.data.Accent
import com.lumina.player.data.ThemeMode

// Masaüstü sürümün tasarım token'ları (docs/ARCHITECTURE.md §5)
object LuminaColors {
    val BgBase = Color(0xFF0F1115)
    val BgRaised = Color(0xFF1A1D23)
    val Surface = Color(0xFF232730)
    val SurfaceHover = Color(0xFF2C313C)
    val Border = Color(0xFF2F3440)
    val TextPrimary = Color(0xFFF2F4F8)
    val TextSecondary = Color(0xFFA3AAB8)
    val TextMuted = Color(0xFF6B7280)
    val Overlay = Color(0xC70F1115)
    val Danger = Color(0xFFF0525F)

    val LightBase = Color(0xFFF5F6F8)
    val LightRaised = Color(0xFFFFFFFF)
    val LightSurface = Color(0xFFEEF0F3)
    val LightText = Color(0xFF14171C)
}

private val LuminaTypography = Typography(
    headlineMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 15.sp),
    bodyMedium = TextStyle(fontSize = 13.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun LuminaTheme(
    themeMode: ThemeMode,
    accent: Accent,
    forceDark: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = forceDark || when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val primary = Color(accent.argb)
    val scheme = if (dark) {
        darkColorScheme(
            primary = primary,
            onPrimary = Color.White,
            primaryContainer = primary.copy(alpha = 0.22f),
            onPrimaryContainer = LuminaColors.TextPrimary,
            secondary = primary,
            background = LuminaColors.BgBase,
            onBackground = LuminaColors.TextPrimary,
            surface = LuminaColors.BgRaised,
            onSurface = LuminaColors.TextPrimary,
            surfaceVariant = LuminaColors.Surface,
            onSurfaceVariant = LuminaColors.TextSecondary,
            surfaceContainer = LuminaColors.BgRaised,
            surfaceContainerHigh = LuminaColors.Surface,
            surfaceContainerHighest = LuminaColors.SurfaceHover,
            surfaceContainerLow = LuminaColors.BgRaised,
            outline = LuminaColors.Border,
            outlineVariant = LuminaColors.Border,
            error = LuminaColors.Danger,
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = Color.White,
            primaryContainer = primary.copy(alpha = 0.16f),
            onPrimaryContainer = LuminaColors.LightText,
            secondary = primary,
            background = LuminaColors.LightBase,
            onBackground = LuminaColors.LightText,
            surface = LuminaColors.LightRaised,
            onSurface = LuminaColors.LightText,
            surfaceVariant = LuminaColors.LightSurface,
            surfaceContainer = LuminaColors.LightRaised,
            surfaceContainerHigh = LuminaColors.LightSurface,
            error = LuminaColors.Danger,
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
            @Suppress("DEPRECATION")
            window.navigationBarColor = Color.Transparent.toArgb()
        }
    }

    MaterialTheme(colorScheme = scheme, typography = LuminaTypography, content = content)
}
