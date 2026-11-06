# compose-raven

The layer that lets Kotlin/Native code describe a Raven user interface with the Compose runtime,
the way compose-ui does for Skia and Mosaic does for terminals.

## Language

**compose-raven**:
The Kotlin/Native library that drives Raven's retained widgets from the Compose runtime.
_Avoid_: compose-ui (that is the Skia-bound JetBrains/Google layer), Raven bindings (those are the per-language C API wrappers in `bindings/`)

**Compose runtime**:
The tree-agnostic core of Compose (composition, recomposition, snapshot state), below UI, Foundation and Material.
_Avoid_: Compose (unqualified), Jetpack Compose

**Raven widget**:
An object in Raven's retained widget tree, addressed by a handle; Raven owns its layout and painting.
_Avoid_: control, view, component
