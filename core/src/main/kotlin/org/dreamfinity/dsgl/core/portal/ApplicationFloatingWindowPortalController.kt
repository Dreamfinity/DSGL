package org.dreamfinity.dsgl.core.portal

import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.applyParent
import org.dreamfinity.dsgl.core.dom.elements.ButtonNode
import org.dreamfinity.dsgl.core.dom.elements.ImageNode
import org.dreamfinity.dsgl.core.dom.elements.TextNode
import org.dreamfinity.dsgl.core.dom.elements.TextSource
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.Size
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.portal.panel.FloatingPanel
import org.dreamfinity.dsgl.core.portal.panel.FloatingPanelDragSession
import org.dreamfinity.dsgl.core.portal.panel.FloatingPanelState
import org.dreamfinity.dsgl.core.portal.panel.FloatingPanelStyle

internal class ApplicationFloatingWindowPortalController {
    private val portalHost: PortalHost = PortalHost(ScreenDomainSurfaces.ApplicationPortal)
    private val entry: ApplicationFloatingWindowPortalEntry = ApplicationFloatingWindowPortalEntry()
    private var viewportWidth: Int = 1
    private var viewportHeight: Int = 1

    init {
        portalHost.register(entry)
    }

    fun toggle(anchorX: Int, anchorY: Int) {
        entry.toggle(anchorX, anchorY, viewportWidth, viewportHeight)
    }

    val open: Boolean
        get() = entry.isOpen()

    fun onInputFrame(viewportWidth: Int, viewportHeight: Int) {
        this.viewportWidth = viewportWidth.coerceAtLeast(1)
        this.viewportHeight = viewportHeight.coerceAtLeast(1)
        portalHost.onInputFrame(PortalFrameContext(Rect(0, 0, this.viewportWidth, this.viewportHeight)))
    }

    fun onFrameCursor(
        viewportWidth: Int,
        viewportHeight: Int,
        mouseX: Int,
        mouseY: Int,
    ) {
        this.viewportWidth = viewportWidth.coerceAtLeast(1)
        this.viewportHeight = viewportHeight.coerceAtLeast(1)
        entry.updateActiveDrag(
            mouseX = mouseX,
            mouseY = mouseY,
            viewportWidth = this.viewportWidth,
            viewportHeight = this.viewportHeight,
        )
    }

    fun sync(rootNode: DOMNode, viewportWidth: Int, viewportHeight: Int) {
        this.viewportWidth = viewportWidth.coerceAtLeast(1)
        this.viewportHeight = viewportHeight.coerceAtLeast(1)
        entry.sync(this.viewportWidth, this.viewportHeight)
        reconcileMountedNode(rootNode)
    }

    fun close() {
        entry.close()
    }

    fun clearRefs() {
        entry.close()
        detachEntry()
    }

    internal fun debugNode(): ApplicationFloatingWindowNode = entry.node

    internal fun debugState(): PortalEntryState = entry.state

    private fun reconcileMountedNode(rootNode: DOMNode) {
        val activeNodes = portalHost.entriesInPaintOrder().mapNotNull { it.node }
        if (entry.node !in activeNodes) {
            detachEntry()
        }
        activeNodes.forEach { node ->
            if (node.parent !== rootNode) {
                node.parent
                    ?.children
                    ?.remove(node)
                node.parent = rootNode
            }
        }
        rootNode.children.removeAll(activeNodes.toSet())
        rootNode.children.addAll(activeNodes)
    }

    private fun detachEntry() {
        entry.node.parent
            ?.children
            ?.remove(entry.node)
        entry.node.parent = null
    }
}

