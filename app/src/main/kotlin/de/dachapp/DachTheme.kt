package de.dachapp

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Gemeinsames Aussehen der Dach-App: Farben aus [grundfarbe] (Übersicht: Farbe der Variante,
 * Unter-App: Farbe ihres Icons), runde Formen und eine runde Schrift (Nunito) für Überschriften und Knöpfe.
 * Fließtext bleibt in der Systemschrift, damit er gut lesbar bleibt.
 */
@Composable
fun DachTheme(grundfarbe: Color, content: @Composable () -> Unit) {
    val dunkel = isSystemInDarkTheme()
    val farben = remember(grundfarbe, dunkel) { Farbschema.aus(grundfarbe, dunkel) }
    MaterialTheme(colorScheme = farben, shapes = Formen, typography = Schrift, content = content)
}

private val Nunito = FontFamily(
    Font(R.font.nunito_semibold, FontWeight.SemiBold),
    Font(R.font.nunito_bold, FontWeight.Bold),
    Font(R.font.nunito_extrabold, FontWeight.ExtraBold),
)

private val Formen = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

private val Schrift = Typography().run {
    fun TextStyle.rund(gewicht: FontWeight) = copy(fontFamily = Nunito, fontWeight = gewicht)
    copy(
        displayLarge = displayLarge.rund(FontWeight.Bold),
        displayMedium = displayMedium.rund(FontWeight.Bold),
        displaySmall = displaySmall.rund(FontWeight.Bold),
        headlineLarge = headlineLarge.rund(FontWeight.ExtraBold),
        headlineMedium = headlineMedium.rund(FontWeight.ExtraBold),
        headlineSmall = headlineSmall.rund(FontWeight.Bold),
        titleLarge = titleLarge.rund(FontWeight.Bold),
        titleMedium = titleMedium.rund(FontWeight.Bold),
        titleSmall = titleSmall.rund(FontWeight.Bold),
        // Knöpfe und Chips rund, aber nur halbfett: Nunito läuft etwas breiter als die Systemschrift.
        // Kleine Beschriftungen (Navigationsleiste) bleiben in der Systemschrift und etwas enger gesetzt,
        // damit lange Wörter wie „Einladungen“ nicht umbrechen.
        labelLarge = labelLarge.rund(FontWeight.SemiBold),
        labelMedium = labelMedium.copy(letterSpacing = 0.sp),
    )
}
