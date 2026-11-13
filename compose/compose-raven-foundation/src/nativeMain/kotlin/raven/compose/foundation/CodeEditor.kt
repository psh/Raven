package raven.compose.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import raven.cinterop.RLanguageDesc
import raven.cinterop.RMarkerKind
import raven.cinterop.R_FALSE
import raven.cinterop.R_LANGUAGE_CHAR_LITERALS
import raven.cinterop.R_LANGUAGE_EMBEDS
import raven.cinterop.R_LANGUAGE_FORMATTED_TEXT
import raven.cinterop.R_LANGUAGE_IGNORE_CASE
import raven.cinterop.R_LANGUAGE_INDENT_ON_COLON
import raven.cinterop.R_LANGUAGE_MARKUP
import raven.cinterop.R_LANGUAGE_NESTED_COMMENTS
import raven.cinterop.R_LANGUAGE_STYLE_SHEET
import raven.cinterop.R_MARKER_ERROR
import raven.cinterop.R_MARKER_INFO
import raven.cinterop.R_MARKER_WARNING
import raven.cinterop.R_TRUE
import raven.cinterop.R_WIDGET_TEXT_EDITOR
import raven.cinterop.Rid
import raven.cinterop.Ris_ui_thread
import raven.cinterop.Rlanguage_for_file
import raven.cinterop.Rlanguage_get_count
import raven.cinterop.Rlanguage_get_name
import raven.cinterop.Rlanguage_register
import raven.cinterop.Rtext_editor_add_completion
import raven.cinterop.Rtext_editor_add_marker
import raven.cinterop.Rtext_editor_clear_markers
import raven.cinterop.Rtext_editor_get_cursor
import raven.cinterop.Rtext_editor_get_marker_count
import raven.cinterop.Rtext_editor_insert_text
import raven.cinterop.Rtext_editor_redo
import raven.cinterop.Rtext_editor_select_all
import raven.cinterop.R_COLOR_TEXT
import raven.cinterop.Rtext_editor_set_font
import raven.cinterop.Rtext_editor_set_language
import raven.cinterop.Rtext_editor_set_line_numbers
import raven.cinterop.Rtext_editor_set_placeholder
import raven.cinterop.Rtext_editor_set_read_only
import raven.cinterop.Rtext_editor_set_word_wrap
import raven.cinterop.Rtext_editor_show_completions
import raven.cinterop.Rtext_editor_undo
import raven.compose.ui.Modifier
import raven.compose.ui.Property
import raven.compose.ui.text.TextStyle
import raven.compose.ui.theme.RavenTheme
import raven.compose.ui.text.font.BaseFont
import raven.compose.ui.text.font.rememberRavenFont
import raven.compose.ui.unit.TextUnit
import raven.compose.ui.unit.isSpecified
import raven.compose.ui.RavenWidget
import raven.compose.ui.TextEditorChanged
import raven.compose.ui.TextEditorCompletion
import raven.compose.ui.TextEditorText
import raven.compose.ui.WidgetDescriptor
import raven.compose.ui.WidgetFollower
import raven.compose.ui.bind
import raven.compose.ui.rememberPerHost
import raven.native.toRavenString

private val CodeEditorWidget = WidgetDescriptor(
    "CodeEditor",
    R_WIDGET_TEXT_EDITOR,
    events = listOf(TextEditorChanged, TextEditorCompletion),
)

/**
 * Raven keeps the old language when it doesn't know a name, and warns. Clearing it first makes
 * an unknown name plain text instead.
 */
private val Language = Property<String?> { widget, name ->
    Rtext_editor_set_language(widget, null)
    if (!name.isNullOrEmpty()) Rtext_editor_set_language(widget, name)
}
private val EditorReadOnly = Property<Boolean> { widget, readOnly -> Rtext_editor_set_read_only(widget, if (readOnly) R_TRUE else R_FALSE) }
private val LineNumbers = Property<Boolean> { widget, shown -> Rtext_editor_set_line_numbers(widget, if (shown) R_TRUE else R_FALSE) }
private val WordWrap = Property<Boolean> { widget, wrapped -> Rtext_editor_set_word_wrap(widget, if (wrapped) R_TRUE else R_FALSE) }
private val EditorPlaceholder = Property<String> { widget, text -> editorPlaceholderSetter(widget, text) }

