---
status: closed
---

# Icon and Google's icons

Add `Icon(painter, contentDescription, modifier, tint)` to compose-raven-foundation, drawing a
`painterResource` as a mask, and prove that any icon downloaded from
<https://fonts.google.com/icons> renders correctly. Spec:
[8: Images and resources](../spec.md#images-and-resources-phase-3) (the `Icon` bullet) and section
12. Planned in [Icons and resources: vector icons](archive/0207-icons-and-resources-vector-icons.md).

**Depends on:** [Raven: mask textures](archive/0221-raven-mask-textures.md).

## Deliverables

- [x] `Icon(painter, contentDescription, modifier = Modifier, tint: Color = Color.Unspecified)`, 24 dp by default; `Unspecified` is the text colour where it sits (`Rimage_set_tint_text`).
- [x] A resource painter gives `Icon` its mask texture (`Rtexture_create_mask_from_file_async`), cached beside its colour texture by path, so one `painterResource` serves `Image` and `Icon`; another painter is multiplied.
- [x] Fixtures under the tests' resources, with Apache 2.0's `LICENSE` beside them and a `THIRD_PARTY.md` line: from Google's repository, Material Symbols Outlined, Rounded and Sharp, unfilled and filled, and a two-tone Material Icon. **The user downloads two icons with the website's Download button**, to settle what it writes (the research believed `24px` units and a grey fill): `search` in Material Symbols Outlined with the defaults (weight 400, grade 0, 24, no fill), and `star` in Material Symbols Rounded with Fill on, saved unrenamed and unedited in `compose-raven-foundation/src/nativeTest/resources/icons/google/downloaded/`. The repository's fixtures go in `icons/google/`, with the licence. **Done 2026-11-25**: the user downloaded `home.svg` and `star.svg` (the outlined star, not the filled one) into that folder. They show what the button writes, which the research could only guess: `viewBox="0 0 24 24"` (the path rescaled to 24 units, not Symbols' `0 -960 960 960`), `width`/`height` of `24px`, a root `fill`, and absolute commands with commas. `home.svg` and `star.svg` have a black `fill="#000000"`; then `favorite.svg`, downloaded with the site's **default** parameters, has the grey `fill="#e3e3e3"` the research expected (a multiplying tint would give 89% of the colour, which the mask avoids) and relative `q`/`t` curves. The licence file still comes with this ticket.
- [x] Headless tests: every fixture loads as a mask with coverage where the icon is; `Icon` follows the text colour and a given tint; the size is 24 dp unless the modifier says otherwise. With `RAVEN_GPU_TESTS=1`: each fixture drawn tinted shows the tint's colour and the two-tone layer lighter.
- [x] `TextField`'s KDoc example uses `Icon`; the spec's `Icon` bullet and section 12's two rows checked against what was built; `api-coverage.md` updated.

## Resolution

Built on 2026-11-26.

- **`Icon(painter, contentDescription, modifier, tint = Color.Unspecified)`** in
  `raven.compose.foundation` (`Icon.kt`): an `Image` of the painter's mask, `Modifier.size(24.dp)`
  then the modifier (the last size in a chain wins), `ContentScale.Fit`, and `ColorFilter.tint(tint)`
  or, unspecified, `ColorFilter.textColor()` (`Rimage_set_tint_text`).
- **`Painter.mask`** (internal API, in compose-raven-ui): a `ResourcePainter`'s is a `MaskPainter` made
  lazily the first time an `Icon` asks (`Rtexture_create_mask_from_file_async`); since the host keeps
  one painter per path, its mask is kept beside its colour texture and one `painterResource` serves
  `Image` and `Icon`. Other painters have none, and `Icon` multiplies them.
- **Fixtures**, unmodified, in `compose-raven-foundation/src/nativeTest/resources/icons/google/`: from
  `google/material-design-icons` at `10ecc14` (the research's commit), `star` unfilled and filled in
  Material Symbols Outlined, Rounded and Sharp, `search`, and the two-tone Material Icon `star`; the
  user's three downloads; the repository's `LICENSE`, a `README.md` saying where each came from, and
  a row in Raven's `THIRD_PARTY.md`. The build writes their folder's path into the tests
  (`TEST_RESOURCES`).
- **Tests** (`IconTest`, 7, passing): all 11 fixtures decode with coverage between 5% and 90% of their
  box and none in the corner; the two-tone `.3` layer and the solid outline; the grey download whole
  coverage; an `Icon` gets the mask, not the `Image`'s colour texture, ready at 48 x 48; the tint is the
  text colour unless given; 24 dp unless the modifier says 48; a `BitmapPainter` multiplied. With
  `RAVEN_GPU_TESTS=1` (run from the test binary, as Gradle doesn't pass the variable on), drawn at
  96 dp in red on a window and captured: the filled star exactly (255, 0, 0), the outlined grey heart's
  stroke exactly (255, 0, 0) where a multiplied tint would give 89%, and the two-tone star's middle the
  tint at .3 over the background.
- `TextField`'s KDoc example uses `Icon` for the lock and the show/hide toggle. The spec's `Icon`
  bullet and section 12's rows match what was built. `api-coverage.md`: 480 of 1,223, and a row for
  `Icon`.
