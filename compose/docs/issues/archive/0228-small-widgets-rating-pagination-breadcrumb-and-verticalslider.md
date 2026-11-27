---
status: closed
---

# Small widgets: Rating, Pagination, Breadcrumb and VerticalSlider

Add `Rating`, `Pagination`, `Breadcrumb` and `VerticalSlider`, controlled composables over Raven's
widgets. Spec: [8: Rating, Pagination, Breadcrumb and VerticalSlider](../../spec.md#rating-pagination-breadcrumb-and-verticalslider-phase-5)
and section 12. Planned in [Small widgets: Rating, Pagination, Breadcrumb and a vertical Slider](0211-small-widgets-rating-pagination-breadcrumb-and-a-vertical-slider.md).

**Depends on:** [Small widgets: DatePicker and TimePicker](0227-small-widgets-datepicker-and-timepicker.md).

## Deliverables

- [x] `Rating(value, onValueChange, modifier, enabled, readOnly, max, allowHalf, starSize)`; `max` outside 1 to 20 throws.
- [x] `Pagination(page, pageCount, onPageChange, modifier, enabled, siblings)`, pages from 0.
- [x] `Breadcrumb(items, onItemClick, modifier)`, the items given one by one so a text may hold `|`.
- [x] `VerticalSlider(value, onValueChange, modifier, enabled, valueRange, steps, colors)`, with the minimum at the bottom: check which end Raven puts it at, and flip it in the binding if Raven's is the top.
- [x] Headless tests for each: the app's value reaches Raven, a user's change (input injected) reaches the callback and the app's value wins; Breadcrumb's items and the clicked index; a read-only Rating refuses a change.
- [x] The spec's section and section 12's rows checked against what was built; `api-coverage.md` updated.

## Resolution

Built on 2026-11-27.

- **The binding layer** gains `RatingValue`, `RatingChanged`, `PaginationPage`,
  `PaginationChanged` and `BreadcrumbClicked`.
- **`Rating`, `Pagination` and `Breadcrumb`** are in `Navigation.kt`, with the spec's parameters:
  - `Rating` binds the stars and halves before the value, so Raven snaps it. It coerces `value`
    into 0 to `max`, and `max` outside 1 to 20 throws.
  - `Pagination` binds the count before the page and coerces `page` into it. `pageCount` must be
    at least 1, and `siblings` mustn't be negative.
  - `Breadcrumb` empties Raven's items and adds them one by one, so a text may hold `|`. Raven
    changes nothing on a click: the app shortens its list.
- **`VerticalSlider`** is in `Inputs.kt`. It shares `Slider`'s body, adding
  `Rslider_set_orientation`. Raven's vertical slider already grows upwards, which the test checks
  with clicks at the bottom and the top, so nothing is flipped.
- **Tests**: 12 in `NavigationWidgetsTest`. Each widget gets:
  - the app's value reaching Raven;
  - a click or key reaching the callback, and the app's value winning.

  Also covered:
  - Rating: half stars, a read-only rating refusing both keys and clicks, and 21 stars throwing;
  - Pagination: the page kept within a smaller count;
  - Breadcrumb: an item with `|`, and the clicked index (Left, Enter) with the app shortening the
    trail;
  - VerticalSlider: upright, with its minimum at the bottom;
  - all three can be disabled.
- **`api-coverage.md`**: 520 of 1,701 functions, 42 of 58 widget types. The three rows are marked
  built, and the Slider's missing vertical orientation is gone from the partial bindings.
