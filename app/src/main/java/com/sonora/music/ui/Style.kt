package com.sonora.music.ui

import android.content.Context
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Stili grafici dell'app. Il predefinito è Material 3 Expressive (forme morbide e molle). */
enum class StyleId(val key: String, val label: String) {
    EXPRESSIVE("expressive", "Material 3 Expressive"),
    YOU("you", "Material You"),
    GLASS("glass", "Liquid Glass"),
    MIUIX("miuix", "Miuix")
}

object AppStyle {
    var id by mutableStateOf(StyleId.EXPRESSIVE)
        private set

    fun load(c: Context) {
        val k = c.getSharedPreferences("sonora", Context.MODE_PRIVATE).getString("style", null)
        id = StyleId.values().firstOrNull { it.key == k } ?: StyleId.EXPRESSIVE
    }

    fun set(c: Context, value: StyleId) {
        id = value
        c.getSharedPreferences("sonora", Context.MODE_PRIVATE).edit().putString("style", value.key).apply()
    }

    /** Lo stile più adatto al telefono: Miuix per Xiaomi/Redmi/POCO, Material You per Samsung, Expressive per gli altri. */
    fun suggested(): StyleId {
        val b = (Build.MANUFACTURER + " " + Build.BRAND).lowercase()
        return when {
            listOf("xiaomi", "redmi", "poco").any { it in b } -> StyleId.MIUIX
            "samsung" in b -> StyleId.YOU
            else -> StyleId.EXPRESSIVE
        }
    }

    val bouncy: Boolean get() = id == StyleId.EXPRESSIVE

    /** Raggio degli angoli delle schede. */
    val card: Dp
        get() = when (id) {
            StyleId.EXPRESSIVE -> 28.dp
            StyleId.YOU -> 20.dp
            StyleId.GLASS -> 32.dp
            StyleId.MIUIX -> 16.dp
        }

    /** Trasparenza delle schede. */
    val cardAlpha: Float
        get() = when (id) {
            StyleId.EXPRESSIVE -> 0.4f
            StyleId.YOU -> 0.6f
            StyleId.GLASS -> 0.22f
            StyleId.MIUIX -> 1f
        }

    val shapes: Shapes
        get() = when (id) {
            StyleId.EXPRESSIVE -> Shapes(
                extraSmall = RoundedCornerShape(10.dp), small = RoundedCornerShape(16.dp),
                medium = RoundedCornerShape(22.dp), large = RoundedCornerShape(28.dp), extraLarge = RoundedCornerShape(36.dp)
            )
            StyleId.YOU -> Shapes()
            StyleId.GLASS -> Shapes(
                extraSmall = RoundedCornerShape(14.dp), small = RoundedCornerShape(20.dp),
                medium = RoundedCornerShape(26.dp), large = RoundedCornerShape(32.dp), extraLarge = RoundedCornerShape(40.dp)
            )
            StyleId.MIUIX -> Shapes(
                extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp),
                medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(20.dp), extraLarge = RoundedCornerShape(24.dp)
            )
        }
}

/** Colori dello stile scelto: Miuix ha una tavolozza neutra con accento blu; gli altri seguono i colori del telefono. */
fun styleScheme(context: Context, dark: Boolean): ColorScheme {
    if (AppStyle.id == StyleId.MIUIX) {
        val blue = Color(0xFF3482FF)
        return if (dark) darkColorScheme(
            primary = blue, onPrimary = Color.White,
            primaryContainer = Color(0xFF1E3A66), onPrimaryContainer = Color(0xFFD7E6FF),
            secondary = Color(0xFF9DB7E0), secondaryContainer = Color(0xFF2C2C2E), onSecondaryContainer = Color.White,
            tertiaryContainer = Color(0xFF2C2C2E), onTertiaryContainer = Color.White,
            background = Color.Black, onBackground = Color.White,
            surface = Color(0xFF1C1C1E), onSurface = Color.White,
            surfaceVariant = Color(0xFF2C2C2E), onSurfaceVariant = Color(0xFF9B9B9B),
            surfaceContainer = Color(0xFF1C1C1E), surfaceContainerHigh = Color(0xFF2C2C2E), surfaceContainerHighest = Color(0xFF3A3A3C),
            outline = Color(0xFF5A5A5E)
        ) else lightColorScheme(
            primary = blue, onPrimary = Color.White,
            primaryContainer = Color(0xFFD7E6FF), onPrimaryContainer = Color(0xFF0B2A57),
            secondary = Color(0xFF5B7399), secondaryContainer = Color(0xFFFFFFFF), onSecondaryContainer = Color.Black,
            tertiaryContainer = Color(0xFFFFFFFF), onTertiaryContainer = Color.Black,
            background = Color(0xFFF7F7F7), onBackground = Color.Black,
            surface = Color.White, onSurface = Color.Black,
            surfaceVariant = Color(0xFFEDEDED), onSurfaceVariant = Color(0xFF6B6B6B),
            surfaceContainer = Color.White, surfaceContainerHigh = Color.White, surfaceContainerHighest = Color(0xFFEDEDED),
            outline = Color(0xFFC7C7CC)
        )
    }
    return when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
}
