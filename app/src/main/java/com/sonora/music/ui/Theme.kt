package com.sonora.music.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.sonora.music.R

/** Outfit (Google Fonts, licenza OFL): font variabile, quindi ogni peso è nitido. */
val Outfit = FontFamily(
    listOf(
        FontWeight.Light to 300,
        FontWeight.Normal to 400,
        FontWeight.Medium to 500,
        FontWeight.SemiBold to 600,
        FontWeight.Bold to 700,
        FontWeight.ExtraBold to 800
    ).map { (weight, value) ->
        Font(
            resId = R.font.outfit,
            weight = weight,
            variationSettings = FontVariation.Settings(FontVariation.weight(value))
        )
    }
)

private fun TextStyle.outfit() = copy(fontFamily = Outfit)

private val SonoraTypography: Typography = Typography().let { t ->
    t.copy(
        displayLarge = t.displayLarge.outfit(),
        displayMedium = t.displayMedium.outfit(),
        displaySmall = t.displaySmall.outfit(),
        headlineLarge = t.headlineLarge.outfit(),
        headlineMedium = t.headlineMedium.outfit(),
        headlineSmall = t.headlineSmall.outfit(),
        titleLarge = t.titleLarge.outfit(),
        titleMedium = t.titleMedium.outfit(),
        titleSmall = t.titleSmall.outfit(),
        bodyLarge = t.bodyLarge.outfit(),
        bodyMedium = t.bodyMedium.outfit(),
        bodySmall = t.bodySmall.outfit(),
        labelLarge = t.labelLarge.outfit(),
        labelMedium = t.labelMedium.outfit(),
        labelSmall = t.labelSmall.outfit()
    )
}

/** Material 3 con colori dinamici (Material You) e font Outfit. Lo stile Expressive (molle, forme morbide) è nei componenti. */
@Composable
fun SonoraTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }

    MaterialTheme(
        colorScheme = colors,
        typography = SonoraTypography,
        content = content
    )
}
