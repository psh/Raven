@file:OptIn(InternalComposeRavenApi::class)

package raven.sample.viewportvulkan

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.FloatVar
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.cstr
import kotlinx.cinterop.invoke
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.ptr
import kotlinx.cinterop.rawValue
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.posix.pthread_mutex_init
import platform.posix.pthread_mutex_lock
import platform.posix.pthread_mutex_t
import platform.posix.pthread_mutex_unlock
import raven.cinterop.vulkan.Rvulkan_set_queue_lock_callbacks
import raven.compose.ui.InternalComposeRavenApi
import raven.compose.ui.graphics.ImageBitmap
import raven.compose.vulkan.VulkanDevice
import raven.compose.vulkan.VulkanFrame
import raven.compose.vulkan.fromVulkanImageView
import raven.compose.vulkan.setVulkanImageView
import raven.sample.viewportvulkan.vk.*
import kotlin.concurrent.AtomicInt

/** A pthread mutex for the life of the process. */
class Mutex {
    val native: pthread_mutex_t = nativeHeap.alloc<pthread_mutex_t>().also { pthread_mutex_init(it.ptr, null) }

    inline fun <T> withLock(block: () -> T): T {
        pthread_mutex_lock(native.ptr)
        try {
            return block()
        } finally {
            pthread_mutex_unlock(native.ptr)
        }
    }
}

/**
 * The lock the app and Raven take around every use of Raven's queue: Raven renders on its own thread,
 * and a VkQueue must not be used by two threads at once. Installed before Raven starts.
 */
object QueueLock {
    val mutex: Mutex = Mutex()
    private var installed = false

    /**
     * compose-raven-vulkan takes a queue lock only with the app's own device
     * (`VulkanSetup.External`), so for Raven's device, as the C example uses, the sample calls
     * raven_vulkan.h itself: a port gap of the module.
     */
    fun install() {
        if (installed) return
        installed = true
        Rvulkan_set_queue_lock_callbacks(
            staticCFunction { it: kotlinx.cinterop.COpaquePointer? -> pthread_mutex_lock(it!!.reinterpret()); Unit },
            staticCFunction { it: kotlinx.cinterop.COpaquePointer? -> pthread_mutex_unlock(it!!.reinterpret()); Unit },
            mutex.native.ptr,
        )
    }
}

private fun check(result: VkResult, call: String) {
    if (result != VK_SUCCESS) println("viewport-vulkan: $call failed ($result)")
}

/** The image the scene is rendered into, in texture mode. */
private class Target(val image: VkImage?, val memory: VkDeviceMemory?, val view: VkImageView?, val framebuffer: VkFramebuffer?, val width: Int, val height: Int)

/**
 * The C example's scene, a turning triangle, shown both ways raven_viewport.h offers, on Raven's
 * device: rendered into the app's own image ([image], [renderOffscreen], on the UI thread), and drawn
 * in Raven's render pass ([drawInPass], on the render thread). Its Vulkan objects go in [close],
 * before Raven's device.
 */
class VulkanScene(raven: VulkanDevice, shaders: TriangleShaders, private val queueLock: Mutex = QueueLock.mutex) : AutoCloseable {
    private val physicalDevice: VkPhysicalDevice = raven.physicalDevice.reinterpret()
    private val device: VkDevice = raven.device.reinterpret()
    private val queue: VkQueue = raven.graphicsQueue.reinterpret()
    private val vk = VulkanFunctions(raven.instance.reinterpret(), device)

    // The angle, which both threads read, the pipeline of Raven's render pass, which the render
    // thread makes, and whether the scene is closed, under this lock.
    private val sceneLock = Mutex()
    private var angle = 0f
    private var closed = false
    private var windowPass: VkRenderPass? = null
    private var windowPipeline: VkPipeline? = null

    private val vertexShader: VkShaderModule?
    private val fragmentShader: VkShaderModule?
    private val layout: VkPipelineLayout?
    private val offscreenPass: VkRenderPass?
    private val offscreenPipeline: VkPipeline?
    private val pool: VkCommandPool?
    private val commands: VkCommandBuffer?
    private val fence: VkFence?
    private var submitted = false
    private var target: Target
    private var wanted = 1 to 1

    /** The texture-mode image, as a bitmap Raven samples with no copy. */
    val image: ImageBitmap

    /** Offscreen frames submitted, on the UI thread. */
    var offscreenFrames: Int = 0
        private set

    /** Frames drawn in Raven's render pass, on the render thread. */
    val passFrames: AtomicInt = AtomicInt(0)

