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

**raven-native**:
The lowest compose-raven module: Raven's C API as seen from Kotlin/Native, with no Compose in it. The modules above it wrap it, so apps never see it.
_Avoid_: compose-raven (that is the family), the binding (ambiguous with the generated bindings in `bindings/`)

**Node**:
What the Compose runtime's tree is made of: each node stands for exactly one Raven widget, and the tree of nodes mirrors the Raven widget tree under a window's root.
_Avoid_: element, view, layout node (that is compose-ui's measuring node)
