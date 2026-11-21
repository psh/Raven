package raven.compose.ui.test

import raven.cinterop.R_TRUE
import raven.cinterop.R_WIDGET_CHECKBOX
import raven.cinterop.R_WIDGET_LABEL
import raven.cinterop.R_WIDGET_MENU_ITEM
import raven.cinterop.Rlabel_get_text
import raven.cinterop.Rmenu_item_get_text
import raven.cinterop.Rwidget_get_accessible_name
import raven.cinterop.Rwidget_get_type
import raven.cinterop.Rwidget_is_enabled
import raven.cinterop.Rwidget_is_focused
import raven.compose.ui.InternalComposeRavenApi
import raven.compose.ui.RavenNode
import raven.native.toRavenString

/**
 * What a finder looks for, as Compose's `SemanticsMatcher`: [hasText], [hasTestTag], [isEnabled]
 * and the others, combined with [and] and [or]. [description] names it in a failure.
 */
public class NodeMatcher internal constructor(
    public val description: String,
    /** Whether it accepts a node: for compose-raven-test and the UI tools. */
    @property:InternalComposeRavenApi public val matches: (RavenNode) -> Boolean,
) {
    /** Both this and [other]. */
    public infix fun and(other: NodeMatcher): NodeMatcher =
        NodeMatcher("($description) and (${other.description})") { matches(it) && other.matches(it) }

    /** This or [other]. */
    public infix fun or(other: NodeMatcher): NodeMatcher =
        NodeMatcher("($description) or (${other.description})") { matches(it) || other.matches(it) }

    override fun toString(): String = description
}

/** A matcher of the UI tools' own, as [description] names it in failures: `role=button`, say. */
@InternalComposeRavenApi
public fun nodeMatcher(description: String, matches: (RavenNode) -> Boolean): NodeMatcher = NodeMatcher(description, matches)

/** A Text or a menu item showing [text], or a part of it with [substring]. */
public fun hasText(text: String, substring: Boolean = false): NodeMatcher =
    NodeMatcher("text \"$text\"") { node ->
        val shown = labelText(node) ?: menuItemText(node)
        shown != null && if (substring) text in shown else shown == text
    }

/** A node tagged [tag] with `Modifier.testTag`. */
public fun hasTestTag(tag: String): NodeMatcher = NodeMatcher("tag \"$tag\"") { it.testTag == tag }

/**
 * A widget whose accessible name is [description], or holds it with [substring]. Raven names a
 * widget after its text when nothing else names it, so Texts are left out: [hasText] finds them.
 */
public fun hasContentDescription(description: String, substring: Boolean = false): NodeMatcher =
    NodeMatcher("content description \"$description\"") { node ->
        if (labelText(node) != null) return@NodeMatcher false
        val name = Rwidget_get_accessible_name(node.widget).toRavenString()
        if (substring) description in name else name == description
    }

/** A widget the user can use: enabled, as are its ancestors. */
public fun isEnabled(): NodeMatcher = NodeMatcher("enabled") { Rwidget_is_enabled(it.widget) == R_TRUE }

/** A widget that is disabled, or in a disabled ancestor. */
public fun isNotEnabled(): NodeMatcher = NodeMatcher("not enabled") { Rwidget_is_enabled(it.widget) != R_TRUE }

/** The widget with the keyboard focus. */
public fun isFocused(): NodeMatcher = NodeMatcher("focused") { Rwidget_is_focused(it.widget) == R_TRUE }

/** A checkbox, a switch, a radio button or a checkbox menu item: a node that is on or off. */
public fun isToggleable(): NodeMatcher = NodeMatcher("toggleable") { node ->
    Rwidget_get_type(node.widget) == R_WIDGET_CHECKBOX || node.name == "CheckboxItem"
}

/** What a Text shows, or null for another node. */
@InternalComposeRavenApi
public fun labelText(node: RavenNode): String? =
    if (Rwidget_get_type(node.widget) == R_WIDGET_LABEL) Rlabel_get_text(node.widget).toRavenString() else null

/** What a menu item shows, or null for another node or a separator. */
@InternalComposeRavenApi
public fun menuItemText(node: RavenNode): String? =
    if (Rwidget_get_type(node.widget) == R_WIDGET_MENU_ITEM && !isMenuSeparator(node.widget)) {
        Rmenu_item_get_text(node.widget).toRavenString()
    } else {
        null
    }
