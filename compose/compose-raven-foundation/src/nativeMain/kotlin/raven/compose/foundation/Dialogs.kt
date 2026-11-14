package raven.compose.foundation

import androidx.compose.runtime.Composable
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.cstr
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import raven.cinterop.RFileDialogDesc
import raven.cinterop.RFileFilter
import raven.cinterop.RMessageButtons
import raven.cinterop.RMessageResult
import raven.cinterop.R_FILE_DIALOG_MULTIPLE
import raven.cinterop.R_MESSAGE_OK
import raven.cinterop.R_MESSAGE_OK_CANCEL
import raven.cinterop.R_MESSAGE_RESULT_NO
import raven.cinterop.R_MESSAGE_RESULT_OK
import raven.cinterop.R_MESSAGE_RESULT_YES
import raven.cinterop.R_MESSAGE_YES_NO
import raven.cinterop.R_MESSAGE_YES_NO_CANCEL
import raven.cinterop.Rfile_dialog_get_result
import raven.cinterop.Rfile_dialog_get_result_count
import raven.cinterop.Rfile_dialog_open
import raven.cinterop.Rfile_dialog_save
import raven.cinterop.Rfile_dialog_select_folder
import raven.cinterop.Rid
import raven.cinterop.Rmessage_box
import raven.compose.foundation.layout.Arrangement
import raven.compose.foundation.layout.Column
import raven.compose.foundation.layout.Row
import raven.compose.foundation.layout.padding
import raven.compose.foundation.layout.size
import raven.compose.foundation.layout.widthIn
import raven.compose.ui.Alignment
import raven.compose.ui.Dialog
import raven.compose.ui.Modifier
import raven.compose.ui.awaitNativeDialog
import raven.compose.ui.awaitWindowDialog
import raven.compose.ui.graphics.RoundedCornerShape
import raven.compose.ui.theme.RavenTheme
import raven.compose.ui.unit.dp

/** The buttons of a [messageBox], the main one last. */
public enum class MessageButtons(internal val raven: RMessageButtons) {
    Ok(R_MESSAGE_OK),
    OkCancel(R_MESSAGE_OK_CANCEL),
    YesNo(R_MESSAGE_YES_NO),
    YesNoCancel(R_MESSAGE_YES_NO_CANCEL),
}

/** The answer to a [messageBox]. */
public enum class MessageResult {
    /** The Cancel button, or Escape. Escape answers [No] when there is no Cancel button. */
    Cancel,
    Ok,
    Yes,
    No,
}

/**
 * A kind of file to choose from in a file dialog, shown as [name] ("Images"). Its [patterns]
 * ("*.png", "*.jpg") match file names, and "*" matches every name.
 */
public data class FileFilter(val name: String, val patterns: List<String>) {
    public constructor(name: String, vararg patterns: String) : this(name, patterns.toList())

    init {
        require(patterns.isNotEmpty()) { "the file filter $name has no pattern" }
        require(patterns.none { ';' in it }) { "a pattern of the file filter $name has a ';': give each pattern on its own" }
    }
}

/**
 * Shows [text] under [title] (none when empty) with [buttons], and waits for the answer. Call it
 * from a coroutine, such as `rememberCoroutineScope().launch`; the window doesn't stop recomposing
 * while it waits.
 *
 * By default it is a [Dialog] over the active window, which never blocks: Escape answers Cancel, or
 * No when there is no Cancel button. With `native = true` it is Raven's own message box, from its
 * blocking `Rmessage_box`; calls that wait together that way return in reverse order, the first one
 * opened resuming only once every later one has closed. Cancelling the call takes the dialog away.
 */
public suspend fun messageBox(
    title: String,
    text: String,
    buttons: MessageButtons = MessageButtons.Ok,
    native: Boolean = false,
): MessageResult {
    if (native) return awaitNativeDialog { window -> Rmessage_box(window, title, text, buttons.raven).toMessageResult() }
    return awaitWindowDialog { answer -> MessageBoxDialog(title, text, buttons, answer) }
}