    init {
        vertexShader = shaderModule(shaders.vertex)
        fragmentShader = shaderModule(shaders.fragment)
        layout = memScoped {
            val range = alloc<VkPushConstantRange>().apply {
                stageFlags = VK_SHADER_STAGE_VERTEX_BIT
                offset = 0u
                size = (2 * 4).toUInt()
            }
            val info = alloc<VkPipelineLayoutCreateInfo>().apply {
                sType = VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO
                pushConstantRangeCount = 1u
                pPushConstantRanges = range.ptr
            }
            val out = alloc<VkPipelineLayoutVar>()
            check(vk.createPipelineLayout(device, info.ptr, null, out.ptr), "vkCreatePipelineLayout")
            out.value
        }
        offscreenPass = createOffscreenPass()
        offscreenPipeline = createPipeline(offscreenPass)
        memScoped {
            val poolInfo = alloc<VkCommandPoolCreateInfo>().apply {
                sType = VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO
                flags = VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT
                queueFamilyIndex = raven.graphicsQueueFamily
            }
            val poolOut = alloc<VkCommandPoolVar>()
            check(vk.createCommandPool(device, poolInfo.ptr, null, poolOut.ptr), "vkCreateCommandPool")
            val allocation = alloc<VkCommandBufferAllocateInfo>().apply {
                sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO
                commandPool = poolOut.value
                level = VK_COMMAND_BUFFER_LEVEL_PRIMARY
                commandBufferCount = 1u
            }
            val commandsOut = alloc<VkCommandBufferVar>()
            check(vk.allocateCommandBuffers(device, allocation.ptr, commandsOut.ptr), "vkAllocateCommandBuffers")
            val fenceInfo = alloc<VkFenceCreateInfo>().apply { sType = VK_STRUCTURE_TYPE_FENCE_CREATE_INFO }
            val fenceOut = alloc<VkFenceVar>()
            check(vk.createFence(device, fenceInfo.ptr, null, fenceOut.ptr), "vkCreateFence")
            pool = poolOut.value
            commands = commandsOut.value
            fence = fenceOut.value
        }
        // A first image of 1 x 1 pixels, so there is a bitmap to show; the viewport's size follows.
        target = createTarget(1, 1)
        image = ImageBitmap.fromVulkanImageView(target.view.rawValue.toLong(), 0L, 1, 1)
    }

    /** Turns the triangle by [seconds], as the C example's frame callback does while not paused. */
    fun advance(seconds: Float) {
        sceneLock.withLock { angle += seconds }
    }

    /** The size, in pixels, to render the texture-mode image at: the viewport's. */
    fun resize(width: Int, height: Int) {
        if (width > 0 && height > 0) wanted = width to height
    }

    /** Renders the scene into the app's image: the UI thread, once a frame. */
    fun renderOffscreen() {
        if (sceneLock.withLock { closed }) return
        waitOffscreen()
        val (width, height) = wanted
        if (target.width != width || target.height != height) {
            // A new image, handed to Raven; it returns once Raven no longer uses the previous one.
            val previous = target
            target = createTarget(width, height)
            image.setVulkanImageView(target.view.rawValue.toLong(), 0L, width, height)
            destroyTarget(previous)
        }
        val angle = sceneLock.withLock { angle }
        memScoped {
            val begin = alloc<VkCommandBufferBeginInfo>().apply {
                sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO
                flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT
            }
            check(vk.beginCommandBuffer(commands, begin.ptr), "vkBeginCommandBuffer")
            val clear = alloc<VkClearValue>()
            clear.color.float32[0] = 0.05f
            clear.color.float32[1] = 0.06f
            clear.color.float32[2] = 0.10f
            clear.color.float32[3] = 1.0f
            val pass = alloc<VkRenderPassBeginInfo>().apply {
                sType = VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO
                renderPass = offscreenPass
                framebuffer = target.framebuffer
                renderArea.extent.width = target.width.toUInt()
                renderArea.extent.height = target.height.toUInt()
                clearValueCount = 1u
                pClearValues = clear.ptr
            }
            vk.cmdBeginRenderPass(commands, pass.ptr, VK_SUBPASS_CONTENTS_INLINE)
            drawScene(commands, offscreenPipeline, 0f, 0f, target.width.toFloat(), target.height.toFloat(), angle, setViewport = true)
            vk.cmdEndRenderPass(commands)
            check(vk.endCommandBuffer(commands), "vkEndCommandBuffer")
            val commandList = allocArray<VkCommandBufferVar>(1)
            commandList[0] = commands
            val submit = alloc<VkSubmitInfo>().apply {
                sType = VK_STRUCTURE_TYPE_SUBMIT_INFO
                commandBufferCount = 1u
                pCommandBuffers = commandList
            }
            // Submitted before Raven submits the frame that samples the image: the queue keeps that order.
            queueLock.withLock { check(vk.queueSubmit(queue, 1u, submit.ptr, fence), "vkQueueSubmit") }
        }
        submitted = true
        offscreenFrames++
        // The bitmap's viewers draw it again: what it shows changed on the GPU. A port gap:
        // compose-raven-vulkan has no public call for it (textureChanged is internal), where the C
        // example's viewport is Realtime="true" and foundation's Viewport has no realtime.
        image.textureChanged()
    }