private class ApplicationFloatingWindowPortalEntry : PortalEntry {
    private val panelState: FloatingPanelState = FloatingPanelState()
    private val dragSession: FloatingPanelDragSession = FloatingPanelDragSession()
    private val floatingPanel: FloatingPanel =
        FloatingPanel(
            ownerId = "application.f10-floating-window",
            panelState = panelState,
            dragSession = dragSession,
        )
    override val node: ApplicationFloatingWindowNode =
        ApplicationFloatingWindowNode(
            floatingPanel = floatingPanel,
            onPositionChanged = panelState::updateFromRect,
            onCaptureCancelled = dragSession::end,
        )
    override val state: PortalEntryState =
        PortalEntryState(
            id = PortalEntryId("application.f10-floating-window"),
            ownerToken = this,
            surface = ScreenDomainSurfaces.ApplicationPortal,
            order = PortalEntryOrder(zIndex = 100),
            dismissPolicy = PortalDismissPolicy.None,
            inputPolicy = PortalInputPolicy.DomOnly,
            focusPolicy = PortalFocusPolicy.Preserve,
            insidePointerPolicy = PortalInsidePointerPolicy.ConsumePointerDown,
        )
    private var opened: Boolean = false

    fun toggle(
        anchorX: Int,
        anchorY: Int,
        viewportWidth: Int,
        viewportHeight: Int,
    ) {
        if (opened) {
            close()
            return
        }
        val width = PANEL_WIDTH
        val height = PANEL_HEIGHT
        val maxX = (viewportWidth - width - PANEL_MARGIN).coerceAtLeast(PANEL_MARGIN)
        val maxY = (viewportHeight - height - PANEL_MARGIN).coerceAtLeast(PANEL_MARGIN)
        val x = anchorX.coerceIn(PANEL_MARGIN, maxX)
        val y = anchorY.coerceIn(PANEL_MARGIN, maxY)
        panelState.updateFromRect(Rect(x, y, width, height))
        opened = true
        activate(viewportWidth, viewportHeight)
    }

    fun isOpen(): Boolean = opened

    fun sync(viewportWidth: Int, viewportHeight: Int) {
        if (!opened) {
            panelState.hide()
            dragSession.end()
            state.deactivate()
            return
        }
        floatingPanel.configure(
            title = "Application Portal",
            draggable = true,
            style = FloatingPanelStyle(fontSize = 16),
            onClose = ::close,
        )
        floatingPanel.syncPanelRect(panelState.currentRectOrNull())
        activate(viewportWidth, viewportHeight)
    }

    fun updateActiveDrag(
        mouseX: Int,
        mouseY: Int,
        viewportWidth: Int,
        viewportHeight: Int,
    ) {
        if (!opened) return
        if (!floatingPanel.isDragging()) return
        node.updateActiveDrag(
            mouseX = mouseX,
            mouseY = mouseY,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
        )
        activate(viewportWidth, viewportHeight)
    }

    override fun close() {
        opened = false
        panelState.hide()
        dragSession.end()
        state.deactivate()
    }

    private fun activate(viewportWidth: Int, viewportHeight: Int) {
        val panelRect = panelState.currentRectOrNull() ?: return
        state.activate(
            PortalEntryPlacement(
                anchorBounds = null,
                bounds =
                    PortalEntryBounds(
                        viewportBounds = Rect(0, 0, viewportWidth.coerceAtLeast(1), viewportHeight.coerceAtLeast(1)),
                        entryBounds = panelRect,
                    ),
            ),
        )
    }

    private companion object {
        const val PANEL_WIDTH: Int = 300
        const val PANEL_HEIGHT: Int = 190
        const val PANEL_MARGIN: Int = 2
    }
}

