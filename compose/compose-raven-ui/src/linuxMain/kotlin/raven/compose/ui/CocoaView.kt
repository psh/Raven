package raven.compose.ui

import kotlinx.cinterop.COpaquePointer

/** No Cocoa on Linux. */
internal actual fun cocoaViewOfLayer(window: COpaquePointer?, layer: COpaquePointer?): COpaquePointer? = null