    /** Draws the scene in Raven's render pass: the render thread, inside Raven's frame. */
    fun drawInPass(frame: VulkanFrame) {
        sceneLock.withLock {
            if (closed) return
            val commands: VkCommandBuffer = frame.commandBuffer?.reinterpret() ?: return
            val renderPass: VkRenderPass = frame.renderPass?.reinterpret() ?: return
            // A pipeline must be compatible with the render pass it is used in: made on first use.
            if (windowPass != renderPass) {
                windowPipeline?.let { vk.destroyPipeline(device, it, null) }
                windowPipeline = createPipeline(renderPass)
                windowPass = renderPass
            }
            // The viewport and scissor are Raven's, set to the widget and its visible part.
            drawScene(commands, windowPipeline, 0f, 0f, frame.width.toFloat(), frame.height.toFloat(), -angle, setViewport = false)
            passFrames.incrementAndGet()
        }
    }

    override fun close() {
        sceneLock.withLock { closed = true }
        // Returns once Raven no longer samples the image.
        image.close()
        waitOffscreen()
        queueLock.withLock { vk.deviceWaitIdle(device) }
        destroyTarget(target)
        vk.destroyFence(device, fence, null)
        vk.destroyCommandPool(device, pool, null)
        windowPipeline?.let { vk.destroyPipeline(device, it, null) }
        vk.destroyPipeline(device, offscreenPipeline, null)
        vk.destroyRenderPass(device, offscreenPass, null)
        vk.destroyPipelineLayout(device, layout, null)
        vk.destroyShaderModule(device, vertexShader, null)
        vk.destroyShaderModule(device, fragmentShader, null)
    }

    private fun waitOffscreen() {
        if (!submitted) return
        memScoped {
            val fences = allocArray<VkFenceVar>(1)
            fences[0] = fence
            check(vk.waitForFences(device, 1u, fences, VK_TRUE, ULong.MAX_VALUE), "vkWaitForFences")
            check(vk.resetFences(device, 1u, fences), "vkResetFences")
        }
        submitted = false
    }

    private fun drawScene(commands: VkCommandBuffer?, pipeline: VkPipeline?, x: Float, y: Float, width: Float, height: Float, angle: Float, setViewport: Boolean) =
        memScoped {
            if (setViewport) {
                val viewport = alloc<VkViewport>().also {
                    it.x = x
                    it.y = y
                    it.width = width
                    it.height = height
                    it.minDepth = 0f
                    it.maxDepth = 1f
                }
                val scissor = alloc<VkRect2D>().also {
                    it.offset.x = x.toInt()
                    it.offset.y = y.toInt()
                    it.extent.width = width.toUInt()
                    it.extent.height = height.toUInt()
                }
                vk.cmdSetViewport(commands, 0u, 1u, viewport.ptr)
                vk.cmdSetScissor(commands, 0u, 1u, scissor.ptr)
            }
            vk.cmdBindPipeline(commands, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline)
            val constants = allocArray<FloatVar>(2)
            constants[0] = angle
            constants[1] = width / height
            vk.cmdPushConstants(commands, layout, VK_SHADER_STAGE_VERTEX_BIT, 0u, (2 * 4).toUInt(), constants)
            vk.cmdDraw(commands, 3u, 1u, 0u, 0u)
        }

    private fun shaderModule(words: UIntArray): VkShaderModule? = memScoped {
        val out = alloc<VkShaderModuleVar>()
        words.usePinned { pinned ->
            val info = alloc<VkShaderModuleCreateInfo>().apply {
                sType = VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO
                codeSize = (words.size * 4).toULong()
                pCode = pinned.addressOf(0)
            }
            check(vk.createShaderModule(device, info.ptr, null, out.ptr), "vkCreateShaderModule")
        }
        out.value
    }

