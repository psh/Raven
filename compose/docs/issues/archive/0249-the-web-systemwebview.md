---
status: closed
wayfinder: grilling
parent: 0241
blocked_by: [0246]
assignee: claude
---

# The web: SystemWebView

## Question

How is Raven's SystemWebView (the system's web engine, WKWebView or WebView2, in a view kept over
the widget: `Rsystem_web_view_*`) bound? Decide the composable and its state and navigator
(URL or HTML, loading, title, back and forward), scripts run from Kotlin and messages from the
page, what shows where the system has none (headless tests), and how a native view over the
widget sits with Compose's drawing order. Uses
[The web: how Compose web views shape their APIs](archive/0246-the-web-how-compose-web-views-shape-their-apis.md).
The answer is the spec section and its implementation tickets, with the `web_view` port.

## Resolution

Resolved on 2026-11-27, with the user, in two rounds: designed into
[spec section 8: WebView](../../spec.md#webview-phase-5), section 10's
[samples/web-view](../../spec.md#samplesweb-view-phase-5) and section 12, from
[The web: how Compose web views shape their APIs](0246-the-web-how-compose-web-views-shape-their-apis.md).

- **`WebView(state, onMessage)`** with **one `WebViewState`**: observed `url`, `title`, `isLoading`,
  `error`, `canGoBack`, `canGoForward` re-read on Raven's events, and the commands on the same
  object, so no navigator and no URL fed back as a parameter.
- **No progress**; **`suspend evaluateJavaScript(script): String`** throwing
  `WebViewScriptException`; **raw messages** through `onMessage`.
- **The same composable everywhere**, Raven's message where there is no engine; `isSupported`.
- **Left out**: interception, replies to the page, the native handle, the back key; popups going
  under the view is documented.
- Headless tests on the unsupported path; the `web_view` port without port gaps; the XML translator
  covers `<SystemWebView>`.

Filed as [The web: WebView and WebViewState](../0269-the-web-webview-and-webviewstate.md) and
[samples/web-view](../0270-samples-web-view.md).
