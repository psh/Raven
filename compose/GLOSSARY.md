# compose-raven

The layer that lets Kotlin/Native code describe a Raven user interface with the Compose runtime,
the way compose-ui does for Skia and Mosaic does for terminals.

## Language

**compose-raven**:
The family of Kotlin/Native modules that drives Raven's retained widgets from the Compose runtime, the way the Compose name covers runtime, ui, foundation and material.
_Avoid_: compose-ui (that is the Skia-bound JetBrains/Google layer), Raven bindings (those are the per-language C API wrappers in `bindings/`)

**Compose runtime**:
The tree-agnostic core of Compose (composition, recomposition, snapshot state), below UI, Foundation and Material.
_Avoid_: Compose (unqualified), Jetpack Compose

**Raven widget**:
An object in Raven's retained widget tree, addressed by a handle; Raven owns its layout and painting.
_Avoid_: control, view, component

**compose-raven-native**:
The lowest compose-raven module: Raven's C API as seen from Kotlin/Native, with no Compose in it. The modules above it wrap it, so apps never see it.
_Avoid_: compose-raven (that is the family), the binding (ambiguous with the generated bindings in `bindings/`)

**Node**:
What the Compose runtime's tree is made of: each node stands for exactly one Raven widget, and the tree of nodes mirrors the Raven widget tree under a window's root.
_Avoid_: element, view, layout node (that is compose-ui's measuring node)

**UI thread**:
The one thread that starts Raven and runs its event loop; every change to Raven widgets, and every recomposition, happens on it. Other threads reach it only by posting work to it.
_Avoid_: main thread (it is the main thread on macOS, but the term names the role, not the OS thread)

**Modifier**:
How a composable states its place in its parent (padding around it, its size, how it aligns, what share of free space it takes) the decoration any widget can have (its material, its tooltip), the input any widget can receive (clicks, pointer and key events, focus), and what it tells screen readers (its semantics). What one kind of widget is or shows, including its own colours, belongs to its parameters, not its Modifier.
_Avoid_: slot (Raven's word for the same settings, as seen from the parent), layout params

**Controlled input**:
An input composable whose shown value is always the value the app composed. A user's edit is only a proposal, which the app accepts by updating its state; otherwise the input goes back to the composed value.
_Avoid_: bound field, two-way binding

**Phase**:
A numbered stage of compose-raven's roadmap (phase 1, phase 2, ...): a set of capabilities built together, and the sample ports they unlock.
_Avoid_: v1, wave, release (a phase is a stage of planning, not a published version)

**Content node**:
The hidden container compose-raven puts inside a widget that takes only one child (a button, a surface, an expander), so that its content may hold any number of children. Modifiers, semantics and tests never see it.
_Avoid_: wrapper, inner box

**Host epoch**:
Which run of Raven an object belongs to. Raven starts and stops more than once in a process (every test does), and an object from an earlier run must never touch the current one.
_Avoid_: session, generation (Raven's own word for its handle counters)

**Resource root**:
The folder relative resource paths (images, fonts) resolve against: `resources/` beside the executable unless the application names another. Files embedded with `registerResource` are found before it.
_Avoid_: assets folder, working directory (the current directory plays no part)

**Port gap**:
A part of a C example that its sample port leaves out because the phase it ports in doesn't provide it yet. Every port gap names the later phase that completes it; there is no gap without one.
_Avoid_: missing feature, TODO (a TODO has no owner; a port gap always has a phase)

**Accessible**:
One thing a screen reader, a test or an agent sees in a Raven window: the window, a widget that means something (not one that only lays others out), or an item of a widget, such as a list row or a tab. What the user perceives, rather than how the UI is built.
_Avoid_: node (compose-raven's Compose node, above), element, control

**Selector**:
A short text that says which accessibles to find, by role, name, text or the widget's XML name (`role=button name="Save"`). Tests, previews' scripts and agents' tools all write the same selectors.
_Avoid_: locator, query, matcher (compose-raven's `NodeMatcher`, over Compose nodes)

**Idle**:
When a host has nothing left to do before the screen shows the app's state: no recomposition, snapshot apply or dispatched task pending. Running animations don't count, so an animation never keeps it from being idle. Tests and agents' tools both wait for it before they look at the screen.
_Avoid_: settled, stable, quiescent

**Preview**:
A piece of UI rendered to an image file with no window shown, for people and agents to look at: how a design idea, a variant of a screen or a change looks, without running the app.
_Avoid_: snapshot (Compose's word for its state), screenshot (a capture of the screen, which a preview never touches), mock-up

**Raven MCP**:
The part of Raven that lets an agent use a running app over the Model Context Protocol: the app publishes tools, and the agent lists and calls them.
_Avoid_: MCP middleware, MCP bridge, the MCP server (the server is only its listening end)

**Action**:
Raven's named event: a name a widget fires (`file.save`), answered by the function bound to it or by one of Raven's own. An action is Raven's; it reaches an agent only when it is published as a tool.
_Avoid_: command, tool

**Tool**:
Something an app publishes in Raven MCP's list for an agent to call: a name, a description, the arguments it takes, and the app's code that answers it.
_Avoid_: action (Raven's own word, see above), command, endpoint

**Call**:
One use of a tool by an agent, with its arguments. Every call gets exactly one reply, even one the agent has cancelled.
_Avoid_: request, invocation

**Reply**:
The app's answer to a call: a result the agent reads, or a failure it can react to.
_Avoid_: response (the protocol's word for its own messages), return value
