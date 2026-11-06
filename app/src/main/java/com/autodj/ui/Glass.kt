package com.autodj.ui

import androidx.compose.foundation.Canvas
import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The liquid-glass look: clear panels with a bright rim on their lit upper-left edge and a softer
 * one opposite, floating over one solid ground: soft grey in light mode, near-black in dark. Panels
 * have no live backdrop blur, which keeps every frame cheap, on phones and on TVs.
 *
 * Colours follow [LocalGlassDark], so a screen in a song's deep colour (Now Playing, the decks)
 * stays dark glass with white text even when the app is light.
 */
object Glass {
    /** The ground everything floats on. */
    val Ground: Color @Composable @ReadOnlyComposable get() = if (dark()) DarkGround else LightGround
    val Fill: Color @Composable @ReadOnlyComposable get() = if (dark()) Color.White.copy(alpha = 0.08f) else Ink.copy(alpha = 0.06f)
    val FillStrong: Color @Composable @ReadOnlyComposable get() = if (dark()) Color.White.copy(alpha = 0.14f) else Ink.copy(alpha = 0.09f)
    val FillSelected: Color @Composable @ReadOnlyComposable get() = if (dark()) Color.White.copy(alpha = 0.22f) else Color.White
    /** Over a busy visualizer: darker, so text on it stays readable. */
    val FillDark = Color(0xFF140E12).copy(alpha = 0.55f)
    val Text: Color @Composable @ReadOnlyComposable get() = if (dark()) Color.White else Ink
    val Muted: Color @Composable @ReadOnlyComposable get() = if (dark()) Color.White.copy(alpha = 0.78f) else Color(0xFF5E5E66)
    /** The one accent: flat orange. Text on it is [OnAccent]. */
    val Ember = Color(0xFFFF9F0A)
    /** Orange text: lighter on dark, a deeper burnt orange on white so it stays readable. */
    val EmberText: Color @Composable @ReadOnlyComposable get() = if (dark()) Color(0xFFFFB340) else Color(0xFFB35C00)
    val OnAccent = Color.Black
    /** Solid base for sheets and dialogs, so the screen behind never shows through the words. */
    val Sheet: Color @Composable @ReadOnlyComposable get() = if (dark()) Color(0xFF1C1C1F) else Color(0xFFF7F7FA)
    /** Main buttons: flat orange (black text on it reads at about 10:1). */
    val Fire: Brush = SolidColor(Ember)
    val FireSelected = Ember.copy(alpha = 0.18f)

    /** The liquid rim: light catching the upper-left edge, faint along the sides, a softer glint bottom-right. */
    val Edge: Brush @Composable @ReadOnlyComposable get() = if (dark()) DarkEdge else LightEdge

    private val Ink = Color(0xFF1C1C1E)
    private val DarkEdge = Brush.linearGradient(
        0f to Color.White.copy(alpha = 0.50f), 0.35f to Color.White.copy(alpha = 0.10f),
        0.7f to Color.White.copy(alpha = 0.06f), 1f to Color.White.copy(alpha = 0.24f),
    )
    private val LightEdge = Brush.linearGradient(
        0f to Color.White, 0.4f to Ink.copy(alpha = 0.05f), 1f to Ink.copy(alpha = 0.10f),
    )

    @Composable @ReadOnlyComposable
    private fun dark() = LocalGlassDark.current
}

/** The dark-mode ground, and the ground behind a song whose cover has no colour. */
val DarkGround = Color(0xFF0B0B0D)

/** The light-mode ground: a soft grey, gentler than pure white. */
val LightGround = Color(0xFFF2F2F7)

/** True where the glass is dark: dark mode, or a screen in a song's deep colour. */
val LocalGlassDark = staticCompositionLocalOf { true }

/**
 * Light or dark, from Settings and remembered across launches. With [matchPhone] on (the default)
 * the app follows the device's dark-theme setting; off, [light] decides.
 */
object Appearance {
    private const val PREFS = "appearance"
    private const val KEY = "light"
    private const val KEY_MATCH = "match_phone"
    var light by mutableStateOf(true)
        private set
    var matchPhone by mutableStateOf(true)
        private set

    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        light = prefs.getBoolean(KEY, true)
        matchPhone = prefs.getBoolean(KEY_MATCH, true)
    }

    fun setLight(context: Context, on: Boolean) {
        light = on
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply()
    }

    fun setMatchPhone(context: Context, on: Boolean) {
        matchPhone = on
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_MATCH, on).apply()
    }

    /** Whether the app shows light right now: the device's setting when matching it, else the switch. */
    @Composable
    fun isLight(): Boolean = if (matchPhone) !isSystemInDarkTheme() else light
}

/** Shows [content] in the glass of [dark] (dark mode or a song's colour) or light. */
@Composable
fun GlassTheme(dark: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalGlassDark provides dark,
        LocalContentColor provides if (dark) Color.White else Color(0xFF1C1C1E),
        content = content,
    )
}

/** Shows [content] in dark glass whatever the setting: for screens in a song's colour or over a visualizer. */
@Composable
fun DarkGlass(content: @Composable () -> Unit) = GlassTheme(true, content)

/** Matches the system bars' icons to the ground: dark icons over light, white ones over dark. */
@Composable
fun SystemBarIcons(darkGround: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    SideEffect {
        val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
        // Clear bars, so the ground shows under them in either mode.
        @Suppress("DEPRECATION")
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        @Suppress("DEPRECATION")
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        // No whitish scrim behind the navigation bar: the ground runs to the bottom edge.
        if (android.os.Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !darkGround
            isAppearanceLightNavigationBars = !darkGround
        }
    }
}

