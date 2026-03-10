package org.dreamfinity.dsgl.core.overlay.panel

import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.render.RenderCommand

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
    var title: String = ""
        private set
    var draggable: Boolean = true
        private set
    private var onClose: (() -> Unit)? = null
    private var frame: OverlayPanelFrame? = null

    fun configure(
        title: String,
        draggable: Boolean,
        style: OverlayPanelStyle = this.style,
        onClose: (() -> Unit)? = this.onClose
    ) {
        this.title = title
        this.draggable = draggable
        this.style = style
        this.onClose = onClose
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

    fun appendCommands(
        viewportWidth: Int,
        viewportHeight: Int,
        out: MutableList<RenderCommand>,
        appendBody: (bodyRect: Rect, out: MutableList<RenderCommand>) -> Unit,
        appendOverlay: ((out: MutableList<RenderCommand>) -> Unit)? = null
    ) {
        val localFrame = frame ?: return
        out += RenderCommand.PushClip(0, 0, viewportWidth.coerceAtLeast(1), viewportHeight.coerceAtLeast(1))
        out += RenderCommand.DrawRect(
            localFrame.panelRect.x + 2,
            localFrame.panelRect.y + 2,
            localFrame.panelRect.width,
            localFrame.panelRect.height,
            style.panelShadowColor
        )
        out += RenderCommand.DrawRect(
            localFrame.panelRect.x,
            localFrame.panelRect.y,
            localFrame.panelRect.width,
            localFrame.panelRect.height,
            style.panelBackgroundColor
        )
        drawBorder(out, localFrame.panelRect, style.panelBorderColor)
        out += RenderCommand.DrawRect(
            localFrame.headerRect.x,
            localFrame.headerRect.y,
            localFrame.headerRect.width,
            localFrame.headerRect.height,
            style.headerBackgroundColor
        )
        drawBorder(out, localFrame.headerRect, style.headerBorderColor)
        out += RenderCommand.DrawText(
            text = title,
            x = localFrame.headerRect.x + 6,
            y = localFrame.headerRect.y + 3,
            color = style.textColor,
            fontSize = style.fontSize
        )
        out += RenderCommand.DrawRect(
            localFrame.closeRect.x,
            localFrame.closeRect.y,
            localFrame.closeRect.width,
            localFrame.closeRect.height,
            style.closeButtonBackgroundColor
        )
        drawBorder(out, localFrame.closeRect, style.closeButtonBorderColor)
        out += RenderCommand.DrawText(
            text = style.closeGlyph,
            x = localFrame.closeRect.x + 5,
            y = localFrame.closeRect.y + 2,
            color = style.textColor,
            fontSize = style.fontSize
        )
        out += RenderCommand.PushClip(
            localFrame.bodyRect.x,
            localFrame.bodyRect.y,
            localFrame.bodyRect.width,
            localFrame.bodyRect.height
        )
        appendBody(localFrame.bodyRect, out)
        out += RenderCommand.PopClip
        appendOverlay?.invoke(out)
        out += RenderCommand.PopClip
    }

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

    private fun drawBorder(out: MutableList<RenderCommand>, rect: Rect, color: Int) {
        if (rect.width <= 0 || rect.height <= 0) return
        out += RenderCommand.DrawRect(rect.x, rect.y, rect.width, 1, color)
        out += RenderCommand.DrawRect(rect.x, rect.y + rect.height - 1, rect.width, 1, color)
        out += RenderCommand.DrawRect(rect.x, rect.y, 1, rect.height, color)
        out += RenderCommand.DrawRect(rect.x + rect.width - 1, rect.y, 1, rect.height, color)
    }
}
