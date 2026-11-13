package raven.compose.foundation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import raven.cinterop.R_METRIC_FONT_SIZE
import raven.cinterop.Rtheme_get_metric
import raven.compose.foundation.layout.Column
import raven.compose.ui.Modifier
import raven.compose.ui.Window
import raven.compose.ui.testTag
import raven.compose.ui.text.TextStyle
import raven.compose.ui.text.font.FontFamily
import raven.compose.ui.theme.Look
import raven.compose.ui.theme.RavenTheme
import raven.compose.ui.theme.Typography
import raven.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TypographyTest {
    private fun RavenTestScope.height(tag: String) = widgetRect(onNodeWithTag(tag).fetchWidget()).height

    @Test
    fun theRolesFollowTheThemesFontSize() = runRavenTest {
        var look by mutableStateOf(Look.Raven)
        var typography: Typography? = null
        host.start {
            RavenTheme(look = look) {
                Window(onCloseRequest = {}, title = "Test") { typography = RavenTheme.typography }
            }
        }
        waitForIdle()
        fun assertSizedFrom(base: Float) {
            val roles = typography!!
            assertEquals(base * 1.9f, roles.display.fontSize.value)
            assertEquals(base * 1.52f, roles.headline.fontSize.value)
            assertEquals(base * 1.26f, roles.title.fontSize.value)
            assertEquals(base * 1.1f, roles.subtitle.fontSize.value)
            assertEquals(base * 0.92f, roles.caption.fontSize.value)
            assertEquals(TextStyle.Default, roles.body, "Raven's own size")
            assertEquals(TextStyle.Default, roles.label)
            assertEquals(TextStyle(fontFamily = FontFamily.Monospace), roles.code)
        }
        val ravenBase = Rtheme_get_metric(R_METRIC_FONT_SIZE)
        assertSizedFrom(ravenBase)

        // High contrast has larger text: the roles are sized again.
        look = Look.HighContrast
        waitForIdle()
        val contrastBase = Rtheme_get_metric(R_METRIC_FONT_SIZE)
        assertNotEquals(ravenBase, contrastBase)
        assertSizedFrom(contrastBase)
    }

    @Test
    fun aTitleIsTallerThanTheBody() = runRavenTest {
        setContent {
            Column {
                Text("Body", Modifier.testTag("body"))
                Text("Title", Modifier.testTag("title"), style = RavenTheme.typography.title)
                Text("Caption", Modifier.testTag("caption"), style = RavenTheme.typography.caption)
            }
        }
        assertTrue(height("title") > height("body"))
        assertTrue(height("caption") < height("body"))
    }

    @Test
    fun aNestedTypographyChangesItsSubtree() = runRavenTest {
        var inner: Typography? = null
        setContent {
            Column {
                val custom = RavenTheme.typography.copy(body = TextStyle(fontSize = 30.sp))
                RavenTheme(typography = custom) {
                    inner = RavenTheme.typography
                    Text("Inside", Modifier.testTag("inside"))
                    TextField("Field", {}, Modifier.testTag("field"))
                }
                Text("Outside", Modifier.testTag("outside"))
                Text("Big", Modifier.testTag("big"), fontSize = 30.sp)
            }
        }
        assertEquals(30.sp, inner!!.body.fontSize)
        assertEquals(height("big"), height("inside"))
        assertTrue(height("outside") < height("inside"))
    }

    @Test
    fun buttonContentUsesTheLabelRole() = runRavenTest {
        setContent {
            Column {
                RavenTheme(typography = RavenTheme.typography.copy(label = TextStyle(fontSize = 30.sp))) {
                    Button(onClick = {}) { Text("Go", Modifier.testTag("label")) }
                    Text("Go", Modifier.testTag("body"))
                }
                Text("Go", Modifier.testTag("big"), fontSize = 30.sp)
            }
        }
        assertEquals(height("big"), height("label"))
        assertTrue(height("body") < height("label"), "outside a button, the body")
    }
}
