package org.dreamfinity.dsgl.core.colorpicker

import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.event.KeyCodes
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.popup.FloatingPaneDragModel
import org.dreamfinity.dsgl.core.render.RenderCommand

interface ColorPickerPopupHost {
    fun open(request: ColorPickerPopupRequest)
    fun close(owner: Any)
    fun closeAll()
    fun isOpenFor(owner: Any): Boolean
    fun isOpen(): Boolean
}

data class ColorPickerPopupRequest(
    val owner: Any,
    val anchorRect: Rect,
    val title: String = "Color Picker",
    val state: ColorPickerState,
    val style: ColorPickerStyle = ColorPickerStyle(),
    val width: Int = 320,
    val draggable: Boolean = true,
    val closeOnOutsideClick: Boolean = false,
    val onPreview: ((RgbaColor) -> Unit)? = null,
    val onChange: ((RgbaColor) -> Unit)? = null,
    val onCommit: ((RgbaColor) -> Unit)? = null,
    val onClose: (() -> Unit)? = null
)

class ColorPickerPopupEngine : ColorPickerPopupHost {
    private data class PopupState(
        val owner: Any,
        var request: ColorPickerPopupRequest,
        val controller: ColorPickerController,
        var panelRect: Rect,
        var headerRect: Rect,
        var bodyRect: Rect,
        var closeRect: Rect,
        var layout: ColorPickerLayout,
        val dragModel: FloatingPaneDragModel = FloatingPaneDragModel(),
        var consumedEyedropperPress: Boolean = false
    )

    private var popup: PopupState? = null
    private var viewportWidth: Int = 0
    private var viewportHeight: Int = 0
    private val headerHeight: Int = 26
    private val panelPadding: Int = 6
    private val positionStore: ColorPickerPopupPositionStore = ColorPickerPopupPositionStore()

    override fun open(request: ColorPickerPopupRequest) {
        val current = popup
        if (current != null && current.owner == request.owner) {
            sync(request)
            return
        }
        current?.let {
            positionStore.remember(it.owner, it.panelRect)
            it.request.onClose?.invoke()
        }

        val controller = ColorPickerController(
            initial = request.state,
            style = request.style
        )
        val rememberedPanel = positionStore.remembered(request.owner)
        val initialX = rememberedPanel?.x ?: request.anchorRect.x
        val initialY = rememberedPanel?.y ?: request.anchorRect.y
        val initialRect = Rect(initialX, initialY, request.width.coerceAtLeast(220), 1)
        val initialBody = Rect(initialRect.x + panelPadding, initialRect.y + headerHeight + panelPadding, 1, 1)
        val initialLayout = controller.buildLayout(initialBody)
        val state = PopupState(
            owner = request.owner,
            request = request,
            controller = controller,
            panelRect = initialRect,
            headerRect = Rect(initialRect.x, initialRect.y, initialRect.width, headerHeight),
            bodyRect = initialBody,
            closeRect = Rect(initialRect.x + initialRect.width - 20, initialRect.y + 3, 16, 18),
            layout = initialLayout
        )
        popup = state
        bindController(state)
        relayout(state, keepPosition = rememberedPanel != null)
    }

    fun sync(request: ColorPickerPopupRequest) {
        val current = popup ?: return
        if (current.owner != request.owner) return
        val previous = current.request
        current.request = request
        current.controller.onPreview = request.onPreview
        current.controller.onChange = request.onChange
        current.controller.onCommit = { color ->
            request.onCommit?.invoke(color)
            if (request.state.closeOnSelect) {
                close(current.owner)
            }
        }
        current.controller.onRequestClose = {
            close(current.owner)
        }
        if (previous.state != request.state) {
            current.controller.setState(request.state)
        }
        if (previous.anchorRect != request.anchorRect ||
            previous.width != request.width ||
            previous.style != request.style ||
            previous.state.alphaEnabled != request.state.alphaEnabled
        ) {
            relayout(current, keepPosition = true)
        }
    }

    override fun close(owner: Any) {
        val current = popup ?: return
        if (current.owner != owner) return
        positionStore.remember(current.owner, current.panelRect)
        current.dragModel.end()
        current.request.onClose?.invoke()
        popup = null
    }

    override fun closeAll() {
        val current = popup ?: return
        positionStore.remember(current.owner, current.panelRect)
        current.dragModel.end()
        current.request.onClose?.invoke()
        popup = null
    }

    override fun isOpenFor(owner: Any): Boolean {
        val current = popup ?: return false
        return current.owner == owner
    }

    override fun isOpen(): Boolean = popup != null

    internal fun debugPanelRect(owner: Any): Rect? {
        val current = popup ?: return null
        if (current.owner != owner) return null
        return current.panelRect
    }

    internal fun debugHeaderRect(owner: Any): Rect? {
        val current = popup ?: return null
        if (current.owner != owner) return null
        return current.headerRect
    }

