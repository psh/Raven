@file:Suppress("DEPRECATION") // NSOpenGLContext, deprecated since macOS 10.14, still works.

package raven.compose.opengl

import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.set
import kotlinx.cinterop.interpretObjCPointerOrNull
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.rawValue
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.AppKit.NSOpenGLContext
import platform.AppKit.NSOpenGLPFAAccelerated
import platform.AppKit.NSOpenGLPFAAlphaSize
import platform.AppKit.NSOpenGLPFAColorSize
import platform.AppKit.NSOpenGLPFADepthSize
import platform.AppKit.NSOpenGLPFADoubleBuffer
import platform.AppKit.NSOpenGLPFAOpenGLProfile
import platform.AppKit.NSOpenGLPixelFormat
import platform.AppKit.NSOpenGLProfileVersion3_2Core
import platform.AppKit.NSView
import platform.AppKit.wantsBestResolutionOpenGLSurface
import platform.OpenGL3.GL_COLOR_ATTACHMENT0
import platform.OpenGL3.GL_DEPTH24_STENCIL8
import platform.OpenGL3.GL_DEPTH_STENCIL_ATTACHMENT
import platform.OpenGL3.GL_FRAMEBUFFER
import platform.OpenGL3.GL_FRAMEBUFFER_COMPLETE
import platform.OpenGL3.GL_PACK_ALIGNMENT
import platform.OpenGL3.GL_RENDERBUFFER
import platform.OpenGL3.GL_RGBA
import platform.OpenGL3.GL_RGBA8
import platform.OpenGL3.GL_UNSIGNED_BYTE
import platform.OpenGL3.glBindFramebuffer
import platform.OpenGL3.glBindRenderbuffer
import platform.OpenGL3.glCheckFramebufferStatus
import platform.OpenGL3.glDeleteFramebuffers
import platform.OpenGL3.glDeleteRenderbuffers
import platform.OpenGL3.glFinish
import platform.OpenGL3.glFramebufferRenderbuffer
import platform.OpenGL3.glGenFramebuffers
import platform.OpenGL3.glGenRenderbuffers
import platform.OpenGL3.glPixelStorei
import platform.OpenGL3.glReadPixels
import platform.OpenGL3.glRenderbufferStorage
import platform.OpenGL3.glViewport
import raven.compose.ui.NativeWindowHandle
import raven.compose.ui.WindowSystem

private class CocoaGlContext(private val context: NSOpenGLContext) : GlContext {
    override fun makeCurrent() = context.makeCurrentContext()

    override fun update() = context.update()

    override fun flush() = context.flushBuffer()

    override fun close() {
        if (NSOpenGLContext.currentContext == context) NSOpenGLContext.clearCurrentContext()
        context.clearDrawable()
    }
}

/** A 3.2 core context, double-buffered for a view, or single for an offscreen framebuffer. */
private fun newContext(doubleBuffer: Boolean): NSOpenGLContext? = memScoped {
    val attributes = listOfNotNull(
        NSOpenGLPFAOpenGLProfile, NSOpenGLProfileVersion3_2Core,
        NSOpenGLPFAColorSize, 24u, NSOpenGLPFAAlphaSize, 8u, NSOpenGLPFADepthSize, 24u,
        NSOpenGLPFAAccelerated, if (doubleBuffer) NSOpenGLPFADoubleBuffer else null,
        0u,
    )
    val array = allocArray<UIntVar>(attributes.size)
    attributes.forEachIndexed { i, attribute -> array[i] = attribute }
    val format = NSOpenGLPixelFormat(attributes = array)
    format?.let { NSOpenGLContext(format = it, shareContext = null) }
}

