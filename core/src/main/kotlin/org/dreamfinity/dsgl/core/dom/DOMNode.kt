package org.dreamfinity.dsgl.core.dom

import org.dreamfinity.dsgl.core.ComponentProps
import org.dreamfinity.dsgl.core.DsglColors
import org.dreamfinity.dsgl.core.StyleScope
import org.dreamfinity.dsgl.core.dom.layout.*
import org.dreamfinity.dsgl.core.event.*
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.style.ComputedStyle
import org.dreamfinity.dsgl.core.style.ComputedStyleDefaults
import org.dreamfinity.dsgl.core.style.StyleAlign
import org.dreamfinity.dsgl.core.style.StyleDecls
import org.dreamfinity.dsgl.core.style.StyleExpression
import org.dreamfinity.dsgl.core.style.StyleProperty

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
    var borderRadius: Int = 0
    var align: StyleAlign = StyleAlign.START
    var styleId: String? = null
    val styleClasses: MutableSet<String> = linkedSetOf()
    var inlineStyleDecls: StyleDecls = StyleDecls()
    var styleHovered: Boolean = false
        private set
    var styleActive: Boolean = false
        private set
    var styleFocused: Boolean = false
        private set
    var styleDisabled: Boolean = false
        set(value) {
            field = value
            if (value) {
                styleHovered = false
                styleActive = false
                styleFocused = false
                if (FocusManager.isFocused(this)) {
                    FocusManager.clearFocus()
                }
            }
        }
    var draggable: Boolean = false
    var droppable: Boolean = false
    open val focusable: Boolean = false
    open val styleType: String = "node"
    open var onmouseenter: ((MouseEvent) -> Unit)? = null
    open var onmouseleave: ((MouseEvent) -> Unit)? = null
    open var onmouseover: ((MouseEvent) -> Unit)? = null
    private var styledBackgroundImage: String? = null
    private var styleDefaultsSnapshot: ComputedStyleDefaults? = null
    private var appliedComputedStyle: ComputedStyle? = null

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
    private var onDragStartHandler: ((DragStartEvent) -> Unit)? = null
    private var onDragHandler: ((DragEvent) -> Unit)? = null
    private var onDragEndHandler: ((DragEndEvent) -> Unit)? = null
    private var onDragEnterHandler: ((DragEnterEvent) -> Unit)? = null
    private var onDragOverHandler: ((DragOverEvent) -> Unit)? = null
    private var onDragLeaveHandler: ((DragLeaveEvent) -> Unit)? = null
    private var onDropHandler: ((DropEvent) -> Unit)? = null
    private var externalEventBridgeInstalled: Boolean = false

    var onMouseDown: ((MouseDownEvent) -> Unit)?
        get() = onMouseDownHandler
        set(value) {
            onMouseDownHandler = value
            if (value != null) ensureExternalEventBridge()
        }

    var onMouseUp: ((MouseUpEvent) -> Unit)?
        get() = onMouseUpHandler
        set(value) {
            onMouseUpHandler = value
            if (value != null) ensureExternalEventBridge()
        }

    var onMouseClick: ((MouseClickEvent) -> Unit)?
        get() = onMouseClickHandler
        set(value) {
            onMouseClickHandler = value
            if (value != null) ensureExternalEventBridge()
        }

    var onMouseDrag: ((MouseDragEvent) -> Unit)?
        get() = onMouseDragHandler
        set(value) {
            onMouseDragHandler = value
            if (value != null) ensureExternalEventBridge()
        }

    var onMouseWheel: ((MouseWheelEvent) -> Unit)?
        get() = onMouseWheelHandler
        set(value) {
            onMouseWheelHandler = value
            if (value != null) ensureExternalEventBridge()
        }

    var onMouseMove: ((MouseMoveEvent) -> Unit)?
        get() = onMouseMoveHandler
        set(value) {
            onMouseMoveHandler = value
            if (value != null) ensureExternalEventBridge()
        }

    var onKeyDown: ((KeyboardKeyDownEvent) -> Unit)?
        get() = onKeyDownHandler
        set(value) {
            onKeyDownHandler = value
            if (value != null) ensureExternalEventBridge()
        }

    var onKeyUp: ((KeyboardKeyUpEvent) -> Unit)?
        get() = onKeyUpHandler
        set(value) {
            onKeyUpHandler = value
            if (value != null) ensureExternalEventBridge()
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
            if (value != null) ensureExternalEventBridge()
        }

    var onMouseLeave: ((MouseLeaveEvent) -> Unit)?
        get() = onMouseLeaveHandler
        set(value) {
            onMouseLeaveHandler = value
            if (value != null) ensureExternalEventBridge()
        }

    var onMouseOver: ((MouseOverEvent) -> Unit)?
        get() = onMouseOverHandler
        set(value) {
            onMouseOverHandler = value
            if (value != null) ensureExternalEventBridge()
        }

    var onFocusGain: ((FocusGainEvent) -> Unit)?
        get() = onFocusGainHandler
        set(value) {
            onFocusGainHandler = value
            if (value != null) ensureExternalEventBridge()
        }

    var onFocusLose: ((FocusLoseEvent) -> Unit)?
        get() = onFocusLoseHandler
        set(value) {
            onFocusLoseHandler = value
            if (value != null) ensureExternalEventBridge()
        }

    var onInput: ((InputEvent) -> Unit)?
        get() = onInputHandler
        set(value) {
            onInputHandler = value
            if (value != null) ensureExternalEventBridge()
        }

    var onValueChange: ((ValueChangedEvent) -> Unit)?
        get() = onValueChangeHandler
        set(value) {
            onValueChangeHandler = value
            if (value != null) ensureExternalEventBridge()
        }

    var onDragStart: ((DragStartEvent) -> Unit)?
        get() = onDragStartHandler
        set(value) {
            onDragStartHandler = value
            if (value != null) ensureExternalEventBridge()
        }

    var onDrag: ((DragEvent) -> Unit)?
        get() = onDragHandler
        set(value) {
            onDragHandler = value
            if (value != null) ensureExternalEventBridge()
        }

    var onDragEnd: ((DragEndEvent) -> Unit)?
        get() = onDragEndHandler
        set(value) {
            onDragEndHandler = value
            if (value != null) ensureExternalEventBridge()
        }

    var onDragEnter: ((DragEnterEvent) -> Unit)?
        get() = onDragEnterHandler
        set(value) {
            onDragEnterHandler = value
            if (value != null) ensureExternalEventBridge()
        }

    var onDragOver: ((DragOverEvent) -> Unit)?
        get() = onDragOverHandler
        set(value) {
            onDragOverHandler = value
            if (value != null) ensureExternalEventBridge()
        }

    var onDragLeave: ((DragLeaveEvent) -> Unit)?
        get() = onDragLeaveHandler
        set(value) {
            onDragLeaveHandler = value
            if (value != null) ensureExternalEventBridge()
        }

    var onDrop: ((DropEvent) -> Unit)?
        get() = onDropHandler
        set(value) {
            onDropHandler = value
            if (value != null) ensureExternalEventBridge()
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
        this@DOMNode.styleId = props.id
        this@DOMNode.setClassNames(props.className)
        this@DOMNode.styleClasses.addAll(props.classes)
        this@DOMNode.styleDisabled = props.disabled
        this@DOMNode.draggable = props.draggable
        this@DOMNode.droppable = props.droppable ||
            props.onDragEnter != null ||
            props.onDragOver != null ||
            props.onDragLeave != null ||
            props.onDrop != null
        this@DOMNode.onMouseEnter = props.onMouseEnter
        this@DOMNode.onMouseLeave = props.onMouseLeave
        this@DOMNode.onMouseOver = props.onMouseOver
        this@DOMNode.onMouseMove = props.onMouseMove
        this@DOMNode.onMouseDown = props.onMouseDown
        this@DOMNode.onMouseUp = props.onMouseUp
        this@DOMNode.onMouseClick = props.onMouseClick
        this@DOMNode.onMouseDrag = props.onMouseDrag
        this@DOMNode.onMouseWheel = props.onMouseWheel
        this@DOMNode.onKeyDown = props.onKeyPressed ?: props.onKeyDown
        this@DOMNode.onKeyUp = props.onKeyReleased ?: props.onKeyUp
        this@DOMNode.onFocusGain = props.onFocusGain
        this@DOMNode.onFocusLose = props.onFocusLose
        this@DOMNode.onInput = props.onInput
        this@DOMNode.onValueChange = props.onValueChange
        this@DOMNode.onDragStart = props.onDragStart
        this@DOMNode.onDrag = props.onDrag
        this@DOMNode.onDragEnd = props.onDragEnd
        this@DOMNode.onDragEnter = props.onDragEnter
        this@DOMNode.onDragOver = props.onDragOver
        this@DOMNode.onDragLeave = props.onDragLeave
        this@DOMNode.onDrop = props.onDrop
    }

    /** Applies [StyleScope] DSL to this node. */
    fun applyStyle(style: StyleScope.() -> Unit) {
        StyleScope(this).style()
    }

    fun setClassNames(value: String) {
        styleClasses.clear()
        styleClasses.addAll(parseClassNames(value))
    }

    fun addClass(name: String) {
        val normalized = name.trim()
        if (normalized.isNotEmpty()) {
            styleClasses.add(normalized)
        }
    }

    fun setHoveredState(value: Boolean) {
        styleHovered = value && !styleDisabled
    }

    fun setActiveState(value: Boolean) {
        styleActive = value && !styleDisabled
    }

    fun setFocusedState(value: Boolean) {
        styleFocused = value && !styleDisabled
    }

    /**
     * Indicates whether this node should receive pointer drag capture when pressed.
     * Default behavior enables capture for nodes with explicit external onMouseDrag handlers.
     */
    open fun shouldCapturePointerDrag(mouseX: Int, mouseY: Int): Boolean {
        return onMouseDrag != null && bounds.contains(mouseX, mouseY)
    }

    internal fun syncBaseFrom(template: DOMNode) {
        key = template.key
        width = template.width
        height = template.height
        margin = template.margin
        padding = template.padding
        border = template.border
        borderRadius = template.borderRadius
        align = template.align
        styleId = template.styleId
        styleClasses.clear()
        styleClasses.addAll(template.styleClasses)
        inlineStyleDecls = copyStyleDecls(template.inlineStyleDecls)
        styleDisabled = template.styleDisabled
        draggable = template.draggable
        droppable = template.droppable
        onMouseEnter = template.onMouseEnter
        onMouseLeave = template.onMouseLeave
        onMouseOver = template.onMouseOver
        onMouseMove = template.onMouseMove
        onMouseDown = template.onMouseDown
        onMouseUp = template.onMouseUp
        onMouseClick = template.onMouseClick
        onMouseDrag = template.onMouseDrag
        onMouseWheel = template.onMouseWheel
        onKeyDown = template.onKeyDown
        onKeyUp = template.onKeyUp
        onFocusGain = template.onFocusGain
        onFocusLose = template.onFocusLose
        onInput = template.onInput
        onValueChange = template.onValueChange
        onDragStart = template.onDragStart
        onDrag = template.onDrag
        onDragEnd = template.onDragEnd
        onDragEnter = template.onDragEnter
        onDragOver = template.onDragOver
        onDragLeave = template.onDragLeave
        onDrop = template.onDrop
        styleDefaultsSnapshot = null
        appliedComputedStyle = null
    }

    internal fun captureStyleDefaults(): ComputedStyleDefaults {
        val existing = styleDefaultsSnapshot
        if (existing != null) return existing
        val computed = ComputedStyleDefaults(
            margin = margin,
            padding = padding,
            backgroundColor = defaultBackgroundColor(),
            backgroundImage = defaultBackgroundImage(),
            borderColor = border.color,
            borderWidth = maxOf(border.top, border.right, border.bottom, border.left),
            borderRadius = borderRadius,
            foregroundColor = defaultForegroundColor(),
            fontSize = defaultFontSize(),
            width = width,
            height = height,
            align = align
        )
        styleDefaultsSnapshot = computed
        return computed
    }

    internal fun applyComputedStyle(style: ComputedStyle): Boolean {
        val previous = appliedComputedStyle
        if (previous == style) {
            return false
        }
        margin = style.margin
        padding = style.padding
        border = Border.all(style.borderWidth, style.borderColor)
        borderRadius = style.borderRadius
        width = style.width
        height = style.height
        align = style.align
        applyBackgroundColor(style.backgroundColor)
        applyBackgroundImage(style.backgroundImage)
        applyForegroundColor(style.foregroundColor)
        applyFontSize(style.fontSize)
        appliedComputedStyle = style

        if (previous == null) return true
        return previous.margin != style.margin ||
            previous.padding != style.padding ||
            previous.borderWidth != style.borderWidth ||
            previous.borderColor != style.borderColor ||
            previous.borderRadius != style.borderRadius ||
            previous.width != style.width ||
            previous.height != style.height ||
            previous.align != style.align
    }

    protected open fun defaultBackgroundColor(): Int? = null

    protected open fun defaultBackgroundImage(): String? = styledBackgroundImage

    protected open fun defaultForegroundColor(): Int = DsglColors.TEXT

    protected open fun defaultFontSize(): Int? = null

    protected open fun applyBackgroundColor(value: Int?) {}

    protected open fun applyBackgroundImage(value: String?) {
        styledBackgroundImage = value
    }

    protected open fun applyForegroundColor(value: Int) {}

    protected open fun applyFontSize(value: Int?) {}

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

    protected fun addBackgroundImageCommand(out: MutableList<RenderCommand>) {
        val image = styledBackgroundImage ?: return
        out.add(RenderCommand.DrawImage(image, bounds.x, bounds.y, bounds.width, bounds.height))
    }

    private fun ensureExternalEventBridge() {
        if (externalEventBridgeInstalled) return
        externalEventBridgeInstalled = true
        EventBus.run {
            this@DOMNode.addEventListener(Events.MOUSEDOWN) { event: MouseDownEvent ->
                this@DOMNode.onMouseDownHandler?.invoke(event)
            }
            this@DOMNode.addEventListener(Events.MOUSEUP) { event: MouseUpEvent ->
                this@DOMNode.onMouseUpHandler?.invoke(event)
            }
            this@DOMNode.addEventListener(Events.CLICK) { event: MouseClickEvent ->
                this@DOMNode.onMouseClickHandler?.invoke(event)
            }
            this@DOMNode.addEventListener(Events.DRAG) { event: MouseDragEvent ->
                this@DOMNode.onMouseDragHandler?.invoke(event)
            }
            this@DOMNode.addEventListener(Events.WHEEL) { event: MouseWheelEvent ->
                this@DOMNode.onMouseWheelHandler?.invoke(event)
            }
            this@DOMNode.addEventListener(Events.MOUSEMOVE) { event: MouseMoveEvent ->
                this@DOMNode.onMouseMoveHandler?.invoke(event)
            }
            this@DOMNode.addEventListener(Events.KEYDOWN) { event: KeyboardKeyDownEvent ->
                this@DOMNode.onKeyDownHandler?.invoke(event)
            }
            this@DOMNode.addEventListener(Events.KEYUP) { event: KeyboardKeyUpEvent ->
                this@DOMNode.onKeyUpHandler?.invoke(event)
            }
            this@DOMNode.addEventListener(Events.MOUSEENTER) { event: MouseEnterEvent ->
                this@DOMNode.onMouseEnterHandler?.invoke(event)
            }
            this@DOMNode.addEventListener(Events.MOUSELEAVE) { event: MouseLeaveEvent ->
                this@DOMNode.onMouseLeaveHandler?.invoke(event)
            }
            this@DOMNode.addEventListener(Events.MOUSEOVER) { event: MouseOverEvent ->
                this@DOMNode.onMouseOverHandler?.invoke(event)
            }
            this@DOMNode.addEventListener(Events.FOCUS) { event: FocusGainEvent ->
                this@DOMNode.onFocusGainHandler?.invoke(event)
            }
            this@DOMNode.addEventListener(Events.BLUR) { event: FocusLoseEvent ->
                this@DOMNode.onFocusLoseHandler?.invoke(event)
            }
            this@DOMNode.addEventListener(Events.INPUT) { event: InputEvent ->
                this@DOMNode.onInputHandler?.invoke(event)
            }
            this@DOMNode.addEventListener(Events.CHANGE) { event: ValueChangedEvent ->
                this@DOMNode.onValueChangeHandler?.invoke(event)
            }
            this@DOMNode.addEventListener(Events.DRAGSTART) { event: DragStartEvent ->
                this@DOMNode.onDragStartHandler?.invoke(event)
            }
            this@DOMNode.addEventListener(Events.DRAGGING) { event: DragEvent ->
                this@DOMNode.onDragHandler?.invoke(event)
            }
            this@DOMNode.addEventListener(Events.DRAGEND) { event: DragEndEvent ->
                this@DOMNode.onDragEndHandler?.invoke(event)
            }
            this@DOMNode.addEventListener(Events.DRAGENTER) { event: DragEnterEvent ->
                this@DOMNode.onDragEnterHandler?.invoke(event)
            }
            this@DOMNode.addEventListener(Events.DRAGOVER) { event: DragOverEvent ->
                this@DOMNode.onDragOverHandler?.invoke(event)
            }
            this@DOMNode.addEventListener(Events.DRAGLEAVE) { event: DragLeaveEvent ->
                this@DOMNode.onDragLeaveHandler?.invoke(event)
            }
            this@DOMNode.addEventListener(Events.DROP) { event: DropEvent ->
                this@DOMNode.onDropHandler?.invoke(event)
            }
        }
    }

    private fun copyStyleDecls(source: StyleDecls): StyleDecls {
        return StyleDecls(linkedMapOf<StyleProperty, StyleExpression>().apply {
            putAll(source.values)
        })
    }
}

private fun parseClassNames(value: String): Set<String> {
    if (value.isBlank()) return emptySet()
    return value.trim()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .toSet()
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