internal class ApplicationFloatingWindowNode(
    private val floatingPanel: FloatingPanel,
    private val onPositionChanged: (Rect) -> Unit,
    private val onCaptureCancelled: () -> Unit,
    key: Any? = "dsgl-application-f10-floating-window",
) : DOMNode(key) {
    override val styleType: String = "dsgl-application-f10-floating-window"

    private val hitTargetNode: DOMNode =
        FloatingWindowPanelHitNode(
            floatingPanel = floatingPanel,
            viewportBoundsProvider = { viewportBounds },
            onPositionChanged = onPositionChanged,
            onCaptureCancelled = onCaptureCancelled,
            onDragUpdated = ::invalidatePanelRenderCommands,
        ).applyParent(this)
    private val panelNode: DOMNode = floatingPanel.node().applyParent(this)
    private val bodyNode: FloatingWindowBodyNode =
        FloatingWindowBodyNode().also(floatingPanel::setBodyContent)
    private var viewportBounds: Rect = Rect(0, 0, 1, 1)

    fun currentButtonClicks(): Int = bodyNode.currentButtonClicks()

    fun buttonRect(): Rect? = bodyNode.buttonRect()

    fun panelRect(): Rect? = floatingPanel.panelRect()

    fun bodyRect(): Rect? = floatingPanel.bodyRect()

    override fun measure(ctx: UiMeasureContext): Size =
        Size(bounds.width.coerceAtLeast(0), bounds.height.coerceAtLeast(0))

    override fun render(
        ctx: UiMeasureContext,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ) {
        viewportBounds = Rect(x, y, width.coerceAtLeast(1), height.coerceAtLeast(1))
        bounds = viewportBounds
        val panelRect = floatingPanel.panelRect()
        if (panelRect == null) {
            hitTargetNode.render(ctx, 0, 0, 0, 0)
        } else {
            hitTargetNode.render(ctx, panelRect.x, panelRect.y, panelRect.width, panelRect.height)
        }
        panelNode.render(ctx, x, y, width, height)
    }

    fun updateActiveDrag(
        mouseX: Int,
        mouseY: Int,
        viewportWidth: Int,
        viewportHeight: Int,
    ) {
        if (
            floatingPanel.handleMouseMove(
                mouseX = mouseX,
                mouseY = mouseY,
                viewportWidth = viewportWidth.coerceAtLeast(1),
                viewportHeight = viewportHeight.coerceAtLeast(1),
                onDragRectChanged = onPositionChanged,
            )
        ) {
            invalidatePanelRenderCommands()
        }
    }

    private fun invalidatePanelRenderCommands() {
        requestRenderCommandsInvalidation()
        hitTargetNode.requestRenderCommandsInvalidation()
        panelNode.requestRenderCommandsInvalidation()
    }
}

private class FloatingWindowPanelHitNode(
    private val floatingPanel: FloatingPanel,
    private val viewportBoundsProvider: () -> Rect,
    private val onPositionChanged: (Rect) -> Unit,
    private val onCaptureCancelled: () -> Unit,
    private val onDragUpdated: () -> Unit,
    key: Any? = "dsgl-application-f10-floating-window-hit-target",
) : DOMNode(key) {
    override val styleType: String = "dsgl-application-f10-floating-window-hit-target"

    init {
        onMouseMove = { it.cancelled = true }
        onMouseDown = { it.cancelled = true }
        onMouseUp = { it.cancelled = true }
        onMouseClick = { it.cancelled = true }
        onMouseWheel = { it.cancelled = true }
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
    }

    override fun shouldCapturePointerDrag(mouseX: Int, mouseY: Int): Boolean {
        val header = floatingPanel.headerRect() ?: return false
        val close = floatingPanel.closeRect()
        return header.contains(mouseX, mouseY) && close?.contains(mouseX, mouseY) != true
    }

    override fun beginPointerCapture(mouseX: Int, mouseY: Int, button: MouseButton) {
        if (button != MouseButton.LEFT) return
        floatingPanel.beginHeaderDrag(mouseX, mouseY)
    }

    override fun continuePointerCapture(
        mouseX: Int,
        mouseY: Int,
        mouseDX: Int,
        mouseDY: Int,
        button: MouseButton,
    ) {
        if (button != MouseButton.LEFT) return
        updateActiveDrag(mouseX, mouseY)
    }

    override fun endPointerCapture(mouseX: Int, mouseY: Int, button: MouseButton) {
        if (button != MouseButton.LEFT) return
        val viewport = viewportBoundsProvider()
        if (
            floatingPanel.handleMouseUp(
                mouseX = mouseX,
                mouseY = mouseY,
                button = button,
                viewportWidth = viewport.width.coerceAtLeast(1),
                viewportHeight = viewport.height.coerceAtLeast(1),
                onDragRectChanged = onPositionChanged,
            )
        ) {
            onDragUpdated()
        }
    }

    override fun cancelPointerCapture() {
        onCaptureCancelled()
    }

    private fun updateActiveDrag(mouseX: Int, mouseY: Int) {
        val viewport = viewportBoundsProvider()
        if (
            floatingPanel.handleMouseMove(
                mouseX = mouseX,
                mouseY = mouseY,
                viewportWidth = viewport.width.coerceAtLeast(1),
                viewportHeight = viewport.height.coerceAtLeast(1),
                onDragRectChanged = onPositionChanged,
            )
        ) {
            onDragUpdated()
        }
    }
}