/** R_INVALID_ID is the theme's font, or the fixed-width one with a language; 0 the theme's size. */
private val EditorFont = Property<Pair<Rid, TextUnit>> { widget, (font, size) ->
    Rtext_editor_set_font(widget, font, if (size.isSpecified) size.value else 0f)
}

/** Sets an editor's placeholder. Raven has no getter for it, so tests wrap this to record it. */
internal var editorPlaceholderSetter: (Rid, String) -> Unit = ::Rtext_editor_set_placeholder

/**
 * A multi-line editor showing [value], controlled as [TextField] is: each edit calls
 * [onValueChange] with the whole text. Text the app rewrites moves the caret to the end and clears
 * the editor's undo history.
 *
 * [language] is one of Raven's languages, by name ("Kotlin", "XML"), usual short name ("js") or
 * file extension ("rs"), or one [rememberCodeEditorLanguage] registered. An unknown name shows
 * plain text, and Raven warns.
 *
 * [state] runs the editor's commands and reports its cursor. Long lines continue on the next row
 * with [wordWrap]. [placeholder] is shown, dimmed, while the text is empty. [completions] adds the
 * app's proposals for the start of the word being typed, listed before Raven's own: it is called
 * while Raven gathers them, so it must be fast.
 *
 * [markers] underline parts of the text, and are controlled: Raven drops a marker whose line is
 * edited, and after each edit the editor shows the composed list again, so a marker stays where
 * the app put it until the app composes a new list.
 *
 * [textStyle] gives the text its font, size and colour; the default is the typography's `code`, the
 * theme's size in the fixed-width font. Bold or italic without a family is the fixed-width font's. The editor has no
 * alignment, so `textAlign` is ignored.
 */
@Composable
public fun CodeEditor(
    value: String,
    onValueChange: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    language: String? = null,
    readOnly: Boolean = false,
    lineNumbers: Boolean = true,
    state: CodeEditorState = rememberCodeEditorState(),
    wordWrap: Boolean = false,
    placeholder: String = "",
    completions: ((prefix: String) -> List<Completion>)? = null,
    markers: List<EditorMarker> = emptyList(),
    textStyle: TextStyle = RavenTheme.typography.code,
) {
    val follower = remember { EditorFollower() }
    val font = rememberRavenFont(textStyle.fontFamily, textStyle.fontWeight, textStyle.fontStyle, BaseFont.Monospace)
    val textColor = roleColors(R_COLOR_TEXT to textStyle.color)
    RavenWidget(CodeEditorWidget, modifier, {
        bind(follower)
        bind(font to textStyle.fontSize, EditorFont)
        bind(textColor, WidgetColors)
        set(state) { follower.state = it }
        bind(language, Language)
        bind(readOnly, EditorReadOnly)
        bind(lineNumbers, LineNumbers)
        bind(wordWrap, WordWrap)
        bind(placeholder, EditorPlaceholder)
        bind(value, TextEditorText)
        // Writing the text drops every marker, so they are shown again after it.
        set(markers) { follower.markers = it }
        set(value to markers) { follower.showMarkers() }
        bind({ text: String ->
            follower.showMarkers()
            onValueChange(text)
        }, TextEditorChanged)
        bind(completions?.let { complete ->
            { editor: Rid, prefix: String ->
                complete(prefix).forEach { Rtext_editor_add_completion(editor, it.text, it.detail) }
            }
        }, TextEditorCompletion)
    })
}

/** A place in a [CodeEditor]'s text: a [line] and a [column], both from 0, the column in UTF-8 bytes. */
public data class TextPosition(val line: Int, val column: Int)

/**
 * A [CodeEditor]'s commands, and its [cursor]. It holds no text: the editor shows its `value`.
 * Commands on a state no editor shows do nothing.
 */
@Stable
public class CodeEditorState internal constructor() {
    private var editor: Rid? = null

    /** Where the editor's cursor is, read once a frame: its readers recompose only when it moves. */
    public var cursor: TextPosition by mutableStateOf(TextPosition(0, 0))
        private set

