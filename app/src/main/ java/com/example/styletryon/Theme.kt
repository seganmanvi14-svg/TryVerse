// =====================================================================
// Theme.kt = THE LOOK OF THE APP (design system): colours, fonts, shapes and shared buttons / labels.
// Every screen uses these names (Ink, Primary, Cream...), so changing a colour here changes it everywhere.
// Black (Noir) theme is the default; the user can switch to the light theme (sun icon on Home).
// =====================================================================
package com.example.styletryon

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

// =====================================================================
//  TRYVERSE NOIR – design system
//  - Black background, warm ivory text, ONE accent: soft champagne.
//  - Frosted "glass" cards and fields with rounded corners.
//  - Fonts (in res/font, free licences – see font_licenses/):
//      Gloock  = big editorial titles and the TRYVERSE wordmark
//      Chorus  = cursive accent words ("world.", "Account")
//      Outfit  = everything else, bold by default
//  The colour names are the same as before, so every screen picks up the new look.
// =====================================================================

// ---------- Theme switch (black is the default) ----------
// Is the black theme on? mutableStateOf = when it changes, every screen redraws in the new colours
object ThemeState {
    var dark by mutableStateOf(true)
}

// Reads the saved theme choice from theme.txt (null = never chosen)
fun loadThemeChoice(context: Context): Boolean? {
    val file = File(context.filesDir, "theme.txt")
    return if (file.exists()) file.readText().trim() == "dark" else null
}

// Switches black <-> light and saves the choice in theme.txt
fun toggleBlackTheme(context: Context) {
    ThemeState.dark = !ThemeState.dark
    File(context.filesDir, "theme.txt").writeText(if (ThemeState.dark) "dark" else "light")
}

// Picks the light-theme or the black-theme version of a colour
private fun pick(light: Long, dark: Long) = Color(if (ThemeState.dark) dark else light)

// ---------- Colours ----------
// *** COLOURS *** each one has a light and a dark version (get() = worked out every time it is used)
val Ink: Color get() = pick(0xFF111111, 0xFFF4EEE2)          // main text (warm ivory)
val Brand: Color get() = pick(0xFF8A7552, 0xFFD9C4A0)        // champagne accent
val Accent: Color get() = Brand
val Primary: Color get() = Brand
val PrimaryDark: Color get() = Brand
val OnInk: Color get() = pick(0xFFFFFFFF, 0xFF15120D)        // text on champagne / ivory
val Paper: Color get() = pick(0xFFFFFFFF, 0xFF0E0D0B)        // bars and sheets
val PrimaryLight: Color get() = pick(0xFFF3EEE5, 0xFF1D1A15) // selected chips
val SoftBlue: Color get() = pick(0xFFF7F5F1, 0xFF12110E)     // info boxes
val Cream: Color get() = pick(0xFFFAF8F4, 0xFF070706)        // screen background
val Sand: Color get() = pick(0xFFEFEBE4, 0xFF15130F)         // behind product photos
val Spotlight: Color get() = pick(0xFFFFFFFF, 0xFF2A2620)    // soft light in the middle of a photo frame
val Muted: Color get() = pick(0xFF6F6A62, 0xFF9B958B)        // secondary text
val Rose: Color get() = pick(0xFFB3132B, 0xFFE0907F)         // errors, sold out
val Star: Color get() = Brand
val Line: Color get() = pick(0x1F000000, 0x1FFFFFFF)         // thin borders (12 %)
val Positive: Color get() = pick(0xFF2E5E3E, 0xFF8FCBA3)
val Disabled: Color get() = pick(0xFFBDBDBD, 0xFF3A3733)
/** Frosted glass fill (6 % white on black). */
val GlassFill: Color get() = pick(0x0A000000, 0x0FFFFFFF)

// Champagne gold colours and gradients (same in both themes)
val Gold = Color(0xFFD9C4A0)
val GoldLight = Color(0xFFF3E7CF)
val GoldDeep = Color(0xFF9C8763)
/** Champagne → light → deep, used for the main pill button and the wordmark. */
val GoldSweep: Brush = Brush.horizontalGradient(listOf(GoldLight, Gold, GoldDeep))
val GoldBrush: Brush = Brush.verticalGradient(listOf(Color(0xFFFFF6E3), Gold, Color(0xFFA88F66)))

// ---------- Fonts ----------
// *** FONTS ***: Gloock for big titles, Chorus for the cursive words, Outfit for normal text
val Display = FontFamily(Font(R.font.gloock_regular))
val ScriptFont = FontFamily(Font(R.font.chorus_script))
val SansText = FontFamily(Font(R.font.outfit_regular, FontWeight.Normal), Font(R.font.outfit_bold, FontWeight.Bold))

