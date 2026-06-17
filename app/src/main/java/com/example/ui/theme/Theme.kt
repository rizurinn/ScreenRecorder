package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val StudioDarkColorScheme = darkColorScheme(
    primary = StudioRed,
    secondary = ElectricBlue,
    tertiary = BrightOrange,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    onBackground = OnDarkBackground,
    onSurface = OnDarkSurface
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Default to true for the dark studio recording vibe!
    dynamicColor: Boolean = false, // Set to false to preserve our custom premium branding palette
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) StudioDarkColorScheme else StudioDarkColorScheme // Solidifying dark motif 

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
