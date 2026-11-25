package raven.compose.ui.platform

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import raven.cinterop.Rclipboard_get_text
import raven.cinterop.Rclipboard_set_text
import raven.native.toRavenString

/**
 * The system clipboard's text, read and written as an app's copy and paste do. Compose's
 * `ClipboardManager` has the same three members, with `AnnotatedString` where compose-raven, which
 * has no `AnnotatedString`, takes a `String`.
 */
public interface ClipboardManager {
    /** Puts [text] on the clipboard, replacing what was there. */
    public fun setText(text: String)

    /** The clipboard's text, or null when it holds none. */
    public fun getText(): String?

    /** Whether the clipboard holds text. */
    public fun hasText(): Boolean = !getText().isNullOrEmpty()
}

/** The [ClipboardManager] of the content: the host's uses Raven's, and an app or a test provides its own. */
public val LocalClipboardManager: ProvidableCompositionLocal<ClipboardManager> = staticCompositionLocalOf { error("no Window") }

/**
 * The host's [ClipboardManager]: `Rclipboard_set_text` and `Rclipboard_get_text`, the clipboard the
 * text fields' and editors' own copy and paste use. Raven reads an empty clipboard as "", which
 * this reads as null, as Compose does.
 */
internal object RavenClipboardManager : ClipboardManager {
    override fun setText(text: String) {
        Rclipboard_set_text(text)
    }

    override fun getText(): String? = Rclipboard_get_text().toRavenString().ifEmpty { null }
}