/**
 * Asks for a file to open, of one of [filters] (any file when empty), or several with [multiple],
 * and returns their paths; an empty list when the user cancelled. [title] replaces the default
 * ("Open"), and the dialog starts in [initialDirectory], else where the last file dialog ended,
 * else the user's home.
 *
 * By default it is a [Dialog] holding a [FileBrowser] over the active window, which never blocks.
 * With `native = true` it is Raven's blocking `Rfile_dialog_open`, which shows the system's dialog
 * on Windows, and on Linux with zenity or kdialog, and Raven's file browser in a dialog elsewhere;
 * calls that wait together that way return in reverse order.
 */
public suspend fun openFileDialog(
    filters: List<FileFilter> = emptyList(),
    multiple: Boolean = false,
    title: String? = null,
    initialDirectory: String? = null,
    native: Boolean = false,
): List<String> {
    val request = FileDialogRequest(title, initialDirectory, filters, multiple = multiple)
    if (native) return awaitNativeDialog { window -> request.run(window, ::Rfile_dialog_open) }
    return awaitWindowDialog { answer -> FileDialog(request, FileBrowserMode.Open, "Open", answer) }
}

/**
 * Asks where to save a file, proposing [fileName], and returns the path; `null` when the user
 * cancelled. A file that exists was confirmed by the user. [defaultExtension] ("txt") is added to
 * a name typed without one. [filters], [title] and [initialDirectory] are as for
 * [openFileDialog] (the default title is "Save as"), and so is [native], with Raven's blocking
 * `Rfile_dialog_save`.
 */
public suspend fun saveFileDialog(
    filters: List<FileFilter> = emptyList(),
    title: String? = null,
    initialDirectory: String? = null,
    fileName: String = "",
    defaultExtension: String? = null,
    native: Boolean = false,
): String? {
    val request = FileDialogRequest(title, initialDirectory, filters, fileName, defaultExtension)
    if (native) return awaitNativeDialog { window -> request.run(window, ::Rfile_dialog_save) }.firstOrNull()
    return awaitWindowDialog<List<String>> { answer -> FileDialog(request, FileBrowserMode.Save, "Save as", answer) }.firstOrNull()
}

/**
 * Asks for a folder and returns its path; `null` when the user cancelled. [title] and
 * [initialDirectory] are as for [openFileDialog] (the default title is "Select a folder"), and so
 * is [native], with Raven's blocking `Rfile_dialog_select_folder`.
 */
public suspend fun selectFolderDialog(
    title: String? = null,
    initialDirectory: String? = null,
    native: Boolean = false,
): String? {
    val request = FileDialogRequest(title, initialDirectory)
    if (native) return awaitNativeDialog { window -> request.run(window, ::Rfile_dialog_select_folder) }.firstOrNull()
    return awaitWindowDialog<List<String>> { answer -> FileDialog(request, FileBrowserMode.Folder, "Select a folder", answer) }.firstOrNull()
}

/** The labels of [MessageButtons]' buttons and their answers, the main one last. */
private val MessageButtons.answers: List<Pair<String, MessageResult>>
    get() = when (this) {
        MessageButtons.Ok -> listOf("OK" to MessageResult.Ok)
        MessageButtons.OkCancel -> listOf("Cancel" to MessageResult.Cancel, "OK" to MessageResult.Ok)
        MessageButtons.YesNo -> listOf("No" to MessageResult.No, "Yes" to MessageResult.Yes)
        MessageButtons.YesNoCancel -> listOf("Cancel" to MessageResult.Cancel, "No" to MessageResult.No, "Yes" to MessageResult.Yes)
    }

/** A message box's sheet: its title, its text, and its buttons on the right, the main one last. */
@Composable
private fun MessageBoxDialog(title: String, text: String, buttons: MessageButtons, answer: (MessageResult) -> Unit) {
    val escape = if (buttons == MessageButtons.YesNo) MessageResult.No else MessageResult.Cancel
    Dialog(onDismissRequest = { answer(escape) }) {
        DialogSheet {
            Column(Modifier.padding(20.dp).widthIn(min = 280.dp, max = 460.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (title.isNotEmpty()) Text(title, style = RavenTheme.typography.title)
                Text(text, softWrap = true)
                Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val answers = buttons.answers
                    answers.forEachIndexed { i, (label, result) ->
                        if (i == answers.lastIndex) {
                            Button(onClick = { answer(result) }) { Text(label) }
                        } else {
                            OutlinedButton(onClick = { answer(result) }) { Text(label) }
                        }
                    }
                }
            }
        }
    }
}

