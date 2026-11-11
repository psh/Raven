package raven.native

import raven.cinterop.R_TRUE
import raven.cinterop.Ris_ui_thread

/** Fails unless Raven is running and this is its UI thread, the thread that called `Rinit`. */
public fun requireUiThread() {
    check(Ris_ui_thread() == R_TRUE) { "Raven must be called on its UI thread, with Raven running" }
}