private val base = Typography()
// Which font and size each kind of text uses (titles, body text, labels)
val ZaraTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = Display),
    displayMedium = base.displayMedium.copy(fontFamily = Display),
    displaySmall = base.displaySmall.copy(fontFamily = Display),
    headlineLarge = base.headlineLarge.copy(fontFamily = Display),
    headlineMedium = base.headlineMedium.copy(fontFamily = Display),
    headlineSmall = base.headlineSmall.copy(fontFamily = Display),
    titleLarge = base.titleLarge.copy(fontFamily = Display, fontSize = 22.sp),           // dialog titles
    titleMedium = base.titleMedium.copy(fontFamily = SansText, fontWeight = FontWeight.Bold, fontSize = 14.sp),
    titleSmall = base.titleSmall.copy(fontFamily = SansText, fontWeight = FontWeight.Bold, fontSize = 12.sp),
    bodyLarge = base.bodyLarge.copy(fontFamily = SansText, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyMedium = base.bodyMedium.copy(fontFamily = SansText, fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 18.sp),
    bodySmall = base.bodySmall.copy(fontFamily = SansText, fontSize = 11.sp),
    labelLarge = base.labelLarge.copy(fontFamily = SansText, fontWeight = FontWeight.Bold, fontSize = 13.sp),
    labelMedium = base.labelMedium.copy(fontFamily = SansText, fontWeight = FontWeight.Bold, fontSize = 11.sp),
    labelSmall = base.labelSmall.copy(fontFamily = SansText, fontWeight = FontWeight.Bold, fontSize = 10.sp)
)

// ---------- Shapes ----------
// *** SHAPES ***: rounded corners for cards, pill buttons, text boxes
val Sharp = RoundedCornerShape(14.dp)
val Pill = RoundedCornerShape(50)
val CardShape = RoundedCornerShape(22.dp)
val FieldShape = RoundedCornerShape(12.dp)

val ZaraShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp), extraLarge = RoundedCornerShape(28.dp)
)

// Gives Material 3 (buttons, dialogs, text boxes) our colours, for the black or the light theme
@Composable
fun zaraColors(): ColorScheme =
    if (ThemeState.dark) darkColorScheme(
        primary = Brand, onPrimary = OnInk, primaryContainer = PrimaryLight, onPrimaryContainer = Ink,
        secondary = Brand, onSecondary = OnInk, secondaryContainer = PrimaryLight, onSecondaryContainer = Ink,
        tertiary = Brand, onTertiary = OnInk,
        background = Cream, onBackground = Ink, surface = Paper, onSurface = Ink,
        surfaceVariant = PrimaryLight, onSurfaceVariant = Muted, surfaceTint = Paper,
        surfaceContainerLowest = Paper, surfaceContainerLow = Paper, surfaceContainer = Paper,
        surfaceContainerHigh = Color(0xFF16140F), surfaceContainerHighest = PrimaryLight,
        outline = Color(0x55FFFFFF), outlineVariant = Line, error = Rose, onError = OnInk
    ) else lightColorScheme(
        primary = Brand, onPrimary = Color.White, primaryContainer = PrimaryLight, onPrimaryContainer = Ink,
        secondary = Brand, onSecondary = Color.White, secondaryContainer = PrimaryLight, onSecondaryContainer = Ink,
        tertiary = Brand, onTertiary = Color.White,
        background = Cream, onBackground = Ink, surface = Paper, onSurface = Ink,
        surfaceVariant = PrimaryLight, onSurfaceVariant = Muted, surfaceTint = Paper,
        surfaceContainerLowest = Paper, surfaceContainerLow = Paper, surfaceContainer = Paper,
        surfaceContainerHigh = Paper, surfaceContainerHighest = PrimaryLight,
        outline = Color(0x55000000), outlineVariant = Line, error = Rose, onError = Color.White
    )

// Wraps the whole app so every screen uses our colours, fonts and shapes
@Composable
fun ZaraTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = zaraColors(), typography = ZaraTypography, shapes = ZaraShapes, content = content)
}

// =====================================================================
//  Modifiers
// =====================================================================

// 'Frosted glass' look: rounded, slightly see-through fill with a thin border
fun Modifier.glass(shape: Shape = FieldShape): Modifier =
    this.clip(shape).background(GlassFill).border(1.dp, Line, shape)

// Rounded photo frame with a thin border
fun Modifier.goldFrame(glow: Dp = 0.dp): Modifier = this.clip(Sharp).border(1.dp, Line, Sharp)

// Text filled with the gold gradient (the TRYVERSE logo)
@OptIn(ExperimentalTextApi::class)
fun goldText(glow: Float = 0f) = TextStyle(brush = GoldBrush)

// =====================================================================
//  Shared pieces
// =====================================================================