internal actual fun glContextOn(handle: NativeWindowHandle): GlContext? {
    if (handle.system != WindowSystem.Cocoa) return null
    val view = handle.view?.let { interpretObjCPointerOrNull<NSView>(it.rawValue) } ?: return null
    val context = newContext(doubleBuffer = true) ?: return null
    // Pixels, not points, as the viewport's size is: the view's backing at its full resolution.
    view.wantsBestResolutionOpenGLSurface = true
    context.view = view
    return CocoaGlContext(context)
}

private class CocoaOffscreenTarget(private val context: NSOpenGLContext, private var width: Int, private var height: Int) : OffscreenTarget {
    private var framebuffer = 0u
    private var color = 0u
    private var depth = 0u

    init {
        create()
    }

    private fun create() = memScoped {
        context.makeCurrentContext()
        val ids = alloc<UIntVar>()
        glGenFramebuffers(1, ids.ptr)
        framebuffer = ids.value
        glGenRenderbuffers(1, ids.ptr)
        color = ids.value
        glGenRenderbuffers(1, ids.ptr)
        depth = ids.value
        glBindFramebuffer(GL_FRAMEBUFFER.toUInt(), framebuffer)
        glBindRenderbuffer(GL_RENDERBUFFER.toUInt(), color)
        glRenderbufferStorage(GL_RENDERBUFFER.toUInt(), GL_RGBA8.toUInt(), width, height)
        glFramebufferRenderbuffer(GL_FRAMEBUFFER.toUInt(), GL_COLOR_ATTACHMENT0.toUInt(), GL_RENDERBUFFER.toUInt(), color)
        glBindRenderbuffer(GL_RENDERBUFFER.toUInt(), depth)
        glRenderbufferStorage(GL_RENDERBUFFER.toUInt(), GL_DEPTH24_STENCIL8.toUInt(), width, height)
        glFramebufferRenderbuffer(GL_FRAMEBUFFER.toUInt(), GL_DEPTH_STENCIL_ATTACHMENT.toUInt(), GL_RENDERBUFFER.toUInt(), depth)
        check(glCheckFramebufferStatus(GL_FRAMEBUFFER.toUInt()) == GL_FRAMEBUFFER_COMPLETE.toUInt()) { "OpenGLTexture: the framebuffer is incomplete" }
    }

    private fun delete() = memScoped {
        context.makeCurrentContext()
        val ids = alloc<UIntVar>()
        ids.value = framebuffer
        glDeleteFramebuffers(1, ids.ptr)
        ids.value = color
        glDeleteRenderbuffers(1, ids.ptr)
        ids.value = depth
        glDeleteRenderbuffers(1, ids.ptr)
    }

    override fun begin() {
        context.makeCurrentContext()
        glBindFramebuffer(GL_FRAMEBUFFER.toUInt(), framebuffer)
        glViewport(0, 0, width, height)
    }

    override fun read(rgba: ByteArray) {
        glFinish()
        glPixelStorei(GL_PACK_ALIGNMENT.toUInt(), 1)
        rgba.usePinned { glReadPixels(0, 0, width, height, GL_RGBA.toUInt(), GL_UNSIGNED_BYTE.toUInt(), it.addressOf(0)) }
        // GL's rows go from the bottom up: turned over, row by row from the top.
        val row = width * 4
        val swap = ByteArray(row)
        for (y in 0 until height / 2) {
            val top = y * row
            val bottom = (height - 1 - y) * row
            rgba.copyInto(swap, 0, top, top + row)
            rgba.copyInto(rgba, top, bottom, bottom + row)
            swap.copyInto(rgba, bottom)
        }
    }

    override fun resize(width: Int, height: Int) {
        delete()
        this.width = width
        this.height = height
        create()
    }

    override fun close() {
        delete()
        if (NSOpenGLContext.currentContext == context) NSOpenGLContext.clearCurrentContext()
    }
}

internal actual fun offscreenTarget(width: Int, height: Int): OffscreenTarget? {
    val context = newContext(doubleBuffer = false) ?: return null
    return CocoaOffscreenTarget(context, width, height)
}
