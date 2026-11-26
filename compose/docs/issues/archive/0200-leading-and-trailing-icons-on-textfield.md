---
status: closed
---

# Leading and trailing icons on TextField

Compose's `TextField` takes `leadingIcon` and `trailingIcon` slots: a search glass at the start, a
clear button or a show/hide toggle for a password at the end. compose-raven's has neither, and an
app can't lay one over the field well: Raven's text input keeps the same small margin on both sides
of its text (a fixed 5 px in the macOS look, 9 px in GNOME's), so long text runs under the icon,
and clicking a button laid over it takes the focus from the field. Raven needs to keep room at the
start and the end of its text, and the binding then places the icons there. Asked for on
2026-11-25, from [Password, max length and submit on TextField](0194-password-max-length-and-submit-on-textfield.md).

**Depends on:** [Password, max length and submit on TextField](0194-password-max-length-and-submit-on-textfield.md),
for the password example.

## Deliverables

- [x] **Raven change** (section 11 of the spec, change 10): `Rtext_input_set_insets(Rid text_input,
      float start, float end)`, in points, added to the look's own margin on that side. Start and
      end follow the layout direction (`Rset_layout_direction`), so in right-to-left they are the
      right and the left. The text, the caret, the placeholder, the selection, scrolling the text
      and clicking to place the caret all keep to the narrower area; the field's background and
      focus ring stay the whole widget. `Rtext_input_get_insets` reads them back. A C test, as for
      the other fork changes.
- [x] `TextField(leadingIcon: (@Composable () -> Unit)? = null, trailingIcon: (@Composable () -> Unit)? = null)`,
      in Compose's place, after `placeholder`. Each icon is composed over the field, centred
      vertically at its edge, and the field's inset on that side is set to the icon's measured width
      plus a gap, following its size when it changes. Without an icon the inset is 0, so a field
      with neither looks as it does now.
- [x] Focus: clicking or pressing an icon that is a button leaves the focus in the field, as
      Compose's do, so a show/hide toggle doesn't stop the typing. An icon that is only an image
      doesn't take clicks from the field.
- [x] Accessibility: the icons are the field's children for the screen reader, after the field, and
      a button among them keeps its own name (`accessibleDescription`).
- [x] The password toggle as the example in the KDoc and the spec: an eye icon in `trailingIcon`
      switching `visualTransformation` between `PasswordVisualTransformation()` and
      `VisualTransformation.None`.
- [x] Spec: the `TextField` row of section 8 and section 7's Text bullets; section 12 if the slots
      differ from Compose's (for instance, no `prefix`/`suffix`).
- [x] Tests: the inset follows each icon's width; text typed past the field's width scrolls short of
      the trailing icon; right-to-left swaps the sides; clicking the trailing button toggles the
      password and leaves the focus and the caret in the field; a field with no icons is unchanged.

## Notes

- **Why insets, not a reveal button built into Raven**: Windows' password boxes draw their own eye,
  and Raven could too, but that covers only passwords. Insets cover a search icon, a clear button, a
  unit ("kg") and the password toggle with one change, and leave the icons to the app.
- **Out of scope**: Compose's `prefix`, `suffix`, `label` and `supportingText` slots. `prefix` and
  `suffix` could reuse the insets later.

## Resolution

Done on macOS. `./gradlew build allTests` passes: 610 tests, 5 of them in foundation's new
`TextFieldIconsTest`. Raven's C tests pass but for `test_shell` and `test_themes`, which fail the
same way without these changes.

- **Two Raven changes**, one commit each (spec section 11, changes 10 and 11), with the bindings
  regenerated:
  - `Rtext_input_set_insets` / `_get_insets` (89e9e14), as the ticket asked. The desired width also
    grows by both insets, so the text keeps its room beside the icons. `test_text_input_insets`
    clicks 102 in from the left: into the text without insets, before its first letter with a
    start inset of 100, and the same with an end inset of 100 in right to left.
  - `Rwidget_set_focus_on_click` / `_get_focus_on_click` (113cc66), which the ticket didn't foresee:
    Raven gives the focus to whatever focusable widget a press lands in, and nothing could ask
    otherwise, so a button over the field took the focus from it. With false, a click on the widget
    or inside it leaves the focus where it is, while Tab still reaches its buttons, as Compose's
    clickables do. `test_focus_on_click`.
- **`TextField(leadingIcon, trailingIcon)`**, after `placeholder` as in Compose. With neither, the
  field is the single widget it was, with no insets. With icons, an Overlay holds the field
  (filling it) and each icon in a box centred at its edge, 4 dp in; a `WidgetFollower` on the field
  sets its insets once a frame to each box's laid-out width plus 4 dp on each side, and each box
  has `Rwidget_set_focus_on_click(false)`. A box lets the pointer through where its content does,
  so an image's click reaches the field.
- **The modifier**, which a Box around the field would otherwise take whole, tag and all: ui's new
  `splitAroundWidget()` gives the field its test tag, semantics, focus, input handlers, tooltip,
  pointer icon and attached requesters, and the Box the layout and visuals. So
  `onNodeWithTag(...).performTextInput` still finds the field.
- **Adding the first icon or removing the last recreates the field**: a different tree. The text is
  the app's, so only the caret and focus are lost; the KDoc says so.
- **Tests**: no icons, no insets; the insets follow each icon's width as it changes; the tag and the
  typing reach the field and the width the Box; a show/hide button toggles the password and the
  field keeps the focus and the typing (it fails with the focus flag off); an image-only icon lets
  the click through to the field.
- **Not tested from Kotlin**: right to left (no layout direction in compose-raven yet) and the text
  scrolling short of the icon; the C test covers where the text area is.
- **Spec**: section 11's changes 10 and 11 (and its branch name corrected: the changes are commits
  on `compose-raven`), section 8's `TextField` row and section 12's.
