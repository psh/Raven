---
status: closed
---

# LocalUriHandler

Compose opens links through `LocalUriHandler`; compose-raven's `MarkdownText` instead leaves
Raven's own link handling in place when `onLinkClick` is null, and a non-null `onLinkClick` takes
over every link, so `samples/themes`' web links would never open. This ticket adds `UriHandler` and
`LocalUriHandler` and sends `MarkdownText`'s default through it. Spec:
[Fonts and text styling](../../spec.md#fonts-and-text-styling-phase-3) (the `MarkdownText` and
`LocalUriHandler` bullets) and section 12. Decided on 2026-11-12, from the limit the themes port
reported ([samples/themes](0071-samples-themes.md),
[MarkdownText](0087-markdowntext.md)).

**Depends on:** nothing open.

## Deliverables

- [x] `interface UriHandler { fun openUri(uri: String) }` and `LocalUriHandler` in
      `raven.compose.ui.platform`, provided by the host to every window. The default calls
      `Ropen_url` and throws `IllegalArgumentException` when Raven can't open the URI.
- [x] `MarkdownText` always registers its link callback: a link goes to `onLinkClick`, or, when it
      is null, to `LocalUriHandler.current.openUri(url)`, read when the link is clicked (inside the
      C-boundary catch). Raven's own handling, and so `action:` links as Raven actions, is no longer
      used.
- [x] `samples/themes` handles its `action:themes.docs` link in `onLinkClick` and passes every other
      link to `LocalUriHandler`, so web links in `notes.md` open; its ticket's note is corrected.
- [x] Tests: a recording `UriHandler` provided in a test receives a clicked link when `onLinkClick`
      is null and nothing when it is set; a nested provider wins; the default handler's failure on
      a URI Raven refuses; the existing MarkdownText test that relied on Raven firing an `action:`
      link is replaced.

## Resolution

Done on macOS. `./gradlew build allTests` passes: 316 tests, 3 of them new in foundation's
`MarkdownTextTest` (which loses the one that relied on Raven firing an `action:` link) and 1 in
ui's `WindowTest`. The themes sample's link test is rewritten.

- **`UriHandler` and `LocalUriHandler`**, in `platform/UriHandler.kt`, have Compose's shapes: an
  interface with `openUri(uri)`, and a static local whose default is an error, as
  `LocalWindowInfo`'s is. `RavenWindow.setContent` provides the internal `RavenUriHandler` to
  every window, next to `LocalWindowInfo`. It calls `Ropen_url` and throws
  `IllegalArgumentException` (`Raven can't open "..."`) when Raven returns false. Raven only says
  whether it asked the OS, not whether a program then opened the URI.
- **`MarkdownText`** always registers its link callback: `bindLinkClick` takes a non-null lambda
  and never clears Raven's callback, so Raven's own handling is not used and an `action:` link is
  not a Raven action. Without `onLinkClick`, the lambda is a remembered one that reads the
  handler, kept with `rememberUpdatedState(LocalUriHandler.current)`, when the link is clicked. A
  changed provider so changes nothing on the node, and since the call is inside `dispatchEvent`,
  what the handler throws goes to the host's error path, not into C.
- **`samples/themes`**: `followLink(link, uriHandler)` gives the C example's status for
  `action:themes.docs` and passes every other link to the window's `LocalUriHandler`, so web links
  in `notes.md` open. `linkStatus` and its "A link to ..." status are gone. The note in
  [samples/themes](0071-samples-themes.md) says this ticket fixed it.
- **Not tested**: the default handler opening a real URI, since that would start the browser.
  Raven refuses an empty URI on every platform before asking the OS, and that is the failure
  tested. A Markdown link with no address can't be clicked, so it is tested on the handler a
  window provides rather than through a click.
- **Tests**:
  - with a recording `UriHandler` provided, a clicked link reaches `onLinkClick` and not the
    handler; with `onLinkClick` null, the `action:` link reaches the handler and Raven fires no
    action;
  - a nested provider wins over the outer one;
  - a changed provider gets the next click;
  - the handler a window provides throws `IllegalArgumentException` for the empty URI;
  - the themes sample's docs link gives its status and opens nothing, and another link goes to the
    handler.
