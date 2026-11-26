---
status: open
---

# Leading and trailing icons on TextField

Compose's `TextField` takes `leadingIcon` and `trailingIcon` slots: a search glass at the start, a
clear button or a show/hide toggle for a password at the end. compose-raven's has neither, and an
app can't lay one over the field well: Raven's text input keeps the same small margin on both sides
of its text (a fixed 5 px in the macOS look, 9 px in GNOME's), so long text runs under the icon,
and clicking a button laid over it takes the focus from the field. Raven needs to keep room at the
start and the end of its text, and the binding then places the icons there. Asked for on
2026-11-25, from [Password, max length and submit on TextField](archive/0194-password-max-length-and-submit-on-textfield.md).

**Depends on:** [Password, max length and submit on TextField](archive/0194-password-max-length-and-submit-on-textfield.md),
for the password example.

## Deliverables

- [ ] **Raven change** (section 11 of the spec, change 10): `Rtext_input_set_insets(Rid text_input,
      float start, float end)`, in points, added to the look's own margin on that side. Start and
      end follow the layout direction (`Rset_layout_direction`), so in right-to-left they are the
      right and the left. The text, the caret, the placeholder, the selection, scrolling the text
      and clicking to place the caret all keep to the narrower area; the field's background and
      focus ring stay the whole widget. `Rtext_input_get_insets` reads them back. A C test, as for
      the other fork changes.
- [ ] `TextField(leadingIcon: (@Composable () -> Unit)? = null, trailingIcon: (@Composable () -> Unit)? = null)`,
      in Compose's place, after `placeholder`. Each icon is composed over the field, centred
      vertically at its edge, and the field's inset on that side is set to the icon's measured width
      plus a gap, following its size when it changes. Without an icon the inset is 0, so a field
      with neither looks as it does now.
- [ ] Focus: clicking or pressing an icon that is a button leaves the focus in the field, as
      Compose's do, so a show/hide toggle doesn't stop the typing. An icon that is only an image
      doesn't take clicks from the field.
- [ ] Accessibility: the icons are the field's children for the screen reader, after the field, and
      a button among them keeps its own name (`accessibleDescription`).
- [ ] The password toggle as the example in the KDoc and the spec: an eye icon in `trailingIcon`
      switching `visualTransformation` between `PasswordVisualTransformation()` and
      `VisualTransformation.None`.
- [ ] Spec: the `TextField` row of section 8 and section 7's Text bullets; section 12 if the slots
      differ from Compose's (for instance, no `prefix`/`suffix`).
- [ ] Tests: the inset follows each icon's width; text typed past the field's width scrolls short of
      the trailing icon; right-to-left swaps the sides; clicking the trailing button toggles the
      password and leaves the focus and the caret in the field; a field with no icons is unchanged.

## Notes

- **Why insets, not a reveal button built into Raven**: Windows' password boxes draw their own eye,
  and Raven could too, but that covers only passwords. Insets cover a search icon, a clear button, a
  unit ("kg") and the password toggle with one change, and leave the icons to the app.
- **Out of scope**: Compose's `prefix`, `suffix`, `label` and `supportingText` slots. `prefix` and
  `suffix` could reuse the insets later.