    internal fun debugBodyLayout(owner: Any): ColorPickerLayout? {
        val current = popup ?: return null
        if (current.owner != owner) return null
        return current.layout
    }

    internal fun debugController(owner: Any): ColorPickerController? {
        val current = popup ?: return null
        if (current.owner != owner) return null
        return current.controller
    }

    internal fun debugActivePanelRect(): Rect? {
        return popup?.panelRect
    }

    internal fun debugIsDraggingPopup(): Boolean {
        return popup?.dragModel?.dragging == true
    }

    fun onFrame(viewportWidth: Int, viewportHeight: Int) {
        if (this.viewportWidth != viewportWidth || this.viewportHeight != viewportHeight) {
            this.viewportWidth = viewportWidth
            this.viewportHeight = viewportHeight
            popup?.let { relayout(it, keepPosition = true) }
        }
    }

    fun onCursorPosition(mouseX: Int, mouseY: Int) {
        val current = popup ?: return
        if (current.dragModel.dragging) {
            val clamped = current.dragModel.update(
                mouseX = mouseX,
                mouseY = mouseY,
                viewportWidth = viewportWidth,
                viewportHeight = viewportHeight,
                clamp = ColorPickerPopupGeometry::clampPanel
            )
            if (clamped != current.panelRect) {
                current.panelRect = clamped
                rebuildRects(current)
            }
            return
        }
        refreshLayout(current)
        current.controller.handleMouseMove(mouseX, mouseY, current.layout)
    }

    fun captureEyedropperSample() {
        popup?.controller?.sampleEyedropperAtHover()
    }

    fun appendOverlayCommands(out: MutableList<RenderCommand>) {
        val current = popup ?: return
        refreshLayout(current)
        val panel = current.panelRect
        out += RenderCommand.PushClip(0, 0, viewportWidth.coerceAtLeast(1), viewportHeight.coerceAtLeast(1))
        out += RenderCommand.DrawRect(panel.x + 2, panel.y + 2, panel.width, panel.height, current.request.style.panelShadowColor)
        out += RenderCommand.DrawRect(panel.x, panel.y, panel.width, panel.height, current.request.style.panelBackgroundColor)
        drawBorder(out, panel, current.request.style.panelBorderColor)

        out += RenderCommand.DrawRect(
            current.headerRect.x,
            current.headerRect.y,
            current.headerRect.width,
            current.headerRect.height,
            current.request.style.buttonBackgroundColor
        )
        drawBorder(out, current.headerRect, current.request.style.inputBorderColor)
        out += RenderCommand.DrawText(
            text = current.request.title,
            x = current.headerRect.x + 6,
            y = current.headerRect.y + 3,
            color = current.request.style.textColor,
            fontSize = current.request.style.fontSize
        )
        out += RenderCommand.DrawRect(
            current.closeRect.x,
            current.closeRect.y,
            current.closeRect.width,
            current.closeRect.height,
            current.request.style.buttonBackgroundColor
        )
        drawBorder(out, current.closeRect, current.request.style.inputBorderColor)
        out += RenderCommand.DrawText(
            text = "x",
            x = current.closeRect.x + 5,
            y = current.closeRect.y + 2,
            color = current.request.style.textColor,
            fontSize = current.request.style.fontSize
        )

        out += RenderCommand.PushClip(current.bodyRect.x, current.bodyRect.y, current.bodyRect.width, current.bodyRect.height)
        current.controller.appendCommands(current.layout, out)
        out += RenderCommand.PopClip
        current.controller.appendEyedropperOverlay(
            viewportWidth = viewportWidth.coerceAtLeast(1),
            viewportHeight = viewportHeight.coerceAtLeast(1),
            out = out
        )
        out += RenderCommand.PopClip
    }

    fun handleMouseMove(mouseX: Int, mouseY: Int): Boolean {
        val current = popup ?: return false
        onCursorPosition(mouseX, mouseY)
        return current.panelRect.contains(mouseX, mouseY) || current.controller.isEyedropperActive()
    }

    fun handleMouseDown(mouseX: Int, mouseY: Int, button: MouseButton): Boolean {
        val current = popup ?: return false
        refreshLayout(current)
        if (current.controller.isEyedropperActive()) {
            val handled = current.controller.handleMouseDown(mouseX, mouseY, button, current.layout)
            if (handled) {
                refreshLayout(current)
                current.consumedEyedropperPress = button == MouseButton.LEFT || button == MouseButton.RIGHT
                return true
            }
        }
        if (button == MouseButton.LEFT && current.closeRect.contains(mouseX, mouseY)) {
            close(current.owner)
            return true
        }
        if (!current.panelRect.contains(mouseX, mouseY)) {
            if (current.request.closeOnOutsideClick && button == MouseButton.LEFT) {
                close(current.owner)
                return true
            }
            return false
        }
        if (button == MouseButton.LEFT && current.request.draggable && current.headerRect.contains(mouseX, mouseY)) {
            current.dragModel.begin(mouseX, mouseY, current.panelRect)
            return true
        }
        val handled = current.controller.handleMouseDown(mouseX, mouseY, button, current.layout)
        if (handled) {
            refreshLayout(current)
        }
        return handled
    }

