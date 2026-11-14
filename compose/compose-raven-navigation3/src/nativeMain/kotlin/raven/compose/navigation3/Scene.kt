package raven.compose.navigation3

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavEntry

/**
 * What [NavDisplay] shows at once: one or more [entries] of the back stack, with [content] laying
 * them out. [key] says which scene it is: a scene with a new key is a new destination, which comes
 * with the transition. [previousEntries] are the entries back would show.
 */
public interface Scene<T : Any> {
    public val key: Any
    public val entries: List<NavEntry<T>>
    public val previousEntries: List<NavEntry<T>>

    /** What the scene's last entry says of itself, for the strategies that read it. */
    public val metadata: Map<String, Any> get() = entries.lastOrNull()?.metadata ?: emptyMap()

    public val content: @Composable () -> Unit
}

/** A scene over another, a dialog: [overlaidEntries] are the entries of the scenes under it. */
public interface OverlayScene<T : Any> : Scene<T> {
    public val overlaidEntries: List<NavEntry<T>>
}

/** What a [SceneStrategy] is given beside the entries: how to go back. */
public class SceneStrategyScope<T : Any>(public val onBack: () -> Unit = {})

/** Makes the [Scene] for the top of a list of entries, or declines with null. */
public fun interface SceneStrategy<T : Any> {
    public fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>?

    /** This strategy, and [sceneStrategy] where it declines. */
    public infix fun then(sceneStrategy: SceneStrategy<T>): SceneStrategy<T> = SceneStrategy { entries ->
        with(this@SceneStrategy) { calculateScene(entries) } ?: with(sceneStrategy) { calculateScene(entries) }
    }
}

/** The last entry, alone: [NavDisplay]'s default, which never declines. */
public class SinglePaneSceneStrategy<T : Any> : SceneStrategy<T> {
    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T> =
        SinglePaneScene(entries.last().contentKey, entries.last(), entries.dropLast(1))
}

/** One entry filling the display. */
public class SinglePaneScene<T : Any>(
    override val key: Any,
    public val entry: NavEntry<T>,
    override val previousEntries: List<NavEntry<T>>,
) : Scene<T> {
    override val entries: List<NavEntry<T>> = listOf(entry)
    override val content: @Composable () -> Unit = { entry.Content() }
}

/** What a [SceneDecoratorStrategy] is given beside the scene: how to go back. */
public class SceneDecoratorStrategyScope<T : Any>(public val onBack: () -> Unit = {})

/** Wraps each scene [NavDisplay] shows, to add what goes around it. */
public fun interface SceneDecoratorStrategy<T : Any> {
    public fun SceneDecoratorStrategyScope<T>.decorateScene(scene: Scene<T>): Scene<T>
}

/**
 * The scenes of a list of entries: [currentScene] at the bottom, and [overlayScenes] over it, the
 * topmost first.
 */
public class SceneState<T : Any> internal constructor(
    public val entries: List<NavEntry<T>>,
    public val currentScene: Scene<T>,
    public val overlayScenes: List<OverlayScene<T>>,
)

/**
 * Asks [sceneStrategy] for the top scene of [entries], then, while it is an overlay, for the scene
 * of the entries under it, each wrapped by [sceneDecoratorStrategies] in order.
 */
public fun <T : Any> calculateSceneState(
    entries: List<NavEntry<T>>,
    sceneStrategy: SceneStrategy<T>,
    sceneDecoratorStrategies: List<SceneDecoratorStrategy<T>> = emptyList(),
    onBack: () -> Unit = {},
): SceneState<T> {
    require(entries.isNotEmpty()) { "a NavDisplay shows a back stack of one entry at least" }
    val strategyScope = SceneStrategyScope<T>(onBack)
    val decoratorScope = SceneDecoratorStrategyScope<T>(onBack)
    fun scene(of: List<NavEntry<T>>): Scene<T> {
        val scene = with(sceneStrategy) { strategyScope.calculateScene(of) } ?: error("the scene strategy made no scene for $of")
        return sceneDecoratorStrategies.fold(scene) { decorated, decorator -> with(decorator) { decoratorScope.decorateScene(decorated) } }
    }
    val overlays = mutableListOf<OverlayScene<T>>()
    var scene = scene(entries)
    while (scene is OverlayScene<T>) {
        overlays += scene
        require(scene.overlaidEntries.isNotEmpty()) { "an overlay scene needs an entry under it" }
        scene = scene(scene.overlaidEntries)
    }
    return SceneState(entries, scene, overlays)
}