    /** Undoes the last edit, which calls `onValueChange`. */
    public fun undo() {
        editor?.let(::Rtext_editor_undo)
    }

    /** Redoes the last edit undone, which calls `onValueChange`. */
    public fun redo() {
        editor?.let(::Rtext_editor_redo)
    }

    public fun selectAll() {
        editor?.let(::Rtext_editor_select_all)
    }

    /** Proposes words for what is before the cursor, as Ctrl+Space does. */
    public fun showCompletions() {
        editor?.let(::Rtext_editor_show_completions)
    }

    /** Types [text] at the cursor, replacing the selection, as the user would: it can be undone, and it calls `onValueChange`. */
    public fun insertText(text: String) {
        editor?.let { Rtext_editor_insert_text(it, text) }
    }

    internal fun attach(widget: Rid) {
        editor = widget
        followCursor(widget)
    }

    internal fun detach(widget: Rid) {
        if (editor == widget) editor = null
    }

    internal fun followCursor(widget: Rid) {
        val position = memScoped {
            val line = alloc<IntVar>()
            val column = alloc<IntVar>()
            Rtext_editor_get_cursor(widget, line.ptr, column.ptr)
            TextPosition(line.value, column.value)
        }
        if (position != cursor) cursor = position
    }
}

/** A [CodeEditorState] remembered across recompositions, for a [CodeEditor]. */
@Composable
public fun rememberCodeEditorState(): CodeEditorState = remember { CodeEditorState() }

/** A word a [CodeEditor] proposes, with a few words of [detail] beside it ("function", "int"). */
public data class Completion(val text: String, val detail: String? = null)

/** What an [EditorMarker] says about the text it underlines. */
public enum class MarkerKind(internal val raven: RMarkerKind) {
    Error(R_MARKER_ERROR),
    Warning(R_MARKER_WARNING),
    Info(R_MARKER_INFO),
}

/**
 * A part of [line] underlined in a [CodeEditor], from [fromColumn] to [toColumn] (null: the end of
 * the line), with a [message] shown while the pointer rests there.
 */
public data class EditorMarker(
    val line: Int,
    val fromColumn: Int,
    val toColumn: Int? = null,
    val kind: MarkerKind,
    val message: String,
)

/**
 * Follows a [CodeEditor]'s widget: points its [state] at it, reads its cursor once a frame, and
 * shows its composed [markers] again when Raven dropped them.
 */
private class EditorFollower : WidgetFollower {
    private var editor: Rid? = null

    var state: CodeEditorState? = null
        set(value) {
            if (value === field) return
            editor?.let { widget ->
                field?.detach(widget)
                value?.attach(widget)
            }
            field = value
        }

    var markers: List<EditorMarker> = emptyList()

    override fun attach(widget: Rid) {
        editor = widget
        state?.attach(widget)
    }

    // An edit shows the markers again at once; a text the host wrote back is caught here.
    override fun follow(widget: Rid) {
        state?.followCursor(widget)
        if (Rtext_editor_get_marker_count(widget) != markers.size) showMarkers()
    }

    override fun detach(widget: Rid) {
        state?.detach(widget)
        editor = null
    }

    /** Shows exactly the composed markers. */
    fun showMarkers() {
        val widget = editor ?: return
        Rtext_editor_clear_markers(widget)
        markers.forEach { Rtext_editor_add_marker(widget, it.line, it.fromColumn, it.toColumn ?: -1, it.kind.raven, it.message) }
    }
}

/**
 * The names of Raven's editor languages, each one a [CodeEditor] `language`. Raven knows them
 * only while it runs, so this is valid only inside the composition, on Raven's UI thread.
 */
public fun codeEditorLanguages(): List<String> {
    check(Ris_ui_thread() == R_TRUE) { "codeEditorLanguages() is valid only inside the composition, on Raven's UI thread" }
    return List(Rlanguage_get_count()) { Rlanguage_get_name(it).toRavenString() }
}

/**
 * The name of the language of a file, from the extension of [path], or null when no language
 * has it. Valid only inside the composition, on Raven's UI thread.
 */
public fun codeEditorLanguageForFile(path: String): String? {
    check(Ris_ui_thread() == R_TRUE) { "codeEditorLanguageForFile() is valid only inside the composition, on Raven's UI thread" }
    return Rlanguage_for_file(path)?.toKString()
}