/** A glass panel: [fill] behind, a liquid rim round [shape]. */
@Composable
fun Modifier.glass(shape: Shape, fill: Color = Glass.Fill, edge: Brush = Glass.Edge, edgeWidth: Dp = 1.dp): Modifier =
    this.clip(shape).background(fill, shape).border(edgeWidth, edge, shape)

/** Floating liquid glass (tab bar, rail, round buttons): a glass panel lifted off the ground by a soft shadow. */
@Composable
fun Modifier.floatingGlass(shape: Shape, fill: Color = Glass.Fill, elevation: Dp = 8.dp): Modifier {
    val shadow = if (LocalGlassDark.current) Color.Black.copy(alpha = 0.5f) else Color(0xFF181428).copy(alpha = 0.22f)
    return this.shadow(elevation, shape, clip = false, ambientColor = shadow, spotColor = shadow).glass(shape, fill)
}

/** The ground behind the glass: one solid colour, white or near-black unless a playing song lends its own. */
@Composable
fun AmbientLight(modifier: Modifier, color: Color = Glass.Ground) {
    Box(modifier.background(color))
}

/**
 * A platter of frosted glass: fine grooves outside [grooveFrom] of its radius, light catching its
 * upper left, and a sheen that shows it turning when it's rotated. [content] sits on top, centred.
 */
@Composable
fun GlassPlatter(modifier: Modifier, grooveFrom: Float = 0.62f, content: @Composable BoxScope.() -> Unit = {}) {
    Box(modifier.glass(CircleShape, Color.Transparent, edgeWidth = 1.5.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.26f), Color.White.copy(alpha = 0.06f)), Offset(r * 0.6f, r * 0.5f), r * 1.4f))
            val step = 4.dp.toPx()
            var g = r * grooveFrom
            while (g < r - step) {
                drawCircle(Color.White.copy(alpha = 0.07f), g, style = Stroke(1f))
                g += step
            }
            // Two soft highlights opposite each other: the sheen moves as the platter turns.
            drawCircle(
                Brush.sweepGradient(
                    0f to Color.Transparent, 0.08f to Color.White.copy(alpha = 0.2f), 0.18f to Color.Transparent,
                    0.55f to Color.Transparent, 0.62f to Color.White.copy(alpha = 0.12f), 0.72f to Color.Transparent,
                ),
            )
        }
        content()
    }
}

/** A round glass button for an icon, labelled for screen readers. */
@Composable
fun GlassIconButton(
    description: String,
    size: Dp = 44.dp,
    fill: Color = Glass.Fill,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    Box(
        modifier.size(size).floatingGlass(CircleShape, fill, elevation = 4.dp)
            .clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { icon() }
}

/** A glass segmented control: one option lit. Remote focus shows as a white ring. */
@Composable
fun <T> GlassSegmented(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 40.dp,
    fontSize: TextUnit = 15.sp,
    fill: Color = Glass.Fill,
    selectedFill: Color = Glass.FillSelected,
    selectedText: Color = Glass.Text,
) {
    Row(
        modifier.glass(RoundedCornerShape(14.dp), fill).padding(4.dp).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { o ->
            val on = o == selected
            val interaction = remember { MutableInteractionSource() }
            val focused by interaction.collectIsFocusedAsState()
            val shape = RoundedCornerShape(10.dp)
            Box(
                Modifier.weight(1f).height(height).clip(shape)
                    .background(if (on) selectedFill else Color.Transparent, shape)
                    .border(if (focused) 2.dp else 0.dp, if (focused) Glass.Text else Color.Transparent, shape)
                    .selectable(on, interactionSource = interaction, indication = null, role = Role.RadioButton) { onSelect(o) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(o), fontSize = fontSize, maxLines = 1,
                    color = if (on) selectedText else Glass.Muted,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold,
                )
            }
        }
    }
}

/** A small caps section heading over glass lists. */
@Composable
fun GlassHeading(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), color = Glass.Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp, modifier = modifier)
}

/** True when Android's "Remove animations" is on: records and discs then hold still. */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
}

/** Room the floating tab bar takes at the bottom: 64dp of bar and its 10dp margin, plus a little air. */
val TAB_BAR_SPACE = 84.dp

/** How much of the bottom the floating tab bar covers; scrolling content pads its end by this. */
val LocalTabBarInset = compositionLocalOf { 0.dp }

/** One tab of the floating glass tab bar; [tourKey] lets the guided tour point at it. */
class GlassTab(val label: String, val tourKey: String? = null, val icon: @Composable (Color) -> Unit)

/** Glass that content scrolls under: nearly solid, since nothing blurs what's behind it, so text under it never shows through. */
val GlassOverContent: Color @Composable @ReadOnlyComposable get() =
    if (LocalGlassDark.current) Color(0xFF1C1C1F).copy(alpha = 0.97f) else Color.White.copy(alpha = 0.96f)

/**
 * A big screen held sideways, like a tablet: wide enough and tall enough for the roomy layouts
 * (a wider rail, bigger records, the mix plan and what's coming up). A phone on its side is
 * wide but too short for them.
 */
@Composable
fun isRoomyLandscape(): Boolean {
    val c = LocalConfiguration.current
    return c.screenWidthDp >= 840 && c.screenHeightDp >= 600
}
