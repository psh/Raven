---
status: closed
---

# Raven reports theme file reloads

Raven reloads a changed theme file (`rvn_theme_watch` in `src/widget/theme.c`) without calling the
reload callback (`Rxml_set_reload_callback`), which only reports interface files
(`src/xml/xml_watch.c`). So [RavenTheme and theme files](0065-raventheme-and-theme-files.md)
refreshes `RavenTheme.themeNames` and `RavenTheme.colors` by re-reading them every frame while
live reload is on and a theme file is loaded, instead of when a reload happens, as
[Raven's live reload under compose-raven](0084-ravens-live-reload-under-compose-raven.md)
planned. This ticket makes it fork change 8 and drops the per-frame check. Spec:
[11. Required Raven changes](../../spec.md#11-required-raven-changes),
[4. Application host](../../spec.md#4-application-host) (live reload).

**Depends on:** nothing open.

## Deliverables

- [x] Fork change 8, one isolated commit in Raven: a theme file's reload calls the reload callback
      with its path and whether it loaded, as an interface file's does; the header's description of
      `Rxml_set_reload_callback` says theme files are reported too. A C test in Raven's `tests/`,
      and the language bindings and reference docs regenerated if the API surface changes.
- [x] compose-raven refreshes `themeNames` and the colours from its reload callback, and the
      per-frame check in `RavenHost.sendFrame` goes.
- [x] The existing test where a theme file is rewritten under a new name and `checkFiles()` runs
      still passes, now through the callback.
- [x] Spec section 11's change 8 is marked made, and section 4's live reload bullet is checked
      against what the code does.

## Resolution

Done on macOS. `./gradlew build allTests` passes: 292 tests, 91 of them in ui and 122 in
foundation. Raven's `test_themes` passes its new test. Its `test_looks` fails one check
(`Rtheme_get_name()` is `MacOSGlassLight` for the native light look on this macOS), and
`test_shell` fails nine, both the same without the change.

- **Fork change 8** (`Rxml_set_reload_callback: report theme file reloads too`, Raven files only):
  `rvn_live_reloaded(path, ok)` in `src/xml/xml_watch.c` calls the reload callback inside
  `rvn_dispatch_begin`/`_end`, as an interface file's reload did inline, and `rvn_theme_watch` in
  `src/widget/theme.c` calls it after `rvn_theme_load_file`, with whether that returned a theme. So
  the callback sees the theme already reloaded, renamed or recoloured. A file caught half written
  is still tried again quietly; one with a mistake is reported once, not ok, when Raven stops
  trying it. `raven_xml.h` says theme files are reported too. No function changes: the bindings
  were regenerated only for the callback's doc comment, and `docs/reference.html` follows it.
- **C test**: `test_themes` gains `test_theme_reload_callback`: nothing is reported when nothing
  changed; a file saved under another name is reported once, ok, with its path, and the new name
  is already listed inside the callback; a mistake is reported once, not ok, after the quiet
  retries; a mended file is reported ok; with no callback the file reloads all the same.
- **compose-raven**: the host's reload callback already refreshed the theme state, so the per-frame
  check in `RavenHost.sendFrame` and `HostTheme.watchesFiles` are gone, and `RavenHost.checkFiles()`
  is `Rxml_check_files()` alone. `ThemeFilesTest`'s rename test now passes only through the
  callback, and the never-reloads test still holds.
- **Docs**: spec section 11 has change 8 made and described, section 4's live reload bullet says
  Raven calls the callback for theme files and that it refreshes `themeNames` and `colors`, and the
  roadmap's fork section lists 8 as done. Both link this ticket under `archive/`, where it goes.
