---
status: closed
---

# samples/carousel

Port Raven's `examples/carousel`. Spec: [10: samples/carousel](../../spec.md#samplescarousel-phase-5).
Planned in [Overlays and pagers: Carousel](0242-overlays-and-pagers-carousel.md).

**Depends on:** [Overlays and pagers: HorizontalPager and VerticalPager](0253-overlays-and-pagers-horizontalpager-and-verticalpager.md).

## Deliverables

- [x] `samples/carousel`: five pages in a `HorizontalPager`, the page's title and count in a label, check boxes for loop, autoplay (three seconds) and vertical, Back and Next (wrapping from the last page when not looping).
- [x] A headless test checking each control.
- [x] No port gaps; the roadmap's sample table and `api-coverage.md` updated.

## Resolution

Built on 2026-11-27.

- **`samples/carousel`** (`CarouselSample()`, `launch-carousel`):
  - the five pages of `carousel.xml`, each a rounded `Surface` in its colour with the numbered
    ring, the heading, the text and, on the third page, its button;
  - the Loop, Autoplay (three seconds) and Vertical switches. Vertical swaps `HorizontalPager` for
    `VerticalPager` over the one hoisted `PagerState`, so the page is kept;
  - the status "Title  -  n / 5", with Back disabled on the first page unless the pages loop;
  - Next going back to the first page from the last, and reading "Get started" there when the
    pages don't loop, as the C example does.
- **The swap found a bug in the pagers**: the new pager attached before the old one let the state
  go, so the state was refused, and logged as a second pager. Now the follower takes a free state
  as the frame settles, and warns only if another pager still holds it then. The new
  `PagerTest.aPagerSwappedForAnotherKeepsTheStateAndTwoAtOnceWarn` covers it.
- **`CarouselTest`** (3):
  - the five pages and the first status, with Back disabled;
  - Next and Back, through to "Get started" and back to the first page;
  - Loop enabling Back on the first page and wrapping to the last, Autoplay on and off, and
    Vertical keeping the page.
- **No port gaps.** The README lists the sample and its launcher, and the roadmap's table marks
  `carousel` built. `api-coverage.md` needs no change: the sample binds nothing new.
