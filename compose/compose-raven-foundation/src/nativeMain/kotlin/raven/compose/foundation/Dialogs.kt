package raven.compose.foundation

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
import raven.compose.ui.awaitNativeDialog

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
 * Only `native = true` is built for now: Raven's own message box, from its blocking
 * `Rmessage_box`, over the active window. Calls that wait together return in reverse order: the
 * first one opened resumes only once every later one has closed. The default, on `Dialog`, which
 * never blocks, arrives in phase 3 with this same signature; until then a call without
 * `native = true` fails.
 */
public suspend fun messageBox(
    title: String,
    text: String,
    buttons: MessageButtons = MessageButtons.Ok,
    native: Boolean = false,
): MessageResult {
    requireNative(native, "messageBox", "Dialog")
    return awaitNativeDialog { window -> Rmessage_box(window, title, text, buttons.raven).toMessageResult() }
}

/**
 * Asks for a file to open, of one of [filters] (any file when empty), or several with [multiple],
 * and returns their paths; an empty list when the user cancelled. [title] replaces Raven's
 * ("Open"), and the dialog starts in [initialDirectory], else where the last file dialog ended,
 * else the user's home.
 *
 * Only `native = true` is built for now: Raven's blocking `Rfile_dialog_open`, which shows the
 * system's dialog on Windows, and on Linux with zenity or kdialog, and Raven's file browser in a
 * dialog of the active window elsewhere. Calls that wait together return in reverse order. The
 * default, on `Dialog` and `FileBrowser`, arrives in phase 3 with this same signature; until then
 * a call without `native = true` fails.
 */
public suspend fun openFileDialog(
    filters: List<FileFilter> = emptyList(),
    multiple: Boolean = false,
    title: String? = null,
    initialDirectory: String? = null,
    native: Boolean = false,
): List<String> {
    requireNative(native, "openFileDialog", "Dialog and FileBrowser")
    val request = FileDialogRequest(title, initialDirectory, filters, multiple = multiple)
    return awaitNativeDialog { window -> request.run(window, ::Rfile_dialog_open) }
}

/**
 * Asks where to save a file, proposing [fileName], and returns the path; `null` when the user
 * cancelled. A file that exists was confirmed by the user. [defaultExtension] ("txt") is added to
 * a name typed without one. [filters], [title] and [initialDirectory] are as for
 * [openFileDialog] (Raven's title is "Save as").
 *
 * Only `native = true` is built for now, over Raven's blocking `Rfile_dialog_save`, as
 * [openFileDialog] is; a call without it fails until phase 3 brings the default.
 */
public suspend fun saveFileDialog(
    filters: List<FileFilter> = emptyList(),
    title: String? = null,
    initialDirectory: String? = null,
    fileName: String = "",
    defaultExtension: String? = null,
    native: Boolean = false,
): String? {
    requireNative(native, "saveFileDialog", "Dialog and FileBrowser")
    val request = FileDialogRequest(title, initialDirectory, filters, fileName, defaultExtension)
    return awaitNativeDialog { window -> request.run(window, ::Rfile_dialog_save) }.firstOrNull()
}

/**
 * Asks for a folder and returns its path; `null` when the user cancelled. [title] and
 * [initialDirectory] are as for [openFileDialog] (Raven's title is "Select a folder").
 *
 * Only `native = true` is built for now, over Raven's blocking `Rfile_dialog_select_folder`, as
 * [openFileDialog] is; a call without it fails until phase 3 brings the default.
 */
public suspend fun selectFolderDialog(
    title: String? = null,
    initialDirectory: String? = null,
    native: Boolean = false,
): String? {
    requireNative(native, "selectFolderDialog", "Dialog and FileBrowser")
    val request = FileDialogRequest(title, initialDirectory)
    return awaitNativeDialog { window -> request.run(window, ::Rfile_dialog_select_folder) }.firstOrNull()
}

private fun requireNative(native: Boolean, function: String, builtOn: String) {
    if (!native) {
        throw UnsupportedOperationException(
            "$function needs native = true for now: the default, on $builtOn, arrives in phase 3",
        )
    }
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
