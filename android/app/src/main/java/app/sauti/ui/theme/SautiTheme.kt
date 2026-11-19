package app.sauti.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.sauti.R

/** "Ambient Sentience" design tokens (DESIGN.md). Dark only: an obsidian void with bioluminescent accents. */
object Sauti {
    val surface = Color(0xFF111318)
    val surfaceLowest = Color(0xFF0C0E13)
    val surfaceLow = Color(0xFF1A1B21)
    val surfaceContainer = Color(0xFF1E1F25)
    val surfaceHigh = Color(0xFF282A2F)
    val surfaceHighest = Color(0xFF33353A)
    val surfaceBright = Color(0xFF37393F)
    val onSurface = Color(0xFFE2E2E9)
    val onSurfaceVariant = Color(0xFFC9C4D8)
    val outline = Color(0xFF928EA1)
    val outlineVariant = Color(0xFF484555)
    val primary = Color(0xFFC9BFFF)          // lavender
    val onPrimary = Color(0xFF2E009C)
    val primaryContainer = Color(0xFF917EFF)
    val spectralViolet = Color(0xFF7B61FF)   // cognition, dialogue
    val secondary = Color(0xFF41E2D0)        // bioluminescent cyan: voice, listening
    val secondaryContainer = Color(0xFF00C6B4)
    val onSecondaryContainer = Color(0xFF004C45)
    val tertiary = Color(0xFFDBB8FF)         // luminous mist: deliberation
    val error = Color(0xFFFFB4AB)
    val ember = Color(0xFFFFA07A)            // gentle warning
    val hairline = Color(0x14FFFFFF)         // 8% white
    val hairlineStrong = Color(0x24FFFFFF)   // 14% white
}

@OptIn(ExperimentalTextApi::class)
private fun jakarta(w: Int) = Font(R.font.plus_jakarta_sans, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
@OptIn(ExperimentalTextApi::class)
private fun grotesk(w: Int) = Font(R.font.space_grotesk, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))

/** Bundled in the APK (OFL) — no font download, works offline. */
val Jakarta = FontFamily(jakarta(300), jakarta(400), jakarta(500), jakarta(600))
val Grotesk = FontFamily(grotesk(400), grotesk(500))

object Type {
    val displayHero = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.Light, fontSize = 36.sp, lineHeight = 44.sp, letterSpacing = (-0.03).em)
    val headlineLg = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.Normal, fontSize = 26.sp, lineHeight = 34.sp, letterSpacing = (-0.02).em)
    val headlineMd = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 28.sp, letterSpacing = (-0.01).em)
    val bodyLg = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.Light, fontSize = 17.sp, lineHeight = 27.sp, letterSpacing = (-0.01).em)
    val bodyMd = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp)
    val bodySm = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.01.em)
    val labelCaps = TextStyle(fontFamily = Grotesk, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.08.em)
    val labelSm = TextStyle(fontFamily = Grotesk, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.02.em)
}

@Composable
fun SautiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Sauti.primary, onPrimary = Sauti.onPrimary,
            primaryContainer = Sauti.primaryContainer, onPrimaryContainer = Color(0xFF28008A),
            secondary = Sauti.secondary, onSecondary = Color(0xFF003732),
            secondaryContainer = Sauti.secondaryContainer, onSecondaryContainer = Sauti.onSecondaryContainer,
            tertiary = Sauti.tertiary, onTertiary = Color(0xFF3F2160),
            background = Sauti.surface, onBackground = Sauti.onSurface,
            surface = Sauti.surface, onSurface = Sauti.onSurface,
            surfaceVariant = Sauti.surfaceHighest, onSurfaceVariant = Sauti.onSurfaceVariant,
            surfaceContainerLowest = Sauti.surfaceLowest, surfaceContainerLow = Sauti.surfaceLow,
            surfaceContainer = Sauti.surfaceContainer, surfaceContainerHigh = Sauti.surfaceHigh,
            surfaceContainerHighest = Sauti.surfaceHighest, surfaceBright = Sauti.surfaceBright,
            outline = Sauti.outline, outlineVariant = Sauti.outlineVariant,
            error = Sauti.error, onError = Color(0xFF690005),
        ),
        typography = Typography(
            displayLarge = Type.displayHero, headlineLarge = Type.headlineLg, headlineMedium = Type.headlineMd,
            titleLarge = Type.headlineMd, titleMedium = Type.bodyMd.copy(fontWeight = FontWeight.Medium),
            bodyLarge = Type.bodyLg, bodyMedium = Type.bodyMd, bodySmall = Type.bodySm,
            labelLarge = Type.bodyMd.copy(fontWeight = FontWeight.Medium), labelMedium = Type.labelSm, labelSmall = Type.labelCaps,
        ),
        content = content,
    )
}
