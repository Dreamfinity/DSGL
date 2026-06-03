package org.dreamfinity.dsgl.core.portal.system

import org.dreamfinity.dsgl.core.DsglColors
import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.applyParent
import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.Size
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.portal.ApplicationPortalRootNode
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.style.StyleApplicationScope
import org.dreamfinity.dsgl.core.style.StyleEngine
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SystemPortalStyleIsolationTests {
    @AfterTest
    fun cleanup() {
        StyleEngine.setStylesDirectory(null)
        StyleEngine.clearAllInspectorOverrides()
        StyleEngine.clearCache()
    }

    @Test
    fun `system portal scope ignores user stylesheet rules`() {
        val stylesDir =
            createTempStylesDir(
                """
                * { color: #FF5500; }
                probe { color: #00CCAA; }
                .app probe { color: #1133DD; }
                """.trimIndent(),
            )
        StyleEngine.setStylesDirectory(stylesDir)
        StyleEngine.forceReloadStylesheets()

        val appRoot =
            ContainerNode(key = "app-root").apply {
                addClass("app")
            }
        val appProbe = ProbeNode(key = "app-probe").applyParent(appRoot)
        val appPortalRoot = ApplicationPortalRootNode()
        val appPortalProbe = ProbeNode(key = "app-portal-probe").applyParent(appPortalRoot)
        StyleEngine.applyStylesRecursively(appRoot, StyleApplicationScope.Application)
        StyleEngine.applyStylesRecursively(appPortalRoot, StyleApplicationScope.Application)
        assertEquals(0xFF1133DD.toInt(), appProbe.appliedColor)
        assertEquals(0xFF00CCAA.toInt(), appPortalProbe.appliedColor)

        val systemRoot = SystemPortalRootNode()
        val systemProbe = ProbeNode(key = "system-probe").applyParent(systemRoot)
        StyleEngine.applyStylesRecursively(systemRoot, StyleApplicationScope.System)
        assertEquals(DsglColors.TEXT, systemProbe.appliedColor)

        stylesDir.resolve("test.dss").writeText(
            """
            * { color: #33AA55; }
            probe { color: #AA22EE; }
            """.trimIndent(),
        )
        StyleEngine.forceReloadStylesheets()
        StyleEngine.applyStylesRecursively(appRoot, StyleApplicationScope.Application)
        StyleEngine.applyStylesRecursively(appPortalRoot, StyleApplicationScope.Application)
        StyleEngine.applyStylesRecursively(systemRoot, StyleApplicationScope.System)
        assertEquals(0xFFAA22EE.toInt(), appProbe.appliedColor)
        assertEquals(0xFFAA22EE.toInt(), appPortalProbe.appliedColor)
        assertEquals(DsglColors.TEXT, systemProbe.appliedColor)
    }

    @Test
    fun `debug scope ignores user stylesheet rules`() {
        val stylesDir =
            createTempStylesDir(
                """
                * { color: #FF5500; }
                probe { color: #00CCAA; }
                """.trimIndent(),
            )
        StyleEngine.setStylesDirectory(stylesDir)
        StyleEngine.forceReloadStylesheets()

        val appRoot = ContainerNode(key = "app-root")
        val appProbe = ProbeNode(key = "app-probe").applyParent(appRoot)
        val debugRoot = ContainerNode(key = "debug-root")
        val debugProbe = ProbeNode(key = "debug-probe").applyParent(debugRoot)

        StyleEngine.applyStylesRecursively(appRoot, StyleApplicationScope.Application)
        StyleEngine.applyStylesRecursively(debugRoot, StyleApplicationScope.Debug)

        assertEquals(0xFF00CCAA.toInt(), appProbe.appliedColor)
        assertEquals(DsglColors.TEXT, debugProbe.appliedColor)
    }

    private fun createTempStylesDir(dss: String): File {
        val root = Files.createTempDirectory("dsgl-system-style-").toFile()
        root.resolve("test.dss").writeText(dss)
        return root
    }

    private class ProbeNode(
        key: Any?,
    ) : DOMNode(key) {
        override val styleType: String = "probe"
        val defaultColor: Int = 0xFFABCDEF.toInt()
        var appliedColor: Int = defaultColor

        override fun measure(ctx: UiMeasureContext): Size = Size(10, 10)

        override fun render(
            ctx: UiMeasureContext,
            x: Int,
            y: Int,
            width: Int,
            height: Int,
        ) {
            bounds = Rect(x, y, width, height)
        }

        override fun buildRenderCommands(ctx: UiMeasureContext, out: MutableList<RenderCommand>) = Unit

        override fun defaultForegroundColor(): Int = defaultColor

        override fun applyForegroundColor(value: Int) {
            appliedColor = value
        }
    }
}
