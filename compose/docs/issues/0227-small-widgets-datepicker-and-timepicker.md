---
status: open
---

# Small widgets: DatePicker and TimePicker

Add controlled `DatePicker` and `TimePicker` composables over Raven's widgets, with
kotlinx-datetime's types. Spec: [8: Date and time pickers](../spec.md#date-and-time-pickers-phase-5)
and section 12. Planned in [Small widgets: date and time pickers](archive/0210-small-widgets-date-and-time-pickers.md).

**Depends on:** [XML translation: the raven.compose-xml plugin](archive/0226-xml-translation-the-raven-compose-xml-plugin.md),
the ticket before it in phase 5's implementation sequence.

## Deliverables

- [ ] `kotlinx-datetime` in the version catalogue, an `api` dependency of compose-raven-foundation.
- [ ] `DatePicker(date, onDateChange, modifier, enabled, range, firstDayOfWeek, format)` over `Rdate_picker_*`, controlled: the user's choice calls back, and a date the app doesn't take is shown again.
- [ ] `TimePicker(time, onTimeChange, modifier, enabled, showSeconds, minuteStep)` over `Rtime_picker_*`, controlled the same way, with hidden seconds kept.
- [ ] Headless tests: the app's date and time reach Raven; a user's change (keys injected) reaches the callback and the app's value wins; the range refuses a date outside it; `format` gives Raven's text (`Rdate_picker_get_text`); `firstDayOfWeek` and `minuteStep` reach Raven.
- [ ] The spec's section and section 12's row checked against what was built; `api-coverage.md` updated.
