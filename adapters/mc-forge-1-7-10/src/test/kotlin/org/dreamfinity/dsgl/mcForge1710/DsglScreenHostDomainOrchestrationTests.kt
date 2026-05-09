package org.dreamfinity.dsgl.mcForge1710

import org.dreamfinity.dsgl.core.DomTree
import org.dreamfinity.dsgl.core.DsglWindow
import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import org.dreamfinity.dsgl.core.overlay.UiLayerId
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DsglScreenHostDomainOrchestrationTests {
    @Test
    fun `host domain paint orchestration preserves current render order`() {
        val host = createHost()

        val commands =
            host.debugComposeDomainPaintCommandsForTests(
                applicationRoot = listOf(command(1)),
                applicationPortal = listOf(command(2)),
                systemPortal = listOf(command(3)),
                debugRoot = listOf(command(4)),
            )

        assertEquals(listOf(1, 2, 3, 4), commandColors(commands))
    }

    @Test
    fun `host domain paint orchestration preserves render debug skips`() {
        val host = createHost()

        val commands =
            host.debugComposeDomainPaintCommandsForTests(
                applicationRoot = listOf(command(1)),
                applicationPortal = listOf(command(2)),
                systemPortal = listOf(command(3)),
                debugRoot = listOf(command(4)),
                shouldRenderLayer = { layer -> layer != UiLayerId.ApplicationOverlay },
            )

        assertEquals(listOf(1, 3, 4), commandColors(commands))
    }

    @Test
    fun `host domain input orchestration preserves current priority`() {
        val host = createHost()
        val visited = ArrayList<UiLayerId>()

        val consumed =
            host.debugFirstDomainInputConsumerForTests(
                canConsume = { layer ->
                    visited += layer
                    layer == UiLayerId.ApplicationRoot
                },
            )

        assertEquals(UiLayerId.ApplicationRoot, consumed)
        assertEquals(
            listOf(UiLayerId.Debug, UiLayerId.SystemOverlay, UiLayerId.ApplicationOverlay, UiLayerId.ApplicationRoot),
            visited,
        )
    }

    @Test
    fun `host domain input orchestration blocks lower domains after consumption`() {
        val host = createHost()
        val visited = ArrayList<UiLayerId>()

        val consumed =
            host.debugFirstDomainInputConsumerForTests(
                canConsume = { layer ->
                    visited += layer
                    layer == UiLayerId.SystemOverlay
                },
            )

        assertEquals(UiLayerId.SystemOverlay, consumed)
        assertEquals(listOf(UiLayerId.Debug, UiLayerId.SystemOverlay), visited)
    }

    @Test
    fun `host domain input orchestration preserves debug input disables`() {
        val host = createHost()
        val visited = ArrayList<UiLayerId>()

        val consumed =
            host.debugFirstDomainInputConsumerForTests(
                canConsume = { layer ->
                    visited += layer
                    layer == UiLayerId.Debug || layer == UiLayerId.ApplicationOverlay
                },
                isLayerInputEnabled = { layer -> layer != UiLayerId.Debug },
            )

        assertEquals(UiLayerId.ApplicationOverlay, consumed)
        assertEquals(listOf(UiLayerId.SystemOverlay, UiLayerId.ApplicationOverlay), visited)
    }

    @Test
    fun `host domain input orchestration returns null when no surface consumes`() {
        val host = createHost()

        val consumed = host.debugFirstDomainInputConsumerForTests(canConsume = { false })

        assertNull(consumed)
    }

    private fun createHost(): DsglScreenHost =
        object : DsglScreenHost(
            object : DsglWindow() {
                override fun render(): DomTree = DomTree(ContainerNode(key = "root"))
            },
        ) {}

    private fun command(color: Int): RenderCommand = RenderCommand.DrawRect(0, 0, 1, 1, color)

    private fun commandColors(commands: List<RenderCommand>): List<Int> =
        commands.map { command ->
            (command as RenderCommand.DrawRect).color
        }
}
