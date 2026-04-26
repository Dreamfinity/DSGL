package org.dreamfinity.dsgl.core.colorpicker.internal

import org.dreamfinity.dsgl.core.colorpicker.ColorPickerPopupEngine
import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.applyParent
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.Size
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.overlay.panel.OverlayPanel
import org.dreamfinity.dsgl.core.style.Display

internal class ColorPickerPopupOverlayNode(
    private val popupEngine: ColorPickerPopupEngine,
    private val overlayPanel: OverlayPanel,
    key: Any? = "dsgl-system-color-picker",
) : DOMNode(key) {
    override val styleType: String = "dsgl-system-color-picker"

    private var cursorX: Int = 0
    private var cursorY: Int = 0
    private var domInputRoutingReady: Boolean = false

    private val panelNode: DOMNode = overlayPanel.node().applyParent(this)
    private val bodyNode: ColorPickerPopupBodyNode =
        ColorPickerPopupBodyNode(popupEngine = popupEngine).also(overlayPanel::setBodyContent)

    fun updateCursor(mouseX: Int, mouseY: Int) {
        cursorX = mouseX
        cursorY = mouseY
    }

    fun focusInputSlot(index: Int, mouseX: Int, mouseY: Int): Boolean = bodyNode.focusInputSlot(index, mouseX, mouseY)

    fun syncInputFocusForDomEditing() {
        bodyNode.syncFocusedInputForModeOrOrderChange()
    }

    fun isDomInputRoutingReady(): Boolean = domInputRoutingReady

    fun resetDomInputRoutingReadiness() {
        domInputRoutingReady = false
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
        popupEngine.onFrame(width, height)
        popupEngine.onCursorPosition(cursorX, cursorY)

        val panelRect = overlayPanel.panelRect()
        bodyNode.display = if (panelRect == null) Display.None else Display.Block
        domInputRoutingReady = bodyNode.display == Display.Block
        panelNode.render(ctx, x, y, width, height)
    }
}

internal typealias SystemColorPickerOverlayNode = ColorPickerPopupOverlayNode
