package raven.sample.xml

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toKString
import raven.cinterop.RError
import raven.cinterop.RInitDesc
import raven.cinterop.RMemoryStats
import raven.cinterop.RSeverity
import raven.cinterop.R_BACKEND_NULL
import raven.cinterop.R_FALSE
import raven.cinterop.R_SEVERITY_ERROR
import raven.cinterop.R_SEVERITY_FATAL
import raven.cinterop.R_TRUE
import raven.cinterop.Rerror_set_callback
import raven.cinterop.Rinit_ex
import raven.cinterop.Rmemory_get_stats
import raven.cinterop.Rshutdown
import raven.cinterop.Rxml_is_widget_registered
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RavenXmlTest {
    @Test
    fun copiesElementsAttributesTextAndLines() = withRaven {
        val root = parseRavenXml(
            """
            <VBox Spacing="8" Name="main">
                <Label TextSize="17">Hello</Label>
                <HBox>
                    <Button Text="OK" Style="Primary"/>
                </HBox>
            </VBox>
            """.trimIndent(),
        )
        assertEquals("VBox", root.tag)
        assertEquals(listOf("Spacing" to "8", "Name" to "main"), root.attributes.toList())
        assertEquals(1, root.line)
        val (label, row) = root.children
        assertEquals(XmlElement("Label", mapOf("TextSize" to "17"), "Hello", line = 2), label)
        assertEquals(3, row.line)
        assertEquals(XmlElement("Button", mapOf("Text" to "OK", "Style" to "Primary"), line = 4), row.children.single())
    }

    @Test
    fun keepsCommentsInsideTheRoot() = withRaven {
        val root = parseRavenXml("<!-- before -->\n<VBox>\n    <!-- a note -->\n    <Label/>\n</VBox>")
        val (comment, label) = root.children
        assertTrue(comment.isComment)
        assertEquals(" a note ", comment.text)
        assertEquals(3, comment.line)
        assertEquals("Label", label.tag)
        // Raven keeps what precedes the root out of the tree.
        assertEquals(2, root.children.size)
    }

    @Test
    fun aMistakeIsReportedWithItsPlace() = withRaven {
        val error = assertFailsWith<XmlParseException> { parseRavenXml("<VBox>\n    <Label>\n</VBox>") }
        assertEquals(3, error.line)
        assertTrue(error.column > 0, "column ${error.column}")
        assertTrue(error.reason.isNotEmpty())
    }

    @Test
    fun anEmptyTextIsAMistake() = withRaven {
        assertFailsWith<XmlParseException> { parseRavenXml("") }
    }

    @Test
    fun aWidgetDeclarationStaysUnexpandedAndUnregistered() = withRaven {
        val root = parseRavenXml(
            """
            <Widgets>
                <Widget Name="Card" Title="Untitled">
                    <VBox><Label Text="{Title}"/><Children/></VBox>
                </Widget>
            </Widgets>
            """.trimIndent(),
        )
        val widget = root.children.single()
        assertEquals("Widget", widget.tag)
        assertEquals(mapOf("Name" to "Card", "Title" to "Untitled"), widget.attributes)
        assertEquals(listOf("Label", "Children"), widget.children.single().children.map { it.tag })
        assertEquals(R_FALSE, Rxml_is_widget_registered("Card"))
    }

    @Test
    fun needsRavenRunning() {
        assertFailsWith<IllegalStateException> { parseRavenXml("<VBox/>") }
    }
}

/**
 * Runs [block] with Raven started headless, then stops it. Fails if Raven reported an error, or
 * still holds memory after `Rshutdown` (a document left undestroyed).
 */
private fun withRaven(block: () -> Unit) {
    ravenErrors.clear()
    Rerror_set_callback(collectRavenError)
    try {
        memScoped {
            val desc = alloc<RInitDesc>().apply {
                backend = R_BACKEND_NULL
                app_name = null
                worker_threads = -1
                disable_render_thread = R_FALSE
                disable_vsync = R_FALSE
                enable_validation = R_FALSE
                headless = R_TRUE
                ui_scale = 0f
            }
            assertEquals(R_TRUE, Rinit_ex(desc.ptr), "Rinit_ex")
        }
        try {
            block()
        } finally {
            Rshutdown()
        }
    } finally {
        Rerror_set_callback(null)
    }
    assertEquals(emptyList(), ravenErrors.toList(), "errors reported by Raven")
    val held = memScoped {
        val stats = alloc<RMemoryStats>()
        Rmemory_get_stats(stats.ptr)
        stats.allocation_count
    }
    assertEquals(0uL, held, "blocks Raven still holds after Rshutdown")
}

private val ravenErrors = mutableListOf<String>()

private val collectRavenError = staticCFunction { _: RError, severity: RSeverity, detail: CPointer<ByteVar>? ->
    if (severity == R_SEVERITY_ERROR || severity == R_SEVERITY_FATAL) ravenErrors += detail?.toKString() ?: "?"
}
