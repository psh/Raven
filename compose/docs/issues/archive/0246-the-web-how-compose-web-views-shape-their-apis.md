---
status: closed
wayfinder: research
parent: 0241
blocked_by: []
assignee: claude
---

# The web: how Compose web views shape their APIs

## Question

How do Compose web views shape their APIs: Android's `AndroidView` over `WebView` with
accompanist-webview's `WebViewState` and `WebViewNavigator`, compose-webview-multiplatform's
`rememberWebViewState(url)`, `WebViewNavigator` and its JavaScript bridge, and what Compose
Desktop apps use? How are loading, navigation, the title, scripts run from Kotlin and messages from
the page expressed? Facts for [The web: SystemWebView](../0249-the-web-systemwebview.md), set beside
Raven's SystemWebView (`Rsystem_web_view_*`).

## Resolution

Resolved on 2026-11-26 by research: the findings are on the local branch
`research/compose-web-view-apis` (commit `57fae117`), in
`compose/docs/research/the-web-how-compose-web-views-shape-their-apis.md`; the branch is never
merged or pushed.

- **The shape everyone copies** is accompanist-webview's: a `WebViewState` of observable fields
  (last URL, loading with progress, title, errors) and a separate `WebViewNavigator` for commands.
  It is deprecated (Google says fork it) and has no scripts, bridge or interception.
- **compose-webview-multiplatform** is that fork, common to Android, iOS and Desktop:
  `evaluateJavaScript(script, (String) -> Unit)` with no error channel (an iOS error arrives as the
  result); a `RequestInterceptor` (`Allow`, `Reject`, `Modify`) for main-frame navigations; a
  call-by-name JS bridge with replies (`window.kmpJsBridge.callNative`), injected after load.
- **Desktop**: KCEF (Chromium downloaded at run time) calls itself highly outdated; plain JCEF's
  scripts return nothing; JavaFX's `WebEngine` wants its own thread.
- **Z-order**: native views sit above Compose (`SwingPanel` unless `compose.interop.blending`; iOS
  `placedAsOverlay`, experimental); Android's `AndroidView` doesn't clip.
- **Raven's SystemWebView** already reports loading, URL, title, errors and back/forward as events
  and getters; its script results are better specified (JSON, or an error string, promises
  awaited); page messages are one-way (`window.raven.postMessage`); there is no navigation
  interception, and Raven can't draw over the view, so popups and menus go under it.
- **Open trade-offs** for the decision: a state object or plain parameters; progress; the Kotlin
  shape of script results; raw messages or a call-by-name layer; interception (a new C API);
  z-order and popups; the widget where the system has none; a native-handle escape hatch; the
  back key.
