---
status: closed
wayfinder: grilling
parent: 0204
blocked_by: []
assignee: claude
---

# Icons and resources: build-time XML translation

## Question

How does an app translate its Raven XML during its own build? Decide the Gradle task that runs
`raven-xml2compose` (a native executable, since Gradle's JVM can't load Raven): where the tool
comes from, its inputs and outputs and up-to-date checks, how warnings and `--werror` reach the
build, and how it relates to generated resources. The answer is the spec section and its
implementation tickets.

## Resolution

Resolved on 2026-11-26 in conversation. Written into spec section 10 (`compose-raven-xml` and
`raven-xml2compose`, now built in phase 5, and its build-time translation), and filed as
[XML translation: compose-raven-xml and raven-xml2compose](../0225-xml-translation-compose-raven-xml-and-raven-xml2compose.md)
and [XML translation: the raven.compose-xml plugin](../0226-xml-translation-the-raven-compose-xml-plugin.md),
in that order.

- **The module split comes first**: the translator moves out of the sample into
  `compose-raven-xml`, and `raven-xml2compose` is built on it, as section 10 specified for phase 4;
  both are built now and only publishing waits for the release.
- **The tool comes from its project's link task**, as the examples check already takes the
  sample's executable, so a changed translator translates again. Apps outside the repository wait
  for the release.
- **`raven.compose-xml`** is a convention plugin of its own, separate from the resources plugin:
  `ravenXml { source, packageName, main, werror }`, one task over the folder, its output a source
  directory of `nativeMain`. Subfolders are subpackages of `packageName`, which also fits the
  examples check's one package per example.
- **Files named in the XML keep their paths**, resolved against the resource root (their path
  under `composeResources/` with the resources plugin); emitting `Res` from XML can come later.
- **Proof**: the examples check moves onto the plugin (Raven's 43 XML files), with tests of
  re-translation and `werror`.
