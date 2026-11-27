---
status: closed
---

# Generated resources: Res and the plugin

Add the `raven.compose-resources` convention plugin in `build-logic`, which copies an app's
`composeResources/` beside its executables and generates `Res`, and the runtime that takes it.
Spec: [8: Generated resources](../spec.md#generated-resources-phase-5). Planned in
[Icons and resources: generated resources](archive/0208-icons-and-resources-generated-resources.md).

**Depends on:** [Icon and Google's icons](archive/0222-icon-and-googles-icons.md), the ticket before it in
phase 5's implementation sequence.

## Deliverables

- [x] The plugin, with `ravenResources { packageOfResClass; embed = false }`: it syncs `composeResources/` into `resources/` beside each executable, and its task is an input of the link and run tasks.
- [x] Generated `Res` in `<group>.<module>.generated.resources` by default: `Res.drawable.<name>`, `Res.font.<name>`, `suspend Res.readBytes(path)`, names by Compose's rules, a clash of names failing the build with the two files named.
- [x] In compose-raven-ui: `DrawableResource`, `FontResource`, `painterResource(DrawableResource)`, `Font(FontResource, weight, style)`, resolving through Raven's files.
- [x] `samples/resources`, a check module, with an SVG drawable, a font and a file under `composeResources/`; tests load each through `Res` from the copied folder.
- [x] Spec section 8's Generated resources and section 12's row checked against what was built.

## Resolution

Built on 2026-11-26.

- **The plugin**, `build-logic/src/main/kotlin/raven.compose-resources.gradle.kts` with
  `RavenResources.kt`: `ravenResources { packageOfResClass; embed }`; `generateResClass` writes `Res`
  from `src/nativeMain/composeResources/` into `build/generated/ravenResources/kotlin`, a source of
  `nativeMain`; for every executable and test executable a `Sync` task copies the folder into
  `resources/` beside it, run after linking (`finalizedBy`) and before the run and test tasks.
  `embed = true` fails the build until [Generated resources: embedding](0224-generated-resources-embedding.md).
- **`Res`**: `Res.drawable.<name>`, `Res.font.<name>`, `suspend Res.readBytes(path)`. Names by
  Compose's rule, other characters `_` and a leading digit prefixed (`ic-arrow back.svg` is
  `ic_arrow_back`), a Kotlin keyword in backticks. A clash fails the build naming both files (checked
  by hand: `badge.png` beside `badge.svg` gives "composeResources/drawable: badge.png and badge.svg
  both become Res.drawable.badge; rename one"). The package is Compose's default, `<group>.<module>`
  lowercased with other characters `_` plus `.generated.resources`: Gradle's default group here is
  `compose-raven.samples`, hence `compose_raven.samples.resources.generated.resources`.
- **Runtime** in compose-raven-ui's `raven.compose.ui.res` (`GeneratedResources.kt`):
  `DrawableResource`, `FontResource`, `painterResource(DrawableResource)`, `@Composable
  Font(FontResource, weight, style)`, `readResourceBytes(path)`.
- **`samples/resources`**: `ic-arrow back.svg` and `badge.svg`, Raven's Roboto subset as the font
  (its Apache licence in `files/`, shipped with the app), `files/notes.txt`; a screen showing them.
  Tests (3, passing): `Res`'s names and paths; the screen loads each from the folder copied beside
  the test executable (the notes read, the icon at 24 dp, the badge at its 32); the files are there
  and a missing one throws. Checked by eye in the release build.
- The spec's Generated resources section updated to what was built (where the folder lives, the
  package rule, the runtime's package, the sync to test executables).
