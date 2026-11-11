---
status: open
---

# XML translator: parsing

Give `samples/xml-translator` its private `ravenxml.def` cinterop and the one adapter,
`parseRavenXml(text)`, which copies Raven's parsed document into a Kotlin `XmlElement` tree. Spec:
[`samples/xml-translator`](../spec.md#samplesxml-translator) (Parsing).

**Depends on:** [raven.native helpers](archive/0042-raven-native-helpers.md).

## Deliverables

- [ ] `ravenxml.def`: `package = raven.sample.xml.cinterop`, a `headerFilter` of `raven_xml.h`, plus
      `Rinit_ex`/`Rshutdown`/`RInitDesc` for tests. No `staticLibraries`, because `libraven.a`
      already arrives through `raven-native`.
- [ ] `XmlElement(tag, attributes, text, line, children)`, keeping comments.
- [ ] `parseRavenXml(text)`: on the UI thread with Raven running, `Rxml_document_parse`, copy the
      tree, then destroy the document. It needs no clean-up of `<Widget>` declarations (verified).
- [ ] A few headless smoke tests, including a parse error and a `<Widget>` declaration.
