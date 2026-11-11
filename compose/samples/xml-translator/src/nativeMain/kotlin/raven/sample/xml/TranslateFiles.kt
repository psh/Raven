@file:OptIn(InternalComposeRavenApi::class)

package raven.sample.xml

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import raven.cinterop.R_TRUE
import raven.cinterop.Rfile_read
import raven.cinterop.Rfile_write
import raven.compose.ui.InternalComposeRavenApi
import raven.compose.ui.RavenHost
import raven.native.takeRavenString
import kotlin.system.exitProcess

/**
 * The examples check's translator: for each `input output package` triple in [args], translates
 * the XML file `input` into the Kotlin file `output`, in `package`, with its warnings as comments
 * at the top. Raven runs headless, since parsing needs it. Fails only when a file can't be read or
 * written; whether the output compiles is the check's other half.
 */
fun translateFiles(args: Array<String>) {
    require(args.size % 3 == 0) { "expected input, output and package for each file" }
    val host = RavenHost(headless = true, followSystemAccessibility = false)
    var failed = 0
    var warnings = 0
    try {
        host.start {}
        for ((input, output, packageName) in args.toList().chunked(3)) {
            val xml = Rfile_read(input, null).takeRavenString()
            if (xml == null) {
                println("cannot read $input")
                failed++
                continue
            }
            val translation = translate(xml)
            warnings += translation.warnings.size
            val kotlin = buildString {
                append("// Translated from $input by the xml-translator's examples check.\n")
                for (warning in translation.warnings) append("// warning: ${warning.toString().replace("\n", "\\n")}\n")
                append("\npackage $packageName\n\n")
                append(translation.code)
            }
            if (!write(output, kotlin)) {
                println("cannot write $output")
                failed++
            }
        }
    } finally {
        host.close()
    }
    println("xml-translator: ${args.size / 3 - failed} files translated, $warnings warnings")
    if (failed > 0) exitProcess(1)
}

private fun write(path: String, text: String): Boolean {
    val bytes = text.encodeToByteArray()
    return bytes.usePinned { Rfile_write(path, it.addressOf(0), bytes.size.convert()) } == R_TRUE
}
