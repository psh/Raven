package raven.compose.foundation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.runBlocking
import raven.cinterop.R_FALSE
import raven.cinterop.R_INVALID_ID
import raven.cinterop.R_KEY_LEFT_CTRL
import raven.cinterop.R_MOD_CTRL
import raven.cinterop.R_MOUSE_LEFT
import raven.cinterop.R_MOUSE_RIGHT
import raven.cinterop.R_TRUE
import raven.cinterop.RMouseButton
import raven.cinterop.Rid
import raven.cinterop.Rtile_view_get_count
import raven.cinterop.Rtile_view_get_selection
import raven.cinterop.Rtile_view_get_selection_count
import raven.cinterop.Rwidget_focus
import raven.cinterop.Rwidget_get_window
import raven.cinterop.Rwindow_close_popups
import raven.cinterop.Rwindow_get_popup_count
import raven.cinterop.Rwindow_inject_key
import raven.cinterop.Rwindow_inject_mouse_button
import raven.cinterop.Rwindow_inject_mouse_move
import raven.compose.foundation.layout.size
import raven.compose.ui.Modifier
import raven.compose.ui.TileItemValue
import raven.compose.ui.graphics.ImageBitmap
import raven.compose.ui.graphics.painter.BitmapPainter
import raven.compose.ui.graphics.painter.TextureRegion
import raven.compose.ui.input.key.Key
import raven.compose.ui.readTile
import raven.compose.ui.test.RavenTestScope
import raven.compose.ui.test.runRavenTest
import raven.compose.ui.test.widgetRect
import raven.compose.ui.testTag
import raven.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/** TileView over Raven's virtualised tile view. */
class TileViewTest {
    private data class File(val path: String, val size: Int)

    private val files = (0 until 6).map { File("file$it.txt", it * 100) }

    private fun RavenTestScope.view(): Rid = onNodeWithTag("tiles").fetchWidget()

    private fun RavenTestScope.tile(index: Int): TileItemValue = readTile(onNodeWithTag("tiles").fetchNode(), index)

    private fun selection(view: Rid) = (0 until Rtile_view_get_selection_count(view)).map { Rtile_view_get_selection(view, it) }.toSet()

    // Tiles of 112 dp, 8 dp apart, from 8 dp in: three a row in 400 dp.
    private fun RavenTestScope.click(index: Int, button: RMouseButton = R_MOUSE_LEFT, ctrl: Boolean = false) {
        val view = view()
        val rect = widgetRect(view)
        val window = Rwidget_get_window(view)
        if (ctrl) Rwindow_inject_key(window, R_KEY_LEFT_CTRL, R_TRUE, R_MOD_CTRL.toUInt())
        Rwindow_inject_mouse_move(window, rect.x + 8f + 120f * (index % 3) + 56f, rect.y + 8f + 120f * (index / 3) + 56f)
        Rwindow_inject_mouse_button(window, button, R_TRUE)
        Rwindow_inject_mouse_button(window, button, R_FALSE)
        if (ctrl) Rwindow_inject_key(window, R_KEY_LEFT_CTRL, R_FALSE, 0u)
        waitForIdle()
    }

    @Test
    fun theTilesAreAskedOfTheTileFunction() = runRavenTest {
        var shown by mutableStateOf(files)
        setContent {
            val icon = remember { BitmapPainter(ImageBitmap(2, 2, ByteArray(16) { -1 })) }
            TileView(shown, { it.path }, emptySet(), {}, Modifier.size(400.dp, 300.dp).testTag("tiles"), emptyText = "Nothing here") { file ->
                TileContent(file.path, icon = icon.takeIf { file.size > 0 }, detail = "${file.size} B", badge = if (file.size > 400) "big" else null)
            }
        }
        assertEquals(6, Rtile_view_get_count(view()))
        assertEquals(TileItemValue("file0.txt", "0 B", null), tile(0))
        val five = tile(5)
        assertEquals(listOf("file5.txt", "500 B", "big"), listOf(five.text, five.detail, five.badge))
        assertEquals(TextureRegion.Whole, five.region)
        assertEquals(true, five.icon != R_INVALID_ID, "the painter's texture")
        assertEquals(TileItemValue(""), tile(6), "past the items, an empty tile")

        shown = shown.reversed()
        waitForIdle()
        assertEquals("file5.txt", tile(0).text, "the same number: redrawn, the latest items read")
        shown = files.take(2)
        waitForIdle()
        assertEquals(2, Rtile_view_get_count(view()), "a new number: given again")
    }

