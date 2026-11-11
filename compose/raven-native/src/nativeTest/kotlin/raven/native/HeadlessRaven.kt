package raven.native

import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import raven.cinterop.RInitDesc
import raven.cinterop.RMemoryStats
import raven.cinterop.R_BACKEND_NULL
import raven.cinterop.R_FALSE
import raven.cinterop.R_TRUE
import raven.cinterop.Rinit_ex
import raven.cinterop.Rmemory_get_stats
import raven.cinterop.Rshutdown
import kotlin.test.assertEquals

/** Runs [block] with Raven started headless on the null backend, then stops it. */
fun withHeadlessRaven(block: () -> Unit) {
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
        assertEquals(R_TRUE, Rinit_ex(desc.ptr), "Rinit_ex")
    }
    try {
        block()
    } finally {
        Rshutdown()
    }
}

/** Blocks Raven holds right now: zero after `Rshutdown`, or it leaked. */
fun ravenBlocksHeld(): ULong = memScoped {
    val stats = alloc<RMemoryStats>()
    Rmemory_get_stats(stats.ptr)
    stats.allocation_count
}