    private fun createOffscreenPass(): VkRenderPass? = memScoped {
        val attachment = alloc<VkAttachmentDescription>().apply {
            format = VK_FORMAT_R8G8B8A8_UNORM
            samples = VK_SAMPLE_COUNT_1_BIT
            loadOp = VK_ATTACHMENT_LOAD_OP_CLEAR
            storeOp = VK_ATTACHMENT_STORE_OP_STORE
            stencilLoadOp = VK_ATTACHMENT_LOAD_OP_DONT_CARE
            stencilStoreOp = VK_ATTACHMENT_STORE_OP_DONT_CARE
            initialLayout = VK_IMAGE_LAYOUT_UNDEFINED
            // Raven requires the image in this layout whenever it renders.
            finalLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL
        }
        val reference = alloc<VkAttachmentReference>().apply {
            this.attachment = 0u
            layout = VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL
        }
        val subpass = alloc<VkSubpassDescription>().apply {
            pipelineBindPoint = VK_PIPELINE_BIND_POINT_GRAPHICS
            colorAttachmentCount = 1u
            pColorAttachments = reference.ptr
        }
        // Wait for Raven's previous frame to finish sampling the image, and make Raven wait for this one.
        val dependencies = allocArray<VkSubpassDependency>(2)
        dependencies[0].apply {
            srcSubpass = VK_SUBPASS_EXTERNAL
            dstSubpass = 0u
            srcStageMask = VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT
            dstStageMask = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT
            srcAccessMask = VK_ACCESS_SHADER_READ_BIT
            dstAccessMask = VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT
        }
        dependencies[1].apply {
            srcSubpass = 0u
            dstSubpass = VK_SUBPASS_EXTERNAL
            srcStageMask = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT
            dstStageMask = VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT
            srcAccessMask = VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT
            dstAccessMask = VK_ACCESS_SHADER_READ_BIT
        }
        val info = alloc<VkRenderPassCreateInfo>().apply {
            sType = VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO
            attachmentCount = 1u
            pAttachments = attachment.ptr
            subpassCount = 1u
            pSubpasses = subpass.ptr
            dependencyCount = 2u
            pDependencies = dependencies
        }
        val out = alloc<VkRenderPassVar>()
        check(vk.createRenderPass(device, info.ptr, null, out.ptr), "vkCreateRenderPass")
        out.value
    }

    /** The same pipeline for both modes, for [renderPass]; the viewport and scissor are dynamic. */
    private fun createPipeline(renderPass: VkRenderPass?): VkPipeline? = memScoped {
        val stages = allocArray<VkPipelineShaderStageCreateInfo>(2)
        val main = "main".cstr.ptr
        stages[0].apply {
            sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO
            stage = VK_SHADER_STAGE_VERTEX_BIT
            module = vertexShader
            pName = main
        }
        stages[1].apply {
            sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO
            stage = VK_SHADER_STAGE_FRAGMENT_BIT
            module = fragmentShader
            pName = main
        }
        val vertexInput = alloc<VkPipelineVertexInputStateCreateInfo>().apply { sType = VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO }
        val assembly = alloc<VkPipelineInputAssemblyStateCreateInfo>().apply {
            sType = VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO
            topology = VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST
        }
        val viewport = alloc<VkPipelineViewportStateCreateInfo>().apply {
            sType = VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO
            viewportCount = 1u
            scissorCount = 1u
        }
        val raster = alloc<VkPipelineRasterizationStateCreateInfo>().apply {
            sType = VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO
            polygonMode = VK_POLYGON_MODE_FILL
            cullMode = VK_CULL_MODE_NONE
            lineWidth = 1f
        }
        val multisample = alloc<VkPipelineMultisampleStateCreateInfo>().apply {
            sType = VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO
            rasterizationSamples = VK_SAMPLE_COUNT_1_BIT
        }
        val blendAttachment = alloc<VkPipelineColorBlendAttachmentState>().apply {
            colorWriteMask = VK_COLOR_COMPONENT_R_BIT or VK_COLOR_COMPONENT_G_BIT or VK_COLOR_COMPONENT_B_BIT or VK_COLOR_COMPONENT_A_BIT
        }
        val blend = alloc<VkPipelineColorBlendStateCreateInfo>().apply {
            sType = VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO
            attachmentCount = 1u
            pAttachments = blendAttachment.ptr
        }
        val dynamicStates = allocArray<VkDynamicStateVar>(2)
        dynamicStates[0] = VK_DYNAMIC_STATE_VIEWPORT
        dynamicStates[1] = VK_DYNAMIC_STATE_SCISSOR
        val dynamic = alloc<VkPipelineDynamicStateCreateInfo>().apply {
            sType = VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO
            dynamicStateCount = 2u
            pDynamicStates = dynamicStates
        }
        val info = alloc<VkGraphicsPipelineCreateInfo>().apply {
            sType = VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO
            stageCount = 2u
            pStages = stages
            pVertexInputState = vertexInput.ptr
            pInputAssemblyState = assembly.ptr
            pViewportState = viewport.ptr
            pRasterizationState = raster.ptr
            pMultisampleState = multisample.ptr
            pColorBlendState = blend.ptr
            pDynamicState = dynamic.ptr
            layout = this@VulkanScene.layout
            this.renderPass = renderPass
        }
        val out = alloc<VkPipelineVar>()
        check(vk.createGraphicsPipelines(device, null, 1u, info.ptr, null, out.ptr), "vkCreateGraphicsPipelines")
        out.value
    }

