---
status: closed
wayfinder: grilling
parent: 0204
blocked_by: [0206]
assignee: claude
---

# Icons and resources: vector icons

## Question

How does an app show a vector icon? Decide the API (`painterResource("x.svg")`, an `Icon`
composable with Compose's shape, `ImageVector` or not), how it stays crisp at every density and
`RavenUiScale` (rasterise per scale, cache), how it is tinted (`tint`, `ColorFilter.textColor()`,
`LocalContentColor`), and how it fits `TextField`'s icons and the small widgets' arrows and stars.
**Compatibility is required**: any icon downloaded from <https://fonts.google.com/icons>, Material
Symbols and Material Icons alike, renders correctly, crisp and tinted, proved by a few committed
Google icon fixtures in a test. The answer is the spec section and its implementation tickets.

From [Icons and resources: SVG facts](0206-icons-and-resources-svg-facts.md): Raven already
reads every Google file correctly (nanosvg, the negative `viewBox`) and `painterResource` already
redraws an SVG at each density and UI scale. What is missing is **tint**: Raven's tint multiplies
the texture's colour, so the icons, black by default, stay black. Decide how an icon is tinted (a
Raven change drawing an SVG as an alpha mask, or recolouring the fill before rasterising, or
another way), and whether Material Icons' two-tone `opacity` layers keep their opacity.

## Resolution

Resolved on 2026-11-25 in conversation. Written into spec section 8 (the `Icon` bullet under
Images and resources), section 11 (change 12, to be made) and section 12 (two rows), and filed as
[Raven: mask textures](../0221-raven-mask-textures.md) and
[Icon and Google's icons](../0222-icon-and-googles-icons.md), in that order.

- **Tint**: an icon is drawn as a **mask**, its alpha only, so the tint gives exactly its colour.
  This is a Raven change, `Rtexture_create_mask_from_file_async(path)`, into the existing
  `R_TEXTURE_ALPHA8` format, redrawn per scale as colour SVGs are. It was chosen over adding a
  white fill to the SVG text in the binding, which would leave a grey-filled download at 89% and
  lose Raven's async loading.
- **`Icon(painter, contentDescription, modifier, tint)`**, Compose's shape, 24 dp by default
  whatever the file says. It asks a resource painter for its mask texture, cached beside the
  colour one by path; another painter is multiplied as `Image` is. `Image` is unchanged and draws
  an SVG in its own colours.
- **`tint = Color.Unspecified`** is the text colour where the icon sits (`Rimage_set_tint_text`),
  not Compose's "untinted", as there is no `LocalContentColor`; an untinted icon is an `Image`. It
  follows a button's content colour, hover and the disabled fade through Raven, with nothing added.
- **No `ImageVector`** and no bundled icons: apps ship the SVG files they download.
- **Google compatibility** is proved by fixtures from Google's repository (Symbols in three styles,
  filled and not, and a two-tone Material Icon) and two the user downloads with the website's
  button, tested headless as masks and, with `RAVEN_GPU_TESTS=1`, for colour.
