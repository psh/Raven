package raven.native

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.toKString
import raven.cinterop.Rmemory_free

// Strings cross the C boundary as UTF-8. A Kotlin String passed to a `const char*` parameter is
// converted by cinterop for the duration of the call, so only returned strings need helpers.

/** A string Raven returns and keeps (a getter's result): copied, with null read as empty. */
public fun CPointer<ByteVar>?.toRavenString(): String = this?.toKString() ?: ""

/** A string Raven hands over, documented as freed with `Rmemory_free`: copied, then freed. */
public fun CPointer<ByteVar>?.takeRavenString(): String? =
    this?.let { pointer -> pointer.toKString().also { Rmemory_free(pointer) } }
