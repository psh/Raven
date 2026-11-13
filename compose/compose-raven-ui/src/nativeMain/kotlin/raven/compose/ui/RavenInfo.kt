package raven.compose.ui

import raven.cinterop.Rget_version
import raven.native.toRavenString

/** What compose-raven can say about the Raven it runs on. */
public object RavenInfo {
    /** The version of the Raven library linked in, such as `0.1.0`. Raven needn't be running. */
    public val version: String get() = Rget_version().toRavenString()
}
