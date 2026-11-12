---
status: open
---

# Raven reports theme file reloads

Raven reloads a changed theme file (`rvn_theme_watch` in `src/widget/theme.c`) without calling the
reload callback (`Rxml_set_reload_callback`), which only reports interface files
(`src/xml/xml_watch.c`). So [RavenTheme and theme files](archive/0065-raventheme-and-theme-files.md)
refreshes `RavenTheme.themeNames` and `RavenTheme.colors` by re-reading them every frame while
live reload is on and a theme file is loaded, instead of when a reload happens, as
[Raven's live reload under compose-raven](archive/0084-ravens-live-reload-under-compose-raven.md)
planned. This ticket makes it fork change 8 and drops the per-frame check. Spec:
[11. Required Raven changes](../spec.md#11-required-raven-changes),
[4. Application host](../spec.md#4-application-host) (live reload).

**Depends on:** nothing open.

## Deliverables

- [ ] Fork change 8, one isolated commit in Raven: a theme file's reload calls the reload callback
      with its path and whether it loaded, as an interface file's does; the header's description of
      `Rxml_set_reload_callback` says theme files are reported too. A C test in Raven's `tests/`,
      and the language bindings and reference docs regenerated if the API surface changes.
- [ ] compose-raven refreshes `themeNames` and the colours from its reload callback, and the
      per-frame check in `RavenHost.sendFrame` goes.
- [ ] The existing test where a theme file is rewritten under a new name and `checkFiles()` runs
      still passes, now through the callback.
- [ ] Spec section 11's change 8 is marked made, and section 4's live reload bullet is checked
      against what the code does.
