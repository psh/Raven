@file:OptIn(InternalComposeRavenApi::class)

package raven.compose.ui

import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.staticCFunction
import raven.cinterop.R_TRUE
import raven.cinterop.Rid
import raven.cinterop.Rtoast_set_message_ended_callback

/** A message of a toast went (Raven change 15): its id, and whether by its button. */
@InternalComposeRavenApi
public val ToastMessageEnded: Event<(id: Int, byAction: Boolean) -> Unit> = Event { widget, userData ->
    Rtoast_set_message_ended_callback(widget, onToastMessageEnded, userData)
}

private val onToastMessageEnded = staticCFunction { _: Rid, id: Int, byAction: Int, userData: COpaquePointer? ->
    dispatchEvent(userData, ToastMessageEnded) { it(id, byAction == R_TRUE) }
}
