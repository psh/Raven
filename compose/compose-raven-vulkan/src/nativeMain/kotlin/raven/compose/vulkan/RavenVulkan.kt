package raven.compose.vulkan

import androidx.compose.runtime.Composable
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import raven.cinterop.vulkan.Rvulkan_get_device
import raven.cinterop.vulkan.Rvulkan_get_graphics_queue
import raven.cinterop.vulkan.Rvulkan_get_graphics_queue_family
import raven.cinterop.vulkan.Rvulkan_get_instance
import raven.cinterop.vulkan.Rvulkan_get_physical_device
import raven.cinterop.vulkan.Rvulkan_get_required_device_extensions
import raven.cinterop.vulkan.Rvulkan_get_required_instance_extensions
import raven.cinterop.vulkan.Rvulkan_set_external_device
import raven.cinterop.vulkan.Rvulkan_set_queue_lock_callbacks
import raven.compose.ui.ApplicationScope
import raven.compose.ui.application
import kotlin.experimental.ExperimentalNativeApi

/** The Vulkan objects Raven renders with: dispatchable handles as pointers. */
public data class VulkanDevice(
    /** `VkInstance`. */
    val instance: COpaquePointer,
    /** `VkPhysicalDevice`. */
    val physicalDevice: COpaquePointer,
    /** `VkDevice`. */
    val device: COpaquePointer,
    /** The `VkQueue` Raven renders and presents with. */
    val graphicsQueue: COpaquePointer,
    /** The family index of [graphicsQueue]. */
    val graphicsQueueFamily: UInt,
)

/** Raven's Vulkan device, while Raven runs. */
public object RavenVulkan {
    /**
     * The device Raven renders with; null before Raven starts, or on another backend (a headless
     * host). Raven makes a new one after a lost device, so read it again then.
     */
    public val device: VulkanDevice?
        get() {
            val device = Rvulkan_get_device() ?: return null
            return VulkanDevice(
                Rvulkan_get_instance() ?: return null,
                Rvulkan_get_physical_device() ?: return null,
                device,
                Rvulkan_get_graphics_queue() ?: return null,
                Rvulkan_get_graphics_queue_family(),
            )
        }
}

/** Which Vulkan device Raven renders with. */
public sealed interface VulkanSetup {
    /** Raven's own: a discrete GPU when there is one (`RAVEN_VK_DEVICE` forces another). */
    public data object Raven : VulkanSetup

    /**
     * The app's own device, made with [requiredInstanceExtensions] and [requiredDeviceExtensions];
     * Raven doesn't destroy it. [graphicsQueue] must support graphics and presentation. When the app
     * uses the queue too, [lockQueue] and [unlockQueue] are called around each of Raven's uses of it,
     * on Raven's render thread.
     */
    public class External(
        public val instance: COpaquePointer,
        public val physicalDevice: COpaquePointer,
        public val device: COpaquePointer,
        public val graphicsQueue: COpaquePointer,
        public val graphicsQueueFamily: UInt,
        public val lockQueue: (() -> Unit)? = null,
        public val unlockQueue: (() -> Unit)? = null,
    ) : VulkanSetup

    public companion object {
        /** The instance extensions an [External] device's instance must have. */
        public val requiredInstanceExtensions: List<String> get() = extensions { Rvulkan_get_required_instance_extensions(it) }

        /** The device extensions an [External] device must have. */
        public val requiredDeviceExtensions: List<String> get() = extensions { Rvulkan_get_required_device_extensions(it) }

        private fun extensions(get: (CPointer<UIntVar>) -> CPointer<CPointerVar<ByteVar>>?): List<String> = memScoped {
            val count = alloc<UIntVar>()
            val names = get(count.ptr) ?: return emptyList()
            List(count.value.toInt()) { names[it]!!.toKString() }
        }
    }
}

/** The queue lock callbacks of the app's device, for as long as the process lives. */
private class QueueLock(val lock: () -> Unit, val unlock: () -> Unit)

private val onLockQueue = staticCFunction { userData: COpaquePointer? -> userData!!.asStableRef<QueueLock>().get().lock() }
private val onUnlockQueue = staticCFunction { userData: COpaquePointer? -> userData!!.asStableRef<QueueLock>().get().unlock() }

/** Gives Raven the app's device: before Raven starts. */
private fun VulkanSetup.apply() {
    if (this !is VulkanSetup.External) return
    Rvulkan_set_external_device(instance, physicalDevice, device, graphicsQueue, graphicsQueueFamily)
    if (lockQueue != null && unlockQueue != null) {
        val ref = StableRef.create(QueueLock(lockQueue, unlockQueue))
        Rvulkan_set_queue_lock_callbacks(onLockQueue, onUnlockQueue, ref.asCPointer())
    }
}

/**
 * `application` with Raven rendering on [vulkan]'s device: the app's own with
 * [VulkanSetup.External], so Raven and the app share it. The other parameters are `application`'s.
 */
@OptIn(ExperimentalNativeApi::class)
public fun application(
    vulkan: VulkanSetup,
    followSystemAccessibility: Boolean = true,
    liveReload: Boolean = Platform.isDebugBinary,
    resourceRoot: String? = null,
    content: @Composable ApplicationScope.() -> Unit,
) {
    vulkan.apply()
    application(followSystemAccessibility, liveReload, resourceRoot, content)
}
