package raven.sample.viewportvulkan

import kotlinx.cinterop.CFunction
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.cstr
import kotlinx.cinterop.invoke
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.reinterpret
import platform.posix.RTLD_LOCAL
import platform.posix.RTLD_NOW
import platform.posix.dlopen
import platform.posix.dlsym
import raven.sample.viewportvulkan.vk.PFN_vkAllocateCommandBuffers
import raven.sample.viewportvulkan.vk.PFN_vkAllocateMemory
import raven.sample.viewportvulkan.vk.PFN_vkBeginCommandBuffer
import raven.sample.viewportvulkan.vk.PFN_vkBindImageMemory
import raven.sample.viewportvulkan.vk.PFN_vkCmdBeginRenderPass
import raven.sample.viewportvulkan.vk.PFN_vkCmdBindPipeline
import raven.sample.viewportvulkan.vk.PFN_vkCmdDraw
import raven.sample.viewportvulkan.vk.PFN_vkCmdEndRenderPass
import raven.sample.viewportvulkan.vk.PFN_vkCmdPushConstants
import raven.sample.viewportvulkan.vk.PFN_vkCmdSetScissor
import raven.sample.viewportvulkan.vk.PFN_vkCmdSetViewport
import raven.sample.viewportvulkan.vk.PFN_vkCreateCommandPool
import raven.sample.viewportvulkan.vk.PFN_vkCreateFence
import raven.sample.viewportvulkan.vk.PFN_vkCreateFramebuffer
import raven.sample.viewportvulkan.vk.PFN_vkCreateGraphicsPipelines
import raven.sample.viewportvulkan.vk.PFN_vkCreateImage
import raven.sample.viewportvulkan.vk.PFN_vkCreateImageView
import raven.sample.viewportvulkan.vk.PFN_vkCreatePipelineLayout
import raven.sample.viewportvulkan.vk.PFN_vkCreateRenderPass
import raven.sample.viewportvulkan.vk.PFN_vkCreateShaderModule
import raven.sample.viewportvulkan.vk.PFN_vkDestroyCommandPool
import raven.sample.viewportvulkan.vk.PFN_vkDestroyFence
import raven.sample.viewportvulkan.vk.PFN_vkDestroyFramebuffer
import raven.sample.viewportvulkan.vk.PFN_vkDestroyImage
import raven.sample.viewportvulkan.vk.PFN_vkDestroyImageView
import raven.sample.viewportvulkan.vk.PFN_vkDestroyPipeline
import raven.sample.viewportvulkan.vk.PFN_vkDestroyPipelineLayout
import raven.sample.viewportvulkan.vk.PFN_vkDestroyRenderPass
import raven.sample.viewportvulkan.vk.PFN_vkDestroyShaderModule
import raven.sample.viewportvulkan.vk.PFN_vkDeviceWaitIdle
import raven.sample.viewportvulkan.vk.PFN_vkEndCommandBuffer
import raven.sample.viewportvulkan.vk.PFN_vkFreeMemory
import raven.sample.viewportvulkan.vk.PFN_vkGetDeviceProcAddr
import raven.sample.viewportvulkan.vk.PFN_vkGetImageMemoryRequirements
import raven.sample.viewportvulkan.vk.PFN_vkGetInstanceProcAddr
import raven.sample.viewportvulkan.vk.PFN_vkGetPhysicalDeviceMemoryProperties
import raven.sample.viewportvulkan.vk.PFN_vkQueueSubmit
import raven.sample.viewportvulkan.vk.PFN_vkResetFences
import raven.sample.viewportvulkan.vk.PFN_vkWaitForFences
import raven.sample.viewportvulkan.vk.VkDevice
import raven.sample.viewportvulkan.vk.VkInstance

/**
 * The library Raven loads Vulkan from, opened by the names Raven tries (`vk_loader.c`), in its order,
 * so it is the one Raven opened: the system hands back the same library.
 */
private val vulkanLibraryNames: List<String> = listOf(
    "libvulkan.1.dylib", "libvulkan.dylib", "libMoltenVK.dylib",
    "/opt/homebrew/lib/libvulkan.1.dylib", "/opt/homebrew/lib/libvulkan.dylib", "/opt/homebrew/lib/libMoltenVK.dylib",
    "/usr/local/lib/libvulkan.1.dylib", "/usr/local/lib/libvulkan.dylib", "/usr/local/lib/libMoltenVK.dylib",
    "/opt/local/lib/libvulkan.1.dylib", "/opt/local/lib/libvulkan.dylib", "/opt/local/lib/libMoltenVK.dylib",
    "libvulkan.so.1", "libvulkan.so",
)

/**
 * The Vulkan functions the sample calls, resolved on Raven's instance and device. The sample links
 * no Vulkan library, as Raven links none: the C example links the loader instead, which the port
 * can't count on (Homebrew's MoltenVK has no libvulkan).
 */
class VulkanFunctions(instance: VkInstance, private val device: VkDevice) {
    private val getInstanceProcAddr: PFN_vkGetInstanceProcAddr = run {
        val library = vulkanLibraryNames.firstNotNullOfOrNull { dlopen(it, RTLD_NOW or RTLD_LOCAL) }
            ?: error("viewport-vulkan: no Vulkan library could be opened")
        checkNotNull(dlsym(library, "vkGetInstanceProcAddr")) { "viewport-vulkan: no vkGetInstanceProcAddr" }.reinterpret()
    }

