package raven.sample.notepad

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import raven.compose.foundation.CodeEditor
import raven.compose.foundation.CodeEditorLanguage
import raven.compose.foundation.ComboBox
import raven.compose.foundation.Completion
import raven.compose.foundation.EditorMarker
import raven.compose.foundation.FileFilter
import raven.compose.foundation.HeaderBar
import raven.compose.foundation.HorizontalDivider
import raven.compose.foundation.Item
import raven.compose.foundation.LanguageFlag
import raven.compose.foundation.MarkerKind
import raven.compose.foundation.Menu
import raven.compose.foundation.MenuBar
import raven.compose.foundation.MessageButtons
import raven.compose.foundation.MessageResult
import raven.compose.foundation.CheckboxItem
import raven.compose.foundation.Separator
import raven.compose.foundation.Text
import raven.compose.foundation.codeEditorLanguageForFile
import raven.compose.foundation.codeEditorLanguages
import raven.compose.foundation.layout.Arrangement
import raven.compose.foundation.layout.Column
import raven.compose.foundation.layout.Row
import raven.compose.foundation.layout.fillMaxSize
import raven.compose.foundation.layout.fillMaxWidth
import raven.compose.foundation.layout.height
import raven.compose.foundation.layout.padding
import raven.compose.foundation.layout.width
import raven.compose.foundation.messageBox
import raven.compose.foundation.openFileDialog
import raven.compose.foundation.rememberCodeEditorLanguage
import raven.compose.foundation.rememberCodeEditorState
import raven.compose.foundation.saveFileDialog
import raven.compose.ui.Alignment
import raven.compose.ui.DragData
import raven.compose.ui.Modifier
import raven.compose.ui.Window
import raven.compose.ui.application
import raven.compose.ui.graphics.Color
import raven.compose.ui.input.key.Key
import raven.compose.ui.input.key.KeyShortcut
import raven.compose.ui.rememberWindowState
import raven.compose.ui.testTag
import raven.compose.ui.theme.Look
import raven.compose.ui.theme.RavenTheme
import raven.compose.ui.tooltip
import raven.compose.ui.unit.DpSize
import raven.compose.ui.unit.dp

// A port of Raven's examples/notepad: a small text editor under a custom header bar, with File,
// Edit and View menus, a language list (one language declared here, INI), the app's own
// completions, TODO and FIXME markers, the system's file dialogs, "discard the changes?" before
// losing them, and files dropped on the window. Run it with a file to open it: notepad.kexe Main.kt

fun main(args: Array<String>) = application {
    Notepad(initialPath = args.firstOrNull()?.takeUnless { it.startsWith("-") }, onExit = ::exitApplication)
}

/** The View menu's looks. */
enum class ViewLook(val text: String, val look: Look, val dark: Boolean) {
    Dark("Dark", Look.Raven, true),
    Light("Light", Look.Raven, false),
    DarkGlass("Dark glass", Look.Glass, true),
    LightGlass("Light glass", Look.Glass, false),
}

/**
 * The notepad's window, opening [initialPath] if there is one; [onExit] ends the application once
 * the user agreed to lose any unsaved changes. Its `RavenTheme` is application level, so call it in
 * the application's composition.
 */
