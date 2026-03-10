package org.dreamfinity.dsgl.core.overlay.panel

import org.dreamfinity.dsgl.core.UiScope
import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.applyParent
import org.dreamfinity.dsgl.core.dom.elements.ButtonNode
import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import org.dreamfinity.dsgl.core.dom.elements.TextNode
import org.dreamfinity.dsgl.core.dom.elements.TextSource
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.Size
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.style.Display
import org.dreamfinity.dsgl.core.style.FlexDirection
import org.dreamfinity.dsgl.core.style.TextWrap

data class OverlayPanelStyle(
    val headerHeight: Int = 26,
    val panelPadding: Int = 6,
    val closeButtonWidth: Int = 16,
    val closeButtonHeight: Int = 16,
    val closeButtonMarginTop: Int = 4,
    val closeButtonMarginRight: Int = 4,
    val panelBackgroundColor: Int = 0xFF1E252F.toInt(),
    val panelBorderColor: Int = 0xFF5A6C80.toInt(),
    val panelShadowColor: Int = 0x7A0C1118,
    val headerBackgroundColor: Int = 0xFF2E3A49.toInt(),
    val headerBorderColor: Int = 0xFF607A95.toInt(),
    val closeButtonBackgroundColor: Int = 0xFF2E3A49.toInt(),
    val closeButtonBorderColor: Int = 0xFF607A95.toInt(),
    val textColor: Int = 0xFFFFFFFF.toInt(),
    val fontSize: Int = 20,
    val closeGlyph: String = "x"
)

data class OverlayPanelFrame(
    val panelRect: Rect,
    val headerRect: Rect,
    val bodyRect: Rect,
    val closeRect: Rect
)

