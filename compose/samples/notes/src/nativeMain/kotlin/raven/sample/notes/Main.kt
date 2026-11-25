package raven.sample.notes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlinx.serialization.Serializable
import raven.compose.foundation.Button
import raven.compose.foundation.Text
import raven.compose.foundation.TextField
import raven.compose.foundation.layout.Arrangement
import raven.compose.foundation.layout.Column
import raven.compose.foundation.layout.Row
import raven.compose.foundation.layout.fillMaxWidth
import raven.compose.foundation.layout.padding
import raven.compose.mcp.McpConfig
import raven.compose.mcp.McpDescription
import raven.compose.mcp.McpHint
import raven.compose.mcp.McpStatus
import raven.compose.mcp.McpTool
import raven.compose.mcp.singleWindowApplication
import raven.compose.ui.Modifier
import raven.compose.ui.WindowState
import raven.compose.ui.input.key.Key
import raven.compose.ui.input.key.KeyEventType
import raven.compose.ui.input.key.onKeyEvent
import raven.compose.ui.semantics.semantics
import raven.compose.ui.testTag
import raven.compose.ui.unit.DpSize
import raven.compose.ui.unit.dp

/** A note of the list. */
class Note(val text: String)

@Serializable
data class AddNote(@McpDescription("The note's text") val text: String)

@Serializable
data class DeleteNote(@McpDescription("The note's position, from 0") val index: Int)

fun main() = singleWindowApplication(
    mcp = McpConfig.fromEnvironment(McpConfig(name = "notes", instructions = "A list of notes: add, list and delete them.")),
    state = WindowState(size = DpSize(360.dp, 320.dp)),
    title = "Notes",
) {
    NotesApp()
}

/** The notepad: a field and Add, the notes with a Delete each, and the agent marker. */
@Composable
fun NotesApp(notes: SnapshotStateList<Note> = remember { mutableStateListOf() }) {
    var draft by remember { mutableStateOf("") }
    NotesTools(notes)
    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            fun add() {
                if (draft.isNotBlank()) notes += Note(draft)
                draft = ""
            }
            TextField(
                draft, { draft = it },
                Modifier.weight(1f).testTag("draft").semantics { contentDescription = "New note" }
                    .onKeyEvent { if (it.type == KeyEventType.KeyDown && it.key == Key.Enter) { add(); true } else false },
                placeholder = "New note",
            )
            Button(onClick = ::add, Modifier.testTag("add")) { Text("Add") }
        }
        Column(Modifier.fillMaxWidth().testTag("notes"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            notes.forEachIndexed { index, note ->
                Row(Modifier.testTag("note-$index"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(note.text, Modifier.weight(1f))
                    Button(onClick = { notes.removeAt(index) }, Modifier.testTag("delete-$index")) { Text("Delete") }
                }
            }
        }
        Text(if (notes.size == 1) "1 note" else "${notes.size} notes", Modifier.testTag("count"))
        McpStatus()
    }
}

/** What an agent can do with the notes, while the notepad is composed. */
@Composable
private fun NotesTools(notes: SnapshotStateList<Note>) {
    McpTool<AddNote>("notes.add", "Add a note at the end of the list", hints = setOf(McpHint.NotDestructive, McpHint.ClosedWorld)) { args ->
        require(args.text.isNotBlank()) { "a note needs some text" }
        notes += Note(args.text)
        "added note ${notes.size - 1}"
    }
    McpTool("notes.list", "The notes, one a line, each after its position", hints = setOf(McpHint.ReadOnly, McpHint.ClosedWorld)) {
        if (notes.isEmpty()) "no notes" else notes.withIndex().joinToString("\n") { (i, note) -> "$i: ${note.text}" }
    }
    McpTool<DeleteNote>("notes.delete", "Delete the note at a position", hints = setOf(McpHint.ClosedWorld)) { args ->
        require(args.index in notes.indices) { "there is no note ${args.index}" }
        val note = notes.removeAt(args.index)
        "deleted \"${note.text}\""
    }
}