@Composable
fun Notepad(initialPath: String? = null, onExit: () -> Unit) {
    val scope = rememberCoroutineScope()
    val editor = rememberCodeEditorState()
    // Registered before the languages are listed, so the list has it.
    rememberCodeEditorLanguage(IniLanguage)
    val languages = remember { listOf(PLAIN_TEXT) + codeEditorLanguages() }
    var text by remember { mutableStateOf(WELCOME) }
    var path by remember { mutableStateOf<String?>(null) }
    var modified by remember { mutableStateOf(false) }
    var language by remember { mutableStateOf<String?>("C") }
    var markers by remember { mutableStateOf(scanNotes(WELCOME)) }
    var status by remember { mutableStateOf("Drop a file on the window to open it.") }
    var lineNumbers by remember { mutableStateOf(true) }
    var wordWrap by remember { mutableStateOf(false) }
    var look by remember { mutableStateOf<ViewLook?>(null) }

    fun openPath(file: String) {
        val content = readTextFile(file)
        if (content == null) {
            status = "Cannot read $file"
            return
        }
        text = content
        path = file
        modified = false
        // "main.rs" is Rust, "notes.txt" is nothing in particular
        language = codeEditorLanguageForFile(file)
        markers = scanNotes(content)
        status = "Opened $file"
    }

    /** True when the text can be replaced: nothing to lose, or the user agreed to lose it. */
    suspend fun canDiscard(): Boolean = !modified ||
        messageBox("Unsaved changes", "The text has changes that were not saved. Discard them?", MessageButtons.OkCancel, native = true) ==
        MessageResult.Ok

    suspend fun save(ask: Boolean) {
        val current = path
        val target = if (ask || current == null) {
            saveFileDialog(
                FILE_FILTERS,
                title = "Save the text",
                fileName = current?.let(::fileName) ?: "untitled.txt",
                defaultExtension = "txt",
                native = true,
            ) ?: return
        } else {
            current
        }
        if (!writeTextFile(target, text)) {
            status = "Cannot write $target"
            return
        }
        // only once it is written: a failed Save as leaves the text with the file it had
        if (target != current) {
            path = target
            language = codeEditorLanguageForFile(target)
        }
        modified = false
        status = "Saved $target"
    }

    fun quit() {
        scope.launch { if (canDiscard()) onExit() }
    }

    LaunchedEffect(Unit) { initialPath?.let(::openPath) }
    // scanned once the typing pauses
    LaunchedEffect(text) {
        delay(400)
        markers = scanNotes(text)
    }

    RavenTheme(look = look?.look, dark = look?.dark) {
        Window(
            onCloseRequest = ::quit,
            state = rememberWindowState(size = DpSize(980.dp, 640.dp)),
            title = "${path?.let(::fileName) ?: "Untitled"}${if (modified) " *" else ""} - Raven Notepad",
            customHeader = true,
            minimumSize = DpSize(560.dp, 320.dp),
            onExternalDrop = { data ->
                when (data) {
                    is DragData.FilesList -> data.paths.firstOrNull()?.let { file -> scope.launch { if (canDiscard()) openPath(file) } }
                    is DragData.Text -> editor.insertText(data.text)
                }
            },
        ) {
            Column(Modifier.fillMaxSize()) {
                // the header bar shows the window's title, between the menus and the language
                HeaderBar(Modifier.fillMaxWidth()) {
                    // in the system's menu bar on macOS, as Raven puts the C example's
                    MenuBar(native = true) {
                        Menu("File") {
                            Item("New", onClick = {
                                scope.launch {
                                    if (canDiscard()) {
                                        text = ""
                                        path = null
                                        modified = false
                                        language = null
                                        markers = emptyList()
                                        status = "New text."
                                    }
                                }
                            }, shortcut = KeyShortcut(Key.N, ctrl = true))
                            Item("Open...", onClick = {
                                scope.launch {
                                    if (canDiscard()) openFileDialog(FILE_FILTERS, title = "Open a text", native = true).firstOrNull()?.let(::openPath)
                                }
                            }, shortcut = KeyShortcut(Key.O, ctrl = true))
                            Separator()
                            Item("Save", onClick = { scope.launch { save(ask = false) } }, shortcut = KeyShortcut(Key.S, ctrl = true))
                            Item("Save as...", onClick = { scope.launch { save(ask = true) } }, shortcut = KeyShortcut(Key.S, ctrl = true, shift = true))
                            Separator()
                            Item("Quit", onClick = ::quit, shortcut = KeyShortcut(Key.Q, ctrl = true))
                        }
                        Menu("Edit") {
                            Item("Undo", onClick = editor::undo)
                            Item("Redo", onClick = editor::redo)
                            Separator()
                            Item("Propose words", onClick = editor::showCompletions)
                            Item("Select all", onClick = editor::selectAll)
                        }
                        Menu("View") {
                            CheckboxItem("Line numbers", lineNumbers, { lineNumbers = it })
                            CheckboxItem("Word wrap", wordWrap, { wordWrap = it })
                            Separator()
                            ViewLook.entries.forEach { choice -> Item(choice.text, onClick = { look = choice }) }
                        }
                    }
                    // on the right of the bar, before the buttons of the window
                    ComboBox(
                        languages,
                        selectedIndex = languages.indexOf(language ?: PLAIN_TEXT).coerceAtLeast(0),
                        onSelectedIndexChange = { language = if (it > 0) languages[it] else null },
                        Modifier.align(Alignment.End).padding(horizontal = 8.dp).width(130.dp).height(26.dp)
                            .tooltip("Language of the text").testTag("language"),
                    )
                }

                CodeEditor(
                    text,
                    onValueChange = {
                        if (it != text) {
                            text = it
                            modified = true
                        }
                    },
                    Modifier.fillMaxWidth().weight(1f).testTag("editor"),
                    language = language,
                    lineNumbers = lineNumbers,
                    state = editor,
                    wordWrap = wordWrap,
                    completions = { completionsFor(language) },
                    markers = markers,
                )

                HorizontalDivider()
                StatusLine(status, notes = notesText(markers.size), position = "Line ${editor.cursor.line + 1}, column ${editor.cursor.column + 1}")
            }
        }
    }
}

