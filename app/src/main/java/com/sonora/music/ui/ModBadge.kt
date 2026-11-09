package com.sonora.music.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Livelli dei moderatori in base a da quanto tempo lo sono:
 * 0 Nuovo (<1 mese) · 1 Germoglio (1 mese) · 2 Bronzo (3 mesi) · 3 Argento (6 mesi) · 4 Oro (1 anno)
 * · 5 Zaffiro (2 anni) · 6 Ametista (5 anni) · 7 Rubino (10 anni) · 8 Senior (oltre 10 anni, da 11 in poi).
 */
fun modTier(since: Long, now: Long = System.currentTimeMillis()): Int {
    val days = (now - since) / 86_400_000.0
    return when {
        days >= 365.25 * 11 -> 8
        days >= 365.25 * 10 -> 7
        days >= 365.25 * 5 -> 6
        days >= 365.25 * 2 -> 5
        days >= 365.25 -> 4
        days >= 182.6 -> 3
        days >= 91.3 -> 2
        days >= 30.4 -> 1
        else -> 0
    }
}

val MOD_TIER_NAMES = listOf("Nuovo", "Germoglio", "Bronzo", "Argento", "Oro", "Zaffiro", "Ametista", "Rubino", "Senior")

private val TIER_COLORS = listOf(
    Color(0xFF78909C), Color(0xFF66BB6A), Color(0xFFCD7F32), Color(0xFFB0BEC5), Color(0xFFFFC107),
    Color(0xFF2962FF), Color(0xFF8E24AA), Color(0xFFD50057), Color(0xFF212121)
)

private fun DrawScope.shield(color: Color) {
    val w = size.width; val h = size.height
    val p = Path().apply {
        moveTo(w * 0.5f, h * 0.04f)
        lineTo(w * 0.92f, h * 0.2f)
        lineTo(w * 0.92f, h * 0.52f)
        cubicTo(w * 0.92f, h * 0.76f, w * 0.7f, h * 0.9f, w * 0.5f, h * 0.98f)
        cubicTo(w * 0.3f, h * 0.9f, w * 0.08f, h * 0.76f, w * 0.08f, h * 0.52f)
        lineTo(w * 0.08f, h * 0.2f)
        close()
    }
    drawPath(p, color)
}

private fun DrawScope.chevron(cx: Float, y: Float, half: Float, thick: Float) {
    val p = Path().apply {
        moveTo(cx - half, y + half * 0.5f); lineTo(cx, y - half * 0.4f); lineTo(cx + half, y + half * 0.5f)
    }
    drawPath(p, Color.White, style = Stroke(thick, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
}

private fun DrawScope.star(cx: Float, cy: Float, r: Float, color: Color) {
    val p = Path()
    for (i in 0 until 10) {
        val a = -Math.PI / 2 + i * Math.PI / 5
        val rr = if (i % 2 == 0) r else r * 0.45f
        val x = cx + (rr * Math.cos(a)).toFloat(); val y = cy + (rr * Math.sin(a)).toFloat()
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    p.close()
    drawPath(p, color)
}

/** Piccolo scudo del moderatore: colore e simbolo cambiano con l'anzianità. role = "admin" o "mod". */
@Composable
fun ModBadge(role: String, since: Long, size: Dp = 16.dp, modifier: Modifier = Modifier) {
    val tier = if (role == "admin") -1 else modTier(since)
    Canvas(modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val cx = w / 2f; val cy = h * 0.5f
        val thick = w * 0.09f
        when (tier) {
            -1 -> { // admin: scudo arancione con stella e anello chiaro
                shield(Color(0xFFFF6D00))
                drawCircle(Color.White.copy(alpha = 0.35f), radius = w * 0.3f, center = Offset(cx, cy), style = Stroke(thick * 0.7f))
                star(cx, cy, w * 0.22f, Color.White)
            }
            else -> {
                shield(TIER_COLORS[tier])
                if (tier == 8) drawCircle(Color(0xFFFFD54F), radius = w * 0.4f, center = Offset(cx, cy), style = Stroke(thick * 0.8f))
                when (tier) {
                    0 -> drawCircle(Color.White, radius = w * 0.11f, center = Offset(cx, cy))
                    1 -> { // foglia
                        val p = Path().apply {
                            moveTo(cx - w * 0.16f, cy + w * 0.16f)
                            quadraticBezierTo(cx - w * 0.16f, cy - w * 0.2f, cx + w * 0.18f, cy - w * 0.2f)
                            quadraticBezierTo(cx + w * 0.18f, cy + w * 0.16f, cx - w * 0.16f, cy + w * 0.16f)
                            close()
                        }
                        drawPath(p, Color.White)
                    }
                    2 -> chevron(cx, cy, w * 0.17f, thick)
                    3 -> { chevron(cx, cy - h * 0.09f, w * 0.17f, thick); chevron(cx, cy + h * 0.1f, w * 0.17f, thick) }
                    4 -> { chevron(cx, cy - h * 0.15f, w * 0.17f, thick); chevron(cx, cy, w * 0.17f, thick); chevron(cx, cy + h * 0.15f, w * 0.17f, thick) }
                    5 -> star(cx, cy, w * 0.24f, Color.White)
                    6 -> { // diamante
                        val r = w * 0.22f
                        val p = Path().apply { moveTo(cx, cy - r); lineTo(cx + r, cy); lineTo(cx, cy + r); lineTo(cx - r, cy); close() }
                        drawPath(p, Color.White)
                    }
                    else -> { // corona
                        val r = w * 0.24f
                        val p = Path().apply {
                            moveTo(cx - r, cy + r * 0.7f); lineTo(cx - r, cy - r * 0.5f); lineTo(cx - r * 0.5f, cy + r * 0.05f)
                            lineTo(cx, cy - r * 0.7f); lineTo(cx + r * 0.5f, cy + r * 0.05f); lineTo(cx + r, cy - r * 0.5f)
                            lineTo(cx + r, cy + r * 0.7f); close()
                        }
                        drawPath(p, if (tier == 8) Color(0xFFFFD54F) else Color.White)
                    }
                }
            }
        }
    }
}
