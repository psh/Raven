---
status: closed
---

# Small widgets: DatePicker and TimePicker

Add controlled `DatePicker` and `TimePicker` composables over Raven's widgets, with
kotlinx-datetime's types. Spec: [8: Date and time pickers](../../spec.md#date-and-time-pickers-phase-5)
and section 12. Planned in [Small widgets: date and time pickers](0210-small-widgets-date-and-time-pickers.md).

**Depends on:** [XML translation: the raven.compose-xml plugin](0226-xml-translation-the-raven-compose-xml-plugin.md),
the ticket before it in phase 5's implementation sequence.

## Deliverables

- [x] `kotlinx-datetime` in the version catalogue, an `api` dependency of compose-raven-foundation.
- [x] `DatePicker(date, onDateChange, modifier, enabled, range, firstDayOfWeek, format)` over `Rdate_picker_*`, controlled: the user's choice calls back, and a date the app doesn't take is shown again.
- [x] `TimePicker(time, onTimeChange, modifier, enabled, showSeconds, minuteStep)` over `Rtime_picker_*`, controlled the same way, with hidden seconds kept.
- [x] Headless tests: the app's date and time reach Raven; a user's change (keys injected) reaches the callback and the app's value wins; the range refuses a date outside it; `format` gives Raven's text (`Rdate_picker_get_text`); `firstDayOfWeek` and `minuteStep` reach Raven.
- [x] The spec's section and section 12's row checked against what was built; `api-coverage.md` updated.

## Resolution

Built on 2026-11-27.

- **`kotlinx-datetime` 0.8.0** is in the version catalogue and is an `api` dependency of
  compose-raven-foundation.
- **The binding layer** (`Binding.kt`) gains `DatePickerDate`, `DatePickerChanged`,
  `TimePickerTime` and `TimePickerChanged`. compose-raven-ui stays without kotlinx-datetime, so a
  date is packed as `year * 10000 + month * 100 + day` and a time as its second of the day.
- **`DatePicker` and `TimePicker`** are in `DateTimePickers.kt`, with the parameters of the spec:
  - `DatePicker` binds the range before the date, and shows a date outside the range within it,
    as `Slider` coerces its value. A date or range outside Raven's years 1 to 9999 throws.
  - `TimePicker` sets Raven's clock to 24 hours, whatever the locale uses. `minuteStep` must be 1
    to 59. Seconds the picker hides are kept, and a fraction of a second is left out.
- **Tests**: 13 in `DateTimePickersTest`. They cover:
  - the app's date and time reaching Raven with no callback;
  - Down and Up keys reaching the callback, and the app's value winning;
  - the range refusing the day after it;
  - `format` read back through `Rdate_picker_get_text`;
  - the minute step, as what the arrows add;
  - the seconds shown, the 24-hour clock, and both pickers disabled.
- **Not testable through Raven**: it has no getter for the first day of the week, so only its
  mapping is tested (Sunday 0 to Saturday 6). The calendar draws from it.
- **`api-coverage.md`**: 503 of 1,701 functions, 39 of 58 widget types. Both rows are marked built.
  The spec's section records the coercion, the 24-hour clock and the packed binding.
