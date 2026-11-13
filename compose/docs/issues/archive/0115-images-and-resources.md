---
status: closed
---

# Images and resources

Images on the GPU, the resource root, and icons everywhere they are taken. Spec: [8: Images and resources](../../spec.md#images-and-resources-phase-3).

**Depends on:** nothing in phase 3.

## Deliverables

- [x] The resource root (`resources/` beside the executable, `application(resourceRoot)`) and `registerResource(path, bytes)`.
- [x] `ImageBitmap(width, height, rgba)` with `update(width, height, rgba)`, which may resize and invalidates every `Viewport` showing it, on the UI thread; `filterQuality` ([Small port gaps](0107-small-port-gaps.md)); `decodeToImageBitmap`; freed by a `Cleaner` or `close()`, inert after shutdown.
- [x] `Image(painter, contentDescription, modifier, contentScale, colorFilter)` with `painterResource` (async, cached per host), `BitmapPainter(image, srcOffset, srcSize)`, `ContentScale`, `ColorFilter.tint` and `ColorFilter.textColor()` ([Music's class-styled themes](0099-musics-class-styled-themes.md)), and `Modifier.clip(RoundedCornerShape)`.
- [x] Icons on `Item(icon)` and `Window(icon)` (`Tab(icon)`, `TreeView(itemIcon)`, `Panel(icon)` and `drawImage` come with their own tickets).
- [x] Tests: decoding, regions, scaling, tint and text-colour tint, filter quality, resizing updates, and a bitmap freed after shutdown doing nothing.

## Resolution

Done and run on macOS. `./gradlew build allTests` passes: 343 tests, 21 of them new, 13 in ui's
`ImageBitmapTest` and 8 in foundation's `ImageTest`.

- **Resources** (`compose-raven-ui`, `raven.compose.ui.res`): `RavenHost(resourceRoot)` and
  `application(resourceRoot)`/`singleWindowApplication(resourceRoot)`; the default is
  `resources/` beside the executable, found with `NSBundle.mainBundle.executablePath` (appleMain)
  or `/proc/self/exe` (linuxMain). `RavenHost.resolveResource(path)` (internal API, for fonts
  next) keeps an absolute or embedded path as it is and puts any other in the root.
  `registerResource(path, bytes)` copies the bytes into a process-wide list, registers them at
  once when Raven runs, and every host registers the list after `Rinit`, since `Rshutdown` forgets
  them: so it may be called from `main()` before `application`, which the spec now says.
- **`ImageBitmap`** (`raven.compose.ui.graphics`): a class with an internal constructor and the
  factory `ImageBitmap(width, height, rgba, filterQuality = Low)`, plus
  `ByteArray.decodeToImageBitmap()` (Compose Multiplatform's form, `Rtexture_create`, decoded on
  the calling thread; `Medium`, Raven's mipmapped default for files). `width` and `height` follow
  `update`. `filterQuality` is settable (`None` nearest, `Low` linear, `Medium`/`High` mipmap).
  The texture and its epoch live in a small handle, which is all the `Cleaner` holds; the
  `Cleaner` dispatches the free through the host's dispatcher, which drops it once the host has
  closed, and the free checks the epoch again on the UI thread. `close()` is idempotent, and
  both do nothing for a bitmap of an earlier run, whose `texture` and `update` throw.
- **Redrawing on update**: widgets register with `addViewer(widget)` (internal API; `Image` and
  menu items do it through `Painter.addViewer`). `update` forgets the destroyed ones and calls
  `Rwidget_invalidate` (layout and paint) on the rest. Headless Raven happens to lay the image
  out again after `Rtexture_update` anyway, so the test checks the invalidation calls themselves.
  0124's `Viewport` should call `image.addViewer(widget)` when it shows a bitmap.
- **`Painter`** (`raven.compose.ui.graphics.painter`): an abstract class with an internal
  constructor, so every painter is an image: `BitmapPainter(image, srcOffset, srcSize)` (with
  `IntOffset` and `IntSize`, new in `raven.compose.ui.unit`) or `painterResource`'s internal
  `ResourcePainter`. Its internal API is `texture`, `region` (`TextureRegion` in UV) and
  `addViewer`. A `BitmapPainter` of the whole bitmap keeps showing the whole of it after a resize;
  a part is measured when the painter is made, and one outside the bitmap is refused.
- **`painterResource(path)`** (`raven.compose.ui.res`) uses `Rtexture_create_from_file_async`,
  cached in `HostCaches` per path, so it is one painter per host and path.
- **`Image(painter, contentDescription, modifier, contentScale, colorFilter)`** (foundation, over
  `R_WIDGET_IMAGE`), plus `Image(bitmap, ...)`, a shorthand the spec didn't name.
  `ContentScale` (`raven.compose.ui.layout`) is an enum of `Fit`, `Crop`, `FillBounds`, `None`.
  `ColorFilter` is a class with `tint(color)` and `textColor()`; Raven multiplies by the tint
  rather than blending `SrcIn` (spec section 12 says so now). A description gives the Image role
  and the name; a null one gives `R_ROLE_NONE`, so the image is decoration.
- **`Modifier.clip(shape)`** (`Draw.kt`): it folds into `clipToBounds` on every widget, and
  `Image` reads the last shape with `Modifier.clipShape()` (internal API) for
  `Rimage_set_corner_radius`, using `topStart` with a warning for different corners, as buttons'
  shapes do.
- **Icons**: `Item(icon: Painter? = null)`, last parameter, through `Rmenu_item_set_icon`
  untinted. `Window(icon: Painter? = null)`, after `onExternalDrop`, in its own `SideEffect` and
  `RavenWindow.setIcon`: `Rwindow_set_icon` takes pixels and a texture can't be read back, so
  the painter's image file is read again (`Rfile_read`, `Rimage_decode`). Only a
  `painterResource` can be a window icon; another painter logs a warning. `singleWindowApplication`
  takes no icon, since a painter is made in composition.
- **Spec changes** (section 8, images; section 12): `registerResource` before `application`;
  `bytes.decodeToImageBitmap()`; the viewers; `Image(bitmap)`; the multiplying tint; `clip` on
  other widgets; SVG among the formats, since Raven now rasterises SVG files into textures
  (`Rtexture_create_from_svg`, which `Rtexture_create_from_file` uses), so "no SVG" became "no
  `ImageVector`"; the window icon from a file only. The glossary has **Resource root**.
- **Not tested**: the `Cleaner` itself, whose timing depends on the garbage collector; the path it
  takes (a dispatch the closed dispatcher drops, a free that checks the epoch) is the one
  `close()` and the shut-down tests exercise.
- **Tests**: pixels and their size; bad pixel counts; decoding a TGA written by the test, and
  bytes that aren't an image; a resizing update; the filter of each quality; closing once and
  using a closed bitmap; a bitmap from an earlier run doing nothing when closed and not freeing
  the new run's texture; an update invalidating the widgets showing it and forgetting a destroyed
  one; painter regions; a registered resource found before the root, cached per host; a file in
  the resource root, its pixels read back exactly; a window icon set, removed, and refused for a
  bitmap; an image as large as its bitmap following its update; a region; the four scales; tint,
  text colour and none; rounded corners and the warning; described and decorative images; a
  resource image laid out once decoded; a menu item's icon and its removal.
