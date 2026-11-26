---
status: open
---

# XML translation: compose-raven-xml and raven-xml2compose

Move the translator out of `samples/xml-translator` into the library module `compose-raven-xml`,
and build the command-line tool `raven-xml2compose` on it, as spec section 10 specifies. Built and
tested in this repository; publishing waits for the release. Spec:
[10: `compose-raven-xml` and `raven-xml2compose`](../spec.md#compose-raven-xml-and-raven-xml2compose-phase-5-published-in-phase-4).
Planned in [Icons and resources: build-time XML translation](archive/0209-icons-and-resources-build-time-xml-translation.md).

**Depends on:** [Generated resources: embedding](0224-generated-resources-embedding.md), the ticket
before it in phase 5's implementation sequence.

## Deliverables

- [ ] `compose-raven-xml`, holding the translator and its tests (`TranslatorTest`, `TemplatesTest`, the XML parser over Raven), with the sample depending on it.
- [ ] `raven-xml2compose`, headless: `raven-xml2compose <dir> --out <dir> --package <name> [--main <file>] [--werror]`, one Kotlin file per XML file in PascalCase, `<Include>` as calls with cycles as errors, the generated-file header and overwriting only such files, diagnostics as `file:line:column: message`, the exit code.
- [ ] Subfolders as subpackages of `--package`, for the plugin.
- [ ] Tests of the tool on a folder: names, includes, `--main`, a cycle, `--werror`, refusing to overwrite a hand-written file.
- [ ] `samples/xml-translator` unchanged in behaviour; the spec's section checked against what was built.
