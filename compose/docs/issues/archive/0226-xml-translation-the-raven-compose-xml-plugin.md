---
status: closed
---

# XML translation: the raven.compose-xml plugin

Add the convention plugin `raven.compose-xml`, which translates an app's XML during its build with
`raven-xml2compose`, and move the examples check onto it. Spec:
[10: build-time translation](../../spec.md#compose-raven-xml-and-raven-xml2compose-phase-5-published-in-phase-4).
Planned in [Icons and resources: build-time XML translation](0209-icons-and-resources-build-time-xml-translation.md).

**Depends on:** [XML translation: compose-raven-xml and raven-xml2compose](0225-xml-translation-compose-raven-xml-and-raven-xml2compose.md).

## Deliverables

- [x] The plugin in `build-logic`, with `ravenXml { source = "src/nativeMain/xml"; packageName; main; werror = false }`: one task over the folder, its inputs the XML files and the tool's link task, its output `build/generated/ravenXml/<target>` added to `nativeMain`.
- [x] Warnings in the build's output, `werror` failing on them, errors always failing.
- [x] The examples check translates Raven's `examples/` through the plugin, each example in its subpackage, and compiles; the sample's own `translateRavenExamples` task goes.
- [x] Tests: a changed XML file or translator translates again and an unchanged build is up to date; `werror` fails on a warning.
- [x] The spec's build-time translation and `roadmap.md` checked against what was built.

## Resolution

Built on 2026-11-27.

- **The plugin**: `raven.compose-xml` in `build-logic` (`raven.compose-xml.gradle.kts`, with
  `RavenXmlExtension` and the `TranslateRavenXml` task in `RavenXml.kt`).
  `ravenXml { source; packageName; main; werror }` as specified, plus `tool` to name another
  executable. One task per Kotlin/Native target, `translateRavenXml<Target>`. Its inputs are the XML
  folder, by relative path, and the tool's executable, which it depends on through
  `:tools:raven-xml2compose:linkDebugExecutable<Target>`. Its output,
  `build/generated/ravenXml/<target>`, is emptied before each run and is a source folder of `nativeMain`.
- **Diagnostics**: each line the tool reports goes to the build's output, an error as an error and
  a warning as a warning. A failing run fails the task with the count of errors, or of warnings
  with `werror = true`, and the lines.
- **The examples check** is now the check module `samples/xml-examples`. It applies the plugin to
  `../../../examples` under `examples` (57 files, 28 examples, 292 warnings) and compiles them
  against `compose-raven-foundation` as part of `check`. A second run is up to date. It is a module
  of its own rather than part of `samples/xml-translator`, so the sample's executable doesn't carry
  57 translated examples. The sample lost `TranslateFiles.kt`, its `translate` executable, the
  `translateRavenExamples` task, the `examples` compilation and its direct `compose-raven-native`
  dependency. Its UI tests pass.
- **Tests**: `build-logic` has tests for the first time: `RavenXmlPluginTest` (4, Gradle TestKit,
  JUnit 5) runs a build of its own over a stand-in tool script, so no Raven is needed. It checks
  translated then up to date, a changed XML file or tool translating again, warnings in the
  output, `werror` failing on one, and an error failing. Run with `./gradlew -p build-logic test`.
- **Docs**: the spec's section 10 (the examples check's module, the task per target, the emptied
  output, `tool`, the tests), the README (the tool, the plugin and the check), and `roadmap.md`'s
  phase 4 list (only publishing is left there).
