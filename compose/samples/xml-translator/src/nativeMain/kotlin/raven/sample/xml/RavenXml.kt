package raven.sample.xml

import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import raven.cinterop.RXmlError
import raven.cinterop.R_INVALID_ID
import raven.cinterop.Rid
import raven.cinterop.Rxml_document_destroy
import raven.cinterop.Rxml_document_get_root
import raven.cinterop.Rxml_document_parse
import raven.cinterop.Rxml_node_get_attribute_count
import raven.cinterop.Rxml_node_get_attribute_name
import raven.cinterop.Rxml_node_get_attribute_value
import raven.cinterop.Rxml_node_get_child
import raven.cinterop.Rxml_node_get_child_count
import raven.cinterop.Rxml_node_get_line
import raven.cinterop.Rxml_node_get_tag
import raven.cinterop.Rxml_node_get_text
import raven.native.requireUiThread
import raven.native.toRavenString

/** A text Raven's parser refused: where, both from 1, and why. */
internal class XmlParseException(val line: Int, val column: Int, val reason: String) :
    Exception("line $line, column $column: $reason")

/**
 * Parses [text] with Raven's own parser, copies the document into an [XmlElement] tree, and
 * destroys Raven's document. Raven must be running, and this called on its UI thread. The only
 * function of the sample that touches Raven.
 *
 * Comments inside the root are kept. Comments before it aren't: Raven keeps them out of the tree.
 * A `<Widget>` declaration stays an unexpanded element, and parsing registers nothing.
 *
 * @throws XmlParseException when the text isn't well-formed XML.
 */
internal fun parseRavenXml(text: String): XmlElement {
    requireUiThread()
    val document = memScoped {
        val utf8 = text.cstr
        val error = alloc<RXmlError>()
        // (the size leaves out the terminating zero cstr adds)
        val document = Rxml_document_parse(utf8.ptr, (utf8.size - 1).convert(), error.ptr)
        if (document == R_INVALID_ID) {
            throw XmlParseException(error.line, error.column, error.message.toKString().ifEmpty { "Raven could not read the text" })
        }
        document
    }
    try {
        return copy(Rxml_document_get_root(document))
    } finally {
        Rxml_document_destroy(document)
    }
}

// Documents nest at most 256 levels, so the recursion stays shallow.
private fun copy(node: Rid): XmlElement = XmlElement(
    tag = Rxml_node_get_tag(node).toRavenString(),
    attributes = buildMap {
        repeat(Rxml_node_get_attribute_count(node)) {
            put(Rxml_node_get_attribute_name(node, it).toRavenString(), Rxml_node_get_attribute_value(node, it).toRavenString())
        }
    },
    text = Rxml_node_get_text(node).toRavenString(),
    line = Rxml_node_get_line(node),
    children = List(Rxml_node_get_child_count(node)) { copy(Rxml_node_get_child(node, it)) },
)
