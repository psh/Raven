---
status: closed
---

# XML translator: the sample UI and the examples check

Put the translator in its sample UI (XML and generated Kotlin side by side, live) and add the CI
test that translates Raven's `examples/*.xml` and compiles the output against the current
`compose-raven-foundation`. With this, phase 1 is done. Spec:
[`samples/xml-translator`](../../spec.md#samplesxml-translator) (UI, Tests).

**Depends on:** [XML translator: templates and expressions](0056-xml-translator-templates-and-expressions.md),
[Checkbox, Switch and RadioButton](0048-checkbox-switch-and-radiobutton.md),
[TextField and Slider](0049-textfield-and-slider.md),
[Scroll containers, splitters and CodeEditor](0050-scroll-containers-splitters-and-codeeditor.md),
[CI job for compose](../0052-ci-job-for-compose.md).

## Deliverables

- [x] A `HorizontalSplitter`: `CodeEditor(xml, language = "XML")` on the left, starting with the
      example from `docs/XML.md`, and `CodeEditor(code, readOnly = true, language = "Kotlin")` on
      the right, with the warnings in a `Text` below. Translation reruns on every change.
- [x] A CI test that translates every `.xml` file in Raven's `examples/` and compiles the output
      against `compose-raven-foundation`, so the translator can't drift from the API.
- [x] Update the roadmap to mark phase 1 done: done on macOS but for CI (0052) and the Linux build
      (0058), which the roadmap now says.

## Resolution

Done on macOS. 42 tests pass in `samples/xml-translator`, and the examples check compiles all 42
translations; `./gradlew build` passes.

- **The UI** (`Main.kt`): `TranslatorUi(initialXml)` holds the XML in state, translates it with
  `remember(xml)` on every change, and shows `CodeEditor(xml, language = "XML")` beside
  `CodeEditor(code, readOnly = true, language = "Kotlin")` in a `HorizontalSplitter`, with the
  warnings in a `Text` below ("No warnings", or the count and one per line). It starts with the
  example of `docs/XML.md`. `main()` is `singleWindowApplication`, and the entry point moved to
  `raven.sample.xml.main`. `TranslatorUiTest` checks the starting translation and its warnings, and
  that typing into the XML editor translates again (here, into a parse error).
- **The examples check** is in the sample's Gradle build, since a Kotlin/Native test can't run the
  compiler:
  - a second executable, `translateFiles`, starts `RavenHost` headless (no new copy of the headless
    start-up) and translates each file given as `input output package`, with its warnings as
    comments at the top;
  - `translateRavenExamples<Target>` runs it over `../examples/**/*.xml`, each into a package named
    after its path, with the tool binary as an input, so a changed translator always translates
    again (checked: an unchanged run is up to date, an edited translator reruns);
  - an `examples` compilation compiles the output against foundation; `allTests` and `check`
    depend on it, so CI's `allTests` (0052) runs it. A planted type error fails it.
- **What it covers today**: 30 of the 42 files give code, 508 lines, the rest only TODOs (themes,
  and roots such as `<Border>` from later phases). 248 warnings: mostly phase 2 and 3 tags
  (`Border`, `Shortcut`, `HeaderBar`, `ComboBox`, `Grid`...), `Opacity` and `Wrap` on labels, and
  `<Widget>`s that Raven finds in other files (`SettingRow`, `Chip`), which phase 3's
  `raven-xml2compose` follows.
- **Phase 1** is done on macOS but for CI (0052) and the Linux build (0058), which the roadmap now
  says, instead of marking the whole phase done.

