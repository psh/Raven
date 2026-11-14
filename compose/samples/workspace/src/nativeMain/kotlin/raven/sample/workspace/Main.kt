package raven.sample.workspace

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import raven.compose.foundation.CodeEditor
import raven.compose.foundation.ComboBox
import raven.compose.foundation.Dock
import raven.compose.foundation.DockArea
import raven.compose.foundation.DockAreaState
import raven.compose.foundation.FileBrowser
import raven.compose.foundation.HeaderBar
import raven.compose.foundation.HorizontalDivider
import raven.compose.foundation.Island
import raven.compose.foundation.ListView
import raven.compose.foundation.Text
import raven.compose.foundation.TextButton
import raven.compose.foundation.codeEditorLanguageForFile
import raven.compose.foundation.layout.Arrangement
import raven.compose.foundation.layout.Column
import raven.compose.foundation.layout.fillMaxSize
import raven.compose.foundation.layout.fillMaxWidth
import raven.compose.foundation.layout.padding
import raven.compose.foundation.layout.width
import raven.compose.foundation.rememberDockAreaState
import raven.compose.foundation.rememberListViewState
import raven.compose.ui.Modifier
import raven.compose.ui.Window
import raven.compose.ui.alpha
import raven.compose.ui.application
import raven.compose.ui.graphics.Material
import raven.compose.ui.material
import raven.compose.ui.rememberWindowState
import raven.compose.ui.testTag
import raven.compose.ui.theme.Look
import raven.compose.ui.theme.RavenTheme
import raven.compose.ui.tooltip
import raven.compose.ui.unit.DpSize
import raven.compose.ui.unit.dp
import raven.compose.ui.unit.sp

// A port of Raven's examples/workspace: the frame of a tool, with panels that dock, a file browser
// and materials.
//
//   DockArea       panels arranged by the user; the arrangement is a short text to save and load
//                  (DockAreaState), shown live; panels added and closed by the app's state
//   FileBrowser    the folders and files of the disk, as a widget: a double click on a file opens
//                  it in a new panel
//   materials      RavenTheme(material) for everything, Modifier.material for one panel and what
//                  it contains
//   Island         tells what just happened, then folds back
//   HeaderBar      without background: the bar is the top of the window

fun main() = application {
    WorkspaceApp(onCloseRequest = ::exitApplication)
}

/** The Theme list's choices. */
internal enum class WorkspaceTheme(val text: String, val look: Look, val dark: Boolean) {
    Dark("Dark", Look.Raven, true),
    Light("Light", Look.Raven, false),
    DarkGlass("Dark glass", Look.Glass, true),
    LightGlass("Light glass", Look.Glass, false),
}

/** The material lists' choices: the first is none, which leaves it to the theme (or to everything). */
internal val MaterialChoices = listOf(null, Material.Solid, Material.Glass, Material.FrostedGlass, Material.TintedGlass)

/** A panel of an editor added by the app: a note, or a file opened from the browser. */
internal class EditorPanel(val name: String, val title: String, text: String, val language: String?) {
    var text by mutableStateOf(text)
}

/** The workspace's state: its panels, what the island says, the log, and the layout saved. */
internal class Workspace {
    var welcomeOpen by mutableStateOf(true)
    val editors = mutableStateListOf<EditorPanel>()
    val log = mutableStateListOf("Ready.")
    var saved: String? = null
    private var notes = 0
    private var panels = 0

    var islandTitle by mutableStateOf("Workspace")
    var islandDetail by mutableStateOf("Drag a tab to move its panel.")
    var islandExpanded by mutableStateOf(false)
    var islandTells by mutableIntStateOf(0)
        private set

    /** Logs [title] and [detail], and has the island tell them, then fold back. */
    fun say(title: String, detail: String) {
        log += "$title: $detail"
        islandTitle = title
        islandDetail = detail
        islandExpanded = true
        islandTells++
    }

    fun newNote() {
        val title = "Note ${++notes}"
        editors += EditorPanel("panel${++panels}", title, "", null)
        say("New panel", title)
    }

