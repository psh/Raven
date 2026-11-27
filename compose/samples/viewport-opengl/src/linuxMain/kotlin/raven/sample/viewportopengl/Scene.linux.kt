package raven.sample.viewportopengl

/** Kotlin/Native has no GL platform library on Linux, and compose-raven-opengl no GLX context yet. */
actual fun glScene(): Scene? = null
