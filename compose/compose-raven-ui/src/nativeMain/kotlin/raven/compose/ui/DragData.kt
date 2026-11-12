package raven.compose.ui

/** What another application dropped on a window: see `Window(onExternalDrop)`. */
public sealed interface DragData {
    /** Files and folders, as UTF-8 paths. */
    public data class FilesList(val paths: List<String>) : DragData

    /** Text that is not files. */
    public data class Text(val text: String) : DragData
}
