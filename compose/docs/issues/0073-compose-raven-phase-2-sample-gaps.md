---
status: open
wayfinder: map
---

# compose-raven phase 2 sample gaps

## Destination

Every [port gap](../../GLOSSARY.md) listed on the phase 2 sample tickets
([samples/notepad](0069-samples-notepad.md), [samples/components](0070-samples-components.md),
[samples/themes](0071-samples-themes.md), [samples/overlay](0072-samples-overlay.md)) is settled:
either decided into phase 2 (written into `compose/docs/spec.md` and owned by a phase 2 ticket), or
left as a port gap that names the phase completing it, with that phase's part of the spec or
roadmap listing it. The sample tickets then lose their "brought forward or left out" deliverable,
so implementation starts with nothing open. This map plans; it builds nothing.

## Notes

- **Phase 2 is a foundation a later phase completes.** Nothing is left out without a home: every
  port gap names the phase (and, where one exists, the ticket or spec section) that completes it.
  A gap that is "not in the spec" leaves this map either designed into a named phase, or written as
  a named patch of that phase's fog on `compose/docs/roadmap.md`. Never "later" with no owner.
- Settled when charting (2026-11-11):
  - Gaps already designed for phase 3 or later stay port gaps of that phase by default, except
    `notepad`'s file dialogs and discard prompt, which get their own ticket.
  - Spec section 10's rule becomes: a port has the C example's look and the behaviour its phase's
    vocabulary covers, and lists its port gaps with the phase that completes each.
- Domain: the Compose runtime over Raven's retained widget tree. Vocabulary is in
  `compose/GLOSSARY.md`; keep it current as terms resolve.
- Required reading for Raven questions: `docs/ARCHITECTURE.md`, `include/raven/` and the C example
  being ported in `examples/`.
- Skills: grilling tickets call `grilling` + `domain-modeling`.
- Standing preference: **keep it slim.** Lean towards the smallest design that works.
- Standing preference: **favour Compose conventions and ergonomics**, and document every difference
  from Compose in spec section 12.
- The decisions of [compose-raven spec](0001-compose-raven-spec.md) and
  [compose-raven phase 3 spec](0023-compose-raven-phase-3-spec.md) are settled and live in
  `compose/docs/spec.md`. Build on them, don't reopen them.
- Closing a ticket also updates `compose/docs/spec.md` and `compose/docs/roadmap.md`, and the
  sample tickets whose gaps it settled.
- Ticket conventions: `ISSUES-FORMAT.md` in this directory.
- **After this map closes** (asked 2026-11-11): assess whether a better split of work between
  phase 2 and phase 3 exists, given what the gap tickets brought into phase 2. Not a ticket of this
  map; it starts once the map is finished.

## Decisions so far

<!-- one line per closed ticket: [title](file): gist -->

- [Notepad's file dialogs and discard prompt](archive/0074-notepad-file-dialogs-and-discard-prompt.md): the four suspend dialogs come into phase 2 with their final signatures but only `native = true`, in a new ticket, [Native message and file dialogs](0080-native-message-and-file-dialogs.md); `notepad` asks before discarding and opens and saves through them; phase 3 completes the `Dialog`/`FileBrowser` default and dialog tests with no API change.
- [CodeEditor's editing extras](archive/0075-codeeditor-editing-extras.md): all into phase 2 in a new ticket, [CodeEditorState, completions, markers and languages](0081-codeeditorstate-completions-markers-and-languages.md): a `ScrollState`-like `CodeEditorState` with commands and the polled cursor, `wordWrap`, synchronous `completions`, controlled `markers`, and `rememberCodeEditorLanguage`/`codeEditorLanguageForFile`.
- [Window extras the samples use](archive/0076-window-extras-the-samples-use.md): all into phase 2: `Window(opacity)`, `WindowBackdrop` returning the backdrop obtained and `LocalWindowInfo.isTransparent` (in the renamed [Window transparency, opacity and backdrop](0068-window-transparency-opacity-and-backdrop.md)); `Window(onExternalDrop)` in a new ticket, [Files and text dropped on a window](0082-files-and-text-dropped-on-a-window.md); `CodeEditorState.insertText`; the command line is no gap; widget drop targets go to phase 4's gestures. Surfaced [Window minimum size](0083-window-minimum-size.md).
- [Theme extras the themes sample uses](archive/0077-theme-extras-the-themes-sample-uses.md): `RavenTheme(accent)`, `saveTheme` and `RavenTheme.themeNames` join [RavenTheme and theme files](0065-raventheme-and-theme-files.md); accent swatches until phase 4's `ColorPicker`; theme files reload through Raven, interface reload has no counterpart; classes stay unexposed; the Markdown editor is a `CodeEditor` language.

## Not yet specified

- **The samples' headless tests and harness**: once the gaps settle, a sample's test may need
  finders or actions phase 2 doesn't plan yet (a menu item inside a header bar, a dropped file).
  Revisit when the gap tickets close.

## Out of scope

- Implementation of phase 2: the vocabulary and sample tickets do that.
- Port gaps of the phase 3 samples (`editor`, `workspace`, `music`, `animations`, `glass`): their
  own phase's planning.
- Mapping the phase 2 vocabulary in the XML translator: spec section 10 fixes its mapping to
  phase 1's.
