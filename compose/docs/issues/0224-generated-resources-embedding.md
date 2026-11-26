---
status: open
---

# Generated resources: embedding

Add `ravenResources { embed = true }`: the plugin generates the `registerResource` calls that
build every file of `composeResources/` into the binary, in place of copying them, so an app can
be a single file. Spec: [8: Generated resources](../spec.md#generated-resources-phase-5). Planned
in [Icons and resources: generated resources](archive/0208-icons-and-resources-generated-resources.md).

**Depends on:** [Generated resources: Res and the plugin](0223-generated-resources-res-and-the-plugin.md).

## Deliverables

- [ ] With `embed = true`, generated Kotlin holding each file's bytes and registering them before the application starts; no `resources/` folder copied.
- [ ] A measurement of what it costs: the compile time and binary size for `samples/resources`, and a note in the spec if large files need another way (a linker section).
- [ ] `samples/resources`' tests also run embedded: the same `Res` code finds the same files with no folder beside the executable.
