package raven.compose.opengl

import raven.compose.ui.NativeWindowHandle

// GLX waits for the Linux build (phase 4): Kotlin/Native has no GL platform library on Linux, so it
// needs a cinterop of GL/glx.h, which can't be built and checked before then.

internal actual fun glContextOn(handle: NativeWindowHandle): GlContext? = null

internal actual fun offscreenTarget(width: Int, height: Int): OffscreenTarget? = null
