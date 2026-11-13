package raven.compose.vulkan

import raven.cinterop.R_INVALID_ID
import raven.compose.ui.graphics.ImageBitmap
import raven.compose.ui.graphics.imageBitmapOf
import raven.cinterop.vulkan.Rvulkan_texture_create_from_image_view
import raven.cinterop.vulkan.Rvulkan_texture_set_image_view

/**
 * A bitmap showing the app's Vulkan image through [imageView] (a raw `VkImageView`), zero copy,
 * sampled with [sampler] (a raw `VkSampler`, or 0 for Raven's linear one). The image must be in
 * `VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL` whenever Raven renders, and outlive the bitmap: destroy
 * it after `close()`, which returns once Raven no longer uses it. Its values are shown as they are,
 * so use a UNORM view of an sRGB image, and clear it with alpha 1 unless it should be see-through.
 * It needs Raven on Vulkan.
 */
public fun ImageBitmap.Companion.fromVulkanImageView(imageView: Long, sampler: Long, width: Int, height: Int): ImageBitmap {
    require(width > 0 && height > 0) { "an image is at least 1 x 1 pixels, not $width x $height" }
    val texture = Rvulkan_texture_create_from_image_view(imageView.toULong(), sampler.toULong(), width, height)
    check(texture != R_INVALID_ID) { "Raven can't show this image view: it needs Raven on Vulkan" }
    return imageBitmapOf(texture)
}

/**
 * Points the bitmap at another image view of the app (after a resize), as [fromVulkanImageView]
 * shows one; it returns once Raven no longer uses the previous view, which can then be destroyed.
 * Every widget showing the bitmap is laid out and drawn again.
 */
public fun ImageBitmap.setVulkanImageView(imageView: Long, sampler: Long, width: Int, height: Int) {
    require(width > 0 && height > 0) { "an image is at least 1 x 1 pixels, not $width x $height" }
    Rvulkan_texture_set_image_view(texture, imageView.toULong(), sampler.toULong(), width, height)
    textureChanged()
}
