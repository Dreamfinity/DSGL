package org.dreamfinity.dsgl.core.debug

import org.dreamfinity.dsgl.core.DomTree
import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.elements.ButtonNode
import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import org.dreamfinity.dsgl.core.dom.elements.TextNode
import org.dreamfinity.dsgl.core.dom.elements.TextSource
import org.dreamfinity.dsgl.core.dom.layout.Border
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.Size
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.dsl.UiScope
import org.dreamfinity.dsgl.core.dsl.button
import org.dreamfinity.dsgl.core.dsl.div
import org.dreamfinity.dsgl.core.dsl.text
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.portal.DomainSurfaceHost
import org.dreamfinity.dsgl.core.portal.PortalEntry
import org.dreamfinity.dsgl.core.portal.PortalFrameContext
import org.dreamfinity.dsgl.core.portal.PortalHost
import org.dreamfinity.dsgl.core.portal.ScreenDomainSurface
import org.dreamfinity.dsgl.core.portal.ScreenDomainSurfaces
import org.dreamfinity.dsgl.core.portal.input.SurfaceDomInputRouter
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.style.Display
import org.dreamfinity.dsgl.core.style.StyleApplicationScope
import org.dreamfinity.dsgl.core.style.TextWrap
import java.util.Locale

private const val MIN_VIEWPORT_SIZE = 1
private const val PANEL_WIDTH = 300
private const val PANEL_HEIGHT = 232
private const val PANEL_MARGIN = 8
private const val PANEL_MIN_WIDTH = 120
private const val PANEL_MIN_HEIGHT = 96
private const val PANEL_HORIZONTAL_PADDING = 10
private const val TOGGLE_WIDTH = 56
private const val TOGGLE_HEIGHT = 18
private const val FIRST_ROW_OFFSET_Y = 34
private const val ROW_STEP = 24
private const val RESET_BOTTOM_OFFSET = 40
private const val RESET_HEIGHT = 20
private const val SHADOW_OFFSET = 2
private const val TITLE_OFFSET_Y = 8
private const val TITLE_HEIGHT = 18
private const val STATUS_BOTTOM_OFFSET = 14
private const val STATUS_HEIGHT = 14
private const val LABEL_LEFT_PADDING_EXTRA = 6
private const val BUTTON_FONT_SIZE = 14
private const val TITLE_FONT_SIZE = 16

private const val COLOR_SHADOW = 0x5F000000
private const val COLOR_PANEL = 0xEE1A2230.toInt()
private const val COLOR_PANEL_BORDER = 0xFF5A6B80.toInt()
private const val COLOR_TEXT_PRIMARY = 0xFFFFFFFF.toInt()
private const val COLOR_TEXT_MUTED = 0xFFBAC7D6.toInt()
private const val COLOR_LABEL = 0xFFE0E9F2.toInt()
private const val COLOR_RESET_BACKGROUND = 0xFF2E3A49.toInt()
private const val COLOR_RESET_BORDER = 0xFF6886A5.toInt()
private const val COLOR_TOGGLE_ON = 0xFF2F7D4E.toInt()
private const val COLOR_TOGGLE_OFF = 0xFF7A2E3A.toInt()
private const val COLOR_TOGGLE_BORDER = 0xFF9AB3C9.toInt()

internal data class DebugDomainControlLayout(
    val panelRect: Rect,
    val appPortalRenderRect: Rect,
    val appPortalTintRect: Rect,
    val appPortalInputRect: Rect,
    val systemPortalTintRect: Rect,
    val systemPortalRenderRect: Rect,
    val systemPortalInputRect: Rect,
    val resetRect: Rect,
)

private data class DebugDomainToggleSnapshot(
    val applicationPortalRenderEnabled: Boolean,
    val applicationPortalTintEnabled: Boolean,
    val applicationPortalInputEnabled: Boolean,
    val systemPortalRenderEnabled: Boolean,
    val systemPortalTintEnabled: Boolean,
    val systemPortalInputEnabled: Boolean,
)

