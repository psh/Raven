---
status: open
wayfinder: research
blocked_by: []
assignee: null
---

# Research: Decompose, Voyager and Circuit under compose-raven

## Question

Which of the popular navigation libraries besides Navigation 3 can a compose-raven app use, and
what would compose-raven need to provide for each? Navigation 3 works because
`navigation3-runtime` needs only the Compose runtime, and `compose-raven-navigation3` supplies the
UI half (`NavDisplay`) ([compose-raven-navigation3](archive/0131-compose-raven-navigation3.md),
[Verify Navigation 3 under the host](archive/0097-verify-navigation-3-under-the-host.md)). The
same test applies here. Does a library's core reach `androidx.compose.ui`,
`org.jetbrains.compose.ui`, `foundation`, `animation` or `material`, or only the runtime,
`runtime-saveable`, lifecycle and savedstate?

The answer, for each library, is one of:

- works as it is;
- works with a compose-raven module that replaces its UI layer, sized;
- doesn't fit.

Each answer comes from reading the published artifacts and from a spike. It ends in a
recommendation on which to support (likely as `compose-raven-decompose`, and so on) and in tickets
for those.

**The aim** (added 2026-11-27, at the user's request) is a `compose/navigation/` folder holding
compose-raven's navigation modules as peers:

- `compose-raven-navigation3`, moved there from `compose/`;
- one module for each library supported: `compose-raven-decompose`, `compose-raven-voyager` and
  `compose-raven-circuit`, with the code each needs to work under compose-raven.

The research says how close each library can get to that, and what each module would hold. The
tickets it files include the move of `compose-raven-navigation3`, which changes:

- its folder, and whether its Gradle path changes too (`:navigation:compose-raven-navigation3`, or
  the old path kept with a `projectDir`);
- `settings.gradle.kts`, the samples that depend on it (`music`) and the publishing coordinates;
- the docs that name its path (the spec, the roadmap, the README, `api-coverage.md`).

The libraries, with what was believed when this was filed (2026-11-27), to be checked:

- **Decompose** (`com.arkivanov.decompose`) is the likeliest fit. Its core `decompose` module (components,
  `childStack`, back handling, state keeping, with Essenty's lifecycle) has no Compose dependency
  and builds for every Kotlin target. Only `extensions-compose` (`Children`, the stack animations)
  uses Compose UI. compose-raven would need its own `Children`: the stack read with
  `subscribeAsState()`, and the active child shown, as `NavDisplay` shows an entry.
- **Voyager** (`cafe.adriel.voyager`): `Navigator` and `Screen.Content()` look mostly runtime and
  saveable state, but `voyager-core` may depend on Compose UI. The transitions, tab and bottom-sheet
  modules need animation, foundation and Material. It is in maintenance mode, and its macOS native
  targets are uncertain.
- **Circuit** (`com.slack.circuit`): `circuit-runtime` (`Screen`, `Navigator`) and
  `circuit-runtime-presenter` look runtime-only. `circuit-runtime-ui`'s `Ui.Content(state,
  modifier: Modifier)` takes compose-ui's `Modifier`, and `circuit-foundation`
  (`NavigableCircuitContent`, `CircuitContent`) uses animation and foundation. compose-raven would
  have to provide the UI layer. It may not publish macOS native targets.

## To find out, for each library

- **The artifacts**: the dependencies of each module, from their Gradle module metadata on Maven
  Central. Check the version that matches compose-raven's Kotlin (2.4) and Compose runtime (1.12),
  and whether it publishes `macosArm64` (and `linuxX64`, `mingwX64`, for later).
- **Usage**: popularity and maintenance from current numbers (GitHub, Maven downloads, the last
  release). This replaces the remembered ranking: navigation-compose, then Compose Destinations
  (Android only), Voyager, Decompose, Circuit, Appyx, PreCompose.
- **A spike** for each that could fit, a module beside `spikes/navigation3` (`spikes/decompose`,
  `spikes/voyager`, `spikes/circuit`), each checking:
  - it resolves for `macosArm64` with no Compose UI artifact on its classpath, checked in the
    resolved dependencies;
  - a two-screen app navigates forward and back in a compose-raven window, under `runRavenTest`;
  - the back stack, and each screen's `rememberSaveable`, survive as they should. That covers
    Decompose's `StateKeeper`, Voyager's `ScreenModel` and Circuit's `rememberRetained`;
  - the window's back handling (`navigationevent`, as Navigation 3 uses it) reaches the library;
  - how much UI layer had to be written for it (Decompose's `Children`, Circuit's content and
    `Ui`), and what of Compose UI that layer stands in for, such as animations, `Modifier`
    parameters and predictive back.
- **The cost of keeping it**: is a library-specific module worth keeping in step with upstream's
  releases, or would documentation and a sample be enough?

## Out of scope

PreCompose, Appyx, Compose Destinations and JetBrains' multiplatform navigation-compose. They are
bound to Compose UI (PreCompose, Appyx and navigation-compose's `NavHost`) or to Android only
(Compose Destinations). A line in the findings is enough for each.
