---
status: open
---

# Generated resources: Res and the plugin

Add the `raven.compose-resources` convention plugin in `build-logic`, which copies an app's
`composeResources/` beside its executables and generates `Res`, and the runtime that takes it.
Spec: [8: Generated resources](../spec.md#generated-resources-phase-5). Planned in
[Icons and resources: generated resources](archive/0208-icons-and-resources-generated-resources.md).

**Depends on:** [Icon and Google's icons](0222-icon-and-googles-icons.md), the ticket before it in
phase 5's implementation sequence.

## Deliverables

- [ ] The plugin, with `ravenResources { packageOfResClass; embed = false }`: it syncs `composeResources/` into `resources/` beside each executable, and its task is an input of the link and run tasks.
- [ ] Generated `Res` in `<group>.<module>.generated.resources` by default: `Res.drawable.<name>`, `Res.font.<name>`, `suspend Res.readBytes(path)`, names by Compose's rules, a clash of names failing the build with the two files named.
- [ ] In compose-raven-ui: `DrawableResource`, `FontResource`, `painterResource(DrawableResource)`, `Font(FontResource, weight, style)`, resolving through Raven's files.
- [ ] `samples/resources`, a check module, with an SVG drawable, a font and a file under `composeResources/`; tests load each through `Res` from the copied folder.
- [ ] Spec section 8's Generated resources and section 12's row checked against what was built.