@Suppress("TooManyFunctions")
class DebugDomainRootHost(
    private val state: DomainSurfaceDebugState = DomainSurfaceDebugState,
) : DomainSurfaceHost {
    override val surface: ScreenDomainSurface = ScreenDomainSurfaces.DebugRoot

    private var viewportWidth: Int = MIN_VIEWPORT_SIZE
    private var viewportHeight: Int = MIN_VIEWPORT_SIZE
    private var layout: DebugDomainControlLayout? = null
    private val rootNode: DebugDomainRootNode = DebugDomainRootNode(state)
    private val tree: DomTree =
        DomTree(
            root = rootNode,
            styleScope = StyleApplicationScope.Debug,
        )
    private val domInputRouter: SurfaceDomInputRouter = SurfaceDomInputRouter { rootNode }
    private var lastToggleSnapshot: DebugDomainToggleSnapshot? = null

    @Suppress("UnusedParameter")
    override fun render(ctx: UiMeasureContext, width: Int, height: Int) {
        viewportWidth = width.coerceAtLeast(MIN_VIEWPORT_SIZE)
        viewportHeight = height.coerceAtLeast(MIN_VIEWPORT_SIZE)
        if (!state.controlsEnabled) {
            layout = null
            lastToggleSnapshot = null
            return
        }
        layout = buildLayout(viewportWidth, viewportHeight)
    }

    override fun paint(ctx: UiMeasureContext): List<RenderCommand> {
        val currentLayout = layout ?: return emptyList()
        val snapshot = state.snapshot()
        val toggleSnapshot = snapshot.toDebugToggleSnapshot()
        if (lastToggleSnapshot != toggleSnapshot) {
            tree.invalidateRenderCommandChunks()
            lastToggleSnapshot = toggleSnapshot
        }
        rootNode.bind(currentLayout, snapshot)
        tree.render(ctx, viewportWidth, viewportHeight)
        return tree.paint(ctx, applyStyles = true)
    }

    override fun handleMouseMove(mouseX: Int, mouseY: Int): Boolean = domInputRouter.handleMouseMove(mouseX, mouseY)

    override fun handleMouseDown(mouseX: Int, mouseY: Int, button: MouseButton): Boolean =
        domInputRouter.handleMouseDown(mouseX, mouseY, button)

    override fun handleMouseUp(mouseX: Int, mouseY: Int, button: MouseButton): Boolean =
        domInputRouter.handleMouseUp(mouseX, mouseY, button)

    override fun handleMouseWheel(mouseX: Int, mouseY: Int, delta: Int): Boolean {
        if (delta == 0) return false
        return domInputRouter.handleMouseWheel(mouseX, mouseY, delta)
    }

    override fun handleKeyDown(keyCode: Int, keyChar: Char): Boolean = domInputRouter.handleKeyDown(keyCode, keyChar)

    override fun handleKeyUp(keyCode: Int, keyChar: Char): Boolean = domInputRouter.handleKeyUp(keyCode, keyChar)

    override fun clearRefs() {
        layout = null
        lastToggleSnapshot = null
        domInputRouter.clear()
        tree.clearRefs()
    }

    internal fun debugLayout(): DebugDomainControlLayout? = layout

    internal val debugStyleScope: StyleApplicationScope
        get() = StyleApplicationScope.Debug

    private fun buildLayout(viewportWidth: Int, viewportHeight: Int): DebugDomainControlLayout {
        val panelX = PANEL_MARGIN
        val panelY = (viewportHeight - PANEL_HEIGHT - PANEL_MARGIN).coerceAtLeast(PANEL_MARGIN)
        val panelRect =
            Rect(
                x = panelX,
                y = panelY,
                width = PANEL_WIDTH.coerceAtMost((viewportWidth - PANEL_MARGIN * 2).coerceAtLeast(PANEL_MIN_WIDTH)),
                height = PANEL_HEIGHT.coerceAtMost((viewportHeight - PANEL_MARGIN * 2).coerceAtLeast(PANEL_MIN_HEIGHT)),
            )
        val toggleX = panelRect.x + panelRect.width - TOGGLE_WIDTH - PANEL_HORIZONTAL_PADDING
        val firstY = panelRect.y + FIRST_ROW_OFFSET_Y
        var row = 0
        return DebugDomainControlLayout(
            panelRect = panelRect,
            appPortalRenderRect = Rect(toggleX, firstY + ROW_STEP * row++, TOGGLE_WIDTH, TOGGLE_HEIGHT),
            appPortalTintRect = Rect(toggleX, firstY + ROW_STEP * row++, TOGGLE_WIDTH, TOGGLE_HEIGHT),
            appPortalInputRect = Rect(toggleX, firstY + ROW_STEP * row++, TOGGLE_WIDTH, TOGGLE_HEIGHT),
            systemPortalRenderRect = Rect(toggleX, firstY + ROW_STEP * row++, TOGGLE_WIDTH, TOGGLE_HEIGHT),
            systemPortalTintRect = Rect(toggleX, firstY + ROW_STEP * row++, TOGGLE_WIDTH, TOGGLE_HEIGHT),
            systemPortalInputRect = Rect(toggleX, firstY + ROW_STEP * row++, TOGGLE_WIDTH, TOGGLE_HEIGHT),
            resetRect =
                Rect(
                    x = panelRect.x + PANEL_HORIZONTAL_PADDING,
                    y = panelRect.y + panelRect.height - RESET_BOTTOM_OFFSET,
                    width = panelRect.width - PANEL_HORIZONTAL_PADDING * 2,
                    height = RESET_HEIGHT,
                ),
        )
    }
}

