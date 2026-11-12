package raven.sample.overlay

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import raven.compose.foundation.BackgroundBlur
import raven.compose.foundation.BlurStyle
import raven.compose.foundation.ComboBox
import raven.compose.foundation.Slider
import raven.compose.foundation.Surface
import raven.compose.foundation.Text
import raven.compose.foundation.TextButton
import raven.compose.foundation.layout.Arrangement
import raven.compose.foundation.layout.Box
import raven.compose.foundation.layout.Column
import raven.compose.foundation.layout.Row
import raven.compose.foundation.layout.fillMaxHeight
import raven.compose.foundation.layout.fillMaxSize
import raven.compose.foundation.layout.padding
import raven.compose.foundation.layout.width
import raven.compose.ui.Alignment
import raven.compose.ui.Backdrop
import raven.compose.ui.Modifier
import raven.compose.ui.Window
import raven.compose.ui.WindowBackdrop
import raven.compose.ui.application
import raven.compose.ui.clipToBounds
import raven.compose.ui.graphics.Color
import raven.compose.ui.graphics.RoundedCornerShape
import raven.compose.ui.platform.LocalWindowInfo
import raven.compose.ui.rememberWindowState
import raven.compose.ui.testTag
import raven.compose.ui.theme.RavenTheme
import raven.compose.ui.unit.DpSize
import raven.compose.ui.unit.dp
import raven.compose.ui.unit.sp

// A port of Raven's examples/overlay: a window that is seen through, and the ways to blur what is
// behind it.
//
//   Window(transparent = true) + WindowBackdrop(...)   what the window system puts behind it
//   Window(opacity = ...)                              the whole window fades
//   BackgroundBlur(style = Native | Plain | Frosted | Glass)
//
// Whether the desktop shows, and blurred or not, is up to the window system: Windows 10 and 11 do
// it (Mica and Acrylic need Windows 11), X11 needs a compositor (and KWin for the blur).
// WindowBackdrop returns the backdrop obtained, and the panel shows it.

fun main() = application {
    var opacity by remember { mutableFloatStateOf(1f) }
    RavenTheme(dark = true) {
        Window(
            onCloseRequest = ::exitApplication,
            state = rememberWindowState(size = DpSize(640.dp, 420.dp)),
            title = "Raven - overlay",
            transparent = true,
            undecorated = true,
            opacity = opacity,
        ) {
            Overlay(opacity, onOpacityChange = { opacity = it }, onClose = ::exitApplication)
        }
    }
}

/** The backdrops offered, in the ComboBox's order. */
private val backdrops = listOf(Backdrop.Transparent, Backdrop.Blur, Backdrop.Acrylic, Backdrop.Mica)

/** Stripes of colour behind the four blurs: something sharp to blur. */
private val stripeColors = listOf(Color(1f, 0.27f, 0.23f), Color(1f, 0.84f, 0.04f), Color(0.19f, 0.82f, 0.35f), Color(0.04f, 0.52f, 1f))

private val White = Color(0xFFFFFFFF)
private val Dim = Color(0xC0FFFFFF)
private val Dimmer = Color(0x90FFFFFF)

/**
 * The window's content, in a `Window(transparent = true)`: [opacity] is the window's, which the
 * slider changes through [onOpacityChange], and [onClose] runs when Close is clicked.
 */
@Composable
fun Overlay(opacity: Float, onOpacityChange: (Float) -> Unit, onClose: () -> Unit) {
    var backdrop by remember { mutableIntStateOf(backdrops.indexOf(Backdrop.Blur)) }
    // (when the system can't do what is asked, the window keeps what it had, and this says so)
    val obtained = WindowBackdrop(backdrops[backdrop])
    val seeThrough = LocalWindowInfo.current.isTransparent

    BackgroundBlur(
        Modifier.fillMaxSize(),
        style = BlurStyle.Native,
        shape = RoundedCornerShape(24.dp),
        tint = Color(0x9910141C),
    ) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Seen through", Modifier.weight(1f), color = White, fontSize = 24.sp)
                TextButton(onClick = onClose) { Text("Close") }
            }
            Text(
                if (seeThrough) "Where this window draws nothing, what is behind it shows."
                else "This window system did not give a window that can be seen through: the same interface, on a plain background.",
                color = Dim,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Backdrop", Modifier.width(80.dp), color = Dim)
                ComboBox(
                    items = backdrops.map { it.name },
                    selectedIndex = backdrop,
                    onSelectedIndexChange = { backdrop = it },
                    modifier = Modifier.width(160.dp).testTag("backdrop"),
                )
                Text("obtained: ${describe(obtained)}", color = Dimmer)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Opacity", Modifier.width(80.dp), color = Dim)
                Slider(opacity, onOpacityChange, Modifier.width(160.dp).testTag("opacity"), valueRange = 0.2f..1f)
                Text("of the whole window", color = Dimmer)
            }
            // The four ways to blur, over stripes drawn by the application.
            Box(Modifier.weight(1f)) {
                Row(Modifier.fillMaxSize().clipToBounds()) {
                    repeat(40) { Surface(Modifier.width(16.dp).fillMaxHeight(), color = stripeColors[it % 4]) {} }
                }
                Row(Modifier.fillMaxSize().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    for ((style, tint) in listOf(
                        BlurStyle.Plain to Color.Unspecified,
                        BlurStyle.Frosted to Color.Unspecified,
                        BlurStyle.Glass to Color.Unspecified,
                        BlurStyle.Native to Color(0x30000000),
                    )) {
                        BackgroundBlur(
                            Modifier.weight(1f).fillMaxHeight(),
                            style = style,
                            shape = RoundedCornerShape(14.dp),
                            tint = tint,
                        ) {
                            Box(Modifier.fillMaxSize().padding(10.dp), contentAlignment = Alignment.Center) {
                                Text(style.name, color = White)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** What the window system put behind the window, in words. */
fun describe(backdrop: Backdrop): String = when (backdrop) {
    Backdrop.Transparent -> "the desktop, as it is"
    Backdrop.Blur -> "the desktop, blurred"
    Backdrop.Acrylic -> "acrylic"
    Backdrop.Mica -> "mica"
    Backdrop.None -> "nothing (not available here)"
}
