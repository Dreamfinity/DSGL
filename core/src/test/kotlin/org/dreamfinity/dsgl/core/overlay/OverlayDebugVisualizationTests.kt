package org.dreamfinity.dsgl.core.overlay

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.dreamfinity.dsgl.core.DomTree
import org.dreamfinity.dsgl.core.debug.OverlayLayerDebugState
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.overlay.system.SystemOverlayRootNode
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.style.StyleApplicationScope

class OverlayDebugVisualizationTests {
    private val ctx = object : UiMeasureContext {
        override val fontHeight: Int = 9
        override fun measureText(text: String): Int = text.length * 6
        override fun paint(commands: List<RenderCommand>) = Unit
    }

    @AfterTest
    fun cleanup() {
        OverlayDebugVisualization.setTestOverride(null)
        OverlayLayerDebugState.resetAll()
    }

    @Test
    fun `debug visualization disabled by default`() {
        OverlayLayerDebugState.resetAll()
        OverlayDebugVisualization.setTestOverride(false)
        val appTree = DomTree(root = ApplicationOverlayRootNode(), styleScope = StyleApplicationScope.Application)
        val systemTree = DomTree(root = SystemOverlayRootNode(), styleScope = StyleApplicationScope.SystemOverlay)

        appTree.render(ctx, 800, 480)
        systemTree.render(ctx, 800, 480)
        val appCommands = appTree.paint(ctx, applyStyles = true)
        val systemCommands = systemTree.paint(ctx, applyStyles = true)

        assertFalse(appCommands.any { command ->
            command is RenderCommand.DrawRect &&
                    (command.color == OverlayDebugVisualization.applicationOverlayFillColor ||
                            command.color == OverlayDebugVisualization.applicationOverlayBorderColor)
        })
        assertFalse(systemCommands.any { command ->
            command is RenderCommand.DrawRect &&
                    (command.color == OverlayDebugVisualization.systemOverlayFillColor ||
                            command.color == OverlayDebugVisualization.systemOverlayBorderColor)
        })
    }

    @Test
    fun `debug visualization can be enabled without changing overlay logic contracts`() {
        OverlayLayerDebugState.resetAll()
        OverlayLayerDebugState.applicationOverlayTintEnabled = true
        OverlayLayerDebugState.systemOverlayTintEnabled = true
        OverlayDebugVisualization.setTestOverride(true)
        val appTree = DomTree(root = ApplicationOverlayRootNode(), styleScope = StyleApplicationScope.Application)
        val systemTree = DomTree(root = SystemOverlayRootNode(), styleScope = StyleApplicationScope.SystemOverlay)

        appTree.render(ctx, 800, 480)
        systemTree.render(ctx, 800, 480)
        val appCommands = appTree.paint(ctx, applyStyles = true)
        val systemCommands = systemTree.paint(ctx, applyStyles = true)

        assertTrue(appCommands.any { command ->
            command is RenderCommand.DrawRect &&
                    (command.color == OverlayDebugVisualization.applicationOverlayFillColor ||
                            command.color == OverlayDebugVisualization.applicationOverlayBorderColor)
        })
        assertTrue(systemCommands.any { command ->
            command is RenderCommand.DrawRect &&
                    (command.color == OverlayDebugVisualization.systemOverlayFillColor ||
                            command.color == OverlayDebugVisualization.systemOverlayBorderColor)
        })
    }
}