    fun handleMouseUp(mouseX: Int, mouseY: Int, button: MouseButton): Boolean {
        val current = popup ?: return false
        if (current.consumedEyedropperPress && (button == MouseButton.LEFT || button == MouseButton.RIGHT)) {
            current.consumedEyedropperPress = false
            return true
        }
        if (button == MouseButton.LEFT && current.dragModel.dragging) {
            current.dragModel.end()
            return true
        }
        val handled = current.controller.handleMouseUp(mouseX, mouseY, button)
        if (handled) {
            refreshLayout(current)
        }
        return handled
    }

    fun handleMouseWheel(mouseX: Int, mouseY: Int, delta: Int): Boolean {
        val current = popup ?: return false
        if (delta == 0) return current.panelRect.contains(mouseX, mouseY)
        return current.panelRect.contains(mouseX, mouseY)
    }

    fun handleKeyDown(keyCode: Int, keyChar: Char = 0.toChar()): Boolean {
        val current = popup ?: return false
        if (current.controller.handleKeyDown(keyCode, keyChar)) return true
        if (keyCode == KeyCodes.ESCAPE) return false
        return false
    }

    private fun bindController(state: PopupState) {
        state.controller.onPreview = state.request.onPreview
        state.controller.onChange = state.request.onChange
        state.controller.onCommit = { color ->
            state.request.onCommit?.invoke(color)
            if (state.request.state.closeOnSelect) {
                close(state.owner)
            }
        }
        state.controller.onRequestClose = {
            close(state.owner)
        }
        state.controller.setState(state.request.state)
    }

    private fun relayout(state: PopupState, keepPosition: Boolean) {
        val width = state.request.width.coerceAtLeast(state.request.style.minWidth)
        val bodyHeight = state.controller.preferredHeight(state.request.state.alphaEnabled)
        val height = headerHeight + panelPadding + bodyHeight + panelPadding
        state.panelRect = ColorPickerPopupGeometry.resolvePanelRect(
            owner = state.owner,
            anchorRect = state.request.anchorRect,
            width = width,
            height = height,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
            keepPosition = keepPosition,
            currentRect = state.panelRect,
            store = positionStore
        )
        rebuildRects(state)
    }

    private fun rebuildRects(state: PopupState) {
        val frame = ColorPickerPopupGeometry.buildFrame(
            panelRect = state.panelRect,
            headerHeight = headerHeight,
            panelPadding = panelPadding
        )
        state.panelRect = frame.panelRect
        state.headerRect = frame.headerRect
        state.bodyRect = frame.bodyRect
        state.closeRect = frame.closeRect
        state.layout = state.controller.buildLayout(frame.bodyRect)
    }

    private fun refreshLayout(state: PopupState) {
        state.layout = state.controller.buildLayout(state.bodyRect)
    }

    private fun drawBorder(out: MutableList<RenderCommand>, rect: Rect, color: Int) {
        if (rect.width <= 0 || rect.height <= 0) return
        out += RenderCommand.DrawRect(rect.x, rect.y, rect.width, 1, color)
        out += RenderCommand.DrawRect(rect.x, rect.y + rect.height - 1, rect.width, 1, color)
        out += RenderCommand.DrawRect(rect.x, rect.y, 1, rect.height, color)
        out += RenderCommand.DrawRect(rect.x + rect.width - 1, rect.y, 1, rect.height, color)
    }
}

class ColorPickerPopupManager(
    private val host: ColorPickerPopupHost = ColorPickerRuntime.host,
    private val ownerToken: Any = Any()
) {
    fun open(
        anchorRect: Rect,
        title: String,
        state: ColorPickerState,
        style: ColorPickerStyle = ColorPickerStyle(),
        width: Int = 320,
        draggable: Boolean = true,
        closeOnOutsideClick: Boolean = false,
        onPreview: ((RgbaColor) -> Unit)? = null,
        onChange: ((RgbaColor) -> Unit)? = null,
        onCommit: ((RgbaColor) -> Unit)? = null,
        onClose: (() -> Unit)? = null
    ) {
        host.open(
            ColorPickerPopupRequest(
                owner = ownerToken,
                anchorRect = anchorRect,
                title = title,
                state = state,
                style = style,
                width = width,
                draggable = draggable,
                closeOnOutsideClick = closeOnOutsideClick,
                onPreview = onPreview,
                onChange = onChange,
                onCommit = onCommit,
                onClose = onClose
            )
        )
    }

    fun close() {
        host.close(ownerToken)
    }

    fun isOpen(): Boolean = host.isOpenFor(ownerToken)
}

object ColorPickerRuntime {
    val engine: ColorPickerPopupEngine = ColorPickerPopupEngine()
    val host: ColorPickerPopupHost = engine
}
