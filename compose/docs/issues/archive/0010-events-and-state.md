---
status: closed
wayfinder: grilling
parent: 0001
blocked_by: [0006, 0007]
assignee: Paul Hawke
---

# Events and state

## Question

How do Raven event callbacks reach Kotlin lambdas passed to composables (`onClick` and so on), and how does input state flow? For inputs like text fields and checkboxes, are composables controlled (state hoisted, Raven's value overwritten on recomposition) or uncontrolled, and how are update loops avoided?

## Resolution

- **Wiring**: the node factory registers one shared `staticCFunction` per event kind the composable supports, once, with the node's `StableRef` as `user_data` ([Node model and Applier](0007-node-model-and-applier.md)). Lambdas (`onClick`, `onCheckedChange`, ...) live in mutable fields on `RavenNode`. `Updater.set` only replaces the field and makes no C call, and a null lambda makes the callback do nothing. Lambdas run synchronously on the UI thread. Their state writes are recomposed later through `RavenDispatcher`, never re-entrantly inside the Raven callback ([Frame loop and threading](0008-frame-loop-and-threading.md)).
- **Controlled inputs, as in Compose** (`Checkbox(checked, onCheckedChange)`, `TextField(value, onValueChange)`): Raven changes a widget's own value before calling back, so each input node remembers the value it last composed, and a user change marks the node as needing a check. In the frame callback, after the clock has ticked and only when `recomposer.hasPendingWork` is false, every marked node sets its widget back to its composed value if the two differ. An accepted change already matches, so nothing happens and nothing flickers. A rejected change reverts once the composition is settled. Raven's setters don't fire change callbacks, so there is no update loop.
- **Text**: v1 has only `TextField(value: String, onValueChange: (String) -> Unit)`. compose-raven writes text back only when it differs from what the widget shows, so typing never moves the caret. An app that rewrites the input (for example to upper case) sends the caret to the end, and the spec documents this difference from Compose. `TextFieldValue` waits until Raven exposes selection setters.
- **Errors**: a Kotlin exception must not cross a `staticCFunction`, so each shared callback catches it and passes it to the frame loop's error path, which records the first throwable and calls `Rquit()`. `ravenApplication` rethrows it after shutdown.
- **v1 events**: only the callbacks specific to each widget, set per composable by [v1 component vocabulary](0011-v1-component-vocabulary.md). Generic input (`Modifier.clickable`, key and pointer events through `RWidgetEventCallback`) and focus control (`FocusRequester`) are in the Generic input and focus fog.
