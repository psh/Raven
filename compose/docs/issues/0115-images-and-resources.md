---
status: open
---

# Images and resources

Images on the GPU, the resource root, and icons everywhere they are taken. Spec: [8: Images and resources](../spec.md#images-and-resources-phase-3).

**Depends on:** nothing in phase 3.

## Deliverables

- [ ] The resource root (`resources/` beside the executable, `application(resourceRoot)`) and `registerResource(path, bytes)`.
- [ ] `ImageBitmap(width, height, rgba)` with `update(width, height, rgba)`, which may resize and invalidates every `Viewport` showing it, on the UI thread; `filterQuality` ([Small port gaps](archive/0107-small-port-gaps.md)); `decodeToImageBitmap`; freed by a `Cleaner` or `close()`, inert after shutdown.
- [ ] `Image(painter, contentDescription, modifier, contentScale, colorFilter)` with `painterResource` (async, cached per host), `BitmapPainter(image, srcOffset, srcSize)`, `ContentScale`, `ColorFilter.tint` and `ColorFilter.textColor()` ([Music's class-styled themes](archive/0099-musics-class-styled-themes.md)), and `Modifier.clip(RoundedCornerShape)`.
- [ ] Icons on `Item(icon)` and `Window(icon)` (`Tab(icon)`, `TreeView(itemIcon)`, `Panel(icon)` and `drawImage` come with their own tickets).
- [ ] Tests: decoding, regions, scaling, tint and text-colour tint, filter quality, resizing updates, and a bitmap freed after shutdown doing nothing.
