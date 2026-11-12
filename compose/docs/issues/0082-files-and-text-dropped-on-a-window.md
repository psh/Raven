---
status: open
---

# Files and text dropped on a window

`Window(onExternalDrop)` for files and text dropped from other applications, for the `notepad`
port, and the harness action that tests it. Spec: [4. Application host](../spec.md#4-application-host).
Decided in: [Window extras the samples use](archive/0076-window-extras-the-samples-use.md).

**Depends on:** [Window: WindowState and its parameters](archive/0086-window-windowstate-and-its-parameters.md) (the `Window` it adds a parameter to).

## Deliverables

- [ ] `Window(onExternalDrop: ((DragData) -> Unit)? = null)`, on `singleWindowApplication` too,
      over `Rwindow_set_drop_callback`, with `DragData.FilesList(paths)` and `DragData.Text(text)`.
- [ ] A non-null handler accepts drops (Raven shows its "can drop here" cursor), a null one refuses
      them; the handler runs only on `R_DROP_DROPPED`, through the C-boundary catch, with the
      paths and text copied before the callback returns.
- [ ] Changing the handler between null and non-null takes effect at once.
- [ ] Harness: `performExternalDrop(paths, text)` over `Rwindow_inject_drop`, returning whether the
      window took it.
- [ ] Tests: a dropped file list and a dropped text reach the handler; a window without a handler
      refuses a drop.