    private fun createTarget(width: Int, height: Int): Target = memScoped {
        val imageInfo = alloc<VkImageCreateInfo>().apply {
            sType = VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO
            imageType = VK_IMAGE_TYPE_2D
            // UNORM: Raven shows the image's values as they are.
            format = VK_FORMAT_R8G8B8A8_UNORM
            extent.width = width.toUInt()
            extent.height = height.toUInt()
            extent.depth = 1u
            mipLevels = 1u
            arrayLayers = 1u
            samples = VK_SAMPLE_COUNT_1_BIT
            tiling = VK_IMAGE_TILING_OPTIMAL
            usage = VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT or VK_IMAGE_USAGE_SAMPLED_BIT
        }
        val image = alloc<VkImageVar>()
        check(vk.createImage(device, imageInfo.ptr, null, image.ptr), "vkCreateImage")

        val requirements = alloc<VkMemoryRequirements>()
        vk.getImageMemoryRequirements(device, image.value, requirements.ptr)
        val properties = alloc<VkPhysicalDeviceMemoryProperties>()
        vk.getPhysicalDeviceMemoryProperties(physicalDevice, properties.ptr)
        val type = (0 until properties.memoryTypeCount.toInt()).firstOrNull { i ->
            (requirements.memoryTypeBits and (1u shl i)) != 0u &&
                (properties.memoryTypes[i].propertyFlags and VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT) != 0u
        } ?: 0
        val allocation = alloc<VkMemoryAllocateInfo>().apply {
            sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO
            allocationSize = requirements.size
            memoryTypeIndex = type.toUInt()
        }
        val memory = alloc<VkDeviceMemoryVar>()
        check(vk.allocateMemory(device, allocation.ptr, null, memory.ptr), "vkAllocateMemory")
        check(vk.bindImageMemory(device, image.value, memory.value, 0u), "vkBindImageMemory")

        val viewInfo = alloc<VkImageViewCreateInfo>().apply {
            sType = VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO
            this.image = image.value
            viewType = VK_IMAGE_VIEW_TYPE_2D
            format = VK_FORMAT_R8G8B8A8_UNORM
            subresourceRange.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT
            subresourceRange.levelCount = 1u
            subresourceRange.layerCount = 1u
        }
        val view = alloc<VkImageViewVar>()
        check(vk.createImageView(device, viewInfo.ptr, null, view.ptr), "vkCreateImageView")

        val views = allocArray<VkImageViewVar>(1)
        views[0] = view.value
        val framebufferInfo = alloc<VkFramebufferCreateInfo>().apply {
            sType = VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO
            renderPass = offscreenPass
            attachmentCount = 1u
            pAttachments = views
            this.width = width.toUInt()
            this.height = height.toUInt()
            layers = 1u
        }
        val framebuffer = alloc<VkFramebufferVar>()
        check(vk.createFramebuffer(device, framebufferInfo.ptr, null, framebuffer.ptr), "vkCreateFramebuffer")
        Target(image.value, memory.value, view.value, framebuffer.value, width, height)
    }

    private fun destroyTarget(target: Target) {
        vk.destroyFramebuffer(device, target.framebuffer, null)
        vk.destroyImageView(device, target.view, null)
        vk.destroyImage(device, target.image, null)
        vk.freeMemory(device, target.memory, null)
    }
}
