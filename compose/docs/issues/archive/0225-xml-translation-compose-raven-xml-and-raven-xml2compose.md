---
status: closed
---

# XML translation: compose-raven-xml and raven-xml2compose

Move the translator out of `samples/xml-translator` into the library module `compose-raven-xml`,
and build the command-line tool `raven-xml2compose` on it, as spec section 10 specifies. Built and
tested in this repository; publishing waits for the release. Spec:
[10: `compose-raven-xml` and `raven-xml2compose`](../../spec.md#compose-raven-xml-and-raven-xml2compose-phase-5-published-in-phase-4).
Planned in [Icons and resources: build-time XML translation](0209-icons-and-resources-build-time-xml-translation.md).

**Depends on:** [Generated resources: embedding](0224-generated-resources-embedding.md), the ticket
before it in phase 5's implementation sequence.

## Deliverables

- [x] `compose-raven-xml`, holding the translator and its tests (`TranslatorTest`, `TemplatesTest`, the XML parser over Raven), with the sample depending on it.
- [x] `raven-xml2compose`, headless: `raven-xml2compose <dir> --out <dir> --package <name> [--main <file>] [--werror]`, one Kotlin file per XML file in PascalCase, `<Include>` as calls with cycles as errors, the generated-file header and overwriting only such files, diagnostics as `file:line:column: message`, the exit code.
- [x] Subfolders as subpackages of `--package`, for the plugin.
- [x] Tests of the tool on a folder: names, includes, `--main`, a cycle, `--werror`, refusing to overwrite a hand-written file.
- [x] `samples/xml-translator` unchanged in behaviour; the spec's section checked against what was built.

## Resolution

Built on 2026-11-27.

- **`compose-raven-xml`** (package `raven.compose.xml`) holds the translator moved from the sample
  with its history (`Translator.kt`, `Expressions.kt`, `KotlinCode.kt`, `RavenXml.kt`,
  `XmlElement.kt`) and its tests (`TranslatorTest`, `TemplatesTest`, `ExpressionsTest`,
  `RavenXmlTest`). It depends on `compose-raven-native` only: the code it writes is for
  `compose-raven-foundation`, but it needs nothing of it. Public: `translate(xml)`, `Translation`,
  `TranslationWarning`, and for folders `translateFolder(files, packageName, main)` with `XmlFile`,
  `KotlinFile`, `Diagnostic`, `Severity`, `FolderTranslation`, `GENERATED_HEADER`, and
  `withHeadlessRaven { }` to parse outside an app.
- **Folders** (`Folder.kt`): files are translated depth first along their includes, so an included
  file's composable and declarations are known when its includer is read; a file being translated
  again closes a cycle, reported with its path (`a.xml → b.xml → a.xml`). A file's root becomes
  `Name(modifier: Modifier = Modifier)`, placed by the include as a `<Widget>`'s root is by its
  call, imported from its subpackage. `<Window>` roots that aren't `--main` become their content's
  composable, without `main()`.
- **Found while running it on Raven's examples**: `music/ui/settings.xml` uses `<SettingRow>`,
  declared in `widgets.xml`, which only `main.xml` includes. Raven registers declarations for the
  whole app and finds declaration files where it looks for files (next to the file and in its
  subfolders), so a file of declarations only is known in the files of its folder and of the folders
  above it. Also, `now_playing` became `Now_playing` through `pascalCase`, which keeps underscores;
  file names split on them.
- **`raven-xml2compose`** (`tools/raven-xml2compose`, project `:tools:raven-xml2compose`): the
  command line of the spec, POSIX file reading and writing, diagnostics as
  `file:line:column: warning: message` on stderr, exit codes 0, 1 (errors, or warnings with
  `--werror`, or a hand-written file in the way) and 2 (the command line). A generated file whose
  text is unchanged isn't rewritten, so its timestamp stays.
- **Raven's examples**: all 57 XML files translate with no errors and 292 warnings (what has no
  composable yet), 57 Kotlin files in `examples.<example>` packages; compiling them is the plugin's
  examples check, [XML translation: the raven.compose-xml plugin](../0226-xml-translation-the-raven-compose-xml-plugin.md).
  The sample's own examples check is unchanged until then, and passes.
- **Tests**: `FolderTest` (14: names, subpackages, includes and their placing, imported declarations,
  declarations without an include, a shared file once, `--main`, cycles, a missing include, a file
  that doesn't parse, two files on one name, name clashes) and the tool's `Xml2ComposeTest` (7, on a
  temporary folder: writing, diagnostics, `--werror`, a cycle, a missing `--main`, overwriting only
  generated files, the command line). Every test of the module, the tool and the sample passes.
- **`api-coverage.md`**: the translator's `raven_xml.h` calls now count, being in a library module:
  491 of 1,701 functions, `raven_xml.h` 15 of 64.
- **Spec**: the module table and section 10 updated with what was built (the file composable's
  modifier and `Ui` suffix, includes resolved next to the file then from the root, declarations
  without an include, the diagnostics' severity, exit code 2, the header check).
