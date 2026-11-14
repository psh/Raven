package raven.native

import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import raven.cinterop.R_TRUE
import raven.cinterop.R_WIDGET_LABEL
import raven.cinterop.Rfile_read
import raven.cinterop.Rfile_register_memory
import raven.cinterop.Rfile_unregister_memory
import raven.cinterop.Rlabel_get_text
import raven.cinterop.Rlabel_set_text
import raven.cinterop.Rwidget_create
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StringsTest {
    @Test
    fun textRoundTripsThroughRaven() = withHeadlessRaven {
        val label = Rwidget_create(R_WIDGET_LABEL)
        for (text in listOf("", "plain", "Grüße, 你好, emoji 🐦‍⬛, ½ × ∞")) {
            Rlabel_set_text(label, text)
            assertEquals(text, Rlabel_get_text(label).toRavenString())
        }
    }

    @Test
    fun aMissingStringReadsAsEmpty() {
        assertEquals("", null.toRavenString())
    }

    @Test
    fun aHandedOverStringIsCopiedAndFreed() {
        withHeadlessRaven {
            val content = "Raven ✓"
            memScoped {
                val bytes = content.cstr
                Rfile_register_memory("mem:strings-test.txt", bytes.ptr, (bytes.size - 1).toULong(), R_TRUE)
            }
            assertEquals(content, Rfile_read("mem:strings-test.txt", null).takeRavenString())
            assertNull(Rfile_read("mem:no-such-file.txt", null).takeRavenString())
            Rfile_unregister_memory("mem:strings-test.txt")
        }
        assertEquals(0uL, ravenBlocksHeld())
    }
}
