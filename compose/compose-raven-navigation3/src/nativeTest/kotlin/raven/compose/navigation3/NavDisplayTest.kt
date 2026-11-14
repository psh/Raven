package raven.compose.navigation3

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import raven.cinterop.R_FALSE
import raven.cinterop.R_MOUSE_X1
import raven.cinterop.R_TRUE
import raven.cinterop.Rwidget_get_window
import raven.cinterop.Rwindow_has_dialog
import raven.cinterop.Rwindow_inject_mouse_button
import raven.compose.foundation.Button
import raven.compose.foundation.RavenTestScope
import raven.compose.foundation.Surface
import raven.compose.foundation.Text
import raven.compose.foundation.runRavenTest
import raven.compose.ui.AppearTransition
import raven.compose.ui.Modifier
import raven.compose.ui.Window
import raven.compose.ui.input.key.Key
import raven.compose.ui.testTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Serializable
private data object Home : NavKey

@Serializable
private data class Detail(val id: Int) : NavKey

@Serializable
private data object ConfirmDelete : NavKey

/** Kotlin/Native has no reflection, so `NavKey`'s open polymorphism is declared by hand. */
private val navConfig = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(Home::class)
            subclass(Detail::class)
            subclass(ConfirmDelete::class)
        }
    }
}

class NavDisplayTest {
    private lateinit var backStack: NavBackStack<NavKey>
    private val backs = mutableListOf<NavKey>()

    @androidx.compose.runtime.Composable
    private fun Navigation() {
        backStack = rememberNavBackStack(navConfig, Home)
        NavDisplay(
            backStack = backStack,
            modifier = Modifier.testTag("display"),
            onBack = { backStack.removeLastOrNull()?.let { backs += it } },
            sceneStrategy = DialogSceneStrategy<NavKey>() then SinglePaneSceneStrategy(),
            transition = NavTransition(push = AppearTransition.SlideLeft, pop = AppearTransition.SlideRight),
            entryProvider = entryProvider {
                entry<Home> {
                    var count by rememberSaveable { mutableStateOf(0) }
                    Button(onClick = { count++ }) { Text("home $count") }
                }
                entry<Detail> { key -> Text("detail ${key.id}") }
                entry<ConfirmDelete>(metadata = DialogSceneStrategy.dialog()) {
                    Surface { Text("delete?") }
                }
            },
        )
    }

    private fun RavenTestScope.hasDialog(): Boolean = Rwindow_has_dialog(Rwidget_get_window(onNodeWithTag("display").fetchWidget())) == R_TRUE

    @Test
    fun aPushedEntryIsShownAndPoppedBack() = runRavenTest {
        setContent { Navigation() }
        onNodeWithText("home 0").assertExists()
        backStack.add(Detail(7))
        waitForIdle()
        onNodeWithText("detail 7").assertExists()
        onNodeWithText("home 0").assertDoesNotExist()
        backStack.removeLastOrNull()
        waitForIdle()
        onNodeWithText("home 0").assertExists()
    }

    @Test
    fun anEntryKeepsItsSaveableStateUnderAnother() = runRavenTest {
        setContent { Navigation() }
        onNodeWithText("home 0").performClick()
        onNodeWithText("home 1").performClick()
        backStack.add(Detail(1))
        waitForIdle()
        backStack.removeLastOrNull()
        waitForIdle()
        onNodeWithText("home 2").assertExists()
    }

    @Test
    fun aDialogDestinationIsOverTheSceneAndEscapeGoesBack() = runRavenTest {
        setContent { Navigation() }
        backStack.add(ConfirmDelete)
        waitForIdle()
        assertTrue(hasDialog())
        onNodeWithText("delete?").assertExists()
        onNodeWithText("home 0").assertExists()
        pressKey(Key.Escape)
        waitForIdle()
        assertEquals(listOf<NavKey>(ConfirmDelete), backs)
        assertEquals(false, hasDialog())
        onNodeWithText("delete?").assertDoesNotExist()
    }

    @Test
    fun theBackKeysAndButtonGoBack() = runRavenTest {
        setContent { Navigation() }
        backStack.addAll(listOf(Detail(1), Detail(2), Detail(3)))
        waitForIdle()
        pressKey(Key.LeftBracket, meta = true)
        waitForIdle()
        onNodeWithText("detail 2").assertExists()
        pressKey(Key.DirectionLeft, alt = true)
        waitForIdle()
        onNodeWithText("detail 1").assertExists()
        val window = Rwidget_get_window(onNodeWithTag("display").fetchWidget())
        Rwindow_inject_mouse_button(window, R_MOUSE_X1, R_TRUE)
        Rwindow_inject_mouse_button(window, R_MOUSE_X1, R_FALSE)
        waitForIdle()
        onNodeWithText("home 0").assertExists()
        assertEquals(listOf<NavKey>(Detail(3), Detail(2), Detail(1)), backs)

        // At the root there is nowhere to go back to.
        pressKey(Key.LeftBracket, meta = true)
        waitForIdle()
        assertEquals(3, backs.size)
        onNodeWithText("home 0").assertExists()
    }

    @Test
    fun withoutBackKeysTheyDoNothing() = runRavenTest {
        host.start { Window(onCloseRequest = {}, title = "Test", backKeys = false) { Navigation() } }
        waitForIdle()
        backStack.add(Detail(1))
        waitForIdle()
        pressKey(Key.LeftBracket, meta = true)
        waitForIdle()
        onNodeWithText("detail 1").assertExists()
        assertEquals(emptyList(), backs)
    }

    @Test
    fun scenesAreCalculatedWithOverlaysTopmostFirst() {
        val home = androidx.navigation3.runtime.NavEntry<NavKey>(Home) {}
        val first = androidx.navigation3.runtime.NavEntry<NavKey>(ConfirmDelete, contentKey = "first", metadata = DialogSceneStrategy.dialog()) {}
        val second = androidx.navigation3.runtime.NavEntry<NavKey>(ConfirmDelete, contentKey = "second", metadata = DialogSceneStrategy.dialog()) {}
        val state = calculateSceneState(listOf(home, first, second), DialogSceneStrategy<NavKey>() then SinglePaneSceneStrategy())
        assertEquals(listOf<Any>("second", "first"), state.overlayScenes.map { it.key })
        assertEquals(home.contentKey, state.currentScene.key)
        assertTrue(state.currentScene.previousEntries.isEmpty())
    }
}