@Suppress("TooManyFunctions")
class DebugDomainPortalHost : DomainSurfaceHost {
    override val surface: ScreenDomainSurface = ScreenDomainSurfaces.DebugPortal

    private val portalHost: PortalHost = PortalHost(ScreenDomainSurfaces.DebugPortal)
    private var viewportWidth: Int = MIN_VIEWPORT_SIZE
    private var viewportHeight: Int = MIN_VIEWPORT_SIZE

    override fun onInputFrame(viewportWidth: Int, viewportHeight: Int) {
        this.viewportWidth = viewportWidth.coerceAtLeast(MIN_VIEWPORT_SIZE)
        this.viewportHeight = viewportHeight.coerceAtLeast(MIN_VIEWPORT_SIZE)
        portalHost.onInputFrame(PortalFrameContext(Rect(0, 0, this.viewportWidth, this.viewportHeight)))
    }

    override fun render(ctx: UiMeasureContext, width: Int, height: Int) {
        viewportWidth = width.coerceAtLeast(MIN_VIEWPORT_SIZE)
        viewportHeight = height.coerceAtLeast(MIN_VIEWPORT_SIZE)
        portalHost.render(ctx, viewportWidth, viewportHeight)
    }

    override fun paint(ctx: UiMeasureContext): List<RenderCommand> = portalHost.paint(ctx)

    override fun handleMouseMove(mouseX: Int, mouseY: Int): Boolean =
        portalHost.dispatchInput { it.handleMouseMove(mouseX, mouseY) }

    override fun handleMouseDown(mouseX: Int, mouseY: Int, button: MouseButton): Boolean =
        portalHost.dispatchInput { it.handleMouseDown(mouseX, mouseY, button) }

    override fun handleMouseUp(mouseX: Int, mouseY: Int, button: MouseButton): Boolean =
        portalHost.dispatchInput { it.handleMouseUp(mouseX, mouseY, button) }

    override fun handleMouseWheel(mouseX: Int, mouseY: Int, delta: Int): Boolean =
        portalHost.dispatchInput { it.handleMouseWheel(mouseX, mouseY, delta) }

    override fun handleKeyDown(keyCode: Int, keyChar: Char): Boolean =
        portalHost.dispatchInput { it.handleKeyDown(keyCode, keyChar) }

