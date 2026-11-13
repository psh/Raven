package raven.compose.foundation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toKString
import platform.posix.PATH_MAX
import platform.posix.access
import platform.posix.getcwd
import raven.cinterop.R_INVALID_ID
import raven.cinterop.R_METRIC_FONT_SIZE
import raven.cinterop.Rfont_get_default
import raven.cinterop.Rtheme_get_font
import raven.cinterop.Rtheme_get_metric
import raven.cinterop.Rwidget_get_font
import raven.cinterop.Rwidget_get_metric
import raven.compose.foundation.layout.Box
import raven.compose.foundation.layout.Column
import raven.compose.foundation.layout.width
import raven.compose.ui.Modifier
import raven.compose.ui.Window
import raven.compose.ui.testTag
import raven.compose.ui.text.ProvideTextStyle
import raven.compose.ui.text.TextAlign
import raven.compose.ui.text.TextStyle
import raven.compose.ui.text.font.Font
import raven.compose.ui.text.font.FontFamily
import raven.compose.ui.text.font.FontWeight
import raven.compose.ui.theme.RavenTheme
import raven.compose.ui.unit.dp
import raven.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class FontsTest {
    private val sample = "Wide bold words"

    private val regular = Font(fontFile("InstrumentSans-Regular-subset.ttf"))
    private val bold = Font(fontFile("InstrumentSans-Bold-subset.ttf"), FontWeight.Bold)
    private val family = FontFamily(regular, bold)

    // The bold file alone, as the regular font of its own family: what the bold variant must draw.
    private val boldAlone = FontFamily(Font(fontFile("InstrumentSans-Bold-subset.ttf")))
    private val regularAlone = FontFamily(regular)

    private fun RavenTestScope.width(tag: String) = widgetRect(onNodeWithTag(tag).fetchWidget()).width

    private fun RavenTestScope.height(tag: String) = widgetRect(onNodeWithTag(tag).fetchWidget()).height

    @Test
    fun aFamilysBoldDrawsItsBoldFile() = runRavenTest {
        setContent {
            Column {
                Text(sample, Modifier.testTag("regular"), fontFamily = family)
                Text(sample, Modifier.testTag("bold"), fontFamily = family, fontWeight = FontWeight.Bold)
                Text(sample, Modifier.testTag("semibold"), fontFamily = family, fontWeight = FontWeight.W600)
                Text(sample, Modifier.testTag("medium"), fontFamily = family, fontWeight = FontWeight.Medium)
                Text(sample, Modifier.testTag("bold file"), fontFamily = boldAlone)
                Text(sample, Modifier.testTag("regular file"), fontFamily = regularAlone)
            }
        }
        assertEquals(width("regular file"), width("regular"))
        assertEquals(width("bold file"), width("bold"), "the bold variant is the bold file, not Raven's synthesised bold")
        assertNotEquals(width("regular"), width("bold"))
        assertEquals(width("bold"), width("semibold"), "600 and above is bold")
        assertEquals(width("regular"), width("medium"), "500 and below is regular")
    }

    @Test
    fun theDefaultAndMonospaceFamiliesAreRavensOwn() = runRavenTest {
        setContent {
            Column {
                Text("iiii", Modifier.testTag("theme"))
                Text("iiii", Modifier.testTag("default"), fontFamily = FontFamily.Default)
                Text("iiii", Modifier.testTag("mono"), fontFamily = FontFamily.Monospace)
            }
        }
        assertEquals(width("theme"), width("default"))
        assertTrue(width("mono") > width("default"), "a fixed-width i is wider")
    }

    @Test
    fun explicitParametersWinOverTheStyle() = runRavenTest {
        setContent {
            Column {
                ProvideTextStyle(TextStyle(fontSize = 30.sp, fontFamily = family, fontWeight = FontWeight.Bold)) {
                    Text(sample, Modifier.testTag("styled"))
                    Text(sample, Modifier.testTag("overridden"), fontSize = 12.sp, fontWeight = FontWeight.Normal)
                    // An inner ProvideTextStyle merges with the outer one.
                    ProvideTextStyle(TextStyle(fontSize = 12.sp)) { Text(sample, Modifier.testTag("merged")) }
                }
                Text(sample, Modifier.testTag("big bold"), fontSize = 30.sp, fontFamily = boldAlone)
                Text(sample, Modifier.testTag("small regular"), fontSize = 12.sp, fontFamily = regularAlone)
                Text(sample, Modifier.testTag("small bold"), fontSize = 12.sp, fontFamily = boldAlone)
            }
        }
        assertEquals(width("big bold"), width("styled"))
        assertEquals(height("big bold"), height("styled"))
        assertEquals(width("small regular"), width("overridden"))
        assertEquals(width("small bold"), width("merged"))
    }

    @Test
    fun aTextWrapsOnlyWithSoftWrap() = runRavenTest {
        val long = "A sentence long enough to need more than one line in a narrow box"
        setContent {
            Column {
                Box(Modifier.width(120.dp)) { Text(long, Modifier.testTag("one line")) }
                Box(Modifier.width(120.dp)) { Text(long, Modifier.testTag("wrapped"), softWrap = true) }
                Text("A line", Modifier.testTag("line"))
            }
        }
        assertEquals(height("line"), height("one line"))
        assertTrue(height("wrapped") > 2 * height("line"), "wrapped into several lines")
    }

    @Test
    fun textAlignAndColourReachTheLabelWithoutChangingItsSize() = runRavenTest {
        var align by mutableStateOf(TextAlign.Start)
        setContent { Text(sample, Modifier.testTag("text"), textAlign = align) }
        val before = widgetRect(onNodeWithTag("text").fetchWidget())
        align = TextAlign.Center
        waitForIdle()
        assertEquals(before, widgetRect(onNodeWithTag("text").fetchWidget()))
    }

    @Test
    fun aWindowLevelFamilyIsTheSubtreesFont() = runRavenTest {
        setContent {
            Column {
                RavenTheme(fontFamily = boldAlone) {
                    Column(Modifier.testTag("themed")) {
                        Text(sample, Modifier.testTag("inherits"))
                        Text(sample, Modifier.testTag("own"), fontFamily = regularAlone)
                    }
                }
                Text(sample, Modifier.testTag("outside"))
                Text(sample, Modifier.testTag("bold file"), fontFamily = boldAlone)
                Text(sample, Modifier.testTag("regular file"), fontFamily = regularAlone)
            }
        }
        assertNotEquals(R_INVALID_ID, Rwidget_get_font(onNodeWithTag("themed").fetchWidget()))
        assertEquals(width("bold file"), width("inherits"))
        assertEquals(width("regular file"), width("own"), "a Text's own family wins")
        assertNotEquals(width("bold file"), width("outside"))
        assertEquals(R_INVALID_ID, Rwidget_get_font(onNodeWithTag("outside").fetchWidget()))
    }

    @Test
    fun boldWithoutAFamilyIsTheSubtreeFamilysBold() = runRavenTest {
        setContent {
            Column {
                RavenTheme(fontFamily = family) { Text(sample, Modifier.testTag("bold"), fontWeight = FontWeight.Bold) }
                Text(sample, Modifier.testTag("bold file"), fontFamily = boldAlone)
            }
        }
        assertEquals(width("bold file"), width("bold"))
    }

    @Test
    fun anApplicationLevelFamilyIsTheThemesFont() = runRavenTest {
        var themed by mutableStateOf(true)
        host.start {
            RavenTheme(fontFamily = if (themed) boldAlone else null) {
                Window(onCloseRequest = {}, title = "Test") {
                    Column {
                        Text(sample, Modifier.testTag("inherits"))
                        Text(sample, Modifier.testTag("bold file"), fontFamily = boldAlone)
                    }
                }
            }
        }
        waitForIdle()
        val font = Rtheme_get_font()
        assertNotEquals(Rfont_get_default(), font)
        assertEquals(width("bold file"), width("inherits"))

        // Null leaves Raven's as it is, as the other app-wide settings do.
        themed = false
        waitForIdle()
        assertEquals(font, Rtheme_get_font())
    }

    @Test
    fun aTextFieldTakesItsTextStyle() = runRavenTest {
        setContent {
            Column {
                TextField(sample, {}, Modifier.testTag("plain"))
                TextField(sample, {}, Modifier.testTag("big"), textStyle = TextStyle(fontSize = 30.sp, fontFamily = boldAlone))
                RavenTheme(fontFamily = regularAlone) {
                    TextField(sample, {}, Modifier.testTag("own wins"), textStyle = TextStyle(fontFamily = boldAlone))
                }
            }
        }
        // Raven's field keeps the theme's control height, and draws its text at the widget's size.
        val big = onNodeWithTag("big").fetchWidget()
        assertEquals(30f, Rwidget_get_metric(big, R_METRIC_FONT_SIZE))
        val plain = onNodeWithTag("plain").fetchWidget()
        assertEquals(Rtheme_get_metric(R_METRIC_FONT_SIZE), Rwidget_get_metric(plain, R_METRIC_FONT_SIZE))
        assertEquals(R_INVALID_ID, Rwidget_get_font(plain))
        assertNotEquals(R_INVALID_ID, Rwidget_get_font(big))
        assertEquals(Rwidget_get_font(big), Rwidget_get_font(onNodeWithTag("own wins").fetchWidget()), "the field's own font wins over the subtree's")
    }

    @Test
    fun aCodeEditorTakesItsTextStyle() = runRavenTest {
        var style by mutableStateOf(TextStyle.Default)
        setContent { CodeEditor("fun main() {}", modifier = Modifier.testTag("editor"), textStyle = style) }
        style = TextStyle(fontSize = 20.sp, fontFamily = family, fontWeight = FontWeight.Bold)
        waitForIdle()
        style = TextStyle.Default
        waitForIdle()
        onNodeWithTag("editor").assertExists()
    }

    @Test
    fun aMarkdownTextTakesItsStyle() = runRavenTest {
        setContent {
            Column {
                MarkdownText(sample, Modifier.testTag("styled"), style = TextStyle(fontSize = 30.sp, fontFamily = boldAlone))
                Text(sample, Modifier.testTag("bold file"), fontSize = 30.sp, fontFamily = boldAlone)
            }
        }
        assertEquals(width("bold file"), width("styled"))
    }

    @Test
    fun aFamilyIsLoadedOncePerHost() = runRavenTest {
        setContent {
            Column {
                RavenTheme(fontFamily = FontFamily(Font(fontFile("InstrumentSans-Bold-subset.ttf")))) { Text("a", Modifier.testTag("one")) }
                RavenTheme(fontFamily = FontFamily(Font(fontFile("InstrumentSans-Bold-subset.ttf")))) { Text("b", Modifier.testTag("two")) }
            }
        }
        // Equal families are one font.
        assertEquals(Rwidget_get_font(onNodeWithTag("one").fetchWidget()), Rwidget_get_font(onNodeWithTag("two").fetchWidget()))
    }
}

/** The path of [name] in the repository's `tools/designer/fonts`, found from the working directory up. */
private fun fontFile(name: String): String {
    var dir = memScoped { allocArray<ByteVar>(PATH_MAX).let { getcwd(it, PATH_MAX.toULong())!!.toKString() } }
    while (true) {
        val path = "$dir/tools/designer/fonts/$name"
        if (access(path, 0) == 0) return path
        check(dir.length > 1) { "no tools/designer/fonts/$name above the working directory" }
        dir = dir.substringBeforeLast('/').ifEmpty { "/" }
    }
}
