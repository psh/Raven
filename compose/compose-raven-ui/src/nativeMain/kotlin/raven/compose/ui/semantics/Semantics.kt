package raven.compose.ui.semantics

import raven.cinterop.RAccessibleRole
import raven.cinterop.R_FALSE
import raven.cinterop.R_ROLE_ALERT
import raven.cinterop.R_ROLE_BUTTON
import raven.cinterop.R_ROLE_CANVAS
import raven.cinterop.R_ROLE_CHECKBOX
import raven.cinterop.R_ROLE_COMBO_BOX
import raven.cinterop.R_ROLE_DIALOG
import raven.cinterop.R_ROLE_DOCUMENT
import raven.cinterop.R_ROLE_GROUP
import raven.cinterop.R_ROLE_HEADING
import raven.cinterop.R_ROLE_IMAGE
import raven.cinterop.R_ROLE_LINK
import raven.cinterop.R_ROLE_LIST
import raven.cinterop.R_ROLE_LIST_ITEM
import raven.cinterop.R_ROLE_MENU
import raven.cinterop.R_ROLE_MENU_BAR
import raven.cinterop.R_ROLE_MENU_ITEM
import raven.cinterop.R_ROLE_NONE
import raven.cinterop.R_ROLE_PANE
import raven.cinterop.R_ROLE_PROGRESS_BAR
import raven.cinterop.R_ROLE_RADIO_BUTTON
import raven.cinterop.R_ROLE_SCROLL_BAR
import raven.cinterop.R_ROLE_SEPARATOR
import raven.cinterop.R_ROLE_SLIDER
import raven.cinterop.R_ROLE_SPIN_BUTTON
import raven.cinterop.R_ROLE_STATUS_BAR
import raven.cinterop.R_ROLE_TAB
import raven.cinterop.R_ROLE_TABLE
import raven.cinterop.R_ROLE_TAB_LIST
import raven.cinterop.R_ROLE_TAB_PANEL
import raven.cinterop.R_ROLE_TEXT
import raven.cinterop.R_ROLE_TEXT_FIELD
import raven.cinterop.R_ROLE_TOGGLE_BUTTON
import raven.cinterop.R_ROLE_TOOLBAR
import raven.cinterop.R_ROLE_TREE
import raven.cinterop.R_ROLE_TREE_ITEM
import raven.cinterop.R_ROLE_WINDOW
import raven.cinterop.R_TRUE
import raven.cinterop.Raccessibility_announce
import raven.cinterop.Rid
import raven.cinterop.Rwidget_set_accessible_description
import raven.compose.ui.Modifier

// What the screen reader sees beyond Raven's own description of each widget (section 7). Raven
// names and describes every widget by itself; these replace or add to what it says.

/** What a widget is, for the screen reader, as in Compose. [AccessibleRole] has every role Raven knows. */
public enum class Role(internal val raven: RAccessibleRole) {
    Button(R_ROLE_BUTTON),
    Checkbox(R_ROLE_CHECKBOX),
    Switch(R_ROLE_TOGGLE_BUTTON),
    RadioButton(R_ROLE_RADIO_BUTTON),
    Tab(R_ROLE_TAB),
    Image(R_ROLE_IMAGE),
    DropdownList(R_ROLE_COMBO_BOX),
}

/** Each of Raven's accessible roles: compose-raven's extension of [Role]. [None] leaves the widget out, its children kept. */
public enum class AccessibleRole(internal val raven: RAccessibleRole) {
    None(R_ROLE_NONE),
    Window(R_ROLE_WINDOW),
    Group(R_ROLE_GROUP),
    Text(R_ROLE_TEXT),
    Heading(R_ROLE_HEADING),
    Link(R_ROLE_LINK),
    Button(R_ROLE_BUTTON),
    ToggleButton(R_ROLE_TOGGLE_BUTTON),
    Checkbox(R_ROLE_CHECKBOX),
    RadioButton(R_ROLE_RADIO_BUTTON),
    Slider(R_ROLE_SLIDER),
    ProgressBar(R_ROLE_PROGRESS_BAR),
    SpinButton(R_ROLE_SPIN_BUTTON),
    TextField(R_ROLE_TEXT_FIELD),
    Document(R_ROLE_DOCUMENT),
    ComboBox(R_ROLE_COMBO_BOX),
    List(R_ROLE_LIST),
    ListItem(R_ROLE_LIST_ITEM),
    Tree(R_ROLE_TREE),
    TreeItem(R_ROLE_TREE_ITEM),
    Table(R_ROLE_TABLE),
    MenuBar(R_ROLE_MENU_BAR),
    Menu(R_ROLE_MENU),
    MenuItem(R_ROLE_MENU_ITEM),
    TabList(R_ROLE_TAB_LIST),
    Tab(R_ROLE_TAB),
    TabPanel(R_ROLE_TAB_PANEL),
    Toolbar(R_ROLE_TOOLBAR),
    StatusBar(R_ROLE_STATUS_BAR),
    Dialog(R_ROLE_DIALOG),
    Alert(R_ROLE_ALERT),
    Image(R_ROLE_IMAGE),
    Canvas(R_ROLE_CANVAS),
    Separator(R_ROLE_SEPARATOR),
    ScrollBar(R_ROLE_SCROLL_BAR),
    Pane(R_ROLE_PANE),
}

