package raven.compose.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.cstr
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import raven.cinterop.RFileBrowserMode
import raven.cinterop.RFileFilter
import raven.cinterop.R_FALSE
import raven.cinterop.R_FILE_BROWSER_FOLDER
import raven.cinterop.R_FILE_BROWSER_OPEN
import raven.cinterop.R_FILE_BROWSER_SAVE
import raven.cinterop.R_TRUE
import raven.cinterop.R_WIDGET_FILE_BROWSER
import raven.cinterop.Rfile_browser_set_controls_visible
import raven.cinterop.Rfile_browser_set_default_extension
import raven.cinterop.Rfile_browser_set_directory
import raven.cinterop.Rfile_browser_set_file_name
import raven.cinterop.Rfile_browser_set_filters
import raven.cinterop.Rfile_browser_set_mode
import raven.cinterop.Rfile_browser_set_multiple
import raven.cinterop.Rfile_browser_set_places_visible
import raven.cinterop.Rfile_browser_set_show_hidden
import raven.compose.ui.FileBrowserAccepted
import raven.compose.ui.FileBrowserCancelled
import raven.compose.ui.FileBrowserSelectionChanged
import raven.compose.ui.Modifier
import raven.compose.ui.Property
import raven.compose.ui.RavenWidget
import raven.compose.ui.WidgetDescriptor
import raven.compose.ui.bind
import raven.compose.ui.layoutWarningSink

/** What a [FileBrowser] chooses. */
public enum class FileBrowserMode(internal val raven: RFileBrowserMode) {
    /** An existing file. */
    Open(R_FILE_BROWSER_OPEN),

    /** Where to save: a name is typed, and an existing file must be confirmed. */
    Save(R_FILE_BROWSER_SAVE),

    /** A folder. */
    Folder(R_FILE_BROWSER_FOLDER),
}

private val FileBrowserWidget = WidgetDescriptor(
    "FileBrowser", R_WIDGET_FILE_BROWSER, events = listOf(FileBrowserAccepted, FileBrowserCancelled, FileBrowserSelectionChanged),
)

private fun Boolean.toRaven() = if (this) R_TRUE else R_FALSE

private val Mode = Property<FileBrowserMode> { widget, mode -> Rfile_browser_set_mode(widget, mode.raven) }
private val Multiple = Property<Boolean> { widget, multiple -> Rfile_browser_set_multiple(widget, multiple.toRaven()) }
private val ShowHidden = Property<Boolean> { widget, show -> Rfile_browser_set_show_hidden(widget, show.toRaven()) }
private val Controls = Property<Boolean> { widget, visible -> Rfile_browser_set_controls_visible(widget, visible.toRaven()) }
private val Places = Property<Boolean> { widget, visible -> Rfile_browser_set_places_visible(widget, visible.toRaven()) }
private val FileName = Property<String> { widget, name -> Rfile_browser_set_file_name(widget, name) }
private val DefaultExtension = Property<String?> { widget, extension -> Rfile_browser_set_default_extension(widget, extension) }

private val InitialDirectory = Property<String?> { widget, directory ->
    if (directory != null && Rfile_browser_set_directory(widget, directory) != R_TRUE) {
        layoutWarningSink("FileBrowser: the folder $directory can't be read")
    }
}

/** Raven copies the strings, so they live only for the call. */
private val Filters = Property<List<FileFilter>> { widget, filters ->
    memScoped {
        val ravenFilters = allocArray<RFileFilter>(filters.size.coerceAtLeast(1))
        filters.forEachIndexed { i, filter ->
            ravenFilters[i].name = filter.name.cstr.ptr
            ravenFilters[i].patterns = filter.patterns.joinToString(";").cstr.ptr
        }
        Rfile_browser_set_filters(widget, if (filters.isEmpty()) null else ravenFilters, filters.size)
    }
}

/**
 * Raven's file browser, as a widget to put in any layout: the folder it shows is Raven's, starting at
 * [initialDirectory] (the user's home when null; "~" is it too). In [mode], the user chooses files of
 * one of [filters] (any file when empty), several with [multiple], and [onAccept] gets the paths
 * chosen: by a double click, Enter, or the Open or Save button. [onCancel] gets the Cancel button,
 * and [onSelectionChange] the paths selected each time they change. Without [controls] the name
 * field, the filters and the buttons are hidden, leaving an explorer for a side panel; without
 * [places] the list of places is. In the Save mode, [fileName] is the name proposed and
 * [defaultExtension] is added to a name typed without one.
 */
@Composable
public fun FileBrowser(
    modifier: Modifier = Modifier,
    mode: FileBrowserMode = FileBrowserMode.Open,
    initialDirectory: String? = null,
    filters: List<FileFilter> = emptyList(),
    multiple: Boolean = false,
    showHidden: Boolean = false,
    controls: Boolean = true,
    places: Boolean = true,
    fileName: String = "",
    defaultExtension: String? = null,
    onAccept: (List<String>) -> Unit = {},
    onCancel: () -> Unit = {},
    onSelectionChange: (List<String>) -> Unit = {},
) {
    // Raven's from then on: the user goes from folder to folder.
    val directory = remember { initialDirectory }
    RavenWidget(FileBrowserWidget, modifier, {
        bind(mode, Mode)
        bind(filters, Filters)
        bind(multiple, Multiple)
        bind(showHidden, ShowHidden)
        bind(controls, Controls)
        bind(places, Places)
        bind(fileName, FileName)
        bind(defaultExtension, DefaultExtension)
        bind(directory, InitialDirectory)
        bind(onAccept, FileBrowserAccepted)
        bind(onCancel, FileBrowserCancelled)
        bind(onSelectionChange, FileBrowserSelectionChanged)
    })
}