/** What a [CodeEditorLanguage] is like, beyond its words. */
public enum class LanguageFlag(internal val raven: UInt) {
    /** Tags and attributes instead of code (XML, HTML): the words and the comments are not used. */
    Markup(R_LANGUAGE_MARKUP),

    /** Selectors, properties and values (CSS). */
    StyleSheet(R_LANGUAGE_STYLE_SHEET),

    /** A block comment may hold another one (Rust). */
    NestedComments(R_LANGUAGE_NESTED_COMMENTS),

    /** Between single quotes there is one character (C, Java, Rust). */
    CharLiterals(R_LANGUAGE_CHAR_LITERALS),

    /** A line that ends with ':' indents the next one (Python). */
    IndentOnColon(R_LANGUAGE_INDENT_ON_COLON),

    /** The words are recognised whatever their case. */
    IgnoreCase(R_LANGUAGE_IGNORE_CASE),

    /** Markup that holds a script language and CSS (HTML), the script's language in [CodeEditorLanguage.scriptLanguage]. */
    Embeds(R_LANGUAGE_EMBEDS),

    /** Formatted text written in Markdown, shown as it is written with its formatting. */
    FormattedText(R_LANGUAGE_FORMATTED_TEXT),
}

/**
 * A language for [CodeEditor], as Raven describes one: its [name] (compared without case), the
 * [extensions] of its files ("ini"), its [keywords], [types] and [constants], how its comments
 * and texts are written, what starts a [directive] ("#") or an [annotation] ("@"), and its
 * [flags]. [multilineStrings] are the marks of texts that span lines; one whose end differs from
 * its start is written around three dots: `R"(...)"`.
 */
public data class CodeEditorLanguage(
    val name: String,
    val extensions: List<String> = emptyList(),
    val keywords: List<String> = emptyList(),
    val types: List<String> = emptyList(),
    val constants: List<String> = emptyList(),
    val lineComment: String? = null,
    val blockCommentStart: String? = null,
    val blockCommentEnd: String? = null,
    val stringQuotes: String? = null,
    val multilineStrings: List<String> = emptyList(),
    val directive: String? = null,
    val annotation: String? = null,
    val flags: Set<LanguageFlag> = emptySet(),
    val scriptLanguage: String? = null,
) {
    init {
        require(name.isNotEmpty()) { "a language needs a name" }
    }
}

/**
 * Registers [language] with Raven once per run of Raven, and returns its name, for a [CodeEditor]
 * `language`. It replaces a language of the same name, built-in ones included, and lasts until
 * Raven shuts down: Raven can't unregister one. [codeEditorLanguages] read after it lists it.
 */
@Composable
public fun rememberCodeEditorLanguage(language: CodeEditorLanguage): String =
    rememberPerHost(RegisteredLanguage(language)) {
        register(language)
        language.name
    }

/** The key of a registered language in the host's caches. */
private data class RegisteredLanguage(val language: CodeEditorLanguage)

private fun register(language: CodeEditorLanguage) = memScoped {
    fun List<String>.words(separator: String) = if (isEmpty()) null else joinToString(separator).cstr.ptr
    val desc = alloc<RLanguageDesc>().apply {
        name = language.name.cstr.ptr
        extensions = language.extensions.words(";")
        keywords = language.keywords.words(" ")
        types = language.types.words(" ")
        constants = language.constants.words(" ")
        line_comment = language.lineComment?.cstr?.ptr
        block_comment_start = language.blockCommentStart?.cstr?.ptr
        block_comment_end = language.blockCommentEnd?.cstr?.ptr
        string_quotes = language.stringQuotes?.cstr?.ptr
        multiline_strings = language.multilineStrings.words(" ")
        directive = language.directive?.cstr?.ptr
        annotation = language.annotation?.cstr?.ptr
        flags = language.flags.fold(0u) { all, flag -> all or flag.raven }
        script_language = language.scriptLanguage?.cstr?.ptr
    }
    check(Rlanguage_register(desc.ptr) == R_TRUE) { "Raven refused the language ${language.name}" }
}
