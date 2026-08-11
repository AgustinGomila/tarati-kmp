package com.agustin.tarati.ui.screens.main

import com.agustin.tarati.services.notifications.MessageAction
import com.agustin.tarati.services.notifications.UIMessage
import com.agustin.tarati.services.notifications.UIMessageBus
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class UIMessageBusTest {

    private lateinit var bus: UIMessageBus

    @BeforeTest
    fun setUp() {
        bus = UIMessageBus()
    }

    // ── Toast ─────────────────────────────────────────────────────────────────

    @Test
    fun `toast sends message to channel`(): TestResult = runTest {
        val toast = UIMessage.Toast("Hello")

        bus.toast(toast)

        assertEquals(toast, bus.toasts.receive())
    }

    @Test
    fun `multiple toasts are buffered in order`(): TestResult = runTest {
        val t1 = UIMessage.Toast("first")
        val t2 = UIMessage.Toast("second")
        val t3 = UIMessage.Toast("third")

        bus.toast(t1)
        bus.toast(t2)
        bus.toast(t3)

        assertEquals(t1, bus.toasts.receive())
        assertEquals(t2, bus.toasts.receive())
        assertEquals(t3, bus.toasts.receive())
    }

    @Test
    fun `toast preserves message and duration`(): TestResult = runTest {
        val toast = UIMessage.Toast(message = "rating update", duration = 6.seconds)

        bus.toast(toast)
        val received = bus.toasts.receive()

        assertEquals("rating update", received.message)
        assertEquals(6.seconds, received.duration)
    }

    @Test
    fun `toast preserves actions`(): TestResult = runTest {
        var clicked = false
        val toast = UIMessage.Toast(
            message = "Rematch?",
            actions = listOf(
                MessageAction(
                    label = "Accept",
                    onClick = { clicked = true },
                )
            ),
        )

        bus.toast(toast)
        val received = bus.toasts.receive()

        assertEquals(1, received.actions.size)
        received.actions[0].onClick()
        assertTrue(clicked)
    }

    // ── Alert ─────────────────────────────────────────────────────────────────

    @Test
    fun `alert content is null initially`() {
        assertNull(bus.alertContent.value)
    }

    @Test
    fun `alert sets content`() {
        bus.alert { }

        assertNotNull(bus.alertContent.value)
    }

    @Test
    fun `clearAlert removes content`() {
        bus.alert { }
        assertNotNull(bus.alertContent.value)

        bus.clearAlert()

        assertNull(bus.alertContent.value)
    }

    @Test
    fun `second alert overwrites first`() {
        bus.alert { /* first */ }
        val firstContent = bus.alertContent.value

        bus.alert { /* second */ }
        val secondContent = bus.alertContent.value

        // Both non-null, but they should be different lambda instances
        assertNotNull(firstContent)
        assertNotNull(secondContent)
        assertTrue(firstContent !== secondContent, "Second call must replace first content")
    }
}
