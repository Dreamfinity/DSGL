package org.dreamfinity.dsgl.core.dom

import org.dreamfinity.dsgl.core.dom.elements.ButtonNode
import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.portal.input.SurfaceDomInputRouter
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.style.Overflow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OverflowInputClippingTests {
    private val ctx =
        object : UiMeasureContext {
            override val fontHeight: Int = 9

            override fun measureText(text: String): Int = text.length * 6

            override fun paint(commands: List<RenderCommand>) = Unit
        }

    @Test
    fun `generic clipped container rejects pointer input outside viewport`() {
        listOf("app-dom", "app-portal", "system-portal").forEach { layer ->
            val (root, router) = createSurfaceRouter(layer)
            var clicks = 0

            val clippedViewport =
                ContainerNode(key = "$layer-clip").apply {
                    bounds = Rect(20, 20, 100, 40)
                    overflow = Overflow.Hidden
                }
            clippedViewport.applyParent(root)

            val child =
                ButtonNode("child", key = "$layer-child").apply {
                    bounds = Rect(24, 46, 80, 22)
                    onClick { clicks += 1 }
                }
            child.applyParent(clippedViewport)

            assertFalse(router.handleMouseDown(30, 65, MouseButton.LEFT))
            assertFalse(router.handleMouseUp(30, 65, MouseButton.LEFT))
            assertEquals(0, clicks)
        }
    }

    @Test
    fun `generic partial visibility limits pointer interaction to visible area`() {
        val (root, router) = createSurfaceRouter("partial-visible")
        var clicks = 0

        val clippedViewport =
            ContainerNode(key = "partial-clip").apply {
                bounds = Rect(20, 20, 100, 40)
                overflow = Overflow.Hidden
            }
        clippedViewport.applyParent(root)

        val child =
            ButtonNode("child", key = "partial-child").apply {
                bounds = Rect(24, 46, 80, 22)
                onClick { clicks += 1 }
            }
        child.applyParent(clippedViewport)

        assertTrue(router.handleMouseDown(30, 58, MouseButton.LEFT))
        assertTrue(router.handleMouseUp(30, 58, MouseButton.LEFT))
        assertEquals(1, clicks)

        assertFalse(router.handleMouseDown(30, 65, MouseButton.LEFT))
        assertFalse(router.handleMouseUp(30, 65, MouseButton.LEFT))
        assertEquals(1, clicks)
    }

    @Test
    fun `nested clipped containers intersect effective input clip`() {
        val (root, router) = createSurfaceRouter("nested-clip")
        var clicks = 0

        val outer =
            ContainerNode(key = "outer").apply {
                bounds = Rect(10, 10, 130, 90)
                overflow = Overflow.Hidden
            }
        outer.applyParent(root)

        val inner =
            ContainerNode(key = "inner").apply {
                bounds = Rect(20, 20, 90, 40)
                overflow = Overflow.Hidden
            }
        inner.applyParent(outer)

        val child =
            ButtonNode("child", key = "nested-child").apply {
                bounds = Rect(24, 52, 80, 24)
                onClick { clicks += 1 }
            }
        child.applyParent(inner)

        assertTrue(router.handleMouseDown(30, 58, MouseButton.LEFT))
        assertTrue(router.handleMouseUp(30, 58, MouseButton.LEFT))
        assertEquals(1, clicks)

        assertFalse(router.handleMouseDown(30, 68, MouseButton.LEFT))
        assertFalse(router.handleMouseUp(30, 68, MouseButton.LEFT))
        assertEquals(1, clicks)
    }

    @Test
    fun `nested clipped containers clamp interaction to parent child intersection`() {
        val (root, router) = createSurfaceRouter("nested-intersection")
        var clicks = 0

        val outer =
            ContainerNode(key = "outer").apply {
                bounds = Rect(10, 10, 100, 60)
                overflow = Overflow.Hidden
            }
        outer.applyParent(root)

        val inner =
            ContainerNode(key = "inner").apply {
                bounds = Rect(80, 20, 70, 40)
                overflow = Overflow.Hidden
            }
        inner.applyParent(outer)

        val child =
            ButtonNode("child", key = "intersection-child").apply {
                bounds = Rect(84, 24, 60, 24)
                onClick { clicks += 1 }
            }
        child.applyParent(inner)

        // Visible point inside effective intersection.
        assertTrue(router.handleMouseDown(96, 30, MouseButton.LEFT))
        assertTrue(router.handleMouseUp(96, 30, MouseButton.LEFT))
        assertEquals(1, clicks)

        // Inside inner bounds, but outside outer clip intersection.
        assertFalse(router.handleMouseDown(118, 30, MouseButton.LEFT))
        assertFalse(router.handleMouseUp(118, 30, MouseButton.LEFT))
        assertEquals(1, clicks)
    }

    @Test
    fun `paint and pointer clipping stay consistent for clipped containers`() {
        val (root, router) = createSurfaceRouter("paint-input-consistency")
        var clicks = 0

        val clippedViewport =
            ContainerNode(backgroundColor = 0xFF112233.toInt(), key = "clip").apply {
                bounds = Rect(20, 20, 100, 40)
                overflow = Overflow.Hidden
            }
        clippedViewport.applyParent(root)

        val child =
            ButtonNode("child", key = "clip-child").apply {
                bounds = Rect(24, 46, 80, 22)
                onClick { clicks += 1 }
            }
        child.applyParent(clippedViewport)

        val commands = ArrayList<RenderCommand>()
        root.appendRenderCommands(ctx, commands)

        assertTrue(
            commands.any { command ->
                command is RenderCommand.PushClip &&
                    command.x == clippedViewport.bounds.x &&
                    command.y == clippedViewport.bounds.y &&
                    command.width == clippedViewport.bounds.width &&
                    command.height == clippedViewport.bounds.height
            },
        )

        assertTrue(router.handleMouseDown(30, 58, MouseButton.LEFT))
        assertTrue(router.handleMouseUp(30, 58, MouseButton.LEFT))
        assertEquals(1, clicks)

        assertFalse(router.handleMouseDown(30, 65, MouseButton.LEFT))
        assertFalse(router.handleMouseUp(30, 65, MouseButton.LEFT))
        assertEquals(1, clicks)
    }

    private fun createSurfaceRouter(key: String): Pair<ContainerNode, SurfaceDomInputRouter> {
        val root =
            ContainerNode(key = "$key-root").apply {
                bounds = Rect(0, 0, 320, 200)
            }
        return root to SurfaceDomInputRouter { root }
    }
}
