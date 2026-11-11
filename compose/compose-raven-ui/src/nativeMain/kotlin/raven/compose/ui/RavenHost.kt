@file:OptIn(ExperimentalAtomicApi::class)

package raven.compose.ui

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.snapshots.ObserverHandle
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.alloc
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.value
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import raven.cinterop.RInitDesc
import raven.cinterop.R_BACKEND_NULL
import raven.cinterop.R_BACKEND_VULKAN
import raven.cinterop.R_FALSE
import raven.cinterop.R_TRUE
import raven.cinterop.Raccessibility_follow_system
import raven.cinterop.Rdraw_frame
import raven.cinterop.Rerror_get_last
import raven.cinterop.Rget_time
import raven.cinterop.Rinit
import raven.cinterop.Rinit_ex
import raven.cinterop.Rquit
import raven.cinterop.Rset_animation_time_step
import raven.cinterop.Rset_frame_callback
import raven.cinterop.Rshutdown
import raven.cinterop.Rwait_frame
import raven.native.toRavenString
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch

/**
 * One run of Raven with Compose on top: [start] initialises Raven and composes the application,
 * [frame] or [waitFrame] produce frames, and [close] shuts everything down in order. `application`
 * drives it with `while (waitFrame())`, and the test harness with frames of its own.
 *
 * The application composition emits no nodes. Each [Window] in it has its own composition, root
 * and Applier, under the one [Recomposer].
 *
 * Every function but [reportError] must be called on the thread that called [start], which becomes
 * Raven's UI thread.
 */
@InternalComposeRavenApi
public class RavenHost(
    private val headless: Boolean = false,
    private val followSystemAccessibility: Boolean = true,
    private val animationTimeStep: Double = 0.0,
) {
    private enum class State { New, Started, Closed }

    private var state = State.New
    private val firstError = AtomicReference<Throwable?>(null)

    private val dispatcher = RavenDispatcher(::reportError)
    private val clock = BroadcastFrameClock { dispatcher.wakeUp() }
    private val job = Job()
    private val scope = CoroutineScope(
        dispatcher + clock + job + CoroutineExceptionHandler { _, t -> reportError(t) },
    )
    private val recomposer = Recomposer(scope.coroutineContext)
    private val applyPending = AtomicInt(0)
    private var writeObserver: ObserverHandle? = null
    private var composition: Composition? = null
    private var selfRef: StableRef<RavenHost>? = null

    /** The first throwable reported by a coroutine, a callback or composition, if any. */
    public val error: Throwable? get() = firstError.load()

    /** The epoch this host's Raven handles belong to. */
    public var epoch: Int = HostEpoch.current
        private set

    /** Caches of Raven handles, cleared when the host closes. */
    internal val caches: HostCaches = HostCaches()

    /** Advances each time a window's composition has applied a batch of changes. */
    internal var applyGeneration: Long = 0L
        private set

    // Controlled values the user changed, waiting for the app's response to be composed.
    private val markedInputs = LinkedHashSet<ComposedValue<*>>()

    /** Starts Raven on the calling thread and composes [content]. */
    public fun start(content: @Composable () -> Unit) {
        check(state == State.New) { "a RavenHost starts once" }
        initRaven()
        state = State.Started
        epoch = HostEpoch.current
        if (animationTimeStep > 0.0) Rset_animation_time_step(animationTimeStep)
        if (followSystemAccessibility && !headless) Raccessibility_follow_system()

        selfRef = StableRef.create(this).also { Rset_frame_callback(onFrame, it.asCPointer()) }
        scope.launch { recomposer.runRecomposeAndApplyChanges() }
        writeObserver = Snapshot.registerGlobalWriteObserver {
            // One apply per burst of writes, from any thread: the posted task wakes Raven's loop.
            if (applyPending.compareAndSet(0, 1)) {
                dispatcher.dispatch(scope.coroutineContext, Runnable {
                    applyPending.store(0)
                    Snapshot.sendApplyNotifications()
                })
            }
        }
        // Kept before composing, so close() disposes what a failed first composition created.
        val composition = Composition(ApplicationApplier(), recomposer).also { composition = it }
        composition.setContent { CompositionLocalProvider(LocalRavenHost provides this, content = content) }
    }

    /** Draws one frame without waiting. False once Raven was asked to quit. */
    public fun frame(): Boolean = Rdraw_frame() == R_TRUE

    /** Waits for something to do, then draws one frame. False once Raven was asked to quit. */
    public fun waitFrame(): Boolean = Rwait_frame(-1.0) == R_TRUE

    /**
     * Whether nothing is left to do but wait: no dispatched task, no snapshot apply and no
     * recomposition pending. Frame awaiters (animations, `withFrameNanos` loops) don't count.
     */
    public fun isIdle(): Boolean = markedInputs.isEmpty() && !hasPendingRecomposition()

    private fun hasPendingRecomposition(): Boolean {
        if (dispatcher.hasQueuedTasks || applyPending.load() != 0) return true
        // Paused, the frame clock hides its awaiters from hasPendingWork.
        recomposer.pauseCompositionFrameClock()
        val pending = recomposer.hasPendingWork
        recomposer.resumeCompositionFrameClock()
        return pending || dispatcher.hasQueuedTasks
    }

    /**
     * Records [t] if it is the first error, and asks Raven to quit. Thread-safe. The host still
     * shuts down normally, and the caller rethrows [error] after [close].
     */
    public fun reportError(t: Throwable) {
        firstError.compareAndSet(null, t)
        Rquit(1)
    }

    internal fun onChangesApplied() {
        applyGeneration++
    }

    /** Marks a user change to [input], to check once the app's response has been composed. */
    internal fun markUserChange(input: ComposedValue<*>) {
        input.markedAt = applyGeneration
        markedInputs += input
    }

    /**
     * Shows the composed value again in each marked input whose user change the app has answered:
     * a batch was applied since the mark, or nothing is left to recompose, so the app ignored it.
     * Running animations never hold the check back, since frame awaiters aren't pending work.
     */
    private fun checkMarkedInputs() {
        if (markedInputs.isEmpty()) return
        val answered = !hasPendingRecomposition()
        val iterator = markedInputs.iterator()
        while (iterator.hasNext()) {
            val input = iterator.next()
            if (answered || applyGeneration > input.markedAt) {
                iterator.remove()
                input.showComposed()
            }
        }
    }

    /**
     * Disposes the compositions (each window's before the window itself), stops the Recomposer,
     * clears the caches and advances the epoch, then shuts Raven down.
     */
    public fun close() {
        if (state != State.Started) {
            if (state == State.New) dispatcher.release()
            state = State.Closed
            return
        }
        state = State.Closed
        try {
            composition?.dispose()
        } catch (t: Throwable) {
            reportError(t)
        }
        recomposer.cancel()
        scope.cancel()
        // Cancelled coroutines finish on the dispatcher: the Recomposer unregisters its observers.
        dispatcher.drain()
        writeObserver?.dispose()
        markedInputs.clear()
        caches.clear()
        HostEpoch.advance()
        dispatcher.close()
        Rset_frame_callback(null, null)
        Rshutdown()
        dispatcher.release()
        selfRef?.dispose()
    }

    private fun initRaven() {
        val ok = if (headless) {
            memScoped {
                val desc = alloc<RInitDesc>().apply {
                    backend = R_BACKEND_NULL
                    app_name = null
                    worker_threads = -1
                    disable_render_thread = R_FALSE
                    disable_vsync = R_FALSE
                    enable_validation = R_FALSE
                    headless = R_TRUE
                    ui_scale = 0f
                }
                Rinit_ex(desc.ptr)
            }
        } else {
            Rinit(R_BACKEND_VULKAN)
        }
        if (ok != R_TRUE) {
            val detail = memScoped {
                val text = alloc<CPointerVar<ByteVar>>()
                Rerror_get_last(text.ptr, null)
                text.value.toRavenString()
            }
            error("Raven failed to start: $detail")
        }
    }

    internal fun sendFrame() {
        try {
            // What was dispatched since the frame's tasks ran, a timer's state write say, joins this frame.
            dispatcher.drain()
            // Recomposes and applies, inside the frame callback, so layout and paint follow.
            clock.sendFrame((Rget_time() * 1e9).toLong())
            // The frame awaiters it resumed (animations, withFrameNanos loops) take their step now,
            // instead of leaving work queued after every frame.
            dispatcher.drain()
            checkMarkedInputs()
        } catch (t: Throwable) {
            reportError(t)
        }
    }
}

