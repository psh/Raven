package raven.cinterop

import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/** Kotlin/Native reaches libraven: start it headless, make widgets, stop it, and hold nothing. */
class RavenSmokeTest {
    @Test
    fun startsAndStopsHeadlessWithNothingHeld() = memScoped {
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
        assertEquals(R_TRUE, Rinit_ex(desc.ptr))
        assertEquals("0.2.1", Rget_version()?.toKString())

        val column = Rwidget_create(R_WIDGET_VBOX)
        val label = Rwidget_create(R_WIDGET_LABEL)
        assertNotEquals(R_INVALID_ID, column)
        Rlabel_set_text(label, "Hello from Kotlin")
        Rwidget_insert_child(column, label, 0)
        assertEquals("Hello from Kotlin", Rlabel_get_text(label)?.toKString())
        Rwidget_destroy(column)

        Rshutdown()
        val stats = alloc<RMemoryStats>()
        Rmemory_get_stats(stats.ptr)
        assertEquals(0uL, stats.allocation_count)
    }
}
