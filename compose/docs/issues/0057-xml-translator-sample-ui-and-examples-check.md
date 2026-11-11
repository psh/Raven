---
status: open
---

# XML translator: the sample UI and the examples check

Put the translator in its sample UI (XML and generated Kotlin side by side, live) and add the CI
test that translates Raven's `examples/*.xml` and compiles the output against the current
`compose-raven-foundation`. With this, phase 1 is done. Spec:
[`samples/xml-translator`](../spec.md#samplesxml-translator) (UI, Tests).

**Depends on:** [XML translator: templates and expressions](0056-xml-translator-templates-and-expressions.md),
[Checkbox, Switch and RadioButton](archive/0048-checkbox-switch-and-radiobutton.md),
[TextField and Slider](archive/0049-textfield-and-slider.md),
[Scroll containers, splitters and CodeEditor](0050-scroll-containers-splitters-and-codeeditor.md),
[CI job for compose](0052-ci-job-for-compose.md).

## Deliverables

- [ ] A `HorizontalSplitter`: `CodeEditor(xml, language = "XML")` on the left, starting with the
      example from `docs/XML.md`, and `CodeEditor(code, readOnly = true, language = "Kotlin")` on
      the right, with the warnings in a `Text` below. Translation reruns on every change.
- [ ] A CI test that translates every `.xml` file in Raven's `examples/` and compiles the output
      against `compose-raven-foundation`, so the translator can't drift from the API.
- [ ] Update the roadmap to mark phase 1 done.
