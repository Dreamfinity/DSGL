package org.dreamfinity.dsgl.core.overlay.system

import org.dreamfinity.dsgl.core.DsglColors
import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.applyParent
import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.Size
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.overlay.ApplicationOverlayRootNode
import org.dreamfinity.dsgl.core.style.StyleApplicationScope
import org.dreamfinity.dsgl.core.style.StyleEngine
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SystemOverlayStyleIsolationTests {
    @AfterTest
    fun cleanup() {
        StyleEngine.setStylesDirectory(null)
        StyleEngine.clearAllInspectorOverrides()
        StyleEngine.clearCache()
    }

    @Test
    fun `system overlay scope ignores user stylesheet rules`() {
        val stylesDir = createTempStylesDir(
            """
            * { color: #FF5500; }
            probe { color: #00CCAA; }
            .app probe { color: #1133DD; }
            """.trimIndent()
        )
        StyleEngine.setStylesDirectory(stylesDir)
        StyleEngine.forceReloadStylesheets()

        val appRoot = ContainerNode(key = "app-root").apply {
            addClass("app")
        }
        val appProbe = ProbeNode(key = "app-probe").applyParent(appRoot)
        val appOverlayRoot = ApplicationOverlayRootNode()
        val appOverlayProbe = ProbeNode(key = "app-overlay-probe").applyParent(appOverlayRoot)
        StyleEngine.applyStylesRecursively(appRoot, StyleApplicationScope.Application)
        StyleEngine.applyStylesRecursively(appOverlayRoot, StyleApplicationScope.Application)
        assertEquals(0xFF1133DD.toInt(), appProbe.appliedColor)
        assertEquals(0xFF00CCAA.toInt(), appOverlayProbe.appliedColor)

        val systemRoot = SystemOverlayRootNode()
        val systemProbe = ProbeNode(key = "system-probe").applyParent(systemRoot)
        StyleEngine.applyStylesRecursively(systemRoot, StyleApplicationScope.SystemOverlay)
        assertEquals(DsglColors.TEXT, systemProbe.appliedColor)

        stylesDir.resolve("test.dss").writeText(
            """
            * { color: #33AA55; }
            probe { color: #AA22EE; }
            """.trimIndent()
        )
        StyleEngine.forceReloadStylesheets()
        StyleEngine.applyStylesRecursively(appRoot, StyleApplicationScope.Application)
        StyleEngine.applyStylesRecursively(appOverlayRoot, StyleApplicationScope.Application)
        StyleEngine.applyStylesRecursively(systemRoot, StyleApplicationScope.SystemOverlay)
        assertEquals(0xFFAA22EE.toInt(), appProbe.appliedColor)
        assertEquals(0xFFAA22EE.toInt(), appOverlayProbe.appliedColor)
        assertEquals(DsglColors.TEXT, systemProbe.appliedColor)
    }

    private fun createTempStylesDir(dss: String): File {
        val root = Files.createTempDirectory("dsgl-system-style-").toFile()
        root.resolve("test.dss").writeText(dss)
        return root
    }

    private class ProbeNode(
        key: Any?
    ) : DOMNode(key) {
        override val styleType: String = "probe"
        val defaultColor: Int = 0xFFABCDEF.toInt()
        var appliedColor: Int = defaultColor

        override fun measure(ctx: UiMeasureContext): Size = Size(10, 10)

        override fun render(ctx: UiMeasureContext, x: Int, y: Int, width: Int, height: Int) {
            bounds = Rect(x, y, width, height)
        }

        override fun buildRenderCommands(
            ctx: UiMeasureContext,
            out: MutableList<org.dreamfinity.dsgl.core.render.RenderCommand>
        ) = Unit

        override fun defaultForegroundColor(): Int = defaultColor

        override fun applyForegroundColor(value: Int) {
            appliedColor = value
        }
    }
}
