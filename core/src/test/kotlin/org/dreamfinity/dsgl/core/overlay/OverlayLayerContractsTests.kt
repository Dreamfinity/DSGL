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
    fun `paint order is app root then app overlay then system overlay then debug`() {
        assertEquals(
            listOf(UiLayerId.ApplicationRoot, UiLayerId.ApplicationOverlay, UiLayerId.SystemOverlay, UiLayerId.Debug),
            OverlayLayerContracts.paintOrder,
        )
    }

    @Test
    fun `input priority is debug then system overlay then app overlay then app root`() {
        assertEquals(
            listOf(UiLayerId.Debug, UiLayerId.SystemOverlay, UiLayerId.ApplicationOverlay, UiLayerId.ApplicationRoot),
            OverlayLayerContracts.inputPriority,
        )
    }

    @Test
    fun `firstInputConsumer respects configured input priority`() {
        val consumed =
            OverlayLayerContracts.firstInputConsumer(
                canConsume = { layer ->
                    layer == UiLayerId.Debug ||
                        layer == UiLayerId.ApplicationOverlay ||
                        layer == UiLayerId.ApplicationRoot
                },
            )
        assertEquals(UiLayerId.Debug, consumed)
    }

    @Test
    fun `firstInputConsumer returns null when no layer consumes`() {
        val consumed = OverlayLayerContracts.firstInputConsumer(canConsume = { false })
        assertNull(consumed)
    }

    @Test
    fun `transient ownership uses owner scope and not cursor position`() {
        val appAtA =
            OverlayLayerContracts.resolveTransientLayer(
                ownerScope = OverlayOwnerScope.Application,
                cursorX = 10,
                cursorY = 20,
            )
        val appAtB =
            OverlayLayerContracts.resolveTransientLayer(
                ownerScope = OverlayOwnerScope.Application,
                cursorX = 800,
                cursorY = 640,
            )
        val systemAtA =
            OverlayLayerContracts.resolveTransientLayer(
                ownerScope = OverlayOwnerScope.System,
                cursorX = 10,
                cursorY = 20,
            )
        val systemAtB =
            OverlayLayerContracts.resolveTransientLayer(
                ownerScope = OverlayOwnerScope.System,
                cursorX = 800,
                cursorY = 640,
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
        val debug = listOf(RenderCommand.DrawRect(0, 0, 1, 1, 0xFF000004.toInt()))
        val out = ArrayList<RenderCommand>()

        OverlayLayerContracts.composePaintCommands(root, appOverlay, system, debug, out)

        assertEquals(4, out.size)
        assertEquals(0xFF000001.toInt(), (out[0] as RenderCommand.DrawRect).color)
        assertEquals(0xFF000002.toInt(), (out[1] as RenderCommand.DrawRect).color)
        assertEquals(0xFF000003.toInt(), (out[2] as RenderCommand.DrawRect).color)
        assertEquals(0xFF000004.toInt(), (out[3] as RenderCommand.DrawRect).color)
    }

    @Test
    fun `composePaintCommands skips app overlay render when disabled`() {
        val root = listOf(RenderCommand.DrawRect(0, 0, 1, 1, 0xFF000001.toInt()))
        val appOverlay = listOf(RenderCommand.DrawRect(0, 0, 1, 1, 0xFF000002.toInt()))
        val system = listOf(RenderCommand.DrawRect(0, 0, 1, 1, 0xFF000003.toInt()))
        val debug = listOf(RenderCommand.DrawRect(0, 0, 1, 1, 0xFF000004.toInt()))
        val out = ArrayList<RenderCommand>()

        OverlayLayerContracts.composePaintCommands(
            applicationRoot = root,
            applicationOverlay = appOverlay,
            systemOverlay = system,
            debug = debug,
            out = out,
            shouldRenderLayer = { layer -> layer != UiLayerId.ApplicationOverlay },
        )

        assertEquals(
            listOf(0xFF000001.toInt(), 0xFF000003.toInt(), 0xFF000004.toInt()),
            out.map {
                (it as RenderCommand.DrawRect).color
            },
        )
    }

    @Test
    fun `composePaintCommands skips system overlay render when disabled`() {
        val root = listOf(RenderCommand.DrawRect(0, 0, 1, 1, 0xFF000001.toInt()))
        val appOverlay = listOf(RenderCommand.DrawRect(0, 0, 1, 1, 0xFF000002.toInt()))
        val system = listOf(RenderCommand.DrawRect(0, 0, 1, 1, 0xFF000003.toInt()))
        val debug = listOf(RenderCommand.DrawRect(0, 0, 1, 1, 0xFF000004.toInt()))
        val out = ArrayList<RenderCommand>()

        OverlayLayerContracts.composePaintCommands(
            applicationRoot = root,
            applicationOverlay = appOverlay,
            systemOverlay = system,
            debug = debug,
            out = out,
            shouldRenderLayer = { layer -> layer != UiLayerId.SystemOverlay },
        )

        assertEquals(
            listOf(0xFF000001.toInt(), 0xFF000002.toInt(), 0xFF000004.toInt()),
            out.map {
                (it as RenderCommand.DrawRect).color
            },
        )
    }

    @Test
    fun `firstInputConsumer skips app overlay input when disabled`() {
        val order = ArrayList<UiLayerId>()
        val consumed =
            OverlayLayerContracts.firstInputConsumer(
                canConsume = { layer ->
                    order += layer
                    layer == UiLayerId.ApplicationOverlay || layer == UiLayerId.ApplicationRoot
                },
                isLayerInputEnabled = { layer -> layer != UiLayerId.ApplicationOverlay },
            )
        assertEquals(UiLayerId.ApplicationRoot, consumed)
        assertEquals(listOf(UiLayerId.Debug, UiLayerId.SystemOverlay, UiLayerId.ApplicationRoot), order)
    }

    @Test
    fun `firstInputConsumer skips system overlay input when disabled`() {
        val order = ArrayList<UiLayerId>()
        val consumed =
            OverlayLayerContracts.firstInputConsumer(
                canConsume = { layer ->
                    order += layer
                    layer == UiLayerId.SystemOverlay || layer == UiLayerId.ApplicationRoot
                },
                isLayerInputEnabled = { layer -> layer != UiLayerId.SystemOverlay },
            )
        assertEquals(UiLayerId.ApplicationRoot, consumed)
        assertEquals(listOf(UiLayerId.Debug, UiLayerId.ApplicationOverlay, UiLayerId.ApplicationRoot), order)
    }

    @Test
    fun `color picker popup defaults to application overlay ownership`() {
        val request =
            ColorPickerPopupRequest(
                owner = "owner",
                anchorRect = Rect(10, 12, 20, 18),
                state = ColorPickerState(color = RgbaColor.WHITE),
            )
        assertEquals(OverlayOwnerScope.Application, request.ownerScope)
        assertEquals(UiLayerId.ApplicationOverlay, ColorPickerPopupOverlayOwnership.resolveLayer(request))
    }

    @Test
    fun `system-owned color picker popup resolves to system overlay`() {
        val request =
            ColorPickerPopupRequest(
                owner = "owner",
                ownerScope = OverlayOwnerScope.System,
                anchorRect = Rect(10, 12, 20, 18),
                state = ColorPickerState(color = RgbaColor.WHITE),
            )
        assertEquals(UiLayerId.SystemOverlay, ColorPickerPopupOverlayOwnership.resolveLayer(request))
    }
}
