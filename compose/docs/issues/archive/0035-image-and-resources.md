---
status: closed
wayfinder: grilling
parent: 0023
blocked_by: []
assignee: Paul Hawke
---

# Image and resources

## Question

How does compose-raven show images? Decide the shape of `Image` over Raven's Image widget (`Source`, `Tint`, `Stretch`, `Region`, `CornerRadius`), how image files and textures are loaded and referenced (`painterResource`-like, paths relative to what), and how menu item icons (`Rmenu_item_set_icon`) use the same mechanism.

## Resolution

Facts (`raven_resources.h`, `raven_controls.h`, `raven_dialogs.h`): textures come from bytes, files or RGBA pixels. Bytes and files can be **decoded asynchronously** on a worker, drawing nothing until ready. `Rtexture_update` replaces pixels, with linear, nearest or mipmapped filtering. Formats are **PNG, JPEG, BMP, TGA, GIF and PSD** (stb_image), with no SVG. The Image widget has a texture, a tint (or the text colour), a stretch (Fill, Fit, Cover, None), a source region and a corner radius. `Rfile_register_memory(path, bytes)` puts files in memory, and **every file Raven reads** (XML and Include, themes, fonts, textures) is looked up there first.

- **Resource paths**: relative paths resolve against a **resource root**, by default `resources/` next to the executable (not the current directory). `application(resourceRoot = ...)` overrides it, and App bundling will point it at a `.app`'s `Contents/Resources`. `registerResource(path, bytes)` uses `Rfile_register_memory`, so embedded files are found first by fonts, images, theme files and the XML translator alike. A Gradle task that embeds a resources folder, and Compose's generated `Res.drawable.foo` accessors, go to the Generated resources fog.
- **`ImageBitmap` replaces the name `Texture`** used by earlier tickets, following Compose. It wraps a Raven texture:
  - `ImageBitmap(width, height, rgbaPixels)` with `update(pixels)`
  - `decodeToImageBitmap(bytes)`
  - `ImageBitmap.fromVulkanImageView(...)` in `compose-raven-vulkan`
  - `Viewport(image: ImageBitmap)`
  - `OpenGLTexture` produces one
  
  It lives on the GPU and can't be read back (documented).
- **`Image(painter: Painter, contentDescription: String?, modifier, contentScale = ContentScale.Fit, colorFilter: ColorFilter? = null)`**:
  - `painterResource(path)` loads asynchronously (`Rtexture_create_from_file_async`) and is cached per path until shutdown.
  - `BitmapPainter(image, srcOffset, srcSize)`, whose source rectangle uses `Rimage_set_region` (sprite sheets).
  - `ContentScale.Fit`/`Crop`/`FillBounds`/`None` map to FIT/COVER/FILL/NONE. `Inside`, `FillWidth` and `FillHeight` are left out.
  - `ColorFilter.tint(color)` uses `Rimage_set_tint_color`.
  - `Modifier.clip(RoundedCornerShape(r))` is honoured on `Image` only (`Rimage_set_corner_radius`).
  - `contentDescription` sets the accessible name and the Image role.
- **Lifetime**: cached resources live until shutdown. App-made `ImageBitmap`s are destroyed when unreachable, through a Kotlin/Native `Cleaner` that posts `Rtexture_destroy` through `RavenDispatcher`, and `close()` frees one at once. `compose-raven-test`'s leak check also counts live textures.
- **Icons** take `Painter?`:
  - `Item(icon)` in menus uses `Rmenu_item_set_icon`.
  - `Tab(icon)` uses `Rtab_view_set_tab_icon`.
  - `TreeView(itemIcon: (T) -> Painter?)` uses `Rtree_item_set_icon`.
  - `DrawScope.drawImage(image, ...)`.
  - `Window(icon)` uses `Rwindow_set_icon`.
  
  Monochrome icons tint. There is no SVG or `ImageVector`, because Raven draws no vectors (documented, Vector icons fog).
