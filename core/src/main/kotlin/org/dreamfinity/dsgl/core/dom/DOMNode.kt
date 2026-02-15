package org.dreamfinity.dsgl.core.dom

import org.dreamfinity.dsgl.core.ComponentProps
import org.dreamfinity.dsgl.core.StyleScope
import org.dreamfinity.dsgl.core.dom.layout.*
import org.dreamfinity.dsgl.core.event.*
import org.dreamfinity.dsgl.core.render.RenderCommand

/**
 * Base class for all DOM nodes in the retained UI tree.
 *
 * Nodes are measured, laid out, and then converted into [RenderCommand]s by the host.
 */
abstract class DOMNode(
    var key: Any? = null
) {
    val children: MutableList<DOMNode> = mutableListOf()
    var parent: DOMNode? = null
    var bounds: Rect = Rect(0, 0, 0, 0)
    var width: Int? = null
    var height: Int? = null
    var margin: Insets = Insets.ZERO
    var padding: Insets = Insets.ZERO
    var border: Border = Border.NONE
    open val focusable: Boolean = false
    open var onmouseenter: ((MouseEvent) -> Unit)? = null
    open var onmouseleave: ((MouseEvent) -> Unit)? = null
    open var onmouseover: ((MouseEvent) -> Unit)? = null

    private var onMouseDownHandler: ((MouseDownEvent) -> Unit)? = null
    private var onMouseUpHandler: ((MouseUpEvent) -> Unit)? = null
    private var onMouseClickHandler: ((MouseClickEvent) -> Unit)? = null
    private var onMouseDragHandler: ((MouseDragEvent) -> Unit)? = null
    private var onMouseWheelHandler: ((MouseWheelEvent) -> Unit)? = null
    private var onMouseMoveHandler: ((MouseMoveEvent) -> Unit)? = null
    private var onKeyDownHandler: ((KeyboardKeyDownEvent) -> Unit)? = null
    private var onKeyUpHandler: ((KeyboardKeyUpEvent) -> Unit)? = null
    private var onMouseEnterHandler: ((MouseEnterEvent) -> Unit)? = null
    private var onMouseLeaveHandler: ((MouseLeaveEvent) -> Unit)? = null
    private var onMouseOverHandler: ((MouseOverEvent) -> Unit)? = null
    private var onFocusGainHandler: ((FocusGainEvent) -> Unit)? = null
    private var onFocusLoseHandler: ((FocusLoseEvent) -> Unit)? = null
    private var onInputHandler: ((InputEvent) -> Unit)? = null
    private var onValueChangeHandler: ((ValueChangedEvent) -> Unit)? = null

    var onMouseDown: ((MouseDownEvent) -> Unit)?
        get() = onMouseDownHandler
        set(value) {
            onMouseDownHandler = value
            value?.let { handler ->
                EventBus.run { this@DOMNode.addEventListener(Events.MOUSEDOWN, handler) }
            }
        }

    var onMouseUp: ((MouseUpEvent) -> Unit)?
        get() = onMouseUpHandler
        set(value) {
            onMouseUpHandler = value
            value?.let { handler ->
                EventBus.run { this@DOMNode.addEventListener(Events.MOUSEUP, handler) }
            }
        }

    var onMouseClick: ((MouseClickEvent) -> Unit)?
        get() = onMouseClickHandler
        set(value) {
            onMouseClickHandler = value
            value?.let { handler ->
                EventBus.run { this@DOMNode.addEventListener(Events.CLICK, handler) }
            }
        }

    var onMouseDrag: ((MouseDragEvent) -> Unit)?
        get() = onMouseDragHandler
        set(value) {
            onMouseDragHandler = value
            value?.let { handler ->
                EventBus.run { this@DOMNode.addEventListener(Events.DRAG, handler) }
            }
        }

    var onMouseWheel: ((MouseWheelEvent) -> Unit)?
        get() = onMouseWheelHandler
        set(value) {
            onMouseWheelHandler = value
            value?.let { handler ->
                EventBus.run { this@DOMNode.addEventListener(Events.WHEEL, handler) }
            }
        }

    var onMouseMove: ((MouseMoveEvent) -> Unit)?
        get() = onMouseMoveHandler
        set(value) {
            onMouseMoveHandler = value
            value?.let { handler ->
                EventBus.run { this@DOMNode.addEventListener(Events.MOUSEMOVE, handler) }
            }
        }

    var onKeyDown: ((KeyboardKeyDownEvent) -> Unit)?
        get() = onKeyDownHandler
        set(value) {
            onKeyDownHandler = value
            value?.let { handler ->
                EventBus.run { this@DOMNode.addEventListener(Events.KEYDOWN, handler) }
            }
        }

    var onKeyUp: ((KeyboardKeyUpEvent) -> Unit)?
        get() = onKeyUpHandler
        set(value) {
            onKeyUpHandler = value
            value?.let { handler ->
                EventBus.run { this@DOMNode.addEventListener(Events.KEYUP, handler) }
            }
        }

    var onKeyPressed: ((KeyboardKeyDownEvent) -> Unit)?
        get() = onKeyDown
        set(value) {
            onKeyDown = value
        }

    var onKeyReleased: ((KeyboardKeyUpEvent) -> Unit)?
        get() = onKeyUp
        set(value) {
            onKeyUp = value
        }

    var onMouseEnter: ((MouseEnterEvent) -> Unit)?
        get() = onMouseEnterHandler
        set(value) {
            onMouseEnterHandler = value
            value?.let { handler ->
                EventBus.run { this@DOMNode.addEventListener(Events.MOUSEENTER, handler) }
            }
        }

    var onMouseLeave: ((MouseLeaveEvent) -> Unit)?
        get() = onMouseLeaveHandler
        set(value) {
            onMouseLeaveHandler = value
            value?.let { handler ->
                EventBus.run { this@DOMNode.addEventListener(Events.MOUSELEAVE, handler) }
            }
        }

    var onMouseOver: ((MouseOverEvent) -> Unit)?
        get() = onMouseOverHandler
        set(value) {
            onMouseOverHandler = value
            value?.let { handler ->
                EventBus.run { this@DOMNode.addEventListener(Events.MOUSEOVER, handler) }
            }
        }

    var onFocusGain: ((FocusGainEvent) -> Unit)?
        get() = onFocusGainHandler
        set(value) {
            onFocusGainHandler = value
            value?.let { handler ->
                EventBus.run { this@DOMNode.addEventListener(Events.FOCUS, handler) }
            }
        }

    var onFocusLose: ((FocusLoseEvent) -> Unit)?
        get() = onFocusLoseHandler
        set(value) {
            onFocusLoseHandler = value
            value?.let { handler ->
                EventBus.run { this@DOMNode.addEventListener(Events.BLUR, handler) }
            }
        }

    var onInput: ((InputEvent) -> Unit)?
        get() = onInputHandler
        set(value) {
            onInputHandler = value
            value?.let { handler ->
                EventBus.run { this@DOMNode.addEventListener(Events.INPUT, handler) }
            }
        }

    var onValueChange: ((ValueChangedEvent) -> Unit)?
        get() = onValueChangeHandler
        set(value) {
            onValueChangeHandler = value
            value?.let { handler ->
                EventBus.run { this@DOMNode.addEventListener(Events.CHANGE, handler) }
            }
        }

    /** Measures the node's desired size. */
    open fun measure(ctx: UiMeasureContext): Size {
        val contentWidth = width ?: 0
        val contentHeight = height ?: 0
        val totalWidth = contentWidth + padding.horizontal + border.horizontal
        val totalHeight = contentHeight + padding.vertical + border.vertical
        return Size(totalWidth, totalHeight)
    }

    /** Lays out this node and its children for the given bounds. */
    open fun render(ctx: UiMeasureContext, x: Int, y: Int, width: Int, height: Int) {
        bounds = Rect(x, y, width, height)
        val contentX = x + border.left + padding.left
        val contentY = y + border.top + padding.top
        children.forEach { child ->
            val childSize = child.measure(ctx)
            val childX = contentX + child.margin.left
            val childY = contentY + child.margin.top
            child.render(ctx, childX, childY, childSize.width, childSize.height)
        }
    }

    /** Appends render commands for this node and its children. */
    open fun buildRenderCommands(ctx: UiMeasureContext, out: MutableList<RenderCommand>) {
        children.forEach { it.buildRenderCommands(ctx, out) }
    }

    /** Handles a click; return true when consumed. */
    open fun handleClick(event: MouseClickEvent): Boolean = false

    /** Dispatches a click through this node and its subtree. */
    fun dispatchClick(event: MouseClickEvent): Boolean {
        return dispatchClickInternal(this, event)
    }

    /** Returns true if the mouse event is within current bounds. */
    fun hovered(event: MouseEvent): Boolean {
        return bounds.contains(event.mouseX, event.mouseY)
    }

    /** Applies event handlers from [ComponentProps] to this node. */
    fun applyHandlers(props: ComponentProps) {
        if (props.onMouseEnter != null) this.onMouseEnter = props.onMouseEnter
        if (props.onMouseLeave != null) this.onMouseLeave = props.onMouseLeave
        if (props.onMouseOver != null) this.onMouseOver = props.onMouseOver
        if (props.onMouseMove != null) this.onMouseMove = props.onMouseMove
        if (props.onMouseDown != null) this.onMouseDown = props.onMouseDown
        if (props.onMouseUp != null) this.onMouseUp = props.onMouseUp
        if (props.onMouseClick != null) this.onMouseClick = props.onMouseClick
        if (props.onMouseDrag != null) this.onMouseDrag = props.onMouseDrag
        if (props.onMouseWheel != null) this.onMouseWheel = props.onMouseWheel
        if (props.onKeyDown != null) this.onKeyDown = props.onKeyDown
        if (props.onKeyUp != null) this.onKeyUp = props.onKeyUp
        if (props.onKeyPressed != null) this.onKeyPressed = props.onKeyPressed
        if (props.onKeyReleased != null) this.onKeyReleased = props.onKeyReleased
        if (props.onFocusGain != null) this.onFocusGain = props.onFocusGain
        if (props.onFocusLose != null) this.onFocusLose = props.onFocusLose
        if (props.onInput != null) this.onInput = props.onInput
        if (props.onValueChange != null) this.onValueChange = props.onValueChange
    }

    /** Applies [StyleScope] DSL to this node. */
    fun applyStyle(style: StyleScope.() -> Unit) {
        StyleScope(this).style()
    }

    protected fun contentX(): Int = bounds.x + border.left + padding.left

    protected fun contentY(): Int = bounds.y + border.top + padding.top

    protected fun contentWidth(): Int =
        (bounds.width - border.horizontal - padding.horizontal).coerceAtLeast(0)

    protected fun contentHeight(): Int =
        (bounds.height - border.vertical - padding.vertical).coerceAtLeast(0)

    /** Adds border render commands when a border is present. */
    protected fun addBorderCommands(out: MutableList<RenderCommand>) {
        if (border.top <= 0 && border.right <= 0 && border.bottom <= 0 && border.left <= 0) return

        val x = bounds.x
        val y = bounds.y
        val w = bounds.width
        val h = bounds.height

        if (border.top > 0) {
            out.add(RenderCommand.DrawRect(x, y, w, border.top, border.color))
        }
        if (border.bottom > 0) {
            out.add(RenderCommand.DrawRect(x, y + h - border.bottom, w, border.bottom, border.color))
        }
        if (border.left > 0) {
            out.add(RenderCommand.DrawRect(x, y, border.left, h, border.color))
        }
        if (border.right > 0) {
            out.add(RenderCommand.DrawRect(x + w - border.right, y, border.right, h, border.color))
        }
    }
}

/**
 * Attaches this node to [parent] and returns itself for fluent creation.
 */
fun <T : DOMNode> T.applyParent(parent: DOMNode?): T {
    parent?.let {
        this.parent = parent
        parent.children.add(this)
    }
    return this
}
