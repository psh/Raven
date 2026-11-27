---
status: open
---

# Icon and Google's icons

Add `Icon(painter, contentDescription, modifier, tint)` to compose-raven-foundation, drawing a
`painterResource` as a mask, and prove that any icon downloaded from
<https://fonts.google.com/icons> renders correctly. Spec:
[8: Images and resources](../spec.md#images-and-resources-phase-3) (the `Icon` bullet) and section
12. Planned in [Icons and resources: vector icons](archive/0207-icons-and-resources-vector-icons.md).

**Depends on:** [Raven: mask textures](archive/0221-raven-mask-textures.md).

## Deliverables

- [ ] `Icon(painter, contentDescription, modifier = Modifier, tint: Color = Color.Unspecified)`, 24 dp by default; `Unspecified` is the text colour where it sits (`Rimage_set_tint_text`).
- [ ] A resource painter gives `Icon` its mask texture (`Rtexture_create_mask_from_file_async`), cached beside its colour texture by path, so one `painterResource` serves `Image` and `Icon`; another painter is multiplied.
- [ ] Fixtures under the tests' resources, with Apache 2.0's `LICENSE` beside them and a `THIRD_PARTY.md` line: from Google's repository, Material Symbols Outlined, Rounded and Sharp, unfilled and filled, and a two-tone Material Icon. **The user downloads two icons with the website's Download button**, to settle what it writes (the research believed `24px` units and a grey fill): `search` in Material Symbols Outlined with the defaults (weight 400, grade 0, 24, no fill), and `star` in Material Symbols Rounded with Fill on, saved unrenamed and unedited in `compose-raven-foundation/src/nativeTest/resources/icons/google/downloaded/`. The repository's fixtures go in `icons/google/`, with the licence. **Done 2026-11-25**: the user downloaded `home.svg` and `star.svg` (the outlined star, not the filled one) into that folder. They show what the button writes, which the research could only guess: `viewBox="0 0 24 24"` (the path rescaled to 24 units, not Symbols' `0 -960 960 960`), `width`/`height` of `24px`, a root `fill`, and absolute commands with commas. `home.svg` and `star.svg` have a black `fill="#000000"`; then `favorite.svg`, downloaded with the site's **default** parameters, has the grey `fill="#e3e3e3"` the research expected (a multiplying tint would give 89% of the colour, which the mask avoids) and relative `q`/`t` curves. The licence file still comes with this ticket.
- [ ] Headless tests: every fixture loads as a mask with coverage where the icon is; `Icon` follows the text colour and a given tint; the size is 24 dp unless the modifier says otherwise. With `RAVEN_GPU_TESTS=1`: each fixture drawn tinted shows the tint's colour and the two-tone layer lighter.
- [ ] `TextField`'s KDoc example uses `Icon`; the spec's `Icon` bullet and section 12's two rows checked against what was built; `api-coverage.md` updated.
