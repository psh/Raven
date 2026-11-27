---
status: open
---

# The web: WebView and WebViewState

Bind Raven's SystemWebView as `WebView(state, onMessage)` with one `WebViewState`. Spec:
[8: WebView](../spec.md#webview-phase-5) and section 12. Planned in [The web: SystemWebView](archive/0249-the-web-systemwebview.md).

**Depends on:** [samples/diff-view](0267-samples-diff-view.md), the ticket before it in phase 5's implementation sequence.

## Deliverables

- [ ] `WebView(state, modifier, onMessage)` over `Rsystem_web_view_*`, the message callback giving strings as sent and the rest as JSON.
- [ ] `WebViewState` with `rememberWebViewState(url)` and `rememberWebViewStateWithHtml(html, baseUrl)`: `url`, `title`, `isLoading`, `error`, `canGoBack`, `canGoForward` re-read on each event; `loadUrl`, `loadHtml`, `loadFile`, `reload`, `stop`, `goBack`, `goForward`.
- [ ] `suspend evaluateJavaScript(script): String`, throwing `WebViewScriptException`; a coroutine cancelled before the answer drops it.
- [ ] `WebView.isSupported`.
- [ ] Headless tests: the unsupported path (the URL kept, a script throwing), each event updating the state, a message delivered.
- [ ] The XML translator's `<SystemWebView>`.
- [ ] The spec's sections checked against what was built; `api-coverage.md` updated (the SystemWebView row and the numbers).