/** What a [semantics] block sets. A property left null keeps what Raven says by itself. */
public class SemanticsPropertyReceiver internal constructor() {
    /** The name the screen reader reads, in place of the widget's text or the Text that labels it. */
    public var contentDescription: String? = null

    /** What the widget is. */
    public var role: Role? = null

    /** What the widget is, from all of Raven's roles. It wins over [role] and [heading]. */
    public var accessibleRole: AccessibleRole? = null

    /** A longer explanation, read after the name. */
    public var accessibleDescription: String? = null

    internal var isHeading = false
    internal var isHidden = false

    /** Makes the widget a heading, which screen readers let the user jump between. A [role] wins over it. */
    public fun heading() {
        isHeading = true
    }

    /** Hides the widget, and everything in it, from screen readers: for decoration. */
    public fun hideFromAccessibility() {
        isHidden = true
    }
}

/** What a [semantics] block set, read once when the chain is built, so equal blocks make equal chains. */
internal data class SemanticsElement(
    val contentDescription: String? = null,
    val role: Role? = null,
    val accessibleRole: AccessibleRole? = null,
    val accessibleDescription: String? = null,
    val isHeading: Boolean = false,
    val isHidden: Boolean = false,
) : Modifier.Element {
    /** This element, with what [later] sets in the same chain winning. */
    fun then(later: SemanticsElement): SemanticsElement = SemanticsElement(
        contentDescription = later.contentDescription ?: contentDescription,
        role = later.role ?: role,
        accessibleRole = later.accessibleRole ?: accessibleRole,
        accessibleDescription = later.accessibleDescription ?: accessibleDescription,
        isHeading = isHeading || later.isHeading,
        isHidden = isHidden || later.isHidden,
    )

    /** The role Raven is given: [accessibleRole], [role], then a heading's, or else [default]. */
    fun ravenRole(default: RAccessibleRole): RAccessibleRole =
        accessibleRole?.raven ?: role?.raven ?: if (isHeading) R_ROLE_HEADING else default
}

/**
 * Tells screen readers about the widget: `Modifier.semantics { contentDescription = "Play" }`.
 * Several in a chain add up, the later one winning for each property. There is no
 * `mergeDescendants`: Raven decides itself which widgets are read as one.
 */
public fun Modifier.semantics(properties: SemanticsPropertyReceiver.() -> Unit): Modifier {
    val receiver = SemanticsPropertyReceiver().apply(properties)
    return this then SemanticsElement(
        receiver.contentDescription, receiver.role, receiver.accessibleRole, receiver.accessibleDescription,
        receiver.isHeading, receiver.isHidden,
    )
}

/**
 * Has the screen reader read [text] (a save that ended, an error), through the window that is
 * active. With [assertive] it interrupts what is being read. Nothing happens while no screen
 * reader runs. Call it on the UI thread: from an event handler or an effect.
 */
public fun announceForAccessibility(text: String, assertive: Boolean = false) {
    accessibilityCalls.announce(text, assertive)
}

/** The accessibility calls whose effect Raven doesn't let a test read: the tests replace [accessibilityCalls]. */
internal open class AccessibilityCalls {
    open fun setDescription(widget: Rid, description: String?) = Rwidget_set_accessible_description(widget, description)

    open fun announce(text: String, assertive: Boolean) = Raccessibility_announce(text, if (assertive) R_TRUE else R_FALSE)
}

internal var accessibilityCalls: AccessibilityCalls = AccessibilityCalls()
