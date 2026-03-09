package org.dreamfinity.dsgl.core.dom.elements

import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.Size
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.event.EventBus
import org.dreamfinity.dsgl.core.event.Events
import org.dreamfinity.dsgl.core.event.FocusManager
import org.dreamfinity.dsgl.core.event.KeyCodes
import org.dreamfinity.dsgl.core.event.KeyboardKeyDownEvent
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.event.MouseClickEvent
import org.dreamfinity.dsgl.core.event.MouseDownEvent
import org.dreamfinity.dsgl.core.event.postChange
import org.dreamfinity.dsgl.core.event.postInput
import org.dreamfinity.dsgl.core.render.RenderCommand

class ToggleNode(
    controlled: Boolean = false,
    checked: Boolean = false,
    defaultChecked: Boolean = false,
    key: Any? = null
) : DOMNode(key) {
    override val styleType: String = "toggle"
    override val focusable: Boolean = true

    var controlled: Boolean = controlled
        set(value) {
            if (field == value) return
            val wasControlled = field
            field = value
            if (!field && wasControlled) {
                uncontrolledChecked = controlledChecked
            }
            setOpenState(isChecked())
            markRenderCommandsDirty()
        }

    var controlledChecked: Boolean = checked
        set(value) {
            if (field == value) return
            field = value
            if (controlled) {
                setOpenState(isChecked())
                markRenderCommandsDirty()
            }
        }

    var defaultChecked: Boolean = defaultChecked
        set(value) {
            if (field == value) return
            field = value
            if (!controlled && !userChanged) {
                uncontrolledChecked = value
                setOpenState(isChecked())
                markRenderCommandsDirty()
            }
        }

    var trackOnColor: Int = 0xFF34C759.toInt()
    var trackOffColor: Int = 0xFF656A73.toInt()
    var trackDisabledColor: Int = 0xFF4B4F56.toInt()
    var thumbColor: Int = 0xFFFFFFFF.toInt()
    var thumbDisabledColor: Int = 0xFFB7BBC1.toInt()
    var focusOutlineColor: Int = 0xAA6FB4FF.toInt()
    var switchWidthPx: Int = 34
    var switchHeightPx: Int = 20

    private var uncontrolledChecked: Boolean = defaultChecked
    private var userChanged: Boolean = false
    private var trackRect: Rect = Rect(0, 0, 0, 0)
    private var thumbRect: Rect = Rect(0, 0, 0, 0)

    init {
        setOpenState(isChecked())
        EventBus.run {
            this@ToggleNode.addEventListener(Events.MOUSEDOWN) { event: MouseDownEvent ->
                handleMouseDownEvent(event)
            }
            this@ToggleNode.addEventListener(Events.CLICK) { event: MouseClickEvent ->
                handleClickEvent(event)
            }
            this@ToggleNode.addEventListener(Events.KEYDOWN) { event: KeyboardKeyDownEvent ->
                handleKeyDownEvent(event)
            }
        }
    }

    fun isChecked(): Boolean {
        return if (controlled) controlledChecked else uncontrolledChecked
    }

    internal fun syncFrom(template: ToggleNode) {
        val wasControlled = controlled
        controlled = template.controlled
        controlledChecked = template.controlledChecked
        defaultChecked = template.defaultChecked
        trackOnColor = template.trackOnColor
        trackOffColor = template.trackOffColor
        trackDisabledColor = template.trackDisabledColor
        thumbColor = template.thumbColor
        thumbDisabledColor = template.thumbDisabledColor
        focusOutlineColor = template.focusOutlineColor
        switchWidthPx = template.switchWidthPx
        switchHeightPx = template.switchHeightPx
        if (!controlled && wasControlled) {
            uncontrolledChecked = controlledChecked
        }
        setOpenState(isChecked())
        markRenderCommandsDirty()
    }

    internal override fun measureForLayout(ctx: UiMeasureContext, availableOuterWidth: Int?): Size {
        return measureWithConstraint(availableOuterWidth)
    }

    override fun measure(ctx: UiMeasureContext): Size {
        return measureWithConstraint(null)
    }

    private fun measureWithConstraint(availableOuterWidth: Int?): Size {
        val contentLimit = resolvedContentLimit(availableOuterWidth)
        val naturalWidth = width ?: switchWidthPx
        val contentWidth = contentLimit?.let { minOf(it, naturalWidth) } ?: naturalWidth
        val contentHeight = height ?: switchHeightPx
        val totalWidth = contentWidth + padding.horizontal + border.horizontal
        val totalHeight = contentHeight + padding.vertical + border.vertical
        return Size(totalWidth, totalHeight)
    }

    private fun resolvedContentLimit(availableOuterWidth: Int?): Int? {
        val explicit = width
        val extras = margin.horizontal + padding.horizontal + border.horizontal
        val constrainedByParent = availableOuterWidth?.let { (it - extras).coerceAtLeast(0) }
        return when {
            explicit != null && constrainedByParent != null -> minOf(explicit, constrainedByParent)
            explicit != null -> explicit
            else -> constrainedByParent
        }
    }

    override fun render(ctx: UiMeasureContext, x: Int, y: Int, width: Int, height: Int) {
        bounds = Rect(x, y, width, height)
        val tx = contentX()
        val ty = contentY()
        val tw = contentWidth()
        val th = contentHeight()
        trackRect = Rect(tx, ty, tw, th)
        val inset = (th / 10).coerceAtLeast(2)
        val thumbSize = (th - inset * 2).coerceIn(1, tw.coerceAtLeast(1))
        val minThumbX = tx + inset
        val maxThumbX = (tx + tw - inset - thumbSize).coerceAtLeast(minThumbX)
        val thumbX = if (isChecked()) maxThumbX else minThumbX
        val thumbY = ty + ((th - thumbSize) / 2)
        thumbRect = Rect(thumbX, thumbY, thumbSize, thumbSize)
    }

    override fun buildRenderCommands(ctx: UiMeasureContext, out: MutableList<RenderCommand>) {
        addBackgroundImageCommand(out)
        val trackColor = when {
            styleDisabled -> trackDisabledColor
            isChecked() -> trackOnColor
            else -> trackOffColor
        }
        out += RenderCommand.DrawRect(trackRect.x, trackRect.y, trackRect.width, trackRect.height, trackColor)
        addBorderCommands(out)
        val thumb = if (styleDisabled) thumbDisabledColor else thumbColor
        out += RenderCommand.DrawRect(thumbRect.x, thumbRect.y, thumbRect.width, thumbRect.height, thumb)
        if (FocusManager.isFocused(this) && !styleDisabled) {
            addOutline(out, bounds, focusOutlineColor)
        }
    }

    override fun defaultBackgroundColor(): Int? = trackOffColor

    override fun applyBackgroundColor(value: Int?) {
        if (value != null) {
            trackOffColor = value
            trackOnColor = value
        }
    }

    override fun defaultForegroundColor(): Int = thumbColor

    override fun applyForegroundColor(value: Int) {
        thumbColor = value
    }

    private fun handleMouseDownEvent(event: MouseDownEvent) {
        if (styleDisabled) return
        if (event.mouseButton != MouseButton.LEFT) return
        if (!containsGlobalPoint(event.mouseX, event.mouseY)) return
        FocusManager.requestFocus(this)
    }

    private fun handleClickEvent(event: MouseClickEvent) {
        if (styleDisabled) return
        if (event.mouseButton != MouseButton.LEFT) return
        if (!containsGlobalPoint(event.mouseX, event.mouseY)) return
        toggleAndEmit()
    }

    private fun handleKeyDownEvent(event: KeyboardKeyDownEvent) {
        if (styleDisabled) return
        if (!FocusManager.isFocused(this)) return
        if (event.keyCode != KeyCodes.SPACE && event.keyCode != KeyCodes.ENTER) return
        toggleAndEmit()
        event.cancelled = true
    }

    private fun toggleAndEmit() {
        val next = !isChecked()
        if (!controlled) {
            uncontrolledChecked = next
            userChanged = true
        }
        setOpenState(next)
        markRenderCommandsDirty()
        val literal = if (next) "true" else "false"
        postInput(this, literal, next)
        postChange(this, literal, next)
    }

    private fun addOutline(out: MutableList<RenderCommand>, rect: Rect, color: Int) {
        if (rect.width <= 0 || rect.height <= 0) return
        out += RenderCommand.DrawRect(rect.x, rect.y, rect.width, 1, color)
        out += RenderCommand.DrawRect(rect.x, rect.y + rect.height - 1, rect.width, 1, color)
        out += RenderCommand.DrawRect(rect.x, rect.y, 1, rect.height, color)
        out += RenderCommand.DrawRect(rect.x + rect.width - 1, rect.y, 1, rect.height, color)
    }
}
