package org.dreamfinity.dsgl.core.colorpicker

import org.dreamfinity.dsgl.core.colorpicker.internal.ColorPickerDebugCounters
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.event.KeyCodes
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.overlay.OverlayOwnerScope
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
    val ownerScope: OverlayOwnerScope = OverlayOwnerScope.Application,
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
    val onClose: (() -> Unit)? = null,
)

class ColorPickerPopupEngine : ColorPickerPopupHost {
    private data class LayoutDirtyKey(
        val bodyRect: Rect,
        val mode: ColorFormatMode,
        val rgbOrder: RgbChannelOrder,
        val alphaEnabled: Boolean,
        val modeDropdownOpen: Boolean,
    )

    private data class PopupState(
        val owner: Any,
        var request: ColorPickerPopupRequest,
        val controller: ColorPickerController,
        var panelRect: Rect,
        var headerRect: Rect,
        var bodyRect: Rect,
        var closeRect: Rect,
        var layout: ColorPickerLayout,
        var layoutDirtyKey: LayoutDirtyKey? = null,
        val dragModel: FloatingPaneDragModel = FloatingPaneDragModel(),
        var consumedEyedropperPress: Boolean = false,
    )

    private var popup: PopupState? = null
    private var viewportWidth: Int = 0
    private var viewportHeight: Int = 0
    private val headerHeight: Int = 26
    private val panelPadding: Int = 6
    private val positionStore: ColorPickerPopupPositionStore = ColorPickerPopupPositionStore()
    private val debugCountersEnabled: Boolean =
        java.lang.Boolean
            .getBoolean("dsgl.colorPicker.debugCounters")
    private val debugReportIntervalMs: Long = 4000L
    private val nanosPerMillisecond: Double = 1_000_000.0
    private var debugNextReportAtMs: Long = 0L