class OverlayPanel(
    private val ownerId: Any,
    private val panelState: OverlayPanelState,
    private val dragSession: OverlayPanelDragSession,
    private var style: OverlayPanelStyle = OverlayPanelStyle()
) {
    private val rootNode: OverlayPanelRootNode = OverlayPanelRootNode(
        owner = this,
        key = "dsgl-overlay-panel-$ownerId"
    )
    private val shadowNode: ContainerNode
    private val panelNode: ContainerNode
    private val headerNode: ContainerNode
    private var titleNode: TextNode
    private val closeButtonNode: ButtonNode
    private var bodyContentNode: DOMNode? = null
    private var overlayContentNode: DOMNode? = null
    var title: String = ""
        private set
    var draggable: Boolean = true
        private set
    private var onClose: (() -> Unit)? = null
    private var frame: OverlayPanelFrame? = null

    init {
        val scope = UiScope(rootNode)
        shadowNode = scope.div({
            key = "dsgl-overlay-panel-shadow-$ownerId"
            style = {
                display = Display.Block
            }
        })
        panelNode = scope.div({
            key = "dsgl-overlay-panel-frame-$ownerId"
            style = {
                display = Display.Block
            }
        })
        headerNode = scope.div({
            key = "dsgl-overlay-panel-header-$ownerId"
            style = {
                display = Display.Flex
                flexDirection = FlexDirection.Row
            }
        })
        titleNode = scope.text(props = {
            key = "dsgl-overlay-panel-title-$ownerId"
            value = ""
            style = {
                textWrap = TextWrap.NoWrap
            }
        })
        closeButtonNode = scope.button(style.closeGlyph, {
            key = "dsgl-overlay-panel-close-$ownerId"
            onMouseClick = {
                onClose?.invoke()
                it.cancelled = true
            }
        })
        applyStyleToNodes(style)
        rebuildFrameFromState()
    }

    fun node(): DOMNode = rootNode

    fun setBodyContent(node: DOMNode?) {
        if (bodyContentNode === node) return
        detachNode(bodyContentNode)
        bodyContentNode = node
        attachNodeBeforeOverlay(node)
    }

    fun setOverlayContent(node: DOMNode?) {
        if (overlayContentNode === node) return
        detachNode(overlayContentNode)
        overlayContentNode = node
        attachNodeAtTop(node)
    }

    fun configure(
        title: String,
        draggable: Boolean,
        style: OverlayPanelStyle = this.style,
        onClose: (() -> Unit)? = this.onClose
    ) {
        val titleChanged = this.title != title
        val styleChanged = this.style != style
        this.title = title
        this.draggable = draggable
        this.style = style
        this.onClose = onClose
        if (titleChanged) {
            replaceTitleNode()
        }
        if (styleChanged) {
            applyStyleToNodes(style)
        }
        closeButtonNode.text = style.closeGlyph
        rebuildFrameFromState()
    }

    fun syncPanelRect(panelRect: Rect?) {
        if (panelRect == null) {
            panelState.hide()
            frame = null
            return
        }
        panelState.updateFromRect(panelRect)
        rebuildFrameFromState()
    }

    fun panelRect(): Rect? = frame?.panelRect

    fun headerRect(): Rect? = frame?.headerRect

    fun closeRect(): Rect? = frame?.closeRect

    fun bodyRect(): Rect? = frame?.bodyRect

    fun handleMouseDown(mouseX: Int, mouseY: Int, button: MouseButton): Boolean {
        val localFrame = frame ?: return false
        if (button != MouseButton.LEFT) return false
        if (localFrame.closeRect.contains(mouseX, mouseY)) {
            onClose?.invoke()
            return true
        }
        if (!draggable) return false
        if (!localFrame.headerRect.contains(mouseX, mouseY)) return false
        dragSession.begin(
            ownerId = ownerId,
            type = OverlayPanelDragType.PanelMove,
            pointerX = mouseX,
            pointerY = mouseY,
            panelState = panelState
        )
        return true
    }

    fun handleMouseMove(
        mouseX: Int,
        mouseY: Int,
        viewportWidth: Int,
        viewportHeight: Int,
        onDragRectChanged: (Rect) -> Unit
    ): Boolean {
        if (!dragSession.active) return false
        dragSession.update(mouseX, mouseY)
        val rect = buildDraggedRect(viewportWidth, viewportHeight)
        panelState.updateFromRect(rect)
        rebuildFrameFromState()
        onDragRectChanged(rect)
        return true
    }

    fun handleMouseUp(
        mouseX: Int,
        mouseY: Int,
        button: MouseButton,
        viewportWidth: Int,
        viewportHeight: Int,
        onDragRectChanged: (Rect) -> Unit
    ): Boolean {
        if (button != MouseButton.LEFT || !dragSession.active) return false
        dragSession.update(mouseX, mouseY)
        val rect = buildDraggedRect(viewportWidth, viewportHeight)
        panelState.updateFromRect(rect)
        rebuildFrameFromState()
        onDragRectChanged(rect)
        dragSession.end()
        return true
    }

    private fun buildDraggedRect(viewportWidth: Int, viewportHeight: Int): Rect {
        val dx = dragSession.currentPointerX - dragSession.startPointerX
        val dy = dragSession.currentPointerY - dragSession.startPointerY
        val raw = Rect(
            x = dragSession.startPanelX + dx,
            y = dragSession.startPanelY + dy,
            width = dragSession.startPanelWidth,
            height = dragSession.startPanelHeight
        )
        return clampPanel(raw, viewportWidth, viewportHeight)
    }

    private fun rebuildFrameFromState() {
        val panelRect = panelState.currentRectOrNull()
        frame = if (panelRect == null) {
            null
        } else {
            buildFrame(panelRect)
        }
    }

    private fun buildFrame(panelRect: Rect): OverlayPanelFrame {
        val headerRect = Rect(panelRect.x, panelRect.y, panelRect.width, style.headerHeight)
        val bodyRect = Rect(
            x = panelRect.x + style.panelPadding,
            y = panelRect.y + style.headerHeight + style.panelPadding,
            width = (panelRect.width - style.panelPadding * 2).coerceAtLeast(1),
            height = (panelRect.height - style.headerHeight - style.panelPadding * 2).coerceAtLeast(1)
        )
        val closeRect = Rect(
            x = panelRect.x + panelRect.width - style.closeButtonMarginRight - style.closeButtonWidth,
            y = panelRect.y + style.closeButtonMarginTop,
            width = style.closeButtonWidth,
            height = style.closeButtonHeight
        )
        return OverlayPanelFrame(
            panelRect = panelRect,
            headerRect = headerRect,
            bodyRect = bodyRect,
            closeRect = closeRect
        )
    }

    private fun clampPanel(rect: Rect, viewportWidth: Int, viewportHeight: Int): Rect {
        val minX = 2
        val minY = 2
        val maxX = (viewportWidth - rect.width - 2).coerceAtLeast(2)
        val maxY = (viewportHeight - rect.height - 2).coerceAtLeast(2)
        return Rect(
            x = rect.x.coerceIn(minX, maxX),
            y = rect.y.coerceIn(minY, maxY),
            width = rect.width,
            height = rect.height
        )
    }

    private fun applyStyleToNodes(style: OverlayPanelStyle) {
        shadowNode.applyStyle {
            backgroundColor = style.panelShadowColor
            border(0.px)
        }
        panelNode.applyStyle {
            backgroundColor = style.panelBackgroundColor
            border(1.px, style.panelBorderColor)
        }
        headerNode.applyStyle {
            backgroundColor = style.headerBackgroundColor
            border(1.px, style.headerBorderColor)
        }
        titleNode.applyStyle {
            color = style.textColor
            fontSize(style.fontSize.px)
            textWrap = TextWrap.NoWrap
        }
        closeButtonNode.applyStyle {
            backgroundColor = style.closeButtonBackgroundColor
            border(1.px, style.closeButtonBorderColor)
            color = style.textColor
            fontSize(style.fontSize.px)
            width = style.closeButtonWidth.px
            height = style.closeButtonHeight.px
            padding = 0.px
        }
    }

    private fun replaceTitleNode() {
        val oldNode = titleNode
        val oldIndex = rootNode.children.indexOf(oldNode)
        detachNode(oldNode)
        val newNode = TextNode(
            textSource = TextSource.Static(title),
            key = "dsgl-overlay-panel-title-$ownerId"
        )
        newNode.applyStyle {
            textWrap = TextWrap.NoWrap
        }
        titleNode = newNode
        if (oldIndex >= 0 && oldIndex <= rootNode.children.size) {
            rootNode.children.add(oldIndex, newNode)
            newNode.parent = rootNode
        } else {
            newNode.applyParent(rootNode)
        }
        applyStyleToNodes(style)
    }

    private fun detachNode(node: DOMNode?) {
        val local = node ?: return
        val parent = local.parent
        if (parent != null) {
            parent.children.remove(local)
            local.parent = null
        }
    }

    private fun attachNodeBeforeOverlay(node: DOMNode?) {
        val local = node ?: return
        detachNode(local)
        val overlay = overlayContentNode
        if (overlay != null && overlay.parent === rootNode) {
            val overlayIndex = rootNode.children.indexOf(overlay)
            if (overlayIndex >= 0) {
                rootNode.children.add(overlayIndex, local)
                local.parent = rootNode
                return
            }
        }
        local.applyParent(rootNode)
    }

    private fun attachNodeAtTop(node: DOMNode?) {
        val local = node ?: return
        detachNode(local)
        local.applyParent(rootNode)
    }

    private fun renderInto(ctx: UiMeasureContext, viewportRect: Rect) {
        val localFrame = frame
        if (localFrame == null) {
            shadowNode.render(ctx, 0, 0, 0, 0)
            panelNode.render(ctx, 0, 0, 0, 0)
            headerNode.render(ctx, 0, 0, 0, 0)
            titleNode.render(ctx, 0, 0, 0, 0)
            closeButtonNode.render(ctx, 0, 0, 0, 0)
            bodyContentNode?.render(ctx, 0, 0, 0, 0)
            overlayContentNode?.render(ctx, 0, 0, 0, 0)
            return
        }

        val panelRect = localFrame.panelRect
        val headerRect = localFrame.headerRect
        val closeRect = localFrame.closeRect
        val bodyRect = localFrame.bodyRect
        val titleX = headerRect.x + 6
        val titleY = headerRect.y + 3
        val titleWidth = (closeRect.x - titleX - 4).coerceAtLeast(1)
        val titleHeight = (headerRect.height - 6).coerceAtLeast(1)

        shadowNode.render(
            ctx,
            panelRect.x + 2,
            panelRect.y + 2,
            panelRect.width,
            panelRect.height
        )
        panelNode.render(
            ctx,
            panelRect.x,
            panelRect.y,
            panelRect.width,
            panelRect.height
        )
        headerNode.render(
            ctx,
            headerRect.x,
            headerRect.y,
            headerRect.width,
            headerRect.height
        )
        titleNode.render(
            ctx,
            titleX,
            titleY,
            titleWidth,
            titleHeight
        )
        closeButtonNode.render(
            ctx,
            closeRect.x,
            closeRect.y,
            closeRect.width,
            closeRect.height
        )
        bodyContentNode?.render(
            ctx,
            bodyRect.x,
            bodyRect.y,
            bodyRect.width,
            bodyRect.height
        )
        overlayContentNode?.render(
            ctx,
            viewportRect.x,
            viewportRect.y,
            viewportRect.width,
            viewportRect.height
        )
    }

    private class OverlayPanelRootNode(
        private val owner: OverlayPanel,
        key: Any?
    ) : DOMNode(key) {
        override val styleType: String = "dsgl-overlay-panel"

        override fun measure(ctx: UiMeasureContext): Size {
            return Size(bounds.width.coerceAtLeast(0), bounds.height.coerceAtLeast(0))
        }

        override fun render(ctx: UiMeasureContext, x: Int, y: Int, width: Int, height: Int) {
            bounds = Rect(x, y, width, height)
            owner.renderInto(ctx, bounds)
        }
    }
}
