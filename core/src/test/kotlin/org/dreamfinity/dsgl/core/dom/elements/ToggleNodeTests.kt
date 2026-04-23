package org.dreamfinity.dsgl.core.dom.elements

import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.dsl.toggle
import org.dreamfinity.dsgl.core.dsl.ui
import org.dreamfinity.dsgl.core.event.EventBus
import org.dreamfinity.dsgl.core.event.FocusManager
import org.dreamfinity.dsgl.core.event.KeyCodes
import org.dreamfinity.dsgl.core.event.KeyboardKeyDownEvent
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.event.MouseClickEvent
import org.dreamfinity.dsgl.core.event.ValueChangedEvent
import org.dreamfinity.dsgl.core.render.RenderCommand
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ToggleNodeTests {
    private val ctx =
        object : UiMeasureContext {
            override fun measureText(text: String): Int = text.length * 6

            override fun measureText(text: String, fontId: String?, fontSize: Int?): Int = text.length * 6

            override val fontHeight: Int = 9

            override fun fontHeight(fontId: String?, fontSize: Int?): Int = 9

            override fun paint(commands: List<RenderCommand>) = Unit
        }

    @AfterTest
    fun cleanup() {
        FocusManager.clearFocus()
    }

    @Test
    fun `uncontrolled toggle changes state on click and emits change`() {
        val node = ToggleNode(defaultChecked = false, key = "toggle.uncontrolled")
        node.render(ctx, 10, 10, 34, 20)
        var emitted: ValueChangedEvent? = null
        node.onValueChange = { event ->
            emitted = event
        }

        val click = MouseClickEvent(mouseX = 16, mouseY = 18, mouseButton = MouseButton.LEFT)
        click.target = node
        EventBus.post(click)

        assertTrue(node.isChecked())
        assertEquals("true", emitted?.value)
        assertEquals(true, emitted?.parsedValue)
    }

    @Test
    fun `controlled toggle emits next value but keeps current state until prop update`() {
        val node = ToggleNode(controlled = true, checked = false, key = "toggle.controlled")
        node.render(ctx, 0, 0, 34, 20)
        var emitted: ValueChangedEvent? = null
        node.onValueChange = { event ->
            emitted = event
        }

        val click = MouseClickEvent(mouseX = 4, mouseY = 8, mouseButton = MouseButton.LEFT)
        click.target = node
        EventBus.post(click)

        assertFalse(node.isChecked())
        assertEquals("true", emitted?.value)
        assertEquals(true, emitted?.parsedValue)

        node.controlledChecked = true
        assertTrue(node.isChecked())
    }

    @Test
    fun `space toggles only when focused`() {
        val node = ToggleNode(defaultChecked = false, key = "toggle.keyboard")
        node.render(ctx, 0, 0, 34, 20)

        val withoutFocus = KeyboardKeyDownEvent(' ', KeyCodes.SPACE)
        withoutFocus.target = node
        EventBus.post(withoutFocus)
        assertFalse(node.isChecked())

        FocusManager.requestFocus(node)
        val withFocus = KeyboardKeyDownEvent(' ', KeyCodes.SPACE)
        withFocus.target = node
        EventBus.post(withFocus)
        assertTrue(node.isChecked())
        assertTrue(withFocus.cancelled)
    }

    @Test
    fun `uncontrolled toggle keeps value after reconcile`() {
        val current =
            ui {
                toggle({
                    key = "toggle.reconcile"
                    defaultChecked = false
                })
            }
        current.render(ctx, 80, 40)
        val retainedBefore =
            current.root.children
                .single() as ToggleNode

        val click = MouseClickEvent(mouseX = 6, mouseY = 6, mouseButton = MouseButton.LEFT)
        click.target = retainedBefore
        EventBus.post(click)
        assertTrue(retainedBefore.isChecked())

        val next =
            ui {
                toggle({
                    key = "toggle.reconcile"
                    defaultChecked = false
                })
            }

        current.reconcileWith(next)
        val retainedAfter =
            current.root.children
                .single() as ToggleNode
        assertSame(retainedBefore, retainedAfter)
        assertTrue(retainedAfter.isChecked())
    }
}
