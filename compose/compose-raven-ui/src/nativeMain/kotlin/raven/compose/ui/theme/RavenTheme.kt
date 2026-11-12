@file:OptIn(InternalComposeRavenApi::class)

package raven.compose.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import raven.cinterop.R_ROLE_NONE
import raven.cinterop.R_WIDGET_OVERLAY
import raven.cinterop.Rwidget_set_theme
import raven.compose.ui.ChildKind
import raven.compose.ui.InternalComposeRavenApi
import raven.compose.ui.LocalRavenHost
import raven.compose.ui.Modifier
import raven.compose.ui.PanelLayout
import raven.compose.ui.Property
import raven.compose.ui.RavenWidget
import raven.compose.ui.WidgetDescriptor
import raven.compose.ui.bind
import raven.compose.ui.graphics.Color
import raven.compose.ui.graphics.Material

/** How every widget is drawn, app-wide: Raven's own looks and those that imitate a platform. */
public enum class Look {
    /** Raven, flat and dense, for tools. The look at start. */
    Raven,

    /** Raven in glass: translucent capsules that refract and blur what is behind them. */
    Glass,

    /** Black and white, thick borders and larger text, for low vision. */
    HighContrast,

    /** Windows 11 (Fluent). */
    Windows,

    /** macOS Sequoia (Aqua). */
    Macos,

    /** GNOME (libadwaita). */
    Gnome,

    /** The look of the platform the app runs on: Windows, macOS (macOS Glass from macOS 26) or GNOME. */
    Native,

    /** macOS 26 Tahoe (Liquid Glass). */
    MacosGlass,
}

/**
 * Raven's look and theme, in two places.
 *
 * At application level, around the windows (`application { RavenTheme(...) { Window(...) { } } }`,
 * or the outermost `RavenTheme` of `singleWindowApplication`'s content), it emits no node and makes
 * the app-wide settings: [look] and [dark] as one look, [theme] as the app theme, [accent], from
 * which Raven derives the hovered and pressed shades, the focus outline, the selection and the
 * links, and [material], what every widget without one of its own is made of. A look or theme
 * change resets Raven's accent and material, so they are applied again after one.
 *
 * Inside a window, [theme] gives the content its own theme, and the content goes in a Box. The
 * app-wide settings can't be made there: setting [look], [dark], [accent] or [material] logs an
 * error and does nothing.
 *
 * A null setting leaves Raven's as it is. [theme] names a theme from [loadTheme], a built-in one or
 * a look ("WindowsLight"). [Material.Default] is the material the look and theme give. Only Raven's
 * own looks are made of glass: the others keep [Material.Solid], and Raven warns.
 */
@Composable
public fun RavenTheme(
    look: Look? = null,
    dark: Boolean? = null,
    theme: String? = null,
    accent: Color? = null,
    material: Material? = null,
    content: @Composable () -> Unit,
) {
    when (LocalThemePlacement.current) {
        ThemePlacement.Application -> {
            ApplyAppTheme(look, dark, theme, accent, material)
            content()
        }
        ThemePlacement.TopOfSingleWindow -> {
            ApplyAppTheme(look, dark, theme, accent, material)
            CompositionLocalProvider(LocalThemePlacement provides ThemePlacement.Window, content = content)
        }
        ThemePlacement.Window -> {
            if (look != null || dark != null || accent != null || material != null) {
                DisposableEffect(look, dark, accent, material) {
                    themeErrorSink("RavenTheme: look, dark, accent and material are app-wide, and only an application-level RavenTheme may set them")
                    onDispose { }
                }
            }
            RavenWidget(ThemeBoxWidget, Modifier, {
                bind(PanelLayout.Box)
                bind(theme, WidgetTheme)
            }, content)
        }
    }
}

@Composable
private fun ApplyAppTheme(look: Look?, dark: Boolean?, theme: String?, accent: Color?, material: Material?) {
    val host = LocalRavenHost.current
    // An effect, not a SideEffect: it runs before the windows' own effects create and fill them.
    DisposableEffect(look, dark, theme, accent, material) {
        host.theme.apply(look, dark, theme, accent, material)
        onDispose { }
    }
}

/** What the app theme has, for the composition. */
public object RavenTheme {
    /**
     * The app theme's colours. Readers recompose when the look or theme changes. Subtree themes
     * aren't seen here.
     */
    public val colors: RavenColors
        @Composable
        @ReadOnlyComposable
        get() = LocalRavenColors.current

    /**
     * Every named theme Raven has, the built-in ones included, as snapshot state: it follows
     * [loadTheme], a look change, and Raven's reload of a theme file, which can rename it. It is
     * valid only while Raven runs, on its UI thread.
     */
    public val themeNames: List<String>
        get() = runningHost("RavenTheme.themeNames").theme.themeNames
}

/** The app theme's colours, provided by the host to the application composition and every window. */
internal val LocalRavenColors = compositionLocalOf<RavenColors> { error("no RavenHost") }

/**
 * Where a [RavenTheme] is, which decides whether it may make the app-wide settings: in the
 * application composition, at the top of `singleWindowApplication`'s content, or in a window.
 */
internal enum class ThemePlacement { Application, TopOfSingleWindow, Window }

internal val LocalThemePlacement = staticCompositionLocalOf { ThemePlacement.Application }

/** Where [RavenTheme]'s errors go. */
internal var themeErrorSink: (String) -> Unit = { println("compose-raven: error: $it") }

private val ThemeBoxWidget = WidgetDescriptor("RavenTheme", R_WIDGET_OVERLAY, ChildKind.Many, role = R_ROLE_NONE)

/** The theme a widget and everything inside it is drawn with; null follows the parent. */
private val WidgetTheme = Property<String?> { widget, name -> Rwidget_set_theme(widget, name) }
