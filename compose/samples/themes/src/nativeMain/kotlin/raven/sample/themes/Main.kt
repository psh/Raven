package raven.sample.themes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import raven.compose.foundation.Button
import raven.compose.foundation.ButtonDefaults
import raven.compose.foundation.Checkbox
import raven.compose.foundation.CodeEditor
import raven.compose.foundation.ComboBox
import raven.compose.foundation.LinearProgressIndicator
import raven.compose.foundation.ListView
import raven.compose.foundation.MarkdownText
import raven.compose.foundation.OutlinedButton
import raven.compose.foundation.RadioButton
import raven.compose.foundation.Slider
import raven.compose.foundation.SpinBox
import raven.compose.foundation.Surface
import raven.compose.foundation.Switch
import raven.compose.foundation.SwitchDefaults
import raven.compose.foundation.Text
import raven.compose.foundation.TextButton
import raven.compose.foundation.TextField
import raven.compose.foundation.VerticalDivider
import raven.compose.foundation.layout.Arrangement
import raven.compose.foundation.layout.Column
import raven.compose.foundation.layout.Row
import raven.compose.foundation.layout.Spacer
import raven.compose.foundation.layout.VerticalScroll
import raven.compose.foundation.layout.VerticalSplitter
import raven.compose.foundation.layout.fillMaxHeight
import raven.compose.foundation.layout.fillMaxSize
import raven.compose.foundation.layout.fillMaxWidth
import raven.compose.foundation.layout.height
import raven.compose.foundation.layout.padding
import raven.compose.foundation.layout.size
import raven.compose.foundation.layout.width
import raven.compose.ui.Alignment
import raven.compose.ui.Modifier
import raven.compose.ui.PaddingValues
import raven.compose.ui.Window
import raven.compose.ui.application
import raven.compose.ui.graphics.CircleShape
import raven.compose.ui.graphics.Color
import raven.compose.ui.graphics.RoundedCornerShape
import raven.compose.ui.platform.LocalUriHandler
import raven.compose.ui.platform.UriHandler
import raven.compose.ui.rememberWindowState
import raven.compose.ui.testTag
import raven.compose.ui.theme.Look
import raven.compose.ui.theme.RavenTheme
import raven.compose.ui.theme.loadTheme
import raven.compose.ui.theme.saveTheme
import raven.compose.ui.tooltip
import raven.compose.ui.unit.DpSize
import raven.compose.ui.unit.dp
import raven.compose.ui.unit.sp
import kotlin.time.Duration.Companion.milliseconds

// A port of Raven's examples/themes: theme files, a look and a theme chosen at run time, a part of
// the window with a theme of its own, and Markdown in a label and in an editor.
//
//   RavenTheme(look, dark)      the look: Raven's (flat, glass, high contrast), Windows 11, macOS,
//                               GNOME, the platform's
//   loadTheme(path)             reads a theme file and keeps it under its name
//   RavenTheme(theme)           at application level, the theme of the application; inside the
//                               window, the theme of a subtree (the C example's Theme="paper.xml")
//   RavenTheme(accent)          one colour, and the shades that follow from it, from a row of
//                               swatches (the C example's ColorPicker arrives in phase 3)
//   saveTheme(path, name)       writes the theme in use as a theme file
//   colors = ...                the C example's Class="danger"
//   MarkdownText, CodeEditor(language = "Markdown")   Markdown rendered, and styled as you type
//
// A theme file that names no look (tweaks.xml) is laid over the look in use and follows it; one
// that names a look (ocean.xml, paper.xml) imposes it, so choosing a look leaves it.
//
// Raven reloads the theme files when they change (live reload is on here, whatever the build), and
// notes.md is read again every half second. The interface is Kotlin, so the C example's reload of
// themes.xml, and its "Loaded again" status, have no counterpart.

fun main(args: Array<String>) {
    // The folder of the theme files and notes.md: Raven's examples/themes, or the one given.
    val directory = args.firstOrNull() ?: EXAMPLE_DIR
    // Watching the files is what this sample shows, so live reload is on in a release build too.
    application(liveReload = true) {
        ThemesApp(directory, onCloseRequest = ::exitApplication)
    }
}