/**
 * Raven's handles carry only an 8-bit generation and `Rinit` resets every pool, so a handle from
 * an earlier Raven can name a different, valid object. The epoch advances each time a host shuts
 * down, and every handle kept outside a node keeps the epoch it was made in.
 */
internal object HostEpoch {
    private val epoch = AtomicInt(0)

    val current: Int get() = epoch.load()

    fun advance() {
        epoch.incrementAndFetch()
    }

    /** Throws if a handle made in [handleEpoch] belongs to a Raven that has shut down. */
    fun checkCurrent(handleEpoch: Int) {
        check(handleEpoch == current) { "a Raven handle from an earlier run of Raven (epoch $handleEpoch, now $current)" }
    }
}

/**
 * The caches of Raven handles a host keeps (fonts, images, effects in later phases). They belong
 * to the host, never to a process-wide object, because `Rinit` reuses handles.
 */
internal class HostCaches {
    private val entries = mutableMapOf<Any, Any>()

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> getOrPut(key: Any, create: () -> T): T = entries.getOrPut(key, create) as T

    fun clear() = entries.clear()
}

/** The host of the composition, for windows and anything that keeps Raven handles. */
@OptIn(InternalComposeRavenApi::class)
internal val LocalRavenHost = staticCompositionLocalOf<RavenHost> { error("no RavenHost") }

/** The application composition holds windows, which emit no nodes into it. */
private class ApplicationApplier : AbstractApplier<Unit>(Unit) {
    override fun insertTopDown(index: Int, instance: Unit) = error("put content in a Window")
    override fun insertBottomUp(index: Int, instance: Unit) = error("put content in a Window")
    override fun remove(index: Int, count: Int) = Unit
    override fun move(from: Int, to: Int, count: Int) = Unit
    override fun onClear() = Unit
}

@OptIn(InternalComposeRavenApi::class)
private val onFrame = staticCFunction { _: Double, userData: COpaquePointer? ->
    userData!!.asStableRef<RavenHost>().get().sendFrame()
}
