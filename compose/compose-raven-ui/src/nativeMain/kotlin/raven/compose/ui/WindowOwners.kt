package raven.compose.ui

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner

/**
 * A window's [LifecycleOwner] and [SavedStateRegistryOwner], as Compose Desktop's: the lifecycle is
 * RESUMED while the window is shown and active, STARTED while shown but not active, CREATED while
 * minimised or hidden, and DESTROYED once the window has left the composition. The saved state
 * persists nothing: it is there for lifecycle-aware libraries that ask for an owner.
 */
internal class WindowOwners : LifecycleOwner, SavedStateRegistryOwner {
    // Unsafe skips the registry's main-thread check: the host already keeps every call on the UI thread.
    private val registry = LifecycleRegistry.createUnsafe(this)
    private val savedState = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = registry

    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    init {
        // The registry is attached and restored, with nothing, while the lifecycle is still INITIALIZED.
        savedState.performAttach()
        savedState.performRestore(null)
        registry.currentState = Lifecycle.State.CREATED
    }

    /** Moves the lifecycle to what a window that is [shown], [minimized] and [active] is in. */
    fun follow(shown: Boolean, minimized: Boolean, active: Boolean) {
        if (registry.currentState == Lifecycle.State.DESTROYED) return
        registry.currentState = when {
            !shown || minimized -> Lifecycle.State.CREATED
            active -> Lifecycle.State.RESUMED
            else -> Lifecycle.State.STARTED
        }
    }

    /** The window is leaving the composition: its observers see it stop, then end. */
    fun destroy() {
        registry.currentState = Lifecycle.State.DESTROYED
    }
}
