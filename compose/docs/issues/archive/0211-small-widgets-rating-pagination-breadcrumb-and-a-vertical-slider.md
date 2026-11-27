---
status: closed
wayfinder: grilling
parent: 0204
blocked_by: []
assignee: claude
---

# Small widgets: Rating, Pagination, Breadcrumb and a vertical Slider

## Question

How do Raven's Rating, Pagination and Breadcrumb become composables, none of which Compose has,
and how does `Slider` take Raven's vertical orientation (`Rslider_set_orientation`)? Decide their
names, parameters and controlled state in Compose's style, and whether they use the vector icons.
The answer is the spec section and its implementation tickets.

## Resolution

Resolved on 2026-11-26 in conversation. Written into spec section 8 (Rating, Pagination,
Breadcrumb and VerticalSlider) and section 12, and filed as
[Small widgets: Rating, Pagination, Breadcrumb and VerticalSlider](0228-small-widgets-rating-pagination-breadcrumb-and-verticalslider.md).

- **No icons**: Raven draws the stars, chevrons and arrows itself, so none of them takes an icon
  parameter (the batch had expected them to use the vector icons).
- **All controlled**, as the binding's inputs are: `Rating(value, onValueChange, ..., readOnly,
  max, allowHalf, starSize)`; `Pagination(page, pageCount, onPageChange, ..., siblings)` with pages
  from 0; `Breadcrumb(items, onItemClick)`, the app owning the list and the items given one by one.
- **`VerticalSlider`**, Material 3's name with `Slider`'s controlled parameters, rather than an
  `orientation` on `Slider`; the minimum at the bottom, checked against Raven when built.
- **One implementation ticket** for the four.
