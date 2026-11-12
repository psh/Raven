---
status: closed
wayfinder: grilling
parent: 0073
blocked_by: []
assignee: Paul Hawke
---

# Raven's live reload under compose-raven

## Question

Raven reloads what it built from files when they change on disk: interface XML loaded from files,
theme files (`Rtheme_load`) and, while live reload is on, locale CSV files. It is on by default
(`Rxml_set_live_reload`, or `RAVEN_LIVE_RELOAD=0` to turn it off). The first watched file starts a
native watcher thread (kqueue, inotify, ReadDirectoryChangesW) even when reload is off, whose events
are posted to the UI thread with `Rtask_post`; polling every 0.2 s is only a fallback.
`Rxml_set_reload_callback` is a single global slot. Text and memory-registered files are never
watched. (The header's "five per second" is stale: the code in `src/xml/xml_watch.c` is
event-driven.)

Phase 1 loads nothing from files, so none of this runs today. Phase 2's `loadTheme` turns it on,
and [Theme extras the themes sample uses](0077-theme-extras-the-themes-sample-uses.md)
decided that theme files reload through Raven and that `RavenTheme.themeNames` refreshes on reload.

Decide:

- **The default** in a compose-raven app: Raven's (on), off, or on only in debug builds.
- **A switch**: whether apps get one (`application(liveReload = ...)`, the environment variable
  only, or none), and in which phase.
- **The reload callback slot**: compose-raven owning `Rxml_set_reload_callback` outright, as it owns
  the frame callback, and whether apps ever see a reload event.
- **Tests**: whether the harness turns live reload off, so a test that loads a theme file starts no
  watcher reacting to the disk, and whether the watcher thread matters for leak checks.

Then correct spec section 8's theming wording and [RavenTheme and theme files](../0065-raventheme-and-theme-files.md)
to match. Surfaced on 2026-11-11, after the theme extras ticket closed.

## Resolution

Live reload is a development convenience, on in debug executables only, with compose-raven owning
Raven's reload callback. It joins [RavenTheme and theme files](../0065-raventheme-and-theme-files.md),
the first ticket to load a file.

- **Default**: `Platform.isDebugBinary`. A shipped app never changes because a file on disk did, as
  Compose Hot Reload is a development tool. `RAVEN_LIVE_RELOAD`, when set, wins.
- **Switch**: `application(liveReload = Platform.isDebugBinary)` and the same on
  `singleWindowApplication`, phase 2, calling `Rxml_set_live_reload` right after `Rinit`.
- **The callback slot**: compose-raven owns `Rxml_set_reload_callback` outright, as it owns the
  frame callback, uses it only to refresh `RavenTheme.themeNames`, and runs it inside the
  C-boundary catch. Apps get no reload event.
- **Tests**: the harness's host runs with live reload off. A reload test writes the file and calls
  an internal `checkFiles()` over `Rxml_check_files`, which works with live reload off, so it is
  deterministic. `Rshutdown` stops the watcher thread, so leak checks need nothing new.
- **Upstream**: `raven_xml.h`'s "five per second" is stale (the watcher is event-driven); noted in
  `roadmap.md`'s fork section as a doc fix to offer, not a fork change.

Spec section 4 has the live reload bullet and the `application` parameter, section 8's theming
wording follows it, and section 9 has the harness rule.