    @Test
    fun theSelectionIsAControlledSetOfKeys() = runRavenTest {
        var shown by mutableStateOf(files)
        var selection by mutableStateOf(setOf<Any>("file1.txt"))
        var take by mutableStateOf(true)
        val reported = mutableListOf<Set<Any>>()
        setContent {
            TileView(shown, { it.path }, selection, { reported += it; if (take) selection = it }, Modifier.size(400.dp, 300.dp).testTag("tiles")) {
                TileContent(it.path)
            }
        }
        val view = view()
        assertEquals(setOf(1), selection(view))

        click(4)
        assertEquals(setOf<Any>("file4.txt"), reported.last())
        assertEquals(setOf(4), selection(view))

        click(2, ctrl = true)
        assertEquals(setOf<Any>("file2.txt", "file4.txt"), reported.last())
        assertEquals(setOf(2, 4), selection(view))

        take = false
        click(0)
        assertEquals(setOf<Any>("file0.txt"), reported.last())
        assertEquals(setOf(2, 4), selection(view), "refused, it reverts")

        // Reordered, the same keys are selected where they are now.
        shown = files.reversed()
        waitForIdle()
        assertEquals(setOf(1, 3), selection(view))
        // A new number of items: Raven clears its selection, and the app's is shown again.
        shown = files.reversed() + File("new.txt", 1)
        waitForIdle()
        assertEquals(7, Rtile_view_get_count(view))
        assertEquals(setOf(1, 3), selection(view))
    }

    @Test
    fun aRightClickOpensTheMenuAndActivationReportsTheItem() = runRavenTest {
        val asked = mutableListOf<File?>()
        val activated = mutableListOf<File>()
        var selection by mutableStateOf(emptySet<Any>())
        setContent {
            TileView(
                files, { it.path }, selection, { selection = it }, Modifier.size(400.dp, 300.dp).testTag("tiles"),
                onActivate = { activated += it },
                contextMenuItems = { file -> asked += file; listOf(ContextMenuItem("Open") {}) },
            ) { TileContent(it.path) }
        }
        val window = Rwidget_get_window(view())
        click(3, R_MOUSE_RIGHT)
        assertEquals(listOf<File?>(files[3]), asked)
        assertEquals(setOf<Any>("file3.txt"), selection, "a right click selects first")
        assertEquals(1, Rwindow_get_popup_count(window))
        Rwindow_close_popups(window)

        Rwidget_focus(view())
        pressKey(Key.Enter)
        waitForIdle()
        assertEquals(listOf(files[3]), activated)
    }

    @Test
    fun aTileViewStateRevealsAnItem() = runRavenTest {
        val state = TileViewState()
        runBlocking { state.revealItem(3) }
        setContent {
            TileView(
                (0 until 10_000).toList(), { it }, emptySet(), {}, Modifier.size(400.dp, 300.dp).testTag("tiles"),
                layout = TileLayout.Rows, spacing = 4.dp, state = state,
            ) { TileContent("tile $it") }
        }
        assertEquals(10_000, Rtile_view_get_count(view()))
        // Raven has no getter for the position: the call reaches it without an error.
        runBlocking { state.revealItem(9_000) }
        assertEquals("tile 9000", tile(9_000).text)
    }
}
