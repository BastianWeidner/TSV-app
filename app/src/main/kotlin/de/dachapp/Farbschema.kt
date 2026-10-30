package de.dachapp

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

/**
 * Farben aus einer Grundfarbe (dem Icon einer Unter-App) ableiten, ohne Internet und ohne Bibliothek.
 * Wie bei Material 3 entstehen Tonleitern über die Helligkeit L* (CIELAB, 0 = schwarz, 100 = weiß);
 * Farbton und Sättigung bleiben die des Icons, soweit sie sich bei der Helligkeit darstellen lassen.
 */
object Farbschema {

    /** Farbe mit Farbton und Sättigung von [grund], aber der Helligkeit [ton] (0..100). */
    fun ton(grund: Color, ton: Float, saettigung: Float? = null): Color {
        val (_, c, h) = lch(grund)
        return aus(ton, saettigung ?: c, h)
    }

    /** Vollständiges Material-3-Farbschema mit [grund] als Primärfarbe, hell oder dunkel. */
    fun aus(grund: Color, dunkel: Boolean): ColorScheme {
        val (_, c, h) = lch(grund)
        // Primär: Sättigung des Icons, mindestens etwas Farbe. Sekundär gedämpft, Tertiär im Farbkreis versetzt.
        // Flächen leicht in der Icon-Farbe getönt, das wirkt wärmer als reines Grau.
        val p = Leiter(max(c, 24f), h)
        val s = Leiter((c / 3f).coerceIn(12f, 24f), h)
        val t = Leiter((c / 2f).coerceIn(16f, 36f), (h + 60f) % 360f)
        val n = Leiter(5f, h)
        val nv = Leiter(10f, h)

        return if (!dunkel) {
            lightColorScheme(
                primary = p(40), onPrimary = p(100), primaryContainer = p(90), onPrimaryContainer = p(10),
                inversePrimary = p(80),
                secondary = s(40), onSecondary = s(100), secondaryContainer = s(90), onSecondaryContainer = s(10),
                tertiary = t(40), onTertiary = t(100), tertiaryContainer = t(90), onTertiaryContainer = t(10),
                background = n(98), onBackground = n(10), surface = n(98), onSurface = n(10),
                surfaceVariant = nv(90), onSurfaceVariant = nv(30), surfaceTint = p(40),
                inverseSurface = n(20), inverseOnSurface = n(95),
                outline = nv(50), outlineVariant = nv(80),
                surfaceBright = n(98), surfaceDim = n(87),
                surfaceContainerLowest = n(100), surfaceContainerLow = n(96), surfaceContainer = n(94),
                surfaceContainerHigh = n(92), surfaceContainerHighest = n(90),
            )
        } else {
            darkColorScheme(
                primary = p(80), onPrimary = p(20), primaryContainer = p(30), onPrimaryContainer = p(90),
                inversePrimary = p(40),
                secondary = s(80), onSecondary = s(20), secondaryContainer = s(30), onSecondaryContainer = s(90),
                tertiary = t(80), onTertiary = t(20), tertiaryContainer = t(30), onTertiaryContainer = t(90),
                background = n(6), onBackground = n(90), surface = n(6), onSurface = n(90),
                surfaceVariant = nv(30), onSurfaceVariant = nv(80), surfaceTint = p(80),
                inverseSurface = n(90), inverseOnSurface = n(20),
                outline = nv(60), outlineVariant = nv(30),
                surfaceBright = n(24), surfaceDim = n(6),
                surfaceContainerLowest = n(4), surfaceContainerLow = n(10), surfaceContainer = n(12),
                surfaceContainerHigh = n(17), surfaceContainerHighest = n(22),
            )
        }
    }

    private class Leiter(val saettigung: Float, val farbton: Float) {
        operator fun invoke(ton: Int): Color = aus(ton.toFloat(), saettigung, farbton)
    }

    // --- CIELAB / LCh (Weißpunkt D65) ---

    private const val XN = 0.95047
    private const val ZN = 1.08883

    /** Helligkeit L*, Sättigung C* und Farbton h (Grad) einer sRGB-Farbe. */
    internal fun lch(farbe: Color): Triple<Float, Float, Float> {
        val r = linear(farbe.red.toDouble())
        val g = linear(farbe.green.toDouble())
        val b = linear(farbe.blue.toDouble())
        val x = (0.4124 * r + 0.3576 * g + 0.1805 * b) / XN
        val y = 0.2126 * r + 0.7152 * g + 0.0722 * b
        val z = (0.0193 * r + 0.1192 * g + 0.9505 * b) / ZN
        val fx = f(x)
        val fy = f(y)
        val fz = f(z)
        val l = 116 * fy - 16
        val a = 500 * (fx - fy)
        val bb = 200 * (fy - fz)
        val h = Math.toDegrees(atan2(bb, a)).let { if (it < 0) it + 360 else it }
        return Triple(l.toFloat(), hypot(a, bb).toFloat(), h.toFloat())
    }

    /** sRGB-Farbe zu L*, C*, h; ist die Sättigung so nicht darstellbar, wird sie verringert. */
    internal fun aus(l: Float, c: Float, h: Float): Color {
        if (l <= 0f) return Color.Black
        if (l >= 100f) return Color.White
        rgb(l, c, h)?.let { return it }
        var lo = 0f
        var hi = c
        repeat(16) {
            val mitte = (lo + hi) / 2
            if (rgb(l, mitte, h) != null) lo = mitte else hi = mitte
        }
        return rgb(l, lo, h) ?: Color.Gray
    }

    private fun rgb(l: Float, c: Float, h: Float): Color? {
        val rad = Math.toRadians(h.toDouble())
        val a = c * cos(rad)
        val b = c * sin(rad)
        val fy = (l + 16) / 116.0
        val fx = fy + a / 500
        val fz = fy - b / 200
        val x = finv(fx) * XN
        val y = finv(fy)
        val z = finv(fz) * ZN
        val r = 3.2406 * x - 1.5372 * y - 0.4986 * z
        val g = -0.9689 * x + 1.8758 * y + 0.0415 * z
        val bl = 0.0557 * x - 0.2040 * y + 1.0570 * z
        val toleranz = 1e-4
        if (listOf(r, g, bl).any { it < -toleranz || it > 1 + toleranz }) return null
        return Color(gamma(r), gamma(g), gamma(bl))
    }

    private fun linear(c: Double) = if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)

    private fun gamma(c: Double): Float {
        val v = c.coerceIn(0.0, 1.0)
        return (if (v <= 0.0031308) 12.92 * v else 1.055 * v.pow(1 / 2.4) - 0.055).toFloat()
    }

    private fun f(t: Double) = if (t > 0.008856) cbrt(t) else 7.787 * t + 16.0 / 116

    private fun finv(t: Double) = if (t * t * t > 0.008856) t * t * t else (t - 16.0 / 116) / 7.787
}