    private fun <F : Function<*>> instanceFunction(instance: VkInstance, name: String): CPointer<CFunction<F>> =
        checkNotNull(memScoped { getInstanceProcAddr(instance, name.cstr.ptr) }) { "viewport-vulkan: no $name" }.reinterpret()

    private val getDeviceProcAddr: PFN_vkGetDeviceProcAddr = instanceFunction(instance, "vkGetDeviceProcAddr")

    private fun <F : Function<*>> deviceFunction(name: String): CPointer<CFunction<F>> =
        checkNotNull(memScoped { getDeviceProcAddr(device, name.cstr.ptr) }) { "viewport-vulkan: no $name" }.reinterpret()

    val getPhysicalDeviceMemoryProperties: PFN_vkGetPhysicalDeviceMemoryProperties =
        instanceFunction(instance, "vkGetPhysicalDeviceMemoryProperties")

    val createShaderModule: PFN_vkCreateShaderModule = deviceFunction("vkCreateShaderModule")
    val destroyShaderModule: PFN_vkDestroyShaderModule = deviceFunction("vkDestroyShaderModule")
    val createPipelineLayout: PFN_vkCreatePipelineLayout = deviceFunction("vkCreatePipelineLayout")
    val destroyPipelineLayout: PFN_vkDestroyPipelineLayout = deviceFunction("vkDestroyPipelineLayout")
    val createRenderPass: PFN_vkCreateRenderPass = deviceFunction("vkCreateRenderPass")
    val destroyRenderPass: PFN_vkDestroyRenderPass = deviceFunction("vkDestroyRenderPass")
    val createGraphicsPipelines: PFN_vkCreateGraphicsPipelines = deviceFunction("vkCreateGraphicsPipelines")
    val destroyPipeline: PFN_vkDestroyPipeline = deviceFunction("vkDestroyPipeline")
    val createImage: PFN_vkCreateImage = deviceFunction("vkCreateImage")
    val destroyImage: PFN_vkDestroyImage = deviceFunction("vkDestroyImage")
    val getImageMemoryRequirements: PFN_vkGetImageMemoryRequirements = deviceFunction("vkGetImageMemoryRequirements")
    val allocateMemory: PFN_vkAllocateMemory = deviceFunction("vkAllocateMemory")
    val freeMemory: PFN_vkFreeMemory = deviceFunction("vkFreeMemory")
    val bindImageMemory: PFN_vkBindImageMemory = deviceFunction("vkBindImageMemory")
    val createImageView: PFN_vkCreateImageView = deviceFunction("vkCreateImageView")
    val destroyImageView: PFN_vkDestroyImageView = deviceFunction("vkDestroyImageView")
    val createFramebuffer: PFN_vkCreateFramebuffer = deviceFunction("vkCreateFramebuffer")
    val destroyFramebuffer: PFN_vkDestroyFramebuffer = deviceFunction("vkDestroyFramebuffer")
    val createCommandPool: PFN_vkCreateCommandPool = deviceFunction("vkCreateCommandPool")
    val destroyCommandPool: PFN_vkDestroyCommandPool = deviceFunction("vkDestroyCommandPool")
    val allocateCommandBuffers: PFN_vkAllocateCommandBuffers = deviceFunction("vkAllocateCommandBuffers")
    val createFence: PFN_vkCreateFence = deviceFunction("vkCreateFence")
    val destroyFence: PFN_vkDestroyFence = deviceFunction("vkDestroyFence")
    val waitForFences: PFN_vkWaitForFences = deviceFunction("vkWaitForFences")
    val resetFences: PFN_vkResetFences = deviceFunction("vkResetFences")
    val beginCommandBuffer: PFN_vkBeginCommandBuffer = deviceFunction("vkBeginCommandBuffer")
    val endCommandBuffer: PFN_vkEndCommandBuffer = deviceFunction("vkEndCommandBuffer")
    val cmdBeginRenderPass: PFN_vkCmdBeginRenderPass = deviceFunction("vkCmdBeginRenderPass")
    val cmdEndRenderPass: PFN_vkCmdEndRenderPass = deviceFunction("vkCmdEndRenderPass")
    val cmdSetViewport: PFN_vkCmdSetViewport = deviceFunction("vkCmdSetViewport")
    val cmdSetScissor: PFN_vkCmdSetScissor = deviceFunction("vkCmdSetScissor")
    val cmdBindPipeline: PFN_vkCmdBindPipeline = deviceFunction("vkCmdBindPipeline")
    val cmdPushConstants: PFN_vkCmdPushConstants = deviceFunction("vkCmdPushConstants")
    val cmdDraw: PFN_vkCmdDraw = deviceFunction("vkCmdDraw")
    val queueSubmit: PFN_vkQueueSubmit = deviceFunction("vkQueueSubmit")
    val deviceWaitIdle: PFN_vkDeviceWaitIdle = deviceFunction("vkDeviceWaitIdle")
}
