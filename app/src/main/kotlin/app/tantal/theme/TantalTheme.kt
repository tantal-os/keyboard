package app.tantal.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import app.tantal.keyboard.R

private val weights = listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold)

/** Roboto Flex for headings and keys; body text stays in the system Roboto. A variable font needs one entry per weight. */
val RobotoFlex = FontFamily(
    weights.map { Font(R.font.roboto_flex, it, variationSettings = FontVariation.Settings(it, FontStyle.Normal)) },
)

private fun TextStyle.flex() = copy(fontFamily = RobotoFlex)

private fun TextStyle.roboto() = copy(fontFamily = FontFamily.SansSerif)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val typography = Typography().run {
    copy(
        displayLarge = displayLarge.flex(),
        displayMedium = displayMedium.flex(),
        displaySmall = displaySmall.flex(),
        headlineLarge = headlineLarge.flex(),
        headlineMedium = headlineMedium.flex(),
        headlineSmall = headlineSmall.flex(),
        titleLarge = titleLarge.flex(),
        titleMedium = titleMedium.flex(),
        titleSmall = titleSmall.flex(),
        bodyLarge = bodyLarge.roboto(),
        bodyMedium = bodyMedium.roboto(),
        bodySmall = bodySmall.roboto(),
        labelLarge = labelLarge.roboto(),
        labelMedium = labelMedium.roboto(),
        labelSmall = labelSmall.roboto(),
        displayLargeEmphasized = displayLargeEmphasized.flex(),
        displayMediumEmphasized = displayMediumEmphasized.flex(),
        displaySmallEmphasized = displaySmallEmphasized.flex(),
        headlineLargeEmphasized = headlineLargeEmphasized.flex(),
        headlineMediumEmphasized = headlineMediumEmphasized.flex(),
        headlineSmallEmphasized = headlineSmallEmphasized.flex(),
        titleLargeEmphasized = titleLargeEmphasized.flex(),
        titleMediumEmphasized = titleMediumEmphasized.flex(),
        titleSmallEmphasized = titleSmallEmphasized.flex(),
        bodyLargeEmphasized = bodyLargeEmphasized.roboto(),
        bodyMediumEmphasized = bodyMediumEmphasized.roboto(),
        bodySmallEmphasized = bodySmallEmphasized.roboto(),
        labelLargeEmphasized = labelLargeEmphasized.roboto(),
        labelMediumEmphasized = labelMediumEmphasized.roboto(),
        labelSmallEmphasized = labelSmallEmphasized.roboto(),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TantalTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    MaterialExpressiveTheme(
        colorScheme = if (isSystemInDarkTheme()) {
            dynamicDarkColorScheme(context)
        } else {
            dynamicLightColorScheme(context)
        },
        motionScheme = MotionScheme.expressive(),
        typography = typography,
        content = content,
    )
}
