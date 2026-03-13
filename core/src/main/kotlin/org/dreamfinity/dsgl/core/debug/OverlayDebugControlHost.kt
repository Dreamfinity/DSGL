package org.dreamfinity.dsgl.core.debug

import org.dreamfinity.dsgl.core.DomTree
import org.dreamfinity.dsgl.core.UiScope
import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.elements.ButtonNode
import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import org.dreamfinity.dsgl.core.dom.elements.TextNode
import org.dreamfinity.dsgl.core.dom.elements.TextSource
import org.dreamfinity.dsgl.core.dom.layout.Border
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.Size
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.style.Display
import org.dreamfinity.dsgl.core.style.StyleApplicationScope
import org.dreamfinity.dsgl.core.style.TextWrap

internal data class OverlayDebugControlLayout(
    val panelRect: Rect,
    val appOverlayRenderRect: Rect,
    val appOverlayTintRect: Rect,
    val appOverlayInputRect: Rect,
    val systemOverlayTintRect: Rect,
    val systemOverlayRenderRect: Rect,
    val systemOverlayInputRect: Rect,
    val resetRect: Rect
)

class OverlayDebugControlHost(
    private val state: OverlayLayerDebugState = OverlayLayerDebugState
) {
    private var viewportWidth: Int = 1
    private var viewportHeight: Int = 1
    private var layout: OverlayDebugControlLayout? = null
    private val rootNode: OverlayDebugControlRootNode = OverlayDebugControlRootNode()
    private val tree: DomTree = DomTree(
        root = rootNode,
        styleScope = StyleApplicationScope.SystemOverlay
    )

    fun render(viewportWidth: Int, viewportHeight: Int) {
        this.viewportWidth = viewportWidth.coerceAtLeast(1)
        this.viewportHeight = viewportHeight.coerceAtLeast(1)
        if (!state.controlsEnabled) {
            layout = null
            return
        }
        layout = buildLayout(this.viewportWidth, this.viewportHeight)
    }

    fun paint(ctx: UiMeasureContext): List<RenderCommand> {
        val currentLayout = layout ?: return emptyList()
        rootNode.bind(currentLayout, state.snapshot())
        tree.render(ctx, viewportWidth, viewportHeight)
        return tree.paint(ctx, applyStyles = true)
    }

    fun handleMouseMove(mouseX: Int, mouseY: Int): Boolean {
        val currentLayout = layout ?: return false
        return currentLayout.panelRect.contains(mouseX, mouseY)
    }

    fun handleMouseDown(mouseX: Int, mouseY: Int, button: MouseButton): Boolean {
        val currentLayout = layout ?: return false
        if (!currentLayout.panelRect.contains(mouseX, mouseY)) {
            return false
        }
        if (button != MouseButton.LEFT) {
            return true
        }
        when {
            currentLayout.appOverlayRenderRect.contains(mouseX, mouseY) -> {
                state.applicationOverlayRenderEnabled = !state.applicationOverlayRenderEnabled
            }

            currentLayout.appOverlayTintRect.contains(mouseX, mouseY) -> {
                state.applicationOverlayTintEnabled = !state.applicationOverlayTintEnabled
            }

            currentLayout.appOverlayInputRect.contains(mouseX, mouseY) -> {
                state.applicationOverlayInputEnabled = !state.applicationOverlayInputEnabled
            }

            currentLayout.systemOverlayRenderRect.contains(mouseX, mouseY) -> {
                state.systemOverlayRenderEnabled = !state.systemOverlayRenderEnabled
            }

            currentLayout.systemOverlayTintRect.contains(mouseX, mouseY) -> {
                state.systemOverlayTintEnabled = !state.systemOverlayTintEnabled
            }

            currentLayout.systemOverlayInputRect.contains(mouseX, mouseY) -> {
                state.systemOverlayInputEnabled = !state.systemOverlayInputEnabled
            }

            currentLayout.resetRect.contains(mouseX, mouseY) -> {
                state.resetAll()
            }
        }
        return true
    }

    fun handleMouseUp(mouseX: Int, mouseY: Int, button: MouseButton): Boolean {
        val currentLayout = layout ?: return false
        if (button != MouseButton.LEFT) return false
        return currentLayout.panelRect.contains(mouseX, mouseY)
    }

    fun handleMouseWheel(mouseX: Int, mouseY: Int, delta: Int): Boolean {
        val currentLayout = layout ?: return false
        if (delta == 0) return false
        return currentLayout.panelRect.contains(mouseX, mouseY)
    }

    fun handleKeyDown(keyCode: Int, keyChar: Char): Boolean = false

    fun clearRefs() {
        layout = null
        tree.clearRefs()
    }

    internal fun debugLayout(): OverlayDebugControlLayout? = layout

    private fun buildLayout(viewportWidth: Int, viewportHeight: Int): OverlayDebugControlLayout {
        val panelWidth = 300
        val panelHeight = 176 + 56
        val panelX = 8
        val panelY = (viewportHeight - panelHeight - 8).coerceAtLeast(8)
        val panelRect = Rect(
            x = panelX,
            y = panelY,
            width = panelWidth.coerceAtMost((viewportWidth - 16).coerceAtLeast(120)),
            height = panelHeight.coerceAtMost((viewportHeight - 16).coerceAtLeast(96))
        )
        val toggleWidth = 56
        val toggleHeight = 18
        val toggleX = panelRect.x + panelRect.width - toggleWidth - 10
        val firstY = panelRect.y + 34
        val rowStep = 24
        var row = 0
        return OverlayDebugControlLayout(
            panelRect = panelRect,
            appOverlayRenderRect = Rect(toggleX, firstY + rowStep * row++, toggleWidth, toggleHeight),
            appOverlayTintRect = Rect(toggleX, firstY + rowStep * row++, toggleWidth, toggleHeight),
            appOverlayInputRect = Rect(toggleX, firstY + rowStep * row++, toggleWidth, toggleHeight),
            systemOverlayRenderRect = Rect(toggleX, firstY + rowStep * row++, toggleWidth, toggleHeight),
            systemOverlayTintRect = Rect(toggleX, firstY + rowStep * row++, toggleWidth, toggleHeight),
            systemOverlayInputRect = Rect(toggleX, firstY + rowStep * row++, toggleWidth, toggleHeight),
            resetRect = Rect(
                x = panelRect.x + 10,
                y = panelRect.y + panelRect.height - 40,
                width = panelRect.width - 20,
                height = 20
            )
        )
    }
}

