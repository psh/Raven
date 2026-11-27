package raven.compose.foundation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import raven.cinterop.R_CLOCK_24_HOUR
import raven.cinterop.R_FALSE
import raven.cinterop.R_TRUE
import raven.cinterop.Rdate_picker_get_date
import raven.cinterop.Rdate_picker_get_text
import raven.cinterop.Rid
import raven.cinterop.Rtime_picker_get_clock
import raven.cinterop.Rtime_picker_get_text
import raven.cinterop.Rtime_picker_get_time
import raven.cinterop.Rwidget_is_enabled
import raven.compose.foundation.layout.Column
import raven.compose.ui.Modifier
import raven.compose.ui.input.key.Key
import raven.compose.ui.input.key.KeyShortcut
import raven.compose.ui.test.runRavenTest
import raven.compose.ui.testTag
import raven.native.toRavenString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DateTimePickersTest {
    private fun dateOf(picker: Rid): LocalDate = memScoped {
        val year = alloc<IntVar>()
        val month = alloc<IntVar>()
        val day = alloc<IntVar>()
        Rdate_picker_get_date(picker, year.ptr, month.ptr, day.ptr)
        LocalDate(year.value, month.value, day.value)
    }

    private fun timeOf(picker: Rid): LocalTime = memScoped {
        val hour = alloc<IntVar>()
        val minute = alloc<IntVar>()
        val second = alloc<IntVar>()
        Rtime_picker_get_time(picker, hour.ptr, minute.ptr, second.ptr)
        LocalTime(hour.value, minute.value, second.value)
    }

    private val down = KeyShortcut(Key.DirectionDown)
    private val up = KeyShortcut(Key.DirectionUp)

    @Test
    fun theAppsDateReachesRavenWithoutACallback() = runRavenTest {
        var date by mutableStateOf(LocalDate(2026, 11, 3))
        val reported = mutableListOf<LocalDate>()
        setContent { DatePicker(date, { reported += it }, Modifier.testTag("date")) }
        assertEquals(LocalDate(2026, 11, 3), dateOf(onNodeWithTag("date").fetchWidget()))
        date = LocalDate(1999, 2, 28)
        waitForIdle()
        assertEquals(LocalDate(1999, 2, 28), dateOf(onNodeWithTag("date").fetchWidget()))
        assertEquals(emptyList(), reported)
    }

    @Test
    fun aDateTheUserChoosesIsReportedAndTaken() = runRavenTest {
        var date by mutableStateOf(LocalDate(2026, 12, 31))
        setContent { DatePicker(date, { date = it }, Modifier.testTag("date")) }
        // Down is the next day in Raven's field.
        onNodeWithTag("date").performKeyPress(down)
        waitForIdle()
        assertEquals(LocalDate(2027, 1, 1), date)
        assertEquals(LocalDate(2027, 1, 1), dateOf(onNodeWithTag("date").fetchWidget()))
    }

    @Test
    fun theAppsDateWins() = runRavenTest {
        val reported = mutableListOf<LocalDate>()
        setContent { DatePicker(LocalDate(2026, 11, 3), { reported += it }, Modifier.testTag("date")) }
        onNodeWithTag("date").performKeyPress(down)
        waitForIdle()
        assertEquals(listOf(LocalDate(2026, 11, 4)), reported)
        assertEquals(LocalDate(2026, 11, 3), dateOf(onNodeWithTag("date").fetchWidget()), "the app kept its date")
    }

    @Test
    fun theRangeRefusesADateOutsideIt() = runRavenTest {
        var date by mutableStateOf(LocalDate(2026, 11, 30))
        val reported = mutableListOf<LocalDate>()
        val november = LocalDate(2026, 11, 1)..LocalDate(2026, 11, 30)
        setContent { DatePicker(date, { reported += it; date = it }, Modifier.testTag("date"), range = november) }
        onNodeWithTag("date").performKeyPress(down)
        waitForIdle()
        assertEquals(LocalDate(2026, 11, 30), date)
        assertEquals(LocalDate(2026, 11, 30), dateOf(onNodeWithTag("date").fetchWidget()))
        assertEquals(emptyList(), reported, "no day after the range")
        // The app's date outside the range is shown within it.
        date = LocalDate(2027, 5, 5)
        waitForIdle()
        assertEquals(LocalDate(2026, 11, 30), dateOf(onNodeWithTag("date").fetchWidget()))
    }

    @Test
    fun theFormatIsRavens() = runRavenTest {
        var format by mutableStateOf("YYYY-MM-DD")
        setContent { DatePicker(LocalDate(2026, 11, 3), {}, Modifier.testTag("date"), format = format) }
        assertEquals("2026-11-03", Rdate_picker_get_text(onNodeWithTag("date").fetchWidget()).toRavenString())
        format = "D MMMM YYYY"
        waitForIdle()
        assertEquals("3 November 2026", Rdate_picker_get_text(onNodeWithTag("date").fetchWidget()).toRavenString())
    }

    @Test
    fun theFirstDayOfTheWeekIsRavensNumber() {
        // (Raven has no getter for it; the calendar draws from it)
        assertEquals(0, DayOfWeek.SUNDAY.ravenDay())
        assertEquals(1, DayOfWeek.MONDAY.ravenDay())
        assertEquals(6, DayOfWeek.SATURDAY.ravenDay())
    }

    @Test
    fun aDateRavenCantShowThrows() = runRavenTest {
        assertFailsWith<IllegalArgumentException> {
            setContent { DatePicker(LocalDate(10000, 1, 1), {}) }
        }
    }

    @Test
    fun theAppsTimeReachesRavenOnTwentyFourHours() = runRavenTest {
        var time by mutableStateOf(LocalTime(15, 30))
        setContent { TimePicker(time, {}, Modifier.testTag("time")) }
        val picker = onNodeWithTag("time").fetchWidget()
        assertEquals(LocalTime(15, 30), timeOf(picker))
        assertEquals(R_CLOCK_24_HOUR, Rtime_picker_get_clock(picker))
        assertEquals("15:30", Rtime_picker_get_text(picker).toRavenString())
        time = LocalTime(9, 5)
        waitForIdle()
        assertEquals(LocalTime(9, 5), timeOf(onNodeWithTag("time").fetchWidget()))
    }

    @Test
    fun aTimeTheUserChangesIsReportedAndHiddenSecondsAreKept() = runRavenTest {
        var time by mutableStateOf(LocalTime(15, 30, 42))
        setContent { TimePicker(time, { time = it }, Modifier.testTag("time")) }
        // The hours are the part the keys change first.
        onNodeWithTag("time").performKeyPress(up)
        waitForIdle()
        assertEquals(LocalTime(16, 30, 42), time)
        assertEquals(LocalTime(16, 30, 42), timeOf(onNodeWithTag("time").fetchWidget()))
    }

    @Test
    fun theAppsTimeWins() = runRavenTest {
        val reported = mutableListOf<LocalTime>()
        setContent { TimePicker(LocalTime(15, 30), { reported += it }, Modifier.testTag("time")) }
        onNodeWithTag("time").performKeyPress(down)
        waitForIdle()
        assertEquals(listOf(LocalTime(14, 30)), reported)
        assertEquals(LocalTime(15, 30), timeOf(onNodeWithTag("time").fetchWidget()))
    }

    @Test
    fun theMinuteStepIsWhatTheArrowsAdd() = runRavenTest {
        var time by mutableStateOf(LocalTime(10, 0))
        setContent { TimePicker(time, { time = it }, Modifier.testTag("time"), minuteStep = 15) }
        val node = onNodeWithTag("time")
        node.performKeyPress(KeyShortcut(Key.DirectionRight))
        node.performKeyPress(up)
        waitForIdle()
        assertEquals(LocalTime(10, 15), time)
    }

    @Test
    fun secondsAreShownWhenAsked() = runRavenTest {
        var seconds by mutableStateOf(false)
        setContent { TimePicker(LocalTime(8, 1, 2), {}, Modifier.testTag("time"), showSeconds = seconds) }
        assertEquals("08:01", Rtime_picker_get_text(onNodeWithTag("time").fetchWidget()).toRavenString())
        seconds = true
        waitForIdle()
        assertEquals("08:01:02", Rtime_picker_get_text(onNodeWithTag("time").fetchWidget()).toRavenString())
    }

    @Test
    fun bothCanBeDisabled() = runRavenTest {
        var enabled by mutableStateOf(false)
        setContent {
            Column {
                DatePicker(LocalDate(2026, 1, 1), {}, Modifier.testTag("date"), enabled = enabled)
                TimePicker(LocalTime(0, 0), {}, Modifier.testTag("time"), enabled = enabled)
            }
        }
        assertEquals(R_FALSE, Rwidget_is_enabled(onNodeWithTag("date").fetchWidget()))
        assertEquals(R_FALSE, Rwidget_is_enabled(onNodeWithTag("time").fetchWidget()))
        enabled = true
        waitForIdle()
        assertEquals(R_TRUE, Rwidget_is_enabled(onNodeWithTag("time").fetchWidget()))
    }
}
