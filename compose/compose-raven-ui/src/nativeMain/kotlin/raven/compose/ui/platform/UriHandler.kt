package raven.compose.ui.platform

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import raven.cinterop.R_FALSE
import raven.cinterop.Ropen_url

/** Opens a URI: what a clicked link of `MarkdownText` goes to when the app gives no `onLinkClick`. */
public interface UriHandler {
    /** Opens [uri]; throws [IllegalArgumentException] when it can't. */
    public fun openUri(uri: String)
}

/** The [UriHandler] of the content: the host's opens with Raven, and an app or a test provides its own. */
public val LocalUriHandler: ProvidableCompositionLocal<UriHandler> = staticCompositionLocalOf { error("no Window") }

/**
 * The host's [UriHandler]: `Ropen_url` opens a web page in the user's browser, a mailto: in their
 * mail program, a file in its application. Raven can't tell whether a program then really opened
 * it, only whether the OS was asked.
 */
internal object RavenUriHandler : UriHandler {
    override fun openUri(uri: String) {
        if (Ropen_url(uri) == R_FALSE) throw IllegalArgumentException("Raven can't open \"$uri\"")
    }
}
