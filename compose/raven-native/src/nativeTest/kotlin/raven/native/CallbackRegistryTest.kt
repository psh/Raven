package raven.native

import raven.cinterop.R_WIDGET_LABEL
import raven.cinterop.R_WIDGET_VBOX
import raven.cinterop.Rwidget_create
import raven.cinterop.Rwidget_destroy
import raven.cinterop.Rwidget_insert_child
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

class CallbackRegistryTest {
    private class Target

    @Test
    fun callbackDataLeadsBackToTheTarget() = withHeadlessRaven {
        val target = Target()
        val label = Rwidget_create(R_WIDGET_LABEL)
        val userData = CallbackRegistry.register(label, target)
        assertSame(target, userData.callbackTarget<Target>())
        assertSame(target, CallbackRegistry.targetOf(label))
        Rwidget_destroy(label)
        assertEquals(0, CallbackRegistry.liveCount)
    }

    @Test
    fun destroyingAParentReleasesItsChildren() = withHeadlessRaven {
        val column = Rwidget_create(R_WIDGET_VBOX)
        CallbackRegistry.register(column, Target())
        repeat(3) { index ->
            val label = Rwidget_create(R_WIDGET_LABEL)
            CallbackRegistry.register(label, Target())
            Rwidget_insert_child(column, label, index)
        }
        assertEquals(4, CallbackRegistry.liveCount)
        Rwidget_destroy(column)
        assertEquals(0, CallbackRegistry.liveCount)
    }

    @Test
    fun shutdownReleasesWidgetsStillAlive() {
        withHeadlessRaven {
            CallbackRegistry.register(Rwidget_create(R_WIDGET_LABEL), Target())
            assertEquals(1, CallbackRegistry.liveCount)
        }
        assertEquals(0, CallbackRegistry.liveCount)
        assertEquals(0uL, ravenBlocksHeld())
    }

    @Test
    fun aWidgetIsRegisteredOnlyOnce() = withHeadlessRaven {
        val label = Rwidget_create(R_WIDGET_LABEL)
        CallbackRegistry.register(label, Target())
        assertFailsWith<IllegalStateException> { CallbackRegistry.register(label, Target()) }
        Rwidget_destroy(label)
        assertEquals(0, CallbackRegistry.liveCount)
    }

    @Test
    fun anUnregisteredWidgetHasNoTarget() = withHeadlessRaven {
        val label = Rwidget_create(R_WIDGET_LABEL)
        assertNull(CallbackRegistry.targetOf(label))
        Rwidget_destroy(label)
    }
}