/** What happened last, the notes in the text, and where the cursor is, in fainter text than the editor's. */
@Composable
private fun StatusLine(status: String, notes: String, position: String) {
    val text = RavenTheme.colors.text
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(status, Modifier.weight(1f).testTag("status"), color = text.faded(0.8f))
        Text(notes, Modifier.testTag("notes"), color = text.faded(0.6f))
        Text(position, Modifier.testTag("position"), color = text.faded(0.6f))
    }
}

private fun Color.faded(opacity: Float) = Color(red, green, blue, alpha * opacity)

private const val PLAIN_TEXT = "Plain text"

/** A language of our own: the editor only needs to be told what its words and comments look like. */
private val IniLanguage = CodeEditorLanguage(
    name = "INI",
    extensions = listOf("ini", "cfg", "conf"),
    constants = listOf("true", "false", "yes", "no", "on", "off"),
    lineComment = ";",
    stringQuotes = "\"",
    flags = setOf(LanguageFlag.IgnoreCase),
)

private val FILE_FILTERS = listOf(
    FileFilter(
        "Text and code",
        "*.txt", "*.md", "*.c", "*.h", "*.cpp", "*.hpp", "*.cs", "*.java", "*.js", "*.ts", "*.py", "*.rs", "*.html", "*.css",
        "*.xml", "*.json", "*.ini",
    ),
    FileFilter("All files", "*"),
)

/** The app's snippets for each language, listed before the editor's own proposals. A language server would answer here. */
private val SNIPPETS = listOf(
    "C" to "printf(\"%d\\n\", );", "C" to "for (int i = 0; i < n; i++) {", "C" to "#include <stdio.h>",
    "C++" to "std::cout << ", "Python" to "print(f\"{}\")", "Python" to "if __name__ == \"__main__\":",
    "Rust" to "println!(\"{}\", );", "JavaScript" to "console.log();", "HTML" to "<!DOCTYPE html>",
    "Java" to "System.out.println();",
)

/** The snippets of [language] and the two notes; the editor keeps only those that start with what is typed. */
private fun completionsFor(language: String?): List<Completion> =
    SNIPPETS.filter { it.first == language }.map { Completion(it.second, "snippet") } +
        Completion("TODO", "note") + Completion("FIXME", "note")

/** A marker for each line that says FIXME, else TODO, at most 200 of them. Columns are bytes, as Raven counts them. */
internal fun scanNotes(text: String): List<EditorMarker> = text.split('\n').asSequence().mapIndexedNotNull { line, content ->
    val fixme = content.indexOf("FIXME")
    val todo = content.indexOf("TODO")
    when {
        fixme >= 0 -> EditorMarker(line, byteColumn(content, fixme), kind = MarkerKind.Warning, message = "Something to fix")
        todo >= 0 -> EditorMarker(line, byteColumn(content, todo), kind = MarkerKind.Info, message = "Something left to do")
        else -> null
    }
}.take(200).toList()

private fun byteColumn(line: String, index: Int) = line.substring(0, index).encodeToByteArray().size

internal fun notesText(count: Int): String = when (count) {
    0 -> ""
    1 -> "1 note"
    else -> "$count notes"
}

internal fun fileName(path: String): String = path.substringAfterLast('/').substringAfterLast('\\')

internal const val WELCOME = """/* Raven Notepad: this text is C, so the editor colors it. */
#include <stdio.h>

/* TODO: type "pri" on an empty line and choose a proposal with Enter */
static int square(int value) {
	return value * value;
}

int main(void) {
	for (int i = 0; i < 4; i++) {
		printf("%d squared is %d\n", i, square(i));
	}
	return 0; /* FIXME: put the cursor after a brace to see the one it closes */
}
"""
