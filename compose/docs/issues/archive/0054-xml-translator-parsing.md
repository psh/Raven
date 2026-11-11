---
status: closed
---

# XML translator: parsing

Give `samples/xml-translator` the one adapter,
`parseRavenXml(text)`, which copies Raven's parsed document into a Kotlin `XmlElement` tree. Spec:
[`samples/xml-translator`](../../spec.md#samplesxml-translator) (Parsing).

**Depends on:** [raven.native helpers](0042-raven-native-helpers.md).

## Deliverables

- [x] ~~`ravenxml.def`: `package = raven.sample.xml.cinterop`, a `headerFilter` of `raven_xml.h`, plus
      `Rinit_ex`/`Rshutdown`/`RInitDesc` for tests. No `staticLibraries`, because `libraven.a`
      already arrives through `raven-native`.~~ Dropped: `raven.cinterop` already binds `raven_xml.h`.
- [x] `XmlElement(tag, attributes, text, line, children)`, keeping comments.
- [x] `parseRavenXml(text)`: on the UI thread with Raven running, `Rxml_document_parse`, copy the
      tree, then destroy the document. It needs no clean-up of `<Widget>` declarations (verified).
- [x] A few headless smoke tests, including a parse error and a `<Widget>` declaration.

## Resolution

Done on macOS. 6 headless tests pass in `samples/xml-translator`.

- **No private cinterop.** It was decided (in
  [XML to Compose translator sample](0013-xml-to-compose-translator.md)) before
  [Gradle build and libraven](0041-gradle-build-and-libraven.md) gave `raven.def` a `headerFilter`
  of every header but the backend and Vulkan ones, so `raven.cinterop` already binds `raven_xml.h`,
  `RXmlError`, `Rinit_ex` and `RInitDesc`. A `ravenxml.def` would only bind them again under other
  names. The sample takes `raven-native` as an `implementation` dependency instead (foundation
  keeps it to itself), and `compose-raven-xml` will do the same in phase 3, which keeps Raven's
  types out of its API as [XML translator as a tool](0030-translator-as-a-tool.md) asked. Spec
  sections 2 and 10 and the roadmap now say so.
- **`XmlElement(tag, attributes, text, line, children)`**, in `raven.sample.xml`. A comment is an
  element with an empty `tag` and the comment in `text`, as in Raven's tree, with `isComment`.
  Attributes keep their written order, and every field but `tag` has a default, for the
  translator's unit tests.
- **`parseRavenXml(text)`**: `requireUiThread()`, then `Rxml_document_parse` with the UTF-8 text and
  an `RXmlError`, so a mistake throws `XmlParseException(line, column, reason)` instead of reaching
  Raven's error callback. It copies the tree and destroys the document in a `finally`.
- **Limits of Raven's tree**, recorded in the spec:
  - comments before the root are kept in the document's prolog, which has no getter, so they are
    lost;
  - a node has a line but no column, so the translator's warnings (0055) give a line only.
- **Tests** (`RavenXmlTest`), each failing on any error Raven reports or any block it still holds
  after `Rshutdown`: elements, attributes in order, text and lines; comments inside the root; a
  mistake with its line and column; an empty text; a `<Widget>` declaration that stays an
  unexpanded element and isn't registered; and the call failing when Raven isn't running.

