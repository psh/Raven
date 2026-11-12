---
status: open
wayfinder: grilling
parent: 0073
blocked_by: []
assignee: null
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
and [Theme extras the themes sample uses](archive/0077-theme-extras-the-themes-sample-uses.md)
decided that theme files reload through Raven and that `RavenTheme.themeNames` refreshes on reload.

Decide:

- **The default** in a compose-raven app: Raven's (on), off, or on only in debug builds.
- **A switch**: whether apps get one (`application(liveReload = ...)`, the environment variable
  only, or none), and in which phase.
- **The reload callback slot**: compose-raven owning `Rxml_set_reload_callback` outright, as it owns
  the frame callback, and whether apps ever see a reload event.
- **Tests**: whether the harness turns live reload off, so a test that loads a theme file starts no
  watcher reacting to the disk, and whether the watcher thread matters for leak checks.

Then correct spec section 8's theming wording and [RavenTheme and theme files](0065-raventheme-and-theme-files.md)
to match. Surfaced on 2026-11-11, after the theme extras ticket closed.
