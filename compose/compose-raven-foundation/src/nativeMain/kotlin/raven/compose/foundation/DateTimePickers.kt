package raven.compose.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.isoDayNumber
import raven.cinterop.R_CLOCK_24_HOUR
import raven.cinterop.R_FALSE
import raven.cinterop.R_TRUE
import raven.cinterop.R_WIDGET_DATE_PICKER
import raven.cinterop.R_WIDGET_TIME_PICKER
import raven.cinterop.Rdate_picker_set_first_day_of_week
import raven.cinterop.Rdate_picker_set_format
import raven.cinterop.Rdate_picker_set_range
import raven.cinterop.Rtime_picker_set_clock
import raven.cinterop.Rtime_picker_set_minute_step
import raven.cinterop.Rtime_picker_set_seconds_visible
import raven.compose.ui.DatePickerChanged
import raven.compose.ui.DatePickerDate
import raven.compose.ui.Modifier
import raven.compose.ui.Property
import raven.compose.ui.RavenWidget
import raven.compose.ui.TimePickerChanged
import raven.compose.ui.TimePickerTime
import raven.compose.ui.WidgetDescriptor
import raven.compose.ui.bind

private val DatePickerWidget = WidgetDescriptor("DatePicker", R_WIDGET_DATE_PICKER, events = listOf(DatePickerChanged))
private val TimePickerWidget = WidgetDescriptor("TimePicker", R_WIDGET_TIME_PICKER, events = listOf(TimePickerChanged))

private val DateRange = Property<ClosedRange<LocalDate>?> { widget, range ->
    if (range == null) {
        Rdate_picker_set_range(widget, 0, 0, 0, 0, 0, 0)
    } else {
        val (min, max) = range.start to range.endInclusive
        Rdate_picker_set_range(widget, min.year, min.month.number(), min.day, max.year, max.month.number(), max.day)
    }
}
private val FirstDayOfWeek = Property<DayOfWeek> { widget, day -> Rdate_picker_set_first_day_of_week(widget, day.ravenDay()) }
private val DateFormat = Property<String> { widget, format -> Rdate_picker_set_format(widget, format) }
private val SecondsVisible = Property<Boolean> { widget, visible -> Rtime_picker_set_seconds_visible(widget, if (visible) R_TRUE else R_FALSE) }
/** compose-raven's TimePicker is 24-hour, whatever the locale's clock. */
private val TwentyFourHours = Property<Unit> { widget, _ -> Rtime_picker_set_clock(widget, R_CLOCK_24_HOUR) }
private val MinuteStep = Property<Int> { widget, step -> Rtime_picker_set_minute_step(widget, step) }

/** Raven's number for [this]: 0 for Sunday, 1 for Monday, ... 6 for Saturday. */
internal fun DayOfWeek.ravenDay(): Int = isoDayNumber % 7

private fun kotlinx.datetime.Month.number(): Int = ordinal + 1

/** [date] packed as Raven's binding layer keeps it, `year * 10000 + month * 100 + day`. */
private fun LocalDate.packed(): Int = year * 10000 + month.number() * 100 + day

/**
 * Raven's date field showing [date], with a calendar that drops down from it, controlled as
 * [TextField] is: the user's choice calls [onDateChange], and a date the app doesn't take is shown
 * again. [range], when given, is the dates that can be chosen; [date] is shown within it.
 * [format] is Raven's pattern: `YYYY`, `MM`, `M`, `DD`, `D`, `MMMM` (the month's name), `MMM`,
 * `dddd` (the day's name), `ddd`; `"D MMMM YYYY"` gives "3 November 2026".
 */
@Composable
public fun DatePicker(
    date: LocalDate,
    onDateChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    range: ClosedRange<LocalDate>? = null,
    firstDayOfWeek: DayOfWeek = DayOfWeek.SUNDAY,
    format: String = "YYYY-MM-DD",
) {
    require(date.year in 1..9999) { "Raven's DatePicker shows the years 1 to 9999, not ${date.year}" }
    if (range != null) {
        require(!range.isEmpty()) { "the range $range is empty" }
        require(range.start.year in 1..9999 && range.endInclusive.year in 1..9999) { "Raven's DatePicker shows the years 1 to 9999, not $range" }
    }
    val shown = if (range == null) date else date.coerceIn(range.start, range.endInclusive)
    val changed: (Int) -> Unit = remember(onDateChange) {
        { packed -> onDateChange(LocalDate(packed / 10000, packed / 100 % 100, packed % 100)) }
    }
    RavenWidget(DatePickerWidget, modifier, {
        bind(enabled, Enabled)
        bind(firstDayOfWeek, FirstDayOfWeek)
        bind(format, DateFormat)
        // The range before the date: Raven keeps the date inside it.
        bind(range, DateRange)
        bind(shown.packed(), DatePickerDate)
        bind(changed, DatePickerChanged)
    })
}

/**
 * Raven's 24-hour time field showing [time], its hours, minutes and, with [showSeconds], seconds
 * changed by the arrows, the wheel or typing; controlled as [TextField] is. The seconds of [time]
 * are kept when they aren't shown, and its fraction of a second is left out. [minuteStep] is what
 * the arrows and the wheel add to the minutes.
 */
@Composable
public fun TimePicker(
    time: LocalTime,
    onTimeChange: (LocalTime) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showSeconds: Boolean = false,
    minuteStep: Int = 1,
) {
    require(minuteStep in 1..59) { "minuteStep must be 1 to 59, not $minuteStep" }
    val changed: (Int) -> Unit = remember(onTimeChange) {
        { second -> onTimeChange(LocalTime.fromSecondOfDay(second)) }
    }
    RavenWidget(TimePickerWidget, modifier, {
        bind(enabled, Enabled)
        bind(Unit, TwentyFourHours)
        bind(showSeconds, SecondsVisible)
        bind(minuteStep, MinuteStep)
        bind(time.toSecondOfDay(), TimePickerTime)
        bind(changed, TimePickerChanged)
    })
}