    override fun handleKeyUp(keyCode: Int, keyChar: Char): Boolean =
        portalHost.dispatchInput { it.handleKeyUp(keyCode, keyChar) }

    override fun clearRefs() {
        portalHost.clearRefs()
    }

    internal fun debugRegisterPortalEntryForTests(entry: PortalEntry) {
        portalHost.register(entry)
    }

    internal val debugActivePortalEntryIds: List<String>
        get() = portalHost.entriesInPaintOrder().map { it.state.id.value }
}

private fun DomainSurfaceDebugSnapshot.toDebugToggleSnapshot(): DebugDomainToggleSnapshot =
    DebugDomainToggleSnapshot(
        applicationPortalRenderEnabled = applicationPortalRenderEnabled,
        applicationPortalTintEnabled = applicationPortalTintEnabled,
        applicationPortalInputEnabled = applicationPortalInputEnabled,
        systemPortalRenderEnabled = systemPortalRenderEnabled,
        systemPortalTintEnabled = systemPortalTintEnabled,
        systemPortalInputEnabled = systemPortalInputEnabled,
    )

private class DebugDomainRootNode(
    private val state: DomainSurfaceDebugState,
    key: Any? = "dsgl-debug-domain-root",
) : DOMNode(key) {
    override val styleType: String = "dsgl-debug-domain-root"

    private val scope = UiScope(this)
    private val shadowNode: ContainerNode =
        scope.div({
            this.key = "dsgl-debug-domain-shadow"
        })
    private val panelNode: ContainerNode =
        scope.div({
            this.key = "dsgl-debug-domain-panel"
            onMouseDown = { event ->
                event.cancelled = true
            }
            onMouseWheel = { event ->
                event.cancelled = true
            }
        })
    private val titleNode: TextNode =
        scope.text(props = {
            this.key = "dsgl-debug-domain-title"
            source = TextSource.Static("Debug Domain")
            style = {
                textWrap = TextWrap.NoWrap
            }
        })

    private val appRenderLabelNode: TextNode = labelNode("App Portal Render", "dsgl-debug-domain-label-app-render")
    private val appTintLabelNode: TextNode = labelNode("App Portal Tint", "dsgl-debug-domain-label-app-tint")
    private val appInputLabelNode: TextNode = labelNode("App Portal Input", "dsgl-debug-domain-label-app-input")
    private val systemRenderLabelNode: TextNode =
        labelNode("System Portal Render", "dsgl-debug-domain-label-system-render")
    private val systemTintLabelNode: TextNode = labelNode("System Portal Tint", "dsgl-debug-domain-label-system-tint")
    private val systemInputLabelNode: TextNode =
        labelNode("System Portal Input", "dsgl-debug-domain-label-system-input")

    private val appRenderToggleNode: ButtonNode =
        toggleNode("dsgl-debug-domain-toggle-app-render") {
            state.applicationPortalRenderEnabled = !state.applicationPortalRenderEnabled
        }
    private val appTintToggleNode: ButtonNode =
        toggleNode("dsgl-debug-domain-toggle-app-tint") {
            state.applicationPortalTintEnabled = !state.applicationPortalTintEnabled
        }
    private val appInputToggleNode: ButtonNode =
        toggleNode("dsgl-debug-domain-toggle-app-input") {
            state.applicationPortalInputEnabled = !state.applicationPortalInputEnabled
        }
    private val systemRenderToggleNode: ButtonNode =
        toggleNode("dsgl-debug-domain-toggle-system-render") {
            state.systemPortalRenderEnabled = !state.systemPortalRenderEnabled
        }
    private val systemTintToggleNode: ButtonNode =
        toggleNode("dsgl-debug-domain-toggle-system-tint") {
            state.systemPortalTintEnabled = !state.systemPortalTintEnabled
        }
    private val systemInputToggleNode: ButtonNode =
        toggleNode("dsgl-debug-domain-toggle-system-input") {
            state.systemPortalInputEnabled = !state.systemPortalInputEnabled
        }

    private val resetButtonNode: ButtonNode =
        scope.button("Reset All", {
            this.key = "dsgl-debug-domain-reset"
            onMouseDown = { event ->
                state.resetAll()
                event.cancelled = true
            }
            style = {
                textWrap = TextWrap.NoWrap
            }
        })

    private val statusNode: TextNode =
        scope.text(props = {
            this.key = "dsgl-debug-domain-status"
            source = TextSource.Static("")
            style = {
                textWrap = TextWrap.NoWrap
            }
        })

    private var layout: DebugDomainControlLayout? = null
    private var snapshot: DomainSurfaceDebugSnapshot =
        DomainSurfaceDebugSnapshot(
            applicationPortalRenderEnabled = true,
            applicationPortalTintEnabled = false,
            applicationPortalInputEnabled = true,
            systemPortalRenderEnabled = true,
            systemPortalTintEnabled = false,
            systemPortalInputEnabled = true,
            frameFps = 0,
            frameTimeMs = 0f,
            frameFpsWindow = 0,
            frameTimeWindowMs = 0f,
        )

    fun bind(layout: DebugDomainControlLayout, snapshot: DomainSurfaceDebugSnapshot) {
        this.layout = layout
        this.snapshot = snapshot
    }

    override fun measure(ctx: UiMeasureContext): Size =
        Size(bounds.width.coerceAtLeast(0), bounds.height.coerceAtLeast(0))

    override fun render(
        ctx: UiMeasureContext,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ) {
        bounds = Rect(x, y, width, height)
        val localLayout =
            layout ?: run {
                hideAll(ctx)
                return
            }

        val panelRect = localLayout.panelRect
        val shadowRect =
            Rect(panelRect.x + SHADOW_OFFSET, panelRect.y + SHADOW_OFFSET, panelRect.width, panelRect.height)

        shadowNode.backgroundColor = COLOR_SHADOW
        shadowNode.border = Border.NONE

        panelNode.backgroundColor = COLOR_PANEL
        panelNode.border = Border.all(1, COLOR_PANEL_BORDER)

        titleNode.color = COLOR_TEXT_PRIMARY
        titleNode.fontSize = TITLE_FONT_SIZE

        applyLabelStyle(appRenderLabelNode)
        applyLabelStyle(appTintLabelNode)
        applyLabelStyle(appInputLabelNode)
        applyLabelStyle(systemRenderLabelNode)
        applyLabelStyle(systemTintLabelNode)
        applyLabelStyle(systemInputLabelNode)

        configureToggle(appRenderToggleNode, snapshot.applicationPortalRenderEnabled)
        configureToggle(appTintToggleNode, snapshot.applicationPortalTintEnabled)
        configureToggle(appInputToggleNode, snapshot.applicationPortalInputEnabled)
        configureToggle(systemRenderToggleNode, snapshot.systemPortalRenderEnabled)
        configureToggle(systemTintToggleNode, snapshot.systemPortalTintEnabled)
        configureToggle(systemInputToggleNode, snapshot.systemPortalInputEnabled)

        resetButtonNode.backgroundColor = COLOR_RESET_BACKGROUND
        resetButtonNode.border = Border.all(1, COLOR_RESET_BORDER)
        resetButtonNode.textColor = COLOR_TEXT_PRIMARY
        resetButtonNode.fontSize = BUTTON_FONT_SIZE

        val rApp = if (snapshot.applicationPortalRenderEnabled) "A1" else "A0"
        val rSys = if (snapshot.systemPortalRenderEnabled) "S1" else "S0"
        val iApp = if (snapshot.applicationPortalInputEnabled) "A1" else "A0"
        val iSys = if (snapshot.systemPortalInputEnabled) "S1" else "S0"
        val statusTextValue =
            "R:$rApp/$rSys  I:$iApp/$iSys  " +
                "FPS:${snapshot.frameFps} (${String.format(Locale.US, "%.1f", snapshot.frameTimeMs)}ms)  " +
                "AvgFPS:${snapshot.frameFpsWindow} (${String.format(Locale.US, "%.1f", snapshot.frameTimeWindowMs)}ms)"
        statusNode.setText(statusTextValue)
        statusNode.color = COLOR_TEXT_MUTED
        statusNode.fontSize = BUTTON_FONT_SIZE

        renderNode(ctx, shadowNode, shadowRect)
        renderNode(ctx, panelNode, panelRect)
        renderNode(
            ctx,
            titleNode,
            Rect(
                panelRect.x + PANEL_HORIZONTAL_PADDING,
                panelRect.y + TITLE_OFFSET_Y,
                (panelRect.width - PANEL_HORIZONTAL_PADDING * 2).coerceAtLeast(MIN_VIEWPORT_SIZE),
                TITLE_HEIGHT,
            ),
        )

        renderToggleRow(ctx, panelRect, localLayout.appPortalRenderRect, appRenderLabelNode, appRenderToggleNode)
        renderToggleRow(ctx, panelRect, localLayout.appPortalTintRect, appTintLabelNode, appTintToggleNode)
        renderToggleRow(ctx, panelRect, localLayout.appPortalInputRect, appInputLabelNode, appInputToggleNode)
        renderToggleRow(
            ctx,
            panelRect,
            localLayout.systemPortalRenderRect,
            systemRenderLabelNode,
            systemRenderToggleNode,
        )
        renderToggleRow(ctx, panelRect, localLayout.systemPortalTintRect, systemTintLabelNode, systemTintToggleNode)
        renderToggleRow(ctx, panelRect, localLayout.systemPortalInputRect, systemInputLabelNode, systemInputToggleNode)

        renderNode(ctx, resetButtonNode, localLayout.resetRect)
        renderNode(
            ctx,
            statusNode,
            Rect(
                x = panelRect.x + PANEL_HORIZONTAL_PADDING,
                y = panelRect.y + panelRect.height - STATUS_BOTTOM_OFFSET,
                width = (panelRect.width - PANEL_HORIZONTAL_PADDING * 2).coerceAtLeast(MIN_VIEWPORT_SIZE),
                height = STATUS_HEIGHT,
            ),
        )
    }

    private fun labelNode(text: String, key: Any): TextNode =
        scope.text(props = {
            this.key = key
            source = TextSource.Static(text)
            style = {
                textWrap = TextWrap.NoWrap
            }
        })

    private fun toggleNode(key: Any, onToggle: () -> Unit): ButtonNode =
        scope.button("ON", {
            this.key = key
            onMouseDown = { event ->
                onToggle()
                event.cancelled = true
            }
            style = {
                textWrap = TextWrap.NoWrap
            }
        })

    private fun applyLabelStyle(node: TextNode) {
        node.color = COLOR_LABEL
        node.fontSize = BUTTON_FONT_SIZE
    }

    private fun configureToggle(node: ButtonNode, enabled: Boolean) {
        node.text = if (enabled) "ON" else "OFF"
        node.backgroundColor = if (enabled) COLOR_TOGGLE_ON else COLOR_TOGGLE_OFF
        node.border = Border.all(1, COLOR_TOGGLE_BORDER)
        node.textColor = COLOR_TEXT_PRIMARY
        node.fontSize = BUTTON_FONT_SIZE
    }

    private fun renderToggleRow(
        ctx: UiMeasureContext,
        panelRect: Rect,
        toggleRect: Rect,
        labelNode: TextNode,
        toggleNode: ButtonNode,
    ) {
        val labelRect =
            Rect(
                x = panelRect.x + PANEL_HORIZONTAL_PADDING,
                y = toggleRect.y + SHADOW_OFFSET,
                width =
                    (
                        toggleRect.x -
                            (panelRect.x + PANEL_HORIZONTAL_PADDING + LABEL_LEFT_PADDING_EXTRA)
                    ).coerceAtLeast(MIN_VIEWPORT_SIZE),
                height = (toggleRect.height - SHADOW_OFFSET).coerceAtLeast(MIN_VIEWPORT_SIZE),
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
