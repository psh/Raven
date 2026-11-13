package raven.spike.navigation3

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import raven.compose.foundation.Button
import raven.compose.foundation.Text
import raven.compose.foundation.runRavenTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@Serializable
data object Home : NavKey

@Serializable
data class Detail(val id: Int) : NavKey

/** Kotlin/Native has no reflection, so `NavKey`'s open polymorphism is declared by hand. */
private val navConfig = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(Home::class)
            subclass(Detail::class)
        }
    }
}

/** A back stack shown as NavDisplay would show its top entry, with the saveable-state decorator. */
@Composable
private fun Navigation(onBackStack: (NavBackStack<NavKey>) -> Unit) {
    val backStack = rememberNavBackStack(navConfig, Home)
    onBackStack(backStack)
    val entries = rememberDecoratedNavEntries(
        backStack = backStack,
        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
        entryProvider = entryProvider {
            entry<Home> {
                var count by rememberSaveable { mutableStateOf(0) }
                Button(onClick = { count++ }) { Text("home $count") }
            }
            entry<Detail> { key -> Text("detail ${key.id}") }
        },
    )
    entries.last().Content()
}

class Navigation3Spike {
    @Test
    fun theBackStackShowsItsTopEntry() = runRavenTest {
        lateinit var backStack: NavBackStack<NavKey>
        setContent { Navigation { backStack = it } }
        onNodeWithText("home 0").assertExists()

        backStack.add(Detail(7))
        waitForIdle()
        onNodeWithText("detail 7").assertExists()
        onNodeWithText("home 0").assertDoesNotExist()

        backStack.removeLastOrNull()
        waitForIdle()
        onNodeWithText("home 0").assertExists()
    }

    /** The decorator keeps a popped-to entry's saveable state while it was out of the composition. */
    @Test
    fun anEntryKeepsItsStateUnderAnotherEntry() = runRavenTest {
        lateinit var backStack: NavBackStack<NavKey>
        setContent { Navigation { backStack = it } }
        onNodeWithText("home 0").performClick()
        waitForIdle()
        onNodeWithText("home 1").performClick()
        waitForIdle()
        onNodeWithText("home 2").assertExists()

        backStack.add(Detail(1))
        waitForIdle()
        onNodeWithText("detail 1").assertExists()
        backStack.removeLastOrNull()
        waitForIdle()
        onNodeWithText("home 2").assertExists()
    }

    /**
     * The back stack and the entries' state come back after the whole subtree leaves the
     * composition, through a [SaveableStateRegistry] provided where a window's saved-state owner
     * would provide one, as when a window is closed and opened again.
     */
    @Test
    fun theBackStackAndEntryStateAreRestored() = runRavenTest {
        lateinit var backStack: NavBackStack<NavKey>
        var registry by mutableStateOf<SaveableStateRegistry?>(SaveableStateRegistry(null) { true })
        setContent {
            registry?.let { current ->
                CompositionLocalProvider(LocalSaveableStateRegistry provides current) {
                    Navigation { backStack = it }
                }
            }
        }
        onNodeWithText("home 0").performClick()
        waitForIdle()
        backStack.add(Detail(3))
        waitForIdle()
        onNodeWithText("detail 3").assertExists()

        val saved = registry!!.performSave()
        registry = null
        waitForIdle()
        onNodeWithText("detail 3").assertDoesNotExist()

        registry = SaveableStateRegistry(saved) { true }
        waitForIdle()
        onNodeWithText("detail 3").assertExists()
        assertEquals(listOf(Home, Detail(3)), backStack.toList())
        backStack.removeLastOrNull()
        waitForIdle()
        onNodeWithText("home 1").assertExists()
    }

    /** Without a module for `NavKey`, `rememberNavBackStack` refuses at once. */
    @Test
    fun theConfigurationMustDeclareTheKeys() = runRavenTest {
        assertFailsWith<IllegalArgumentException> {
            setContent { rememberNavBackStack(SavedStateConfiguration.DEFAULT, Home) }
        }
    }
}
