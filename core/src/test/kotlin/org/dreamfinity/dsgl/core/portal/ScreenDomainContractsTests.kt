package org.dreamfinity.dsgl.core.portal

import org.dreamfinity.dsgl.core.colorpicker.ColorPickerPopupRequest
import org.dreamfinity.dsgl.core.colorpicker.ColorPickerState
import org.dreamfinity.dsgl.core.colorpicker.RgbaColor
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.render.RenderCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ScreenDomainContractsTests {
    @Test
    fun `all domains expose root and portal surfaces`() {
        assertEquals(
            listOf(
                ScreenDomainSurfaces.ApplicationRoot,
                ScreenDomainSurfaces.ApplicationPortal,
                ScreenDomainSurfaces.SystemRoot,
                ScreenDomainSurfaces.SystemPortal,
                ScreenDomainSurfaces.DebugRoot,
                ScreenDomainSurfaces.DebugPortal,
            ),
            ScreenDomainSurfaces.allSurfaces,
        )
    }

    @Test
    fun `paint order is application root portal then system root portal then debug root portal`() {
        assertEquals(
            listOf(
                ScreenDomainSurfaces.ApplicationRoot,
                ScreenDomainSurfaces.ApplicationPortal,
                ScreenDomainSurfaces.SystemRoot,
                ScreenDomainSurfaces.SystemPortal,
                ScreenDomainSurfaces.DebugRoot,
                ScreenDomainSurfaces.DebugPortal,
            ),
            ScreenDomainSurfaces.paintOrder,
        )
    }

    @Test
    fun `input priority is reverse domain surface order`() {
        assertEquals(
            listOf(
                ScreenDomainSurfaces.DebugPortal,
                ScreenDomainSurfaces.DebugRoot,
                ScreenDomainSurfaces.SystemPortal,
                ScreenDomainSurfaces.SystemRoot,
                ScreenDomainSurfaces.ApplicationPortal,
                ScreenDomainSurfaces.ApplicationRoot,
            ),
            ScreenDomainSurfaces.inputPriority,
        )
    }

    @Test
    fun `owner scope resolves to compatible portal domain surfaces`() {
        assertEquals(
            ScreenDomainSurfaces.ApplicationPortal,
            ScreenDomainSurfaces.portalSurfaceForDomain(ScreenDomainId.Application),
        )
        assertEquals(
            ScreenDomainSurfaces.SystemPortal,
            ScreenDomainSurfaces.portalSurfaceForDomain(ScreenDomainId.System),
        )
    }

    @Test
    fun `transient ownership uses owner scope and not cursor position`() {
        val appAtA =
            ScreenDomainSurfaces.portalSurfaceForDomain(
                ownerDomain = ScreenDomainId.Application,
                cursorX = 10,
                cursorY = 20,
            )
        val appAtB =
            ScreenDomainSurfaces.portalSurfaceForDomain(
                ownerDomain = ScreenDomainId.Application,
                cursorX = 800,
                cursorY = 640,
            )
        val systemAtA =
            ScreenDomainSurfaces.portalSurfaceForDomain(
                ownerDomain = ScreenDomainId.System,
                cursorX = 10,
                cursorY = 20,
            )
        val systemAtB =
            ScreenDomainSurfaces.portalSurfaceForDomain(
                ownerDomain = ScreenDomainId.System,
                cursorX = 800,
                cursorY = 640,
            )

        assertEquals(ScreenDomainSurfaces.ApplicationPortal, appAtA)
        assertEquals(ScreenDomainSurfaces.ApplicationPortal, appAtB)
        assertEquals(ScreenDomainSurfaces.SystemPortal, systemAtA)
        assertEquals(ScreenDomainSurfaces.SystemPortal, systemAtB)
    }

    @Test
    fun `firstInputConsumer respects configured input priority`() {
        val consumed =
            ScreenDomainSurfaces.firstInputConsumer(
                canConsume = { surface ->
                    surface == ScreenDomainSurfaces.DebugRoot ||
                        surface == ScreenDomainSurfaces.ApplicationPortal ||
                        surface == ScreenDomainSurfaces.ApplicationRoot
                },
            )
        assertEquals(ScreenDomainSurfaces.DebugRoot, consumed)
    }

    @Test
    fun `firstInputConsumer returns null when no surface consumes`() {
        val consumed = ScreenDomainSurfaces.firstInputConsumer(canConsume = { false })
        assertNull(consumed)
    }

    @Test
    fun `composePaintCommands follows configured domain surface order`() {
        val root = listOf(RenderCommand.DrawRect(0, 0, 1, 1, 0xFF000001.toInt()))
        val appPortal = listOf(RenderCommand.DrawRect(0, 0, 1, 1, 0xFF000002.toInt()))
        val systemRoot = listOf(RenderCommand.DrawRect(0, 0, 1, 1, 0xFF000003.toInt()))
        val systemPortal = listOf(RenderCommand.DrawRect(0, 0, 1, 1, 0xFF000004.toInt()))
        val debugRoot = listOf(RenderCommand.DrawRect(0, 0, 1, 1, 0xFF000005.toInt()))
        val debugPortal = listOf(RenderCommand.DrawRect(0, 0, 1, 1, 0xFF000006.toInt()))
        val out = ArrayList<RenderCommand>()

        ScreenDomainSurfaces.composePaintCommands(
            applicationRoot = root,
            applicationPortal = appPortal,
            systemRoot = systemRoot,
            systemPortal = systemPortal,
            debugRoot = debugRoot,
            debugPortal = debugPortal,
            out = out,
        )

        assertEquals(
            listOf(
                0xFF000001.toInt(),
                0xFF000002.toInt(),
                0xFF000003.toInt(),
                0xFF000004.toInt(),
                0xFF000005.toInt(),
                0xFF000006.toInt(),
            ),
            out.map { (it as RenderCommand.DrawRect).color },
        )
    }

    @Test
    fun `composePaintCommands accepts empty root and portal surfaces`() {
        val root = listOf(RenderCommand.DrawRect(0, 0, 1, 1, 0xFF000001.toInt()))
        val systemPortal = listOf(RenderCommand.DrawRect(0, 0, 1, 1, 0xFF000004.toInt()))
        val out = ArrayList<RenderCommand>()

        ScreenDomainSurfaces.composePaintCommands(
            applicationRoot = root,
            applicationPortal = emptyList(),
            systemRoot = emptyList(),
            systemPortal = systemPortal,
            debugRoot = emptyList(),
            debugPortal = emptyList(),
            out = out,
        )

        assertEquals(
            listOf(0xFF000001.toInt(), 0xFF000004.toInt()),
            out.map { (it as RenderCommand.DrawRect).color },
        )
    }

    @Test
    fun `composePaintCommands skips application portal render when disabled`() {
        val root = listOf(RenderCommand.DrawRect(0, 0, 1, 1, 0xFF000001.toInt()))
        val appPortal = listOf(RenderCommand.DrawRect(0, 0, 1, 1, 0xFF000002.toInt()))
        val systemPortal = listOf(RenderCommand.DrawRect(0, 0, 1, 1, 0xFF000003.toInt()))
        val debugRoot = listOf(RenderCommand.DrawRect(0, 0, 1, 1, 0xFF000004.toInt()))
        val out = ArrayList<RenderCommand>()

        ScreenDomainSurfaces.composePaintCommands(
            applicationRoot = root,
            applicationPortal = appPortal,
            systemPortal = systemPortal,
            debugRoot = debugRoot,
            out = out,
            shouldRenderSurface = { surface -> surface != ScreenDomainSurfaces.ApplicationPortal },
        )

        assertEquals(
            listOf(0xFF000001.toInt(), 0xFF000003.toInt(), 0xFF000004.toInt()),
            out.map { (it as RenderCommand.DrawRect).color },
        )
    }

    @Test
    fun `firstInputConsumer skips configured surface input`() {
        val order = ArrayList<ScreenDomainSurface>()
        val consumed =
            ScreenDomainSurfaces.firstInputConsumer(
                canConsume = { surface ->
                    order += surface
                    surface == ScreenDomainSurfaces.ApplicationPortal ||
                        surface == ScreenDomainSurfaces.ApplicationRoot
                },
                isSurfaceInputEnabled = { surface -> surface != ScreenDomainSurfaces.ApplicationPortal },
            )

        assertEquals(ScreenDomainSurfaces.ApplicationRoot, consumed)
        assertEquals(
            listOf(
                ScreenDomainSurfaces.DebugPortal,
                ScreenDomainSurfaces.DebugRoot,
                ScreenDomainSurfaces.SystemPortal,
                ScreenDomainSurfaces.SystemRoot,
                ScreenDomainSurfaces.ApplicationRoot,
            ),
            order,
        )
    }

    @Test
    fun `color picker popup defaults to application portal ownership`() {
        val request =
            ColorPickerPopupRequest(
                owner = "owner",
                anchorRect = Rect(10, 12, 20, 18),
                state = ColorPickerState(color = RgbaColor.WHITE),
            )
        assertEquals(ScreenDomainId.Application, request.ownerDomain)
        assertEquals(ScreenDomainSurfaces.ApplicationPortal, ColorPickerPopupPortalOwnership.resolveSurface(request))
    }

    @Test
    fun `system-owned color picker popup resolves to system portal`() {
        val request =
            ColorPickerPopupRequest(
                owner = "owner",
                ownerDomain = ScreenDomainId.System,
                anchorRect = Rect(10, 12, 20, 18),
                state = ColorPickerState(color = RgbaColor.WHITE),
            )
        assertEquals(ScreenDomainSurfaces.SystemPortal, ColorPickerPopupPortalOwnership.resolveSurface(request))
    }
}