// *** MAIN BUTTON *** (gold gradient pill). Grey when disabled
@Composable
fun ZaraButton(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.height(52.dp).clip(Pill).background(if (enabled) GoldSweep else SolidColor(Disabled)),
        enabled = enabled, shape = Pill,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent, contentColor = Color(0xFF15120D),
            disabledContainerColor = Color.Transparent, disabledContentColor = Muted
        ),
        contentPadding = PaddingValues(horizontal = 18.dp)
    ) {
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        if (icon != null) {
            Spacer(Modifier.width(8.dp))
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}

// Second button: outline pill
@Composable
fun ZaraOutlinedButton(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick, modifier = modifier.height(52.dp), enabled = enabled, shape = Pill,
        border = BorderStroke(1.dp, if (enabled) Ink.copy(alpha = 0.55f) else Line),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.Transparent, contentColor = Ink),
        contentPadding = PaddingValues(horizontal = 18.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

// Small spaced CAPITAL label (e.g. NEW SEASON, SIZE)
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = Primary) {
    Text(text.uppercase(), modifier = modifier, color = color, fontSize = 10.sp, letterSpacing = 3.sp, fontWeight = FontWeight.Bold)
}

// Big title in the Gloock font
@Composable
fun DisplayTitle(text: String, modifier: Modifier = Modifier, size: TextUnit = 30.sp, color: Color = Ink, align: TextAlign? = null) {
    Text(text, modifier = modifier, color = color, fontFamily = Display, fontSize = size, lineHeight = size * 1.12f, textAlign = align)
}

// Cursive gold word (e.g. 'on you', 'world.')
@Composable
fun ScriptWord(text: String, size: TextUnit = 34.sp, modifier: Modifier = Modifier, color: Color = Primary) {
    Text(text, modifier = modifier, color = color, fontFamily = ScriptFont, fontWeight = FontWeight.Bold, fontSize = size)
}

// Thin gold line that fades out at both ends
@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Brush.horizontalGradient(listOf(Color.Transparent, Gold.copy(alpha = 0.3f), Color.Transparent))))
}

// Text tabs with a gold underline under the selected one
@Composable
fun TextTabs(options: List<String>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        options.forEach { option ->
            val on = option == selected
            Column(Modifier.width(IntrinsicSize.Max).clickable { onSelect(option) }.padding(vertical = 8.dp)) {
                Text(
                    option, fontSize = 13.sp, maxLines = 1, fontWeight = FontWeight.Bold,
                    color = if (on) Ink else Muted
                )
                Spacer(Modifier.height(5.dp))
                Box(Modifier.fillMaxWidth().height(2.dp).clip(Pill).background(if (on) Primary else Color.Transparent))
            }
        }
    }
}

// *** SIZE BUTTON *** (XS, S, M...). Sold-out sizes are faded, crossed out and can't be tapped
@Composable
fun SizeBox(label: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.defaultMinSize(minWidth = 52.dp).height(40.dp)
            .then(if (selected) Modifier.clip(RoundedCornerShape(10.dp)).background(Primary) else Modifier.glass(RoundedCornerShape(10.dp)))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            color = when { selected -> OnInk; enabled -> Ink; else -> Disabled },
            textDecoration = if (enabled) null else TextDecoration.LineThrough
        )
    }
}

// Round see-through icon button (back, share, heart on photos)
@Composable
fun GlassCircle(icon: ImageVector, contentDescription: String?, tint: Color = Ink, size: Dp = 40.dp, onClick: () -> Unit) {
    Box(
        Modifier.size(size).clip(CircleShape).background(Color(0x66000000)).border(1.dp, Line, CircleShape).clickable { onClick() },
        contentAlignment = Alignment.Center
    ) { Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(size * 0.48f)) }
}

// Small see-through label with a dot (e.g. 'AI fitted')
@Composable
fun GlassChip(text: String, modifier: Modifier = Modifier, dotColor: Color = Primary, onClick: (() -> Unit)? = null) {
    Row(
        modifier.clip(Pill).background(Color(0x73000000)).border(1.dp, Gold.copy(alpha = 0.45f), Pill)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("●", color = dotColor, fontSize = 8.sp)
        Spacer(Modifier.width(6.dp))
        Text(text, color = Color(0xFFF4EEE2), fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

// Colours for the text boxes (glass fill, gold when typing, red on error)
@Composable
fun underlineFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = GlassFill, unfocusedContainerColor = GlassFill, errorContainerColor = GlassFill,
    focusedBorderColor = Primary, unfocusedBorderColor = Line, errorBorderColor = Rose,
    cursorColor = Primary, focusedLabelColor = Primary, unfocusedLabelColor = Muted,
    focusedLeadingIconColor = Primary, unfocusedLeadingIconColor = Muted,
    focusedTrailingIconColor = Ink, unfocusedTrailingIconColor = Muted
)

@Composable
fun boxFieldColors() = underlineFieldColors()