// Where the last default file dialog ended, for the next one given no folder.
private var lastDirectory: String? = null

/** A file dialog's sheet: its title over a [FileBrowser] in [mode], whose buttons answer. */
@Composable
private fun FileDialog(request: FileDialogRequest, mode: FileBrowserMode, defaultTitle: String, answer: (List<String>) -> Unit) {
    val chosen: (List<String>) -> Unit = { paths ->
        paths.firstOrNull()?.let { path -> lastDirectory = if (mode == FileBrowserMode.Folder) path else path.substringBeforeLast('/', path) }
        answer(paths)
    }
    Dialog(onDismissRequest = { answer(emptyList()) }) {
        DialogSheet {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(request.title ?: defaultTitle, style = RavenTheme.typography.title)
                FileBrowser(
                    Modifier.size(640.dp, 420.dp),
                    mode = mode,
                    initialDirectory = request.initialDirectory ?: lastDirectory,
                    filters = request.filters,
                    multiple = request.multiple,
                    fileName = request.fileName,
                    defaultExtension = request.defaultExtension,
                    onAccept = chosen,
                    onCancel = { answer(emptyList()) },
                )
            }
        }
    }
}

/** The surface a default dialog's content is on, as a popup's. */
@Composable
private fun DialogSheet(content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(14.dp), shadowElevation = 24.dp, content = content)
}

/** Raven's answer to `Rmessage_box`. */
internal fun RMessageResult.toMessageResult(): MessageResult = when (this) {
    R_MESSAGE_RESULT_OK -> MessageResult.Ok
    R_MESSAGE_RESULT_YES -> MessageResult.Yes
    R_MESSAGE_RESULT_NO -> MessageResult.No
    else -> MessageResult.Cancel
}

/** What a file dialog is asked, as Raven's `RFileDialogDesc` holds it. */
internal class FileDialogRequest(
    val title: String?,
    val initialDirectory: String?,
    val filters: List<FileFilter> = emptyList(),
    val fileName: String = "",
    val defaultExtension: String? = null,
    val multiple: Boolean = false,
) {
    /** Each filter's patterns as Raven takes them: "*.png;*.jpg". */
    val ravenPatterns: List<String> get() = filters.map { it.patterns.joinToString(";") }

    /**
     * Shows the dialog [open] makes over [window], and returns the paths chosen, copied before
     * the next file dialog can replace them: none when the user cancelled.
     */
    fun run(window: Rid, open: (CPointer<RFileDialogDesc>) -> CPointer<ByteVar>?): List<String> = memScoped {
        val ravenFilters = if (filters.isEmpty()) null else allocArray<RFileFilter>(filters.size)
        filters.zip(ravenPatterns).forEachIndexed { i, (filter, patterns) ->
            ravenFilters!![i].name = filter.name.cstr.ptr
            ravenFilters[i].patterns = patterns.cstr.ptr
        }
        val filterCount = filters.size
        val desc = alloc<RFileDialogDesc>().apply {
            parent = window
            title = this@FileDialogRequest.title?.cstr?.ptr
            directory = initialDirectory?.cstr?.ptr
            file_name = fileName.ifEmpty { null }?.cstr?.ptr
            filters = ravenFilters
            filter_count = filterCount
            default_extension = defaultExtension?.cstr?.ptr
            flags = if (multiple) R_FILE_DIALOG_MULTIPLE else 0u
        }
        if (open(desc.ptr) == null) return@memScoped emptyList()
        List(Rfile_dialog_get_result_count()) { Rfile_dialog_get_result(it)!!.toKString() }
    }
}
