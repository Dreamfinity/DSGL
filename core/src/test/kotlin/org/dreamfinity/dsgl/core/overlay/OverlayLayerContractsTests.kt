package org.dreamfinity.dsgl.core.overlay

import org.dreamfinity.dsgl.core.colorpicker.ColorPickerPopupRequest
import org.dreamfinity.dsgl.core.colorpicker.ColorPickerState
import org.dreamfinity.dsgl.core.colorpicker.RgbaColor
import org.dreamfinity.dsgl.core.dom.layout.Rect
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
    fun `transient ownership uses owner scope and not cursor position`() {
        val appAtA = OverlayLayerContracts.resolveTransientLayer(
            ownerScope = OverlayOwnerScope.Application,
            cursorX = 10,
            cursorY = 20
        )
        val appAtB = OverlayLayerContracts.resolveTransientLayer(
            ownerScope = OverlayOwnerScope.Application,
            cursorX = 800,
            cursorY = 640
        )
        val systemAtA = OverlayLayerContracts.resolveTransientLayer(
            ownerScope = OverlayOwnerScope.System,
            cursorX = 10,
            cursorY = 20
        )
        val systemAtB = OverlayLayerContracts.resolveTransientLayer(
            ownerScope = OverlayOwnerScope.System,
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

    @Test
    fun `color picker popup defaults to application overlay ownership`() {
        val request = ColorPickerPopupRequest(
            owner = "owner",
            anchorRect = Rect(10, 12, 20, 18),
            state = ColorPickerState(color = RgbaColor.WHITE)
        )
        assertEquals(OverlayOwnerScope.Application, request.ownerScope)
        assertEquals(UiLayerId.ApplicationOverlay, ColorPickerPopupOverlayOwnership.resolveLayer(request))
    }

    @Test
    fun `system-owned color picker popup resolves to system overlay`() {
        val request = ColorPickerPopupRequest(
            owner = "owner",
            ownerScope = OverlayOwnerScope.System,
            anchorRect = Rect(10, 12, 20, 18),
            state = ColorPickerState(color = RgbaColor.WHITE)
        )
        assertEquals(UiLayerId.SystemOverlay, ColorPickerPopupOverlayOwnership.resolveLayer(request))
    }
}