/** One entry of the look ComboBox: a [look], dark or light. */
data class LookChoice(val name: String, val look: Look, val dark: Boolean)

/** The C example's looks, in its order: each family dark, then light. */
val lookChoices: List<LookChoice> = listOf(
    "Raven" to Look.Raven,
    "Raven glass" to Look.Glass,
    "Raven high contrast" to Look.HighContrast,
    "Windows" to Look.Windows,
    "macOS" to Look.Macos,
    "GNOME" to Look.Gnome,
    "Native" to Look.Native,
    "macOS 26 glass" to Look.MacosGlass,
).flatMap { (name, look) -> listOf(LookChoice("$name dark", look, true), LookChoice("$name light", look, false)) }

/** The accent swatches, standing in for the C example's ColorPicker; the first is its colour. */
val accentSwatches: List<Color> = listOf(
    Color(0xFF22D3EE),
    Color(0xFF3B82F6),
    Color(0xFF8B5CF6),
    Color(0xFFEC4899),
    Color(0xFFF97316),
    Color(0xFF22C55E),
)

/** The link of notes.md that fires the C example's action. */
const val DOCS_LINK: String = "action:themes.docs"

/** The status line after a click on [DOCS_LINK], as in the C example. */
const val DOCS_STATUS: String = "A link of the Markdown text fired the action \"themes.docs\"."

/**
 * Follows a click on [link] in the notes. [DOCS_LINK] is the sample's own, and gives
 * [DOCS_STATUS] back; any other link, a web page or a mailto:, [uriHandler] opens, and this
 * gives null.
 */
fun followLink(link: String, uriHandler: UriHandler): String? {
    if (link == DOCS_LINK) return DOCS_STATUS
    uriHandler.openUri(link)
    return null
}

/** What the Markdown editor starts with, as in the C example. */
const val EDITOR_START: String = "## Try it\n\nType **bold**, *emphasis*, `code`,\n- a list\n- [a link](https://example.org)\n"

/** The names Raven gave the theme files of [directory] when they were loaded. */
private class ThemeFiles(directory: String) {
    val ocean = loadTheme("$directory/ocean.xml")
    val paper = loadTheme("$directory/paper.xml")
    val tweaks = loadTheme("$directory/tweaks.xml")

    /** The theme files that name no look, so Raven keeps them over a new look. */
    val followingTheLook = setOf(tweaks)
}

/**
 * The application: the theme files of [directory] loaded, the look, theme and accent chosen in the
 * window, and the window. Save writes [savePath]; the notes come from [notesPath].
 */
@Composable
fun ThemesApp(
    directory: String,
    onCloseRequest: () -> Unit,
    notesPath: String = "$directory/notes.md",
    savePath: String = "my-theme.xml",
) {
    val files = remember(directory) { ThemeFiles(directory) }
    var lookIndex by remember { mutableIntStateOf(0) }
    var theme by remember { mutableStateOf<String?>(files.ocean) }
    var accent by remember { mutableStateOf<Color?>(null) }
    val look = lookChoices[lookIndex]

    RavenTheme(look = look.look, dark = look.dark, theme = theme, accent = accent) {
        Window(
            onCloseRequest = onCloseRequest,
            state = rememberWindowState(size = DpSize(1180.dp, 760.dp)),
            title = "Raven - themes",
            minimumSize = DpSize(820.dp, 520.dp),
        ) {
            var status by remember { mutableStateOf("") }
            val uriHandler = LocalUriHandler.current
            Row(Modifier.fillMaxSize()) {
                Appearance(
                    lookIndex = lookIndex,
                    onLookIndexChange = {
                        lookIndex = it
                        // As Raven does: a theme that names a look gives way to the new look's own.
                        if (theme !in files.followingTheLook) theme = null
                    },
                    theme = theme,
                    onThemeChange = { theme = it },
                    onAccentChange = { accent = it },
                    onSave = {
                        status = if (saveTheme(savePath, "Mine")) "Written: my-theme.xml (in the working folder)" else "Could not write my-theme.xml"
                    },
                    status = status,
                    modifier = Modifier.width(250.dp).fillMaxHeight(),
                )
                VerticalDivider()
                VerticalScroll(Modifier.weight(1f).fillMaxHeight()) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        ControlsCard()
                        // A part of the window with a theme of its own, whatever the theme of the application.
                        RavenTheme(theme = files.paper) { PaperCard() }
                        RavenTheme(theme = "GlassDark") { GlassCard() }
                    }
                }
                VerticalDivider()
                Notes(notesPath, onLinkClick = { link -> followLink(link, uriHandler)?.let { status = it } }, Modifier.width(400.dp).fillMaxHeight())
            }
        }
    }
}

