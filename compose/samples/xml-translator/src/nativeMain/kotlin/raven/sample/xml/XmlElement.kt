package raven.sample.xml

/**
 * One node of a Raven XML document, copied out of Raven so the translator works on plain Kotlin.
 * A comment is a node with an empty [tag] and the comment in [text], as in Raven's own tree.
 */
internal data class XmlElement(
    val tag: String,
    /** In the order they were written. */
    val attributes: Map<String, String> = emptyMap(),
    /** The text inside the element (`<Label>this</Label>`), or the comment. */
    val text: String = "",
    /** The line the node starts on, from 1; 0 when it wasn't read from a text. */
    val line: Int = 0,
    val children: List<XmlElement> = emptyList(),
) {
    val isComment: Boolean get() = tag.isEmpty()
}