    /** Opens the file at [path] in a new panel, unless it can't be read or is too large to show. */
    fun open(path: String, language: String?) {
        val name = path.substringAfterLast('/').substringAfterLast('\\')
        val text = readTextFile(path, MAX_FILE)
        when {
            text == null -> say("Cannot open", path)
            text.length > MAX_FILE -> say("Too large to show", name)
            else -> {
                editors += EditorPanel("panel${++panels}", name, text, language)
                say("Opened", name)
            }
        }
    }

    companion object {
        const val MAX_FILE = 2 * 1024 * 1024
    }
}

private const val WELCOME = """/*
 * Drag the tab of a panel:
 *   - onto a tab bar, to put it with the tabs that are there;
 *   - onto the left, right, top or bottom of a panel, to split it;
 *   - onto an edge of the window, to take the whole side.
 * Drag the line between two panels to resize them.
 * Double click a file on the left to open it in a new panel.
 */
int main(void) {
	return 0;
}
"""

/** The application: the theme and the material of everything, which are app-wide, around the window. */
@Composable
internal fun WorkspaceApp(
    onCloseRequest: () -> Unit,
    workspace: Workspace = remember { Workspace() },
    dock: DockAreaState = rememberDockAreaState(),
    directory: String = EXAMPLES_DIR,
) {
    var theme by remember { mutableStateOf(WorkspaceTheme.DarkGlass) }
    var material by remember { mutableStateOf<Material?>(null) }
    RavenTheme(look = theme.look, dark = theme.dark, material = material) {
        Window(
            onCloseRequest = onCloseRequest,
            state = rememberWindowState(size = DpSize(1180.dp, 720.dp)),
            title = "Raven - workspace",
            customHeader = true,
            minimumSize = DpSize(760.dp, 460.dp),
            island = { WorkspaceIsland(workspace) },
        ) {
            Column(Modifier.fillMaxSize()) {
                HeaderBar(Modifier.fillMaxWidth(), title = "", background = false) {
                    TextButton(onClick = {
                        workspace.saved = dock.saveLayout()
                        workspace.say("Layout saved", workspace.saved!!)
                    }, Modifier.tooltip("Remembers how the panels are arranged").testTag("save")) { Text("Save layout") }
                    TextButton(onClick = {
                        val saved = workspace.saved
                        if (saved == null) {
                            workspace.say("Nothing saved yet", "Use \"Save layout\" first.")
                        } else if (dock.loadLayout(saved)) {
                            workspace.say("Layout restored", "The panels fly back to where they were.")
                        }
                    }, Modifier.tooltip("Arranges the panels as they were saved").testTag("restore")) { Text("Restore") }
                    TextButton(onClick = workspace::newNote, Modifier.tooltip("Adds a panel, as a tab of the editor").testTag("note")) { Text("New note") }
                }
                Panels(workspace, dock, directory, theme, { theme = it; material = null }, material, { material = it }, Modifier.weight(1f).fillMaxWidth())
            }
        }
    }
}

@Composable
private fun WorkspaceIsland(workspace: Workspace) {
    LaunchedEffect(workspace.islandTells) {
        if (workspace.islandTells > 0) {
            delay(2000)
            workspace.islandExpanded = false
        }
    }
    Island(
        workspace.islandExpanded, { workspace.islandExpanded = it },
        Modifier.padding(top = 5.dp),
        expandOnClick = false,
        folded = { Text(workspace.islandTitle, Modifier.padding(horizontal = 14.dp, vertical = 5.dp).testTag("island.text")) },
        unfolded = {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp).width(320.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(workspace.islandTitle, fontSize = 16.sp)
                Text(workspace.islandDetail, Modifier.fillMaxWidth().alpha(0.75f), softWrap = true)
            }
        },
    )
}