/** The C example's labels with `Opacity`: the theme's text colour, see-through. */
@Composable
private fun muted(alpha: Float = 0.65f): Color = RavenTheme.colors.text.let { Color(it.red, it.green, it.blue, alpha) }

/** The C example's `Class="eyebrow"` labels: small, in the accent colour. */
@Composable
private fun Eyebrow(text: String, color: Color = RavenTheme.colors.accent) {
    Text(text, color = color, fontSize = 12.sp)
}

/** The left column: the look, the theme, the accent, Save and the status line. */
@Composable
private fun Appearance(
    lookIndex: Int,
    onLookIndexChange: (Int) -> Unit,
    theme: String?,
    onThemeChange: (String) -> Unit,
    onAccentChange: (Color) -> Unit,
    onSave: () -> Unit,
    status: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Eyebrow("APPEARANCE")
        Text("Themes", fontSize = 24.sp)

        Text("Look", Modifier.padding(top = 8.dp), color = muted())
        ComboBox(lookChoices.map { it.name }, lookIndex, onLookIndexChange, Modifier.fillMaxWidth().testTag("looks"))

        Text("Theme file laid over it", Modifier.padding(top = 8.dp), color = muted())
        val names = RavenTheme.themeNames
        ComboBox(
            items = names,
            selectedIndex = names.indexOf(theme),
            onSelectedIndexChange = { if (it >= 0) onThemeChange(RavenTheme.themeNames[it]) },
            modifier = Modifier.fillMaxWidth().testTag("themes"),
        )

        Text("Accent", Modifier.padding(top = 8.dp), color = muted())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (swatch in accentSwatches) {
                Button(
                    onClick = { onAccentChange(swatch) },
                    modifier = Modifier.size(22.dp).tooltip("RavenTheme(accent = $swatch)").testTag("accent $swatch"),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = swatch),
                    contentPadding = PaddingValues(0.dp),
                ) {}
            }
        }

        TextButton(
            onClick = onSave,
            modifier = Modifier.padding(top = 8.dp).tooltip("Writes the theme in use, with the changes made here, as a theme file"),
        ) { Text("Save as my-theme.xml") }
        Text(status, Modifier.fillMaxWidth().testTag("status"), color = muted())

        Spacer(Modifier.weight(1f))
        MarkdownText(
            "Edit **ocean.xml**, **paper.xml**, **tweaks.xml** or **notes.md** while this runs: the window follows.",
            Modifier.fillMaxWidth(),
        )
    }
}

/** A text beside a toggle, as the C example's `Checkbox Text="..."`. */
@Composable
private fun Labelled(text: String, toggle: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        toggle()
        Text(text)
    }
}

/** The C example's `Class="card"`: a panel with large corners. */
@Composable
private fun Card(corner: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(corner.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
    }
}

