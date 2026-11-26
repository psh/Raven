---
status: open
---

# Small widgets: Rating, Pagination, Breadcrumb and VerticalSlider

Add `Rating`, `Pagination`, `Breadcrumb` and `VerticalSlider`, controlled composables over Raven's
widgets. Spec: [8: Rating, Pagination, Breadcrumb and VerticalSlider](../spec.md#rating-pagination-breadcrumb-and-verticalslider-phase-5)
and section 12. Planned in [Small widgets: Rating, Pagination, Breadcrumb and a vertical Slider](archive/0211-small-widgets-rating-pagination-breadcrumb-and-a-vertical-slider.md).

**Depends on:** [Small widgets: DatePicker and TimePicker](0227-small-widgets-datepicker-and-timepicker.md).

## Deliverables

- [ ] `Rating(value, onValueChange, modifier, enabled, readOnly, max, allowHalf, starSize)`; `max` outside 1 to 20 throws.
- [ ] `Pagination(page, pageCount, onPageChange, modifier, enabled, siblings)`, pages from 0.
- [ ] `Breadcrumb(items, onItemClick, modifier)`, the items given one by one so a text may hold `|`.
- [ ] `VerticalSlider(value, onValueChange, modifier, enabled, valueRange, steps, colors)`, with the minimum at the bottom: check which end Raven puts it at, and flip it in the binding if Raven's is the top.
- [ ] Headless tests for each: the app's value reaches Raven, a user's change (input injected) reaches the callback and the app's value wins; Breadcrumb's items and the clicked index; a read-only Rating refuses a change.
- [ ] The spec's section and section 12's rows checked against what was built; `api-coverage.md` updated.