@Composable
private fun Panels(
    workspace: Workspace,
    dock: DockAreaState,
    directory: String,
    theme: WorkspaceTheme,
    onThemeChange: (WorkspaceTheme) -> Unit,
    material: Material?,
    onMaterialChange: (Material?) -> Unit,
    modifier: Modifier,
) {
    var welcome by remember { mutableStateOf(WELCOME) }
    var panelMaterial by remember { mutableStateOf<Material?>(null) }
    LaunchedEffect(Unit) { dock.showPanel("log") }
    DockArea(dock, modifier.testTag("dock")) {
        // Closed, it is gone: a new panel then takes the middle of the area.
        Panel("welcome", "Welcome", open = workspace.welcomeOpen, onClose = { workspace.welcomeOpen = false }) {
            CodeEditor(welcome, { welcome = it }, Modifier.fillMaxSize(), language = "C")
        }
        Panel("files", "Files", Dock.Left(0.24f), closable = false) {
            val language = { path: String -> codeEditorLanguageForFile(path) }
            FileBrowser(
                Modifier.fillMaxSize().testTag("files"),
                initialDirectory = directory,
                controls = false,
                places = false,
                onAccept = { paths -> paths.firstOrNull()?.let { workspace.open(it, language(it)) } },
            )
        }
        Panel("look", "Appearance", Dock.Right(0.24f)) {
            Column(
                Modifier.fillMaxSize().then(panelMaterial?.let { Modifier.material(it) } ?: Modifier).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Theme")
                ComboBox(WorkspaceTheme.entries.map { it.text }, theme.ordinal, {
                    // A theme comes with its material: the choice below starts over.
                    onThemeChange(WorkspaceTheme.entries[it])
                    workspace.say("Theme", WorkspaceTheme.entries[it].text)
                }, Modifier.fillMaxWidth().testTag("theme"))
                Text("Material of everything", Modifier.padding(top = 6.dp))
                val everything = listOf("As the theme", "Solid", "Glass", "Frosted glass", "Tinted glass")
                ComboBox(everything, MaterialChoices.indexOf(material), {
                    onMaterialChange(MaterialChoices[it])
                    workspace.say("Material of everything", everything[it])
                }, Modifier.fillMaxWidth().testTag("material"))
                Text("Material of this panel only", Modifier.padding(top = 6.dp))
                val panel = listOf("As everything", "Solid", "Glass", "Frosted glass", "Tinted glass")
                ComboBox(panel, MaterialChoices.indexOf(panelMaterial), {
                    panelMaterial = MaterialChoices[it]
                    workspace.say("Material of the panel", panel[it])
                }, Modifier.fillMaxWidth().testTag("panel.material"))
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                Text(
                    "Clear glass shows what is behind it, frosted glass blurs it, tinted glass colors it. A popup is always more " +
                        "opaque than the panel it comes from, and a panel than the bar above it.",
                    Modifier.fillMaxWidth().alpha(0.7f),
                    softWrap = true,
                )
            }
        }
        Panel("log", "Log", Dock.Bottom(0.26f, of = "welcome")) {
            val state = rememberListViewState()
            LaunchedEffect(workspace.log.size) { state.revealItem(workspace.log.lastIndex) }
            ListView(workspace.log, { it }, -1, {}, Modifier.fillMaxSize().testTag("log"), state = state)
        }
        Panel("about", "About", Dock.Tab("log")) {
            Column(Modifier.padding(12.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Panels can share a tab bar: drag this tab away and back.")
                // What would be written to a settings file.
                Text(dock.layout, Modifier.fillMaxWidth().alpha(0.6f).testTag("layout"), softWrap = true)
            }
        }
        for (editor in workspace.editors) {
            key(editor.name) {
                // Beside the editor, as a tab of the same group; the welcome closed, in the middle of the area.
                Panel(editor.name, editor.title, Dock.Tab(if (workspace.welcomeOpen) "welcome" else null), onClose = { workspace.editors -= editor }) {
                    CodeEditor(editor.text, { editor.text = it }, Modifier.fillMaxSize().testTag(editor.name), language = editor.language)
                }
                LaunchedEffect(editor.name) { dock.showPanel(editor.name) }
            }
        }
    }
}