/** Every control under the theme in use. */
@Composable
private fun ControlsCard() {
    var checkbox by remember { mutableStateOf(true) }
    var switch by remember { mutableStateOf(true) }
    var dangerSwitch by remember { mutableStateOf(true) }
    var radio by remember { mutableIntStateOf(0) }
    var slider by remember { mutableStateOf(0.6f) }
    var text by remember { mutableStateOf("") }
    var choice by remember { mutableIntStateOf(0) }
    var number by remember { mutableIntStateOf(42) }
    var listed by remember { mutableIntStateOf(-1) }
    val danger = RavenTheme.colors.danger

    Card(corner = 18) {
        Eyebrow("THE THEME IN USE")
        Text("Controls", fontSize = 19.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {}) { Text("Primary") }
            OutlinedButton(onClick = {}) { Text("Default") }
            TextButton(onClick = {}) { Text("Flat") }
            Button(
                onClick = {},
                modifier = Modifier.tooltip("colors = ButtonDefaults.buttonColors(containerColor = RavenTheme.colors.danger)").testTag("delete"),
                colors = ButtonDefaults.buttonColors(containerColor = danger),
            ) { Text("Delete") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Labelled("Checkbox") { Checkbox(checkbox, { checkbox = it }) }
            Labelled("Switch") { Switch(switch, { switch = it }) }
            Labelled("Danger") { Switch(dangerSwitch, { dangerSwitch = it }, colors = SwitchDefaults.colors(checkedTrackColor = danger)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Labelled("Radio") { RadioButton(radio == 0, { radio = 0 }) }
            Labelled("Radio") { RadioButton(radio == 1, { radio = 1 }) }
        }
        Slider(slider, { slider = it }, Modifier.fillMaxWidth())
        LinearProgressIndicator(0.4f, Modifier.fillMaxWidth())
        ListView(listOf("First", "Second", "Third"), { it }, listed, { listed = it }, Modifier.fillMaxWidth().height(110.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextField(text, { text = it }, Modifier.weight(1f), placeholder = "Text")
            ComboBox(listOf("One", "Two", "Three"), choice, { choice = it }, Modifier.width(120.dp))
            SpinBox(number, { number = it }, Modifier.width(90.dp))
        }
    }
}

/** paper.xml's danger colour: the card has the Paper theme, whatever the app theme's colours are. */
private val PaperDanger = Color(0xFFB91C1C)

/** paper.xml's accent, which its eyebrow labels take. */
private val PaperAccent = Color(0xFFB45309)

/** A card that keeps the Paper theme. */
@Composable
private fun PaperCard() {
    var switch by remember { mutableStateOf(true) }
    var slider by remember { mutableStateOf(0.3f) }
    Card(corner = 4, Modifier.testTag("paper card")) {
        Eyebrow("RAVENTHEME(THEME = \"PAPER\")", color = PaperAccent)
        Text("A card that keeps its theme", fontSize = 19.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = {}) { Text("Primary") }
            OutlinedButton(onClick = {}) { Text("Default") }
            Button(onClick = {}, colors = ButtonDefaults.buttonColors(containerColor = PaperDanger)) { Text("Delete") }
            Labelled("Switch") { Switch(switch, { switch = it }) }
        }
        Slider(slider, { slider = it }, Modifier.fillMaxWidth())
    }
}

/** A card in the built-in GlassDark theme. */
@Composable
private fun GlassCard() {
    var switch by remember { mutableStateOf(true) }
    var slider by remember { mutableStateOf(0.7f) }
    Card(corner = 18, Modifier.testTag("glass card")) {
        Eyebrow("RAVENTHEME(THEME = \"GLASSDARK\")", color = muted(0.8f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = {}) { Text("Glass") }
            OutlinedButton(onClick = {}) { Text("Default") }
            Labelled("Switch") { Switch(switch, { switch = it }) }
            Slider(slider, { slider = it }, Modifier.weight(1f))
        }
    }
}

/**
 * The right column: notes.md rendered, read again every half second, over a Markdown editor.
 * [onLinkClick] gets the links of the notes.
 */
@Composable
private fun Notes(notesPath: String, onLinkClick: (String) -> Unit, modifier: Modifier = Modifier) {
    var notes by remember { mutableStateOf("") }
    var editor by remember { mutableStateOf(EDITOR_START) }
    // notes.md is not a theme file: the sample reads it itself, again when it changes.
    LaunchedEffect(notesPath) {
        while (true) {
            readTextFile(notesPath)?.let { notes = it }
            delay(500.milliseconds)
        }
    }
    VerticalSplitter(modifier) {
        VerticalScroll(Modifier.weight(3f)) {
            MarkdownText(notes, Modifier.fillMaxWidth().padding(18.dp).testTag("notes"), onLinkClick = onLinkClick)
        }
        CodeEditor(editor, { editor = it }, Modifier.weight(2f).testTag("editor"), language = "Markdown", lineNumbers = false, wordWrap = true)
    }
}