    override fun open(request: ColorPickerPopupRequest) {
        val current = popup
        if (current != null && current.owner == request.owner) {
            sync(request)
            return
        }
        current?.let {
            positionStore.remember(it.owner, it.panelRect)
            it.request.onClose
                ?.invoke()
        }

        val controller =
            ColorPickerController(
                initial = request.state,
                style = request.style,
            )
        val rememberedPanel = positionStore.remembered(request.owner)
        val initialX = rememberedPanel?.x ?: request.anchorRect.x
        val initialY = rememberedPanel?.y ?: request.anchorRect.y
        val initialRect = Rect(initialX, initialY, request.width.coerceAtLeast(220), 1)
        val initialBody = Rect(initialRect.x + panelPadding, initialRect.y + headerHeight + panelPadding, 1, 1)
        ColorPickerDebugCounters.onBuildLayoutCall(request.ownerScope == OverlayOwnerScope.System)
        val initialLayout = controller.buildLayout(initialBody)
        val state =
            PopupState(
                owner = request.owner,
                request = request,
                controller = controller,
                panelRect = initialRect,
                headerRect = Rect(initialRect.x, initialRect.y, initialRect.width, headerHeight),
                bodyRect = initialBody,
                closeRect = Rect(initialRect.x + initialRect.width - 20, initialRect.y + 3, 16, 18),
                layout = initialLayout,
            )
        popup = state
        bindController(state)
        relayout(state, keepPosition = rememberedPanel != null)
        if (debugCountersEnabled) {
            ColorPickerDebugCounters.reset()
            debugNextReportAtMs = System.currentTimeMillis() + debugReportIntervalMs
        }
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
        val snapshot = current.controller.snapshot()
        val requestedDiffersFromController = !sameStateContract(request.state, snapshot)
        if (requestedDiffersFromController && !current.controller.hasActiveInteraction()) {
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
        current.request.onClose
            ?.invoke()
        popup = null
        if (debugCountersEnabled) {
            debugNextReportAtMs = 0L
        }
    }

    override fun closeAll() {
        val current = popup ?: return
        positionStore.remember(current.owner, current.panelRect)
        current.dragModel.end()
        current.request.onClose
            ?.invoke()
        popup = null
        if (debugCountersEnabled) {
            debugNextReportAtMs = 0L
        }
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

    internal fun debugCloseRect(owner: Any): Rect? {
        val current = popup ?: return null
        if (current.owner != owner) return null
        return current.closeRect
    }

    internal fun debugBodyLayout(owner: Any): ColorPickerLayout? {
        val current = popup ?: return null
        if (current.owner != owner) return null
        return current.layout
    }

    internal fun debugTitle(owner: Any): String? {
        val current = popup ?: return null
        if (current.owner != owner) return null
        return current.request.title
    }

    internal fun debugStyle(owner: Any): ColorPickerStyle? {
        val current = popup ?: return null
        if (current.owner != owner) return null
        return current.request.style
    }

    internal fun debugOwnerScope(owner: Any): OverlayOwnerScope? {
        val current = popup ?: return null
        if (current.owner != owner) return null
        return current.request.ownerScope
    }

    internal fun debugController(owner: Any): ColorPickerController? {
        val current = popup ?: return null
        if (current.owner != owner) return null
        return current.controller
    }

    internal fun debugActiveController(): ColorPickerController? = popup?.controller

    internal fun debugActiveLayout(): ColorPickerLayout? = popup?.layout

    internal fun debugActiveStyle(): ColorPickerStyle? = popup?.request?.style

    internal fun debugActivePanelRect(): Rect? = popup?.panelRect

    internal fun debugActiveOwnerScope(): OverlayOwnerScope? = popup?.request?.ownerScope

    internal fun debugIsDraggingPopup(): Boolean = popup?.dragModel?.dragging == true

    internal fun debugResetCounters() {
        ColorPickerDebugCounters.reset()
    }

    internal fun debugCountersSnapshot(): ColorPickerDebugCounters.Snapshot = ColorPickerDebugCounters.snapshot()

    internal fun forcePanelRect(owner: Any, panelRect: Rect) {
        val current = popup ?: return
        if (current.owner != owner) return
        val clamped =
            ColorPickerPopupGeometry.clampPanel(
                rect = panelRect,
                viewportWidth = viewportWidth,
                viewportHeight = viewportHeight,
            )
        if (clamped == current.panelRect) return
        current.panelRect = clamped
        rebuildRects(current)
    }

    fun onFrame(viewportWidth: Int, viewportHeight: Int) {
        reportDebugCountersIfDue()
        if (this.viewportWidth != viewportWidth || this.viewportHeight != viewportHeight) {
            this.viewportWidth = viewportWidth
            this.viewportHeight = viewportHeight
            popup?.let { relayout(it, keepPosition = true) }
        }
    }

    fun onCursorPosition(mouseX: Int, mouseY: Int) {
        val current = popup ?: return
        if (current.dragModel.dragging) {
            val clamped =
                current.dragModel.update(
                    mouseX = mouseX,
                    mouseY = mouseY,
                    viewportWidth = viewportWidth,
                    viewportHeight = viewportHeight,
                    clamp = ColorPickerPopupGeometry::clampPanel,
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

    fun hasActiveEyedropper(): Boolean = popup?.controller?.isEyedropperActive() == true

    fun appendOverlayCommands(out: MutableList<RenderCommand>) {
        val current = popup ?: return
        refreshLayout(current)
        val panel = current.panelRect
        out += RenderCommand.PushClip(0, 0, viewportWidth.coerceAtLeast(1), viewportHeight.coerceAtLeast(1))
        out +=
            RenderCommand.DrawRect(
                panel.x + 2,
                panel.y + 2,
                panel.width,
                panel.height,
                current.request.style.panelShadowColor,
            )
        out +=
            RenderCommand.DrawRect(
                panel.x,
                panel.y,
                panel.width,
                panel.height,
                current.request.style.panelBackgroundColor,
            )
        drawBorder(out, panel, current.request.style.panelBorderColor)

        out +=
            RenderCommand.DrawRect(
                current.headerRect.x,
                current.headerRect.y,
                current.headerRect.width,
                current.headerRect.height,
                current.request.style.buttonBackgroundColor,
            )
        drawBorder(out, current.headerRect, current.request.style.inputBorderColor)
        out +=
            RenderCommand.DrawText(
                text = current.request.title,
                x = current.headerRect.x + 6,
                y = current.headerRect.y + 3,
                color = current.request.style.textColor,
                fontSize = current.request.style.fontSize,
            )
        out +=
            RenderCommand.DrawRect(
                current.closeRect.x,
                current.closeRect.y,
                current.closeRect.width,
                current.closeRect.height,
                current.request.style.buttonBackgroundColor,
            )
        drawBorder(out, current.closeRect, current.request.style.inputBorderColor)
        out +=
            RenderCommand.DrawText(
                text = "x",
                x = current.closeRect.x + 5,
                y = current.closeRect.y + 2,
                color = current.request.style.textColor,
                fontSize = current.request.style.fontSize,
            )

        out +=
            RenderCommand.PushClip(
                current.bodyRect.x,
                current.bodyRect.y,
                current.bodyRect.width,
                current.bodyRect.height,
            )
        appendOverlayBodyCommands(out)
        out += RenderCommand.PopClip
        appendEyedropperOverlayCommands(
            viewportWidth = viewportWidth.coerceAtLeast(1),
            viewportHeight = viewportHeight.coerceAtLeast(1),
            out = out,
        )
        out += RenderCommand.PopClip
    }

    internal fun appendOverlayBodyCommands(out: MutableList<RenderCommand>) {
        val current = popup ?: return
        current.controller.appendCommands(current.layout, out)
    }

    internal fun appendEyedropperOverlayCommands(
        viewportWidth: Int = this.viewportWidth.coerceAtLeast(1),
        viewportHeight: Int = this.viewportHeight.coerceAtLeast(1),
        out: MutableList<RenderCommand>,
    ) {
        val current = popup ?: return
        current.controller.appendEyedropperOverlay(
            viewportWidth = viewportWidth.coerceAtLeast(1),
            viewportHeight = viewportHeight.coerceAtLeast(1),
            out = out,
        )
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

    fun shouldRouteSystemInputSlotMouseDownToDom(mouseX: Int, mouseY: Int, button: MouseButton): Boolean {
        val current = popup ?: return false
        if (current.request.ownerScope != OverlayOwnerScope.System) return false
        if (button != MouseButton.LEFT) return false
        if (current.controller.isEyedropperActive()) return false
        val hit =
            current.layout.inputSlots
                .any { slot -> slot.inputRect.contains(mouseX, mouseY) }
        ColorPickerDebugCounters.onRouteSystemInputSlotCheck(hit)
        return hit
    }

    fun shouldRouteSystemBodyIntentMouseDownToDom(mouseX: Int, mouseY: Int, button: MouseButton): Boolean {
        val current = popup ?: return false
        if (current.request.ownerScope != OverlayOwnerScope.System) return false
        if (button != MouseButton.LEFT) return false
        if (current.controller.isEyedropperActive()) return false
        refreshLayout(current)
        val hit =
            current.layout.previousSwatchRect
                .contains(mouseX, mouseY) ||
                current.layout.currentSwatchRect
                    .contains(mouseX, mouseY) ||
                current.layout.copyRect
                    .contains(mouseX, mouseY) ||
                current.layout.pasteRect
                    .contains(mouseX, mouseY) ||
                current.layout.pipetteRect
                    .contains(mouseX, mouseY) ||
                current.layout.rgbaOrderRect
                    ?.contains(mouseX, mouseY) == true ||
                current.layout.argbOrderRect
                    ?.contains(mouseX, mouseY) == true ||
                current.layout.modeSelectRect
                    .contains(mouseX, mouseY) ||
                current.layout.modeOptionsRect
                    ?.contains(mouseX, mouseY) == true ||
                current.layout.recentRects
                    .any { rect -> rect.contains(mouseX, mouseY) }
        ColorPickerDebugCounters.onRouteSystemBodyIntentCheck(hit)
        return hit
    }

    fun focusSystemInputSlotForDomEditing(mouseX: Int, mouseY: Int, focusInputByIndex: (Int) -> Boolean): Boolean {
        val current = popup ?: return false
        if (current.request.ownerScope != OverlayOwnerScope.System) return false
        val slotIndex =
            current.layout.inputSlots
                .indexOfFirst { slot -> slot.inputRect.contains(mouseX, mouseY) }
        if (slotIndex < 0) return false
        val slot = current.layout.inputSlots[slotIndex]
        current.controller.handleDomInputFocused(slot.key)
        val focused = focusInputByIndex(slotIndex)
        return focused
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
        return handled || current.panelRect.contains(mouseX, mouseY)
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
            state.request.onCommit
                ?.invoke(color)
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
        val width =
            state.request.width
                .coerceAtLeast(state.request.style.minWidth)
        val bodyHeight = state.controller.preferredHeight(state.request.state.alphaEnabled)
        val height = headerHeight + panelPadding + bodyHeight + panelPadding
        state.panelRect =
            ColorPickerPopupGeometry.resolvePanelRect(
                owner = state.owner,
                anchorRect = state.request.anchorRect,
                width = width,
                height = height,
                viewportWidth = viewportWidth,
                viewportHeight = viewportHeight,
                keepPosition = keepPosition,
                currentRect = state.panelRect,
                store = positionStore,
            )
        rebuildRects(state)
    }

    private fun rebuildRects(state: PopupState) {
        val frame =
            ColorPickerPopupGeometry.buildFrame(
                panelRect = state.panelRect,
                headerHeight = headerHeight,
                panelPadding = panelPadding,
            )
        state.panelRect = frame.panelRect
        state.headerRect = frame.headerRect
        state.bodyRect = frame.bodyRect
        state.closeRect = frame.closeRect
        rebuildLayout(state)
    }

    private fun refreshLayout(state: PopupState) {
        ColorPickerDebugCounters.onRefreshLayoutCall(state.request.ownerScope == OverlayOwnerScope.System)
        ensureLayoutUpToDate(state)
    }

    private fun ensureLayoutUpToDate(state: PopupState) {
        val nextKey = resolveLayoutDirtyKey(state)
        if (state.layoutDirtyKey == nextKey) {
            return
        }
        rebuildLayout(state)
    }

    private fun rebuildLayout(state: PopupState) {
        ColorPickerDebugCounters.onBuildLayoutCall(state.request.ownerScope == OverlayOwnerScope.System)
        state.layout = state.controller.buildLayout(state.bodyRect)
        state.layoutDirtyKey = resolveLayoutDirtyKey(state)
    }

    private fun resolveLayoutDirtyKey(state: PopupState): LayoutDirtyKey {
        val snapshot = state.controller.snapshot()
        return LayoutDirtyKey(
            bodyRect = state.bodyRect,
            mode = snapshot.mode,
            rgbOrder = snapshot.rgbOrder,
            alphaEnabled = snapshot.alphaEnabled,
            modeDropdownOpen = state.controller.viewModeDropdownOpen(),
        )
    }

    private fun drawBorder(out: MutableList<RenderCommand>, rect: Rect, color: Int) {
        if (rect.width <= 0 || rect.height <= 0) return
        out += RenderCommand.DrawRect(rect.x, rect.y, rect.width, 1, color)
        out += RenderCommand.DrawRect(rect.x, rect.y + rect.height - 1, rect.width, 1, color)
        out += RenderCommand.DrawRect(rect.x, rect.y, 1, rect.height, color)
        out += RenderCommand.DrawRect(rect.x + rect.width - 1, rect.y, 1, rect.height, color)
    }

    private fun sameStateContract(a: ColorPickerState, b: ColorPickerState): Boolean =
        a.color.toArgbInt() == b.color.toArgbInt() &&
            a.previous.toArgbInt() == b.previous.toArgbInt() &&
            a.mode == b.mode &&
            a.rgbOrder == b.rgbOrder &&
            a.alphaEnabled == b.alphaEnabled &&
            a.closeOnSelect == b.closeOnSelect

    private fun reportDebugCountersIfDue() {
        if (!debugCountersEnabled) return
        val current = popup ?: return
        val now = System.currentTimeMillis()
        if (debugNextReportAtMs == 0L) {
            debugNextReportAtMs = now + debugReportIntervalMs
            return
        }
        if (now < debugNextReportAtMs) return

        val snapshot = ColorPickerDebugCounters.snapshot()
        val composeMs = nanosToMsString(snapshot.recentSwatchComposeNanos)
        val removeMs = nanosToMsString(snapshot.recentSwatchRemoveNanos)
        println(
            "dsgl.colorPicker.debugCounters " +
                "ownerScope=${current.request.ownerScope} " +
                "recentComposeCalls=${snapshot.recentSwatchGridComposeCalls} " +
                "recentCreated=${snapshot.recentSwatchNodesCreated} " +
                "recentRemoved=${snapshot.recentSwatchNodesRemoved} " +
                "recentComposeMs=$composeMs " +
                "recentRemoveMs=$removeMs " +
                "recentSnapshotReads=${snapshot.recentColorsSnapshotReads} " +
                "refreshLayoutCalls=${snapshot.refreshLayoutCalls} " +
                "buildLayoutCalls=${snapshot.buildLayoutCalls} " +
                "bodyIntentChecks=${snapshot.routeSystemBodyIntentChecks} " +
                "bodyIntentHits=${snapshot.routeSystemBodyIntentHits} " +
                "renderInvalidations=${snapshot.renderInvalidationCalls}",
        )
        ColorPickerDebugCounters.reset()
        debugNextReportAtMs = now + debugReportIntervalMs
    }

    private fun nanosToMsString(nanos: Long): String =
        String.format(java.util.Locale.ROOT, "%.3f", nanos / nanosPerMillisecond)
}

class ColorPickerPopupManager(
    private val host: ColorPickerPopupHost = ColorPickerPortalServices.engine,
    private val ownerToken: Any = Any(),
) {
    fun open(
        ownerScope: OverlayOwnerScope = OverlayOwnerScope.Application,
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
        onClose: (() -> Unit)? = null,
    ) {
        host.open(
            ColorPickerPopupRequest(
                owner = ownerToken,
                ownerScope = ownerScope,
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
                onClose = onClose,
            ),
        )
    }

    fun close() {
        host.close(ownerToken)
    }

    fun isOpen(): Boolean = host.isOpenFor(ownerToken)
}

object ColorPickerPortalServices {
    val engine: ColorPickerPopupEngine = ColorPickerPopupEngine()
}
