@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

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

/** Google Sans Flex (Google Fonts, licenza OFL), in tutta l'app tranne le pagine degli artisti. */
val Outfit = FontFamily(
    Font(R.font.gsf_light, FontWeight.Light),
    Font(R.font.gsf_regular, FontWeight.Normal),
    Font(R.font.gsf_medium, FontWeight.Medium),
    Font(R.font.gsf_semibold, FontWeight.SemiBold),
    Font(R.font.gsf_bold, FontWeight.Bold),
    Font(R.font.gsf_extrabold, FontWeight.ExtraBold)
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
    val colors = styleScheme(context, dark)

    MaterialTheme(
        colorScheme = colors,
        shapes = AppStyle.shapes,
        typography = SonoraTypography
    ) {
        // Superficie di base: il colore del testo segue sempre lo sfondo (nero su chiaro, bianco su scuro).
        androidx.compose.material3.Surface(
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
            content = content
        )
    }
}
