package raven.compose.foundation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import raven.cinterop.R_FALSE
import raven.cinterop.R_TRUE
import raven.cinterop.Rcolor_picker_get_color
import raven.cinterop.Rcolor_picker_get_swatch_count
import raven.cinterop.Rid
import raven.cinterop.Rwidget_get_window
import raven.cinterop.Rwidget_is_enabled
import raven.cinterop.Rwindow_get_popup_count
import raven.compose.foundation.layout.Column
import raven.compose.ui.Modifier
import raven.compose.ui.graphics.Color
import raven.compose.ui.graphics.ravenColor
import raven.compose.ui.testTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ColorPickerTest {
    private val blue = Color(0xFF3B82F6)
    private val red = Color(0xFFD03030)
    private val faintGreen = Color(0x8030C060)

    private fun colorOf(picker: Rid) = ravenColor { r, g, b, a -> Rcolor_picker_get_color(picker, r, g, b, a) }

    @Test
    fun aChangeIsReportedAndTaken() = runRavenTest {
        var color by mutableStateOf(blue)
        val reported = mutableListOf<Color>()
        setContent {
            ColorPicker(color, { reported += it; color = it }, Modifier.testTag("picker"))
        }
        val picker = onNodeWithTag("picker")
        assertEquals(blue, colorOf(picker.fetchWidget()))

        picker.performColorChange(red)
        waitForIdle()
        assertEquals(listOf(red), reported, "once, with no colour on the way")
        assertEquals(red, color)
        assertEquals(red, colorOf(picker.fetchWidget()))

        // With its opacity.
        picker.performColorChange(faintGreen)
        waitForIdle()
        assertEquals(faintGreen, color)
        assertEquals(faintGreen, colorOf(picker.fetchWidget()))
    }

    @Test
    fun aRefusedChangeReverts() = runRavenTest {
        val reported = mutableListOf<Color>()
        setContent { ColorPicker(blue, { reported += it }, Modifier.testTag("picker")) }
        val picker = onNodeWithTag("picker")
        picker.performColorChange(red)
        waitForIdle()
        assertEquals(listOf(red), reported)
        assertEquals(blue, colorOf(picker.fetchWidget()), "the app kept its colour")
    }

    @Test
    fun theAppsChangeIsShownWithoutACallback() = runRavenTest {
        var color by mutableStateOf(blue)
        val reported = mutableListOf<Color>()
        setContent { ColorPicker(color, { reported += it }, Modifier.testTag("picker")) }
        color = red
        waitForIdle()
        assertEquals(red, colorOf(onNodeWithTag("picker").fetchWidget()))
        assertEquals(emptyList(), reported)
    }

    @Test
    fun aPopupPickerOpensAndTellsTheApp() = runRavenTest {
        var color by mutableStateOf(blue)
        setContent { ColorPicker(color, { color = it }, Modifier.testTag("picker"), popup = true, alpha = false) }
        val picker = onNodeWithTag("picker")
        val window = Rwidget_get_window(picker.fetchWidget())
        picker.performColorChange(red)
        waitForIdle()
        assertEquals(red, color)
        assertEquals(red, colorOf(picker.fetchWidget()))
        assertEquals(0, Rwindow_get_popup_count(window), "closed after")
    }

    @Test
    fun withoutAlphaTheColourIsOpaque() = runRavenTest {
        setContent { ColorPicker(faintGreen, {}, Modifier.testTag("picker"), alpha = false) }
        assertEquals(Color(0xFF30C060), colorOf(onNodeWithTag("picker").fetchWidget()))
    }

    @Test
    fun aCompactPickerHasNoCodeToType() = runRavenTest {
        val reported = mutableListOf<Color>()
        setContent { ColorPicker(blue, { reported += it }, Modifier.testTag("picker"), compact = true) }
        assertFailsWith<AssertionError> { onNodeWithTag("picker").performColorChange(red) }
        assertEquals(emptyList(), reported)
    }

    @Test
    fun swatchesAreReplacedWhenTheyChange() = runRavenTest {
        var swatches by mutableStateOf(listOf(red, blue, faintGreen))
        setContent {
            Column { ColorPicker(blue, {}, Modifier.testTag("picker"), swatches = swatches) }
        }
        assertEquals(3, Rcolor_picker_get_swatch_count(onNodeWithTag("picker").fetchWidget()))
        swatches = listOf(red)
        waitForIdle()
        assertEquals(1, Rcolor_picker_get_swatch_count(onNodeWithTag("picker").fetchWidget()))
    }

    @Test
    fun itCanBeDisabled() = runRavenTest {
        var enabled by mutableStateOf(false)
        setContent { ColorPicker(blue, {}, Modifier.testTag("picker"), enabled = enabled) }
        assertEquals(R_FALSE, Rwidget_is_enabled(onNodeWithTag("picker").fetchWidget()))
        enabled = true
        waitForIdle()
        assertEquals(R_TRUE, Rwidget_is_enabled(onNodeWithTag("picker").fetchWidget()))
    }
}
