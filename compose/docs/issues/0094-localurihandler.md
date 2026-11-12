---
status: open
---

# LocalUriHandler

Compose opens links through `LocalUriHandler`; compose-raven's `MarkdownText` instead leaves
Raven's own link handling in place when `onLinkClick` is null, and a non-null `onLinkClick` takes
over every link, so `samples/themes`' web links would never open. This ticket adds `UriHandler` and
`LocalUriHandler` and sends `MarkdownText`'s default through it. Spec:
[Fonts and text styling](../spec.md#fonts-and-text-styling-phase-3) (the `MarkdownText` and
`LocalUriHandler` bullets) and section 12. Decided on 2026-11-12, from the limit the themes port
reported ([samples/themes](archive/0071-samples-themes.md),
[MarkdownText](archive/0087-markdowntext.md)).

**Depends on:** nothing open.

## Deliverables

- [ ] `interface UriHandler { fun openUri(uri: String) }` and `LocalUriHandler` in
      `raven.compose.ui.platform`, provided by the host to every window. The default calls
      `Ropen_url` and throws `IllegalArgumentException` when Raven can't open the URI.
- [ ] `MarkdownText` always registers its link callback: a link goes to `onLinkClick`, or, when it
      is null, to `LocalUriHandler.current.openUri(url)`, read when the link is clicked (inside the
      C-boundary catch). Raven's own handling, and so `action:` links as Raven actions, is no longer
      used.
- [ ] `samples/themes` handles its `action:themes.docs` link in `onLinkClick` and passes every other
      link to `LocalUriHandler`, so web links in `notes.md` open; its ticket's note is corrected.
- [ ] Tests: a recording `UriHandler` provided in a test receives a clicked link when `onLinkClick`
      is null and nothing when it is set; a nested provider wins; the default handler's failure on
      a URI Raven refuses; the existing MarkdownText test that relied on Raven firing an `action:`
      link is replaced.
