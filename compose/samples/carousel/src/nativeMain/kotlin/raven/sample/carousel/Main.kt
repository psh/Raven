package raven.sample.carousel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import raven.compose.foundation.Button
import raven.compose.foundation.OutlinedButton
import raven.compose.foundation.Surface
import raven.compose.foundation.Switch
import raven.compose.foundation.Text
import raven.compose.foundation.layout.Arrangement
import raven.compose.foundation.layout.Box
import raven.compose.foundation.layout.Column
import raven.compose.foundation.layout.Row
import raven.compose.foundation.layout.fillMaxSize
import raven.compose.foundation.layout.fillMaxWidth
import raven.compose.foundation.layout.padding
import raven.compose.foundation.layout.size
import raven.compose.foundation.layout.widthIn
import raven.compose.foundation.pager.HorizontalPager
import raven.compose.foundation.pager.PagerState
import raven.compose.foundation.pager.VerticalPager
import raven.compose.foundation.pager.rememberPagerState
import raven.compose.ui.Alignment
import raven.compose.ui.Modifier
import raven.compose.ui.Window
import raven.compose.ui.alpha
import raven.compose.ui.application
import raven.compose.ui.graphics.Color
import raven.compose.ui.graphics.RoundedCornerShape
import raven.compose.ui.rememberWindowState
import raven.compose.ui.testTag
import raven.compose.ui.text.TextAlign
import raven.compose.ui.theme.RavenTheme
import raven.compose.ui.unit.DpSize
import raven.compose.ui.unit.dp
import raven.compose.ui.unit.sp
import kotlin.time.Duration.Companion.seconds

// A port of Raven's examples/carousel: an onboarding in five pages, swiped with the mouse, a finger
// or a trackpad, turned with the keys, the dots, the arrows under the pointer, and Back and Next.
//
//   HorizontalPager(state, loop, autoplay) { page -> }   Raven's Carousel
//   VerticalPager(...)                                    the same pages, one above the other
//   rememberPagerState { pageCount }                      the page shown, and the moves to a page

fun main() = application {
    RavenTheme(dark = true) {
        Window(
            onCloseRequest = ::exitApplication,
            state = rememberWindowState(size = DpSize(960.dp, 640.dp)),
            title = "Raven carousel",
        ) { CarouselSample() }
    }
}

/** One page of the onboarding: its title (for the status line), its colour, its heading and text. */
internal class Page(val title: String, val color: Color, val heading: String, val text: String, val button: String? = null)

internal val Pages = listOf(
    Page("Welcome", Color(0xFF5B5BD6), "Welcome to Raven", "A small C library for fast, good-looking interfaces, on every desktop and in the browser."),
    Page("Swipe", Color(0xFF12A594), "Swipe the pages", "They follow the pointer, a finger or two fingers on a trackpad, stretch at the ends and settle on the nearest page."),
    Page(
        "Pages", Color(0xFFF76B15), "Any widget is a page",
        "Pictures, forms, whole screens: only the page shown is drawn and reached by the mouse and the keyboard.",
        button = "A button on a page",
    ),
    Page("Dots", Color(0xFFD6409F), "Dots and arrows", "The current dot slides with the pages. The arrows come with the pointer; the keys and the wheel turn the pages too."),
    Page("Ready", Color(0xFF0090FF), "Loop and autoplay", "Turn on the switches above: the last page leads to the first, and the pages turn by themselves until the pointer comes."),
)

private val White = Color(0xFFFFFFFF)
private val Soft = Color(0xD0FFFFFF)
private val Ring = Color(0x2EFFFFFF)

/** The window's content: the switches, the pages, and the status with Back and Next. */
@Composable
fun CarouselSample() {
    val state = rememberPagerState { Pages.size }
    var loop by remember { mutableStateOf(false) }
    var autoplay by remember { mutableStateOf(false) }
    var vertical by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Carousel", fontSize = 26.sp)
                Text(
                    "Swipe with the mouse, a finger or two fingers on a trackpad  -  arrow keys, Home, End  -  click a dot or an arrow",
                    Modifier.alpha(0.6f),
                )
            }
            LabelledSwitch("Loop", loop, { loop = it }, "loop")
            LabelledSwitch("Autoplay", autoplay, { autoplay = it }, "autoplay")
            LabelledSwitch("Vertical", vertical, { vertical = it }, "vertical")
        }

        // One state for both pagers: switching the orientation keeps the page.
        val pagerModifier = Modifier.weight(1f).fillMaxWidth().testTag("pages")
        val every = if (autoplay) 3.seconds else null
        if (vertical) {
            VerticalPager(state, pagerModifier, loop = loop, autoplay = every) { PageContent(Pages[it], it) }
        } else {
            HorizontalPager(state, pagerModifier, loop = loop, autoplay = every) { PageContent(Pages[it], it) }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            val page = state.currentPage
            val last = page == state.pageCount - 1
            Text("${Pages[page].title}  -  ${page + 1} / ${state.pageCount}", Modifier.weight(1f).alpha(0.7f).testTag("status"))
            OutlinedButton({ scope.launch { state.animateScrollToPage(back(state, loop)) } }, Modifier.testTag("back"), enabled = loop || page > 0) {
                Text("Back")
            }
            Button({ scope.launch { state.animateScrollToPage(next(state)) } }, Modifier.testTag("next")) {
                Text(if (last && !loop) "Get started" else "Next")
            }
        }
    }
}

/** The page before the one shown, the last one before the first when the pages loop. */
internal fun back(state: PagerState, loop: Boolean): Int =
    if (state.currentPage == 0 && loop) state.pageCount - 1 else state.currentPage - 1

/**
 * The page after the one shown: the first after the last, whether the pages loop or not, as the C
 * example's Next goes back to the start (an app would go on to its own screens there).
 */
internal fun next(state: PagerState): Int =
    if (state.currentPage == state.pageCount - 1) 0 else state.currentPage + 1

@Composable
private fun LabelledSwitch(text: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, tag: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Switch(checked, onCheckedChange, Modifier.testTag(tag))
        Text(text)
    }
}

@Composable
private fun PageContent(page: Page, index: Int) {
    Surface(Modifier.fillMaxSize(), shape = RoundedCornerShape(20.dp), color = page.color) {
        Box(Modifier.fillMaxSize().padding(56.dp), contentAlignment = Alignment.Center) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(Modifier.size(84.dp), shape = RoundedCornerShape(42.dp), color = Ring) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("${index + 1}", color = White, fontSize = 38.sp)
                    }
                }
                Text(page.heading, color = White, fontSize = 34.sp)
                Text(page.text, Modifier.widthIn(max = 520.dp), color = Soft, fontSize = 17.sp, textAlign = TextAlign.Center, softWrap = true)
                page.button?.let { Button({}) { Text(it) } }
            }
        }
    }
}