private class FloatingWindowBodyNode(
    key: Any? = "dsgl-application-f10-floating-window-body",
) : DOMNode(key) {
    override val styleType: String = "dsgl-application-f10-floating-window-body"

    private val titleNode: TextNode =
        TextNode(TextSource.Static("Reusable panel demo"), key = "application-f10-title").applyParent(this)
    private val counterNode: TextNode =
        TextNode(TextSource.Static("Button clicks: 0"), key = "application-f10-counter").applyParent(this)
    private val actionButton: ButtonNode =
        ButtonNode("Click me", key = "application-f10-button")
            .apply {
                onClick {
                    buttonClicks += 1
                    syncCounter()
                    it.cancelled = true
                }
            }.applyParent(this)
    private val imageNode: ImageNode =
        ImageNode(
            url = "minecraft:textures/gui/options_background.png",
            imageWidth = IMAGE_SIZE,
            imageHeight = IMAGE_SIZE,
            key = "application-f10-image",
        ).applyParent(this)
    private val hintNode: TextNode =
        TextNode(TextSource.Static("Drag the title bar to move."), key = "application-f10-hint").applyParent(this)
    private var buttonClicks: Int = 0

    fun currentButtonClicks(): Int = buttonClicks

    fun buttonRect(): Rect? = actionButton.bounds.takeIf { it.width > 0 && it.height > 0 }

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
        titleNode.render(
            ctx,
            bounds.x + CONTENT_PADDING,
            bounds.y + TITLE_TOP,
            bounds.width - IMAGE_COLUMN_WIDTH,
            TEXT_HEIGHT,
        )
        counterNode.render(
            ctx,
            bounds.x + CONTENT_PADDING,
            bounds.y + COUNTER_TOP,
            bounds.width - IMAGE_COLUMN_WIDTH,
            TEXT_HEIGHT,
        )
        actionButton.render(ctx, bounds.x + CONTENT_PADDING, bounds.y + BUTTON_TOP, BUTTON_WIDTH, BUTTON_HEIGHT)
        imageNode.render(ctx, bounds.x + bounds.width - IMAGE_OFFSET, bounds.y + IMAGE_TOP, IMAGE_SIZE, IMAGE_SIZE)
        hintNode.render(
            ctx,
            bounds.x + CONTENT_PADDING,
            bounds.y + HINT_TOP,
            bounds.width - CONTENT_PADDING * 2,
            TEXT_HEIGHT,
        )
    }

    private fun syncCounter() {
        counterNode.setText("Button clicks: $buttonClicks")
    }

    private companion object {
        const val CONTENT_PADDING: Int = 6
        const val TITLE_TOP: Int = 4
        const val COUNTER_TOP: Int = 22
        const val TEXT_HEIGHT: Int = 18
        const val BUTTON_TOP: Int = 44
        const val BUTTON_WIDTH: Int = 120
        const val BUTTON_HEIGHT: Int = 24
        const val IMAGE_SIZE: Int = 44
        const val IMAGE_TOP: Int = 6
        const val IMAGE_OFFSET: Int = 52
        const val IMAGE_COLUMN_WIDTH: Int = 64
        const val HINT_TOP: Int = 78
    }
}
