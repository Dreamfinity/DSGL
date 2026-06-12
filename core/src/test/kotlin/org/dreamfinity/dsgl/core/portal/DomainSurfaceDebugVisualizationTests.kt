package org.dreamfinity.dsgl.core.portal

import org.dreamfinity.dsgl.core.DomTree
import org.dreamfinity.dsgl.core.debug.DomainSurfaceDebugState
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.portal.system.SystemPortalRootNode
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.style.StyleApplicationScope
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DomainSurfaceDebugVisualizationTests {
    private val ctx =
        object : UiMeasureContext {
            override val fontHeight: Int = 9

            override fun measureText(text: String): Int = text.length * 6

            override fun paint(commands: List<RenderCommand>) = Unit
        }

    @AfterTest
    fun cleanup() {
        DomainSurfaceDebugVisualization.setTestOverride(null)
        DomainSurfaceDebugState.resetAll()
    }

    @Test
    fun `debug visualization disabled by default`() {
        DomainSurfaceDebugState.resetAll()
        DomainSurfaceDebugVisualization.setTestOverride(false)
        val appTree = DomTree(root = ApplicationPortalRootNode(), styleScope = StyleApplicationScope.Application)
        val systemTree = DomTree(root = SystemPortalRootNode(), styleScope = StyleApplicationScope.System)

        appTree.render(ctx, 800, 480)
        systemTree.render(ctx, 800, 480)
        val appCommands = appTree.paint(ctx, applyStyles = true)
        val systemCommands = systemTree.paint(ctx, applyStyles = true)

        assertFalse(
            appCommands.any { command ->
                command is RenderCommand.DrawRect &&
                    (
                        command.color == DomainSurfaceDebugVisualization.applicationPortalFillColor ||
                            command.color == DomainSurfaceDebugVisualization.applicationPortalBorderColor
                    )
            },
        )
        assertFalse(
            systemCommands.any { command ->
                command is RenderCommand.DrawRect &&
                    (
                        command.color == DomainSurfaceDebugVisualization.systemPortalFillColor ||
                            command.color == DomainSurfaceDebugVisualization.systemPortalBorderColor
                    )
            },
        )
    }

    @Test
    fun `debug visualization can be enabled without changing domain-surface logic contracts`() {
        DomainSurfaceDebugState.resetAll()
        DomainSurfaceDebugState.applicationPortalTintEnabled = true
        DomainSurfaceDebugState.systemPortalTintEnabled = true
        DomainSurfaceDebugVisualization.setTestOverride(true)
        val appTree = DomTree(root = ApplicationPortalRootNode(), styleScope = StyleApplicationScope.Application)
        val systemTree = DomTree(root = SystemPortalRootNode(), styleScope = StyleApplicationScope.System)

        appTree.render(ctx, 800, 480)
        systemTree.render(ctx, 800, 480)
        val appCommands = appTree.paint(ctx, applyStyles = true)
        val systemCommands = systemTree.paint(ctx, applyStyles = true)

        assertTrue(
            appCommands.any { command ->
                command is RenderCommand.DrawRect &&
                    (
                        command.color == DomainSurfaceDebugVisualization.applicationPortalFillColor ||
                            command.color == DomainSurfaceDebugVisualization.applicationPortalBorderColor
                    )
            },
        )
        assertTrue(
            systemCommands.any { command ->
                command is RenderCommand.DrawRect &&
                    (
                        command.color == DomainSurfaceDebugVisualization.systemPortalFillColor ||
                            command.color == DomainSurfaceDebugVisualization.systemPortalBorderColor
                    )
            },
        )
    }
}
