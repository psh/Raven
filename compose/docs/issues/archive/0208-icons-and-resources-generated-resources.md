---
status: closed
wayfinder: grilling
parent: 0204
blocked_by: []
assignee: claude
---

# Icons and resources: generated resources

## Question

How does an app ship its images, icons and fonts inside its binary, and name them in code? Decide
whether a Gradle task embeds a resources folder and registers it at start-up (beside today's
`registerResource` and the resource root), and whether compose-raven generates Compose's
`Res.drawable.foo` / `Res.font.bar` accessors, their package, and how `painterResource` and fonts
take them. The answer is the spec section and its implementation tickets.

## Resolution

Resolved on 2026-11-26 in conversation. Written into spec section 8 (Generated resources) and
section 12, and filed as [Generated resources: Res and the plugin](0223-generated-resources-res-and-the-plugin.md)
and [Generated resources: embedding](0224-generated-resources-embedding.md), in that order.

- **Shipping**: copied beside the executable into `resources/`, the default resource root, by
  default; embedding is opt-in (`embed = true`), through generated `registerResource` calls, since
  Kotlin/Native compiles large byte literals slowly and an embedded file stays in memory.
- **Accessors**: Compose Multiplatform's `composeResources/` layout and generated `Res`
  (`Res.drawable`, `Res.font`, `Res.readBytes`), in Compose's default package
  (`packageOfResClass` changes it) and with its naming rules; a clash fails the build. No strings,
  which wait for localisation, and no qualifiers.
- **Runtime**: `painterResource(DrawableResource)`, `Font(FontResource)` and `Res.readBytes`, with
  Compose's signatures, each resource being its path, found by Raven as an embedded file before the
  resource root.
- **Where**: an in-repo convention plugin, `raven.compose-resources`; a published plugin goes with
  the release, phase 4. The XML translation task can join it later.
- **Proof**: `samples/resources`, a check module and not a port, tested copied and embedded.
