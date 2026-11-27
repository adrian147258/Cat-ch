package com.meowclub.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.meowclub.app.data.AppearanceMode

private val LightColors = lightColorScheme(
    primary = Color(0xFFC86D51),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFBE8E0),
    onPrimaryContainer = Color(0xFF5C2615),
    secondary = Color(0xFF3E6B62),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE1EEEB),
    onSecondaryContainer = Color(0xFF1A3832),
    background = Color(0xFFFAF8F5),
    onBackground = Color(0xFF1C1A18),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C1A18),
    surfaceVariant = Color(0xFFF3EFE9),
    onSurfaceVariant = Color(0xFF6E6862),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F1EB),
    surfaceContainer = Color(0xFFEFEAE2),
    surfaceContainerHigh = Color(0xFFE8E2D9),
    surfaceContainerHighest = Color(0xFFE0D9CE),
    outline = Color(0xFFD0C8BD),
    outlineVariant = Color(0xFFE6E0D6),
    error = Color(0xFFC94A3D),
    onError = Color(0xFFFFFFFF)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFE69278),
    onPrimary = Color(0xFF2A120A),
    primaryContainer = Color(0xFF4A2519),
    onPrimaryContainer = Color(0xFFFFDAD0),
    secondary = Color(0xFF8EC5B9),
    onSecondary = Color(0xFF0E2924),
    secondaryContainer = Color(0xFF213C36),
    onSecondaryContainer = Color(0xFFD1EDE7),
    background = Color(0xFF111110),
    onBackground = Color(0xFFF4F1EC),
    surface = Color(0xFF1B1A18),
    onSurface = Color(0xFFF4F1EC),
    surfaceVariant = Color(0xFF252320),
    onSurfaceVariant = Color(0xFFA39C93),
    surfaceContainerLowest = Color(0xFF141312),
    surfaceContainerLow = Color(0xFF1B1A18),
    surfaceContainer = Color(0xFF22201D),
    surfaceContainerHigh = Color(0xFF2B2824),
    surfaceContainerHighest = Color(0xFF34312C),
    outline = Color(0xFF4E4942),
    outlineVariant = Color(0xFF2D2A26),
    error = Color(0xFFF28B82),
    onError = Color(0xFF3E110E)
)

val HeartActiveColor = Color(0xFFE05A47)
val BookmarkActiveColor = Color(0xFFC86D51)

@Composable
fun CatchTheme(
    appearance: AppearanceMode,
    content: @Composable () -> Unit
) {
    val darkTheme = when (appearance) {
        AppearanceMode.DARK -> true
        AppearanceMode.LIGHT -> false
        AppearanceMode.SYSTEM -> isSystemInDarkTheme()
    }
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = colorScheme.background.toArgb()
                window.navigationBarColor = colorScheme.background.toArgb()
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = GalleryTypography,
        content = content
    )
}
