---
status: open
---

# XML translation: the raven.compose-xml plugin

Add the convention plugin `raven.compose-xml`, which translates an app's XML during its build with
`raven-xml2compose`, and move the examples check onto it. Spec:
[10: build-time translation](../spec.md#compose-raven-xml-and-raven-xml2compose-phase-5-published-in-phase-4).
Planned in [Icons and resources: build-time XML translation](archive/0209-icons-and-resources-build-time-xml-translation.md).

**Depends on:** [XML translation: compose-raven-xml and raven-xml2compose](0225-xml-translation-compose-raven-xml-and-raven-xml2compose.md).

## Deliverables

- [ ] The plugin in `build-logic`, with `ravenXml { source = "src/nativeMain/xml"; packageName; main; werror = false }`: one task over the folder, its inputs the XML files and the tool's link task, its output `build/generated/ravenXml/<target>` added to `nativeMain`.
- [ ] Warnings in the build's output, `werror` failing on them, errors always failing.
- [ ] The examples check translates Raven's `examples/` through the plugin, each example in its subpackage, and compiles; the sample's own `translateRavenExamples` task goes.
- [ ] Tests: a changed XML file or translator translates again and an unchanged build is up to date; `werror` fails on a warning.
- [ ] The spec's build-time translation and `roadmap.md` checked against what was built.
