---
status: open
wayfinder: grilling
parent: 0241
blocked_by: [0246]
assignee: null
---

# The web: SystemWebView

## Question

How is Raven's SystemWebView (the system's web engine, WKWebView or WebView2, in a view kept over
the widget: `Rsystem_web_view_*`) bound? Decide the composable and its state and navigator
(URL or HTML, loading, title, back and forward), scripts run from Kotlin and messages from the
page, what shows where the system has none (headless tests), and how a native view over the
widget sits with Compose's drawing order. Uses
[The web: how Compose web views shape their APIs](0246-the-web-how-compose-web-views-shape-their-apis.md).
The answer is the spec section and its implementation tickets, with the `web_view` port.