private class OverlayDebugControlRootNode(
    key: Any? = "dsgl-overlay-debug-root"
) : DOMNode(key) {
    override val styleType: String = "dsgl-overlay-debug-root"

    private val scope = UiScope(this)
    private val shadowNode: ContainerNode = scope.div({
        this.key = "dsgl-overlay-debug-shadow"
    })
    private val panelNode: ContainerNode = scope.div({
        this.key = "dsgl-overlay-debug-panel"
    })
    private val titleNode: TextNode = scope.text(props = {
        this.key = "dsgl-overlay-debug-title"
        source = TextSource.Static("Overlay Debug")
        style = {
            textWrap = TextWrap.NoWrap
        }
    })

    private val appRenderLabelNode: TextNode = labelNode("App Overlay Render", "dsgl-overlay-debug-label-app-render")
    private val appTintLabelNode: TextNode = labelNode("App Overlay Tint", "dsgl-overlay-debug-label-app-tint")
    private val appInputLabelNode: TextNode = labelNode("App Overlay Input", "dsgl-overlay-debug-label-app-input")
    private val systemRenderLabelNode: TextNode = labelNode("System Overlay Render", "dsgl-overlay-debug-label-system-render")
    private val systemTintLabelNode: TextNode = labelNode("System Overlay Tint", "dsgl-overlay-debug-label-system-tint")
    private val systemInputLabelNode: TextNode = labelNode("System Overlay Input", "dsgl-overlay-debug-label-system-input")

    private val appRenderToggleNode: ButtonNode = toggleNode("dsgl-overlay-debug-toggle-app-render")
    private val appTintToggleNode: ButtonNode = toggleNode("dsgl-overlay-debug-toggle-app-tint")
    private val appInputToggleNode: ButtonNode = toggleNode("dsgl-overlay-debug-toggle-app-input")
    private val systemRenderToggleNode: ButtonNode = toggleNode("dsgl-overlay-debug-toggle-system-render")
    private val systemTintToggleNode: ButtonNode = toggleNode("dsgl-overlay-debug-toggle-system-tint")
    private val systemInputToggleNode: ButtonNode = toggleNode("dsgl-overlay-debug-toggle-system-input")

    private val resetButtonNode: ButtonNode = scope.button("Reset All", {
        this.key = "dsgl-overlay-debug-reset"
        style = {
            textWrap = TextWrap.NoWrap
        }
    })

    private var statusTextValue: String = ""
    private val statusNode: TextNode = scope.text(props = {
        this.key = "dsgl-overlay-debug-status"
        source = TextSource.Dynamic { statusTextValue }
        style = {
            textWrap = TextWrap.NoWrap
        }
    })

    private var layout: OverlayDebugControlLayout? = null
    private var snapshot: OverlayLayerDebugSnapshot = OverlayLayerDebugSnapshot(
        applicationOverlayRenderEnabled = true,
        applicationOverlayTintEnabled = false,
        applicationOverlayInputEnabled = true,
        systemOverlayRenderEnabled = true,
        systemOverlayTintEnabled = false,
        systemOverlayInputEnabled = true
    )

    fun bind(layout: OverlayDebugControlLayout, snapshot: OverlayLayerDebugSnapshot) {
        this.layout = layout
        this.snapshot = snapshot
    }

    override fun measure(ctx: UiMeasureContext): Size {
        return Size(bounds.width.coerceAtLeast(0), bounds.height.coerceAtLeast(0))
    }

    override fun render(ctx: UiMeasureContext, x: Int, y: Int, width: Int, height: Int) {
        bounds = Rect(x, y, width, height)
        val localLayout = layout ?: run {
            hideAll(ctx)
            return
        }

        val panelRect = localLayout.panelRect
        val shadowRect = Rect(panelRect.x + 2, panelRect.y + 2, panelRect.width, panelRect.height)

        shadowNode.backgroundColor = 0x5F000000
        shadowNode.border = Border.NONE

        panelNode.backgroundColor = 0xEE1A2230.toInt()
        panelNode.border = Border.all(1, 0xFF5A6B80.toInt())

        titleNode.color = 0xFFFFFFFF.toInt()
        titleNode.fontSize = 16

        applyLabelStyle(appRenderLabelNode)
        applyLabelStyle(appTintLabelNode)
        applyLabelStyle(appInputLabelNode)
        applyLabelStyle(systemRenderLabelNode)
        applyLabelStyle(systemTintLabelNode)
        applyLabelStyle(systemInputLabelNode)

        configureToggle(appRenderToggleNode, snapshot.applicationOverlayRenderEnabled)
        configureToggle(appTintToggleNode, snapshot.applicationOverlayTintEnabled)
        configureToggle(appInputToggleNode, snapshot.applicationOverlayInputEnabled)
        configureToggle(systemRenderToggleNode, snapshot.systemOverlayRenderEnabled)
        configureToggle(systemTintToggleNode, snapshot.systemOverlayTintEnabled)
        configureToggle(systemInputToggleNode, snapshot.systemOverlayInputEnabled)

        resetButtonNode.backgroundColor = 0xFF2E3A49.toInt()
        resetButtonNode.border = Border.all(1, 0xFF6886A5.toInt())
        resetButtonNode.textColor = 0xFFFFFFFF.toInt()
        resetButtonNode.fontSize = 14

        statusTextValue = "R:${if (snapshot.applicationOverlayRenderEnabled) "A1" else "A0"}/${if (snapshot.systemOverlayRenderEnabled) "S1" else "S0"}  I:${if (snapshot.applicationOverlayInputEnabled) "A1" else "A0"}/${if (snapshot.systemOverlayInputEnabled) "S1" else "S0"}"
        statusNode.color = 0xFFBAC7D6.toInt()
        statusNode.fontSize = 14

        renderNode(ctx, shadowNode, shadowRect)
        renderNode(ctx, panelNode, panelRect)
        renderNode(ctx, titleNode, Rect(panelRect.x + 10, panelRect.y + 8, (panelRect.width - 20).coerceAtLeast(1), 18))

        renderToggleRow(ctx, panelRect, localLayout.appOverlayRenderRect, appRenderLabelNode, appRenderToggleNode)
        renderToggleRow(ctx, panelRect, localLayout.appOverlayTintRect, appTintLabelNode, appTintToggleNode)
        renderToggleRow(ctx, panelRect, localLayout.appOverlayInputRect, appInputLabelNode, appInputToggleNode)
        renderToggleRow(ctx, panelRect, localLayout.systemOverlayRenderRect, systemRenderLabelNode, systemRenderToggleNode)
        renderToggleRow(ctx, panelRect, localLayout.systemOverlayTintRect, systemTintLabelNode, systemTintToggleNode)
        renderToggleRow(ctx, panelRect, localLayout.systemOverlayInputRect, systemInputLabelNode, systemInputToggleNode)

        renderNode(ctx, resetButtonNode, localLayout.resetRect)
        renderNode(
            ctx,
            statusNode,
            Rect(
                x = panelRect.x + 10,
                y = panelRect.y + panelRect.height - 14,
                width = (panelRect.width - 20).coerceAtLeast(1),
                height = 14
            )
        )
    }

    private fun labelNode(text: String, key: Any): TextNode {
        return scope.text(props = {
            this.key = key
            source = TextSource.Static(text)
            style = {
                textWrap = TextWrap.NoWrap
            }
        })
    }

    private fun toggleNode(key: Any): ButtonNode {
        return scope.button("ON", {
            this.key = key
            style = {
                textWrap = TextWrap.NoWrap
            }
        })
    }

    private fun applyLabelStyle(node: TextNode) {
        node.color = 0xFFE0E9F2.toInt()
        node.fontSize = 14
    }

    private fun configureToggle(node: ButtonNode, enabled: Boolean) {
        node.text = if (enabled) "ON" else "OFF"
        node.backgroundColor = if (enabled) 0xFF2F7D4E.toInt() else 0xFF7A2E3A.toInt()
        node.border = Border.all(1, 0xFF9AB3C9.toInt())
        node.textColor = 0xFFFFFFFF.toInt()
        node.fontSize = 14
    }

    private fun renderToggleRow(
        ctx: UiMeasureContext,
        panelRect: Rect,
        toggleRect: Rect,
        labelNode: TextNode,
        toggleNode: ButtonNode
    ) {
        val labelRect = Rect(
            x = panelRect.x + 10,
            y = toggleRect.y + 2,
            width = (toggleRect.x - (panelRect.x + 16)).coerceAtLeast(1),
            height = (toggleRect.height - 2).coerceAtLeast(1)
        )
        renderNode(ctx, labelNode, labelRect)
        renderNode(ctx, toggleNode, toggleRect)
    }

    private fun renderNode(ctx: UiMeasureContext, node: DOMNode, rect: Rect?) {
        if (rect == null || rect.width <= 0 || rect.height <= 0) {
            node.display = Display.None
            node.render(ctx, 0, 0, 0, 0)
            return
        }
        node.display = Display.Block
        node.render(ctx, rect.x, rect.y, rect.width, rect.height)
    }

    private fun hideAll(ctx: UiMeasureContext) {
        children.forEach { child ->
            child.display = Display.None
            child.render(ctx, 0, 0, 0, 0)
        }
    }
}