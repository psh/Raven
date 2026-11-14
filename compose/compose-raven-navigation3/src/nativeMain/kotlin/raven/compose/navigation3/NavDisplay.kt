package raven.compose.navigation3

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigationevent.NavigationEventHandler
import androidx.navigationevent.NavigationEventInfo
import raven.compose.foundation.layout.Box
import raven.compose.foundation.layout.fillMaxSize
import raven.compose.ui.AppearTransition
import raven.compose.ui.LocalNavigationEventDispatcher
import raven.compose.ui.Modifier
import raven.compose.ui.appearTransition

/**
 * How [NavDisplay]'s scenes arrive: Raven's appear transition [push] for a destination pushed, [pop]
 * for one shown again by going back. Null is none.
 */
@Immutable
public data class NavTransition(val push: AppearTransition? = null, val pop: AppearTransition? = null)

/** What the window's back sees of a NavDisplay. */
private object NavDisplayInfo : NavigationEventInfo()

/** Goes back when the window does, while there is somewhere to go back to. */
private class NavDisplayBackHandler(private val onBack: () -> Unit) : NavigationEventHandler<NavDisplayInfo>(NavDisplayInfo, isBackEnabled = false) {
    override fun onBackCompleted() = onBack()
}

/** The size of the back stack the last composition showed, to tell a push from a pop. */
private class ShownStack {
    var size = 0
}

/**
 * Shows [backStack] as Navigation 3 does: each key becomes an entry by [entryProvider], decorated
 * by [entryDecorators] (by default each entry keeps its saveable state while it is in the back
 * stack), and [sceneStrategy] lays the top entries out in scenes, dialogs over the scene under them.
 * A new scene arrives with [transition], none by default. The window's back (the mouse's Back
 * button, Cmd+[ or Alt+Left, see `Window(backKeys)`) calls [onBack] while there is somewhere to go
 * back to, and so does dismissing a dialog.
 */
@Composable
public fun <T : Any> NavDisplay(
    backStack: List<T>,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = { (backStack as? MutableList<T>)?.removeLastOrNull() },
    entryDecorators: List<NavEntryDecorator<T>> = listOf(rememberSaveableStateHolderNavEntryDecorator()),
    sceneStrategy: SceneStrategy<T> = SinglePaneSceneStrategy(),
    sceneDecoratorStrategies: List<SceneDecoratorStrategy<T>> = emptyList(),
    transition: NavTransition? = null,
    entryProvider: (key: T) -> NavEntry<T>,
) {
    require(backStack.isNotEmpty()) { "a NavDisplay shows a back stack of one key at least" }
    val currentOnBack by rememberUpdatedState(onBack)
    val goBack: () -> Unit = { currentOnBack() }
    val entries = rememberDecoratedNavEntries(backStack.map(entryProvider), entryDecorators)
    val state = calculateSceneState(entries, sceneStrategy, sceneDecoratorStrategies, goBack)

    // Push or pop: the back stack grew or shrank since it was last shown; nothing the first time.
    val shown = remember { ShownStack() }
    val appear = when {
        shown.size == 0 -> null
        backStack.size < shown.size -> transition?.pop
        else -> transition?.push
    }
    SideEffect { shown.size = backStack.size }

    val dispatcher = LocalNavigationEventDispatcher.current
    val handler = remember { NavDisplayBackHandler(goBack) }
    SideEffect { handler.isBackEnabled = state.currentScene.previousEntries.isNotEmpty() || state.overlayScenes.isNotEmpty() }
    DisposableEffect(dispatcher, handler) {
        dispatcher?.addHandler(handler)
        onDispose { handler.remove() }
    }

    Box(modifier) {
        val scene = state.currentScene
        key(scene.key) {
            val sceneModifier = Modifier.fillMaxSize()
            Box(if (appear != null) sceneModifier.appearTransition(appear) else sceneModifier) { scene.content() }
        }
        // The topmost last, so it is over the others.
        for (overlay in state.overlayScenes.asReversed()) key(overlay.key) { overlay.content() }
    }
}
