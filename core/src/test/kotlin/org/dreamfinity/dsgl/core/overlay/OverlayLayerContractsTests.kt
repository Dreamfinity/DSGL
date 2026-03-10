package org.dreamfinity.dsgl.core.overlay

import org.dreamfinity.dsgl.core.render.RenderCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OverlayLayerContractsTests {
    @Test
    fun `paint order is app root then app overlay then system overlay`() {
        assertEquals(
            listOf(UiLayerId.ApplicationRoot, UiLayerId.ApplicationOverlay, UiLayerId.SystemOverlay),
            OverlayLayerContracts.paintOrder
        )
    }

    @Test
    fun `input priority is system overlay then app overlay then app root`() {
        assertEquals(
            listOf(UiLayerId.SystemOverlay, UiLayerId.ApplicationOverlay, UiLayerId.ApplicationRoot),
            OverlayLayerContracts.inputPriority
        )
    }

    @Test
    fun `firstInputConsumer respects configured input priority`() {
        val consumed = OverlayLayerContracts.firstInputConsumer { layer ->
            layer == UiLayerId.ApplicationOverlay || layer == UiLayerId.ApplicationRoot
        }
        assertEquals(UiLayerId.ApplicationOverlay, consumed)
    }

    @Test
    fun `firstInputConsumer returns null when no layer consumes`() {
        val consumed = OverlayLayerContracts.firstInputConsumer { false }
        assertNull(consumed)
    }

    @Test
    fun `transient ownership uses owner world and not cursor position`() {
        val appAtA = OverlayLayerContracts.resolveTransientLayer(
            ownerWorld = OverlayOwnerWorld.Application,
            cursorX = 10,
            cursorY = 20
        )
        val appAtB = OverlayLayerContracts.resolveTransientLayer(
            ownerWorld = OverlayOwnerWorld.Application,
            cursorX = 800,
            cursorY = 640
        )
        val systemAtA = OverlayLayerContracts.resolveTransientLayer(
            ownerWorld = OverlayOwnerWorld.System,
            cursorX = 10,
            cursorY = 20
        )
        val systemAtB = OverlayLayerContracts.resolveTransientLayer(
            ownerWorld = OverlayOwnerWorld.System,
            cursorX = 800,
            cursorY = 640
        )
        assertEquals(UiLayerId.ApplicationOverlay, appAtA)
        assertEquals(UiLayerId.ApplicationOverlay, appAtB)
        assertEquals(UiLayerId.SystemOverlay, systemAtA)
        assertEquals(UiLayerId.SystemOverlay, systemAtB)
    }

    @Test
    fun `composePaintCommands follows configured layer order`() {
        val root = listOf(RenderCommand.DrawRect(0, 0, 1, 1, 0xFF000001.toInt()))
        val appOverlay = listOf(RenderCommand.DrawRect(0, 0, 1, 1, 0xFF000002.toInt()))
        val system = listOf(RenderCommand.DrawRect(0, 0, 1, 1, 0xFF000003.toInt()))
        val out = ArrayList<RenderCommand>()

        OverlayLayerContracts.composePaintCommands(root, appOverlay, system, out)

        assertEquals(3, out.size)
        assertEquals(0xFF000001.toInt(), (out[0] as RenderCommand.DrawRect).color)
        assertEquals(0xFF000002.toInt(), (out[1] as RenderCommand.DrawRect).color)
        assertEquals(0xFF000003.toInt(), (out[2] as RenderCommand.DrawRect).color)
    }
}
