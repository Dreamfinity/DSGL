package org.dreamfinity.dsgl.core.dom

import org.dreamfinity.dsgl.core.ComponentProps
import org.dreamfinity.dsgl.core.DsglColors
import org.dreamfinity.dsgl.core.StyleScope
import org.dreamfinity.dsgl.core.animation.AnimationSpec
import org.dreamfinity.dsgl.core.animation.StyleAnimationEngine
import org.dreamfinity.dsgl.core.animation.TransitionSpec
import org.dreamfinity.dsgl.core.font.FontRegistry
import org.dreamfinity.dsgl.core.dnd.*
import org.dreamfinity.dsgl.core.dom.layout.*
import org.dreamfinity.dsgl.core.event.*
import org.dreamfinity.dsgl.core.ref.ElementHandle
import org.dreamfinity.dsgl.core.ref.RefTarget
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.style.*
import org.dreamfinity.dsgl.core.dom.layout.AffineTransform2D
import org.dreamfinity.dsgl.core.text.MinecraftFormattingParser
import org.dreamfinity.dsgl.core.text.ParsedText
import org.dreamfinity.dsgl.core.text.TextStyleFlags
import org.dreamfinity.dsgl.core.text.TextStyleMetrics

data class NodeStyleApplyResult(
    val visualDirty: Boolean,
    val layoutDirty: Boolean
)

/**
 * Base class for all DOM nodes in the retained UI tree.
 *
 * Nodes are measured, laid out, and then converted into [RenderCommand]s by the host.
 */
abstract class DOMNode(
    var key: Any? = null
) {
    companion object {
        private val includeChildrenInRenderPass: ThreadLocal<Boolean> =
            ThreadLocal.withInitial { true }

        internal inline fun <T> withChildrenRenderPass(enabled: Boolean, block: () -> T): T {
            val previous = includeChildrenInRenderPass.get()
            includeChildrenInRenderPass.set(enabled)
            return try {
                block()
            } finally {
                includeChildrenInRenderPass.set(previous)
            }
        }

        internal fun isChildrenRenderPassEnabled(): Boolean = includeChildrenInRenderPass.get()
    }

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
    var display: Display = Display.Block
        set(value) {
            field = value
            if (value == Display.None) {
                styleHovered = false
                styleActive = false
                styleFocused = false
                if (FocusManager.isFocused(this)) {
                    FocusManager.clearFocus()
                }
            }
        }
    var flexDirection: FlexDirection = FlexDirection.Row
    var justifyContent: JustifyContent = JustifyContent.Start
    var alignItems: AlignItems = AlignItems.Stretch
    var justifyItems: JustifyItems = JustifyItems.Stretch
    var gap: Int = 0
    var flexGrow: Float = 0f
    var flexShrink: Float = 1f
    var flexBasis: Int? = null
    var gridColumns: Int = 2
    var gridRows: Int? = null
    var gridAutoFlow: GridAutoFlow = GridAutoFlow.Row
    var gridColumnSpan: Int = 1
    var gridRowSpan: Int = 1
    var textWrap: TextWrap = TextWrap.Wrap
    var textFormatting: TextFormatting = TextFormatting.None
    var fontWeight: FontWeight = FontWeight.Normal
    var fontStyle: org.dreamfinity.dsgl.core.style.FontStyle = org.dreamfinity.dsgl.core.style.FontStyle.Normal
    var textDecoration: TextDecoration = TextDecoration.None
    var textObfuscated: Boolean = false
    var fontId: String? = FontRegistry.DEFAULT_FONT_ID
    var fontSize: Int? = null
    var transform: UiTransform = UiTransform.IDENTITY
    var transformOrigin: TransformOrigin = TransformOrigin.CENTER
    var opacity: Float = 1f
    var transitionSpec: TransitionSpec = TransitionSpec.NONE
    var animationSpecs: List<AnimationSpec> = emptyList()
    var styleId: String? = null
        set(value) {
            if (field == value) return
            field = value
            StyleEngine.markSelectorStateChanged(this)
        }
    val styleClasses: MutableSet<String> = linkedSetOf()
    var inlineStyleDeclarations: StyleDeclarations = StyleDeclarations()
    var styleHovered: Boolean = false
        private set
    var styleActive: Boolean = false
        private set
    var styleFocused: Boolean = false
        private set
    var styleDisabled: Boolean = false
        set(value) {
            val changed = field != value
            field = value
            if (value) {
                styleHovered = false
                styleActive = false
                styleFocused = false
                if (FocusManager.isFocused(this)) {
                    FocusManager.clearFocus()
                }
            }
            if (changed) {
                StyleEngine.markPseudoStateChanged(this)
            }
        }
    var draggable: Boolean = false
    var droppable: Boolean = false
    var dragPreviewMode: DragPreviewMode = DragPreviewMode.GHOST
    var hideSourceWhileDragging: Boolean = false
    var dragPreviewBuilder: (DragPreviewScope.() -> Unit)? = null
    var dragPlaceholderBuilder: (PlaceholderScope.() -> Unit)? = null
    var refTarget: RefTarget<ElementHandle>? = null
    var dragRenderHidden: Boolean = false
    var dragHitTestHidden: Boolean = false
    open val focusable: Boolean = false
    open val styleType: String = "node"
    open var onmouseenter: ((MouseEvent) -> Unit)? = null
    open var onmouseleave: ((MouseEvent) -> Unit)? = null
    open var onmouseover: ((MouseEvent) -> Unit)? = null
    private var styledBackgroundImage: String? = null
    private var styleDefaultsSnapshot: ComputedStyleDefaults? = null
    private var appliedComputedStyle: ComputedStyle? = null
    private var baseForegroundColor: Int = DsglColors.TEXT
    private var animatedTransform: UiTransform? = null
    private var animatedOpacity: Float? = null
    private var animatedColor: Int? = null

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
    private var renderCommandsRevision: Long = 1L

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
    internal open fun measureForLayout(ctx: UiMeasureContext, availableOuterWidth: Int?): Size {
        return measure(ctx)
    }

    /** Measures the node's desired size. */
    open fun measure(ctx: UiMeasureContext): Size {
        if (display == Display.None) {
            return Size(0, 0)
        }
        val contentWidth = width ?: 0
        val contentHeight = height ?: 0
        val totalWidth = contentWidth + padding.horizontal + border.horizontal
        val totalHeight = contentHeight + padding.vertical + border.vertical
        return Size(totalWidth, totalHeight)
    }

    /** Lays out this node and its children for the given bounds. */
    open fun render(ctx: UiMeasureContext, x: Int, y: Int, width: Int, height: Int) {
        if (display == Display.None) {
            val next = Rect(x, y, 0, 0)
            if (bounds != next) {
                bounds = next
                markRenderCommandsDirty()
            }
            return
        }
        val next = Rect(x, y, width, height)
        if (bounds != next) {
            bounds = next
            markRenderCommandsDirty()
        }
        val contentX = x + border.left + padding.left
        val contentY = y + border.top + padding.top
        children.forEach { child ->
            if (child.display == Display.None) return@forEach
            val childSize = child.measure(ctx)
            val childX = contentX + child.margin.left
            val childY = contentY + child.margin.top
            child.render(ctx, childX, childY, childSize.width, childSize.height)
        }
    }

    /** Appends render commands for this node and its children. */
    open fun buildRenderCommands(ctx: UiMeasureContext, out: MutableList<RenderCommand>) {
        if (!isChildrenRenderPassEnabled()) return
        children.forEach { child ->
            child.appendRenderCommands(ctx, out)
        }
    }

    /** Appends render commands if this node is currently visible in render tree. */
    fun appendRenderCommands(ctx: UiMeasureContext, out: MutableList<RenderCommand>) {
        if (!isChildrenRenderPassEnabled()) return
        if (dragRenderHidden || display == Display.None) return
        val activeTransform = effectiveTransform()
        val activeOpacity = effectiveOpacity()
        val transformPushed = !activeTransform.isIdentity()
        val opacityPushed = activeOpacity < 0.999f
        if (transformPushed) {
            val ox = bounds.x + bounds.width * transformOrigin.originX
            val oy = bounds.y + bounds.height * transformOrigin.originY
            out += RenderCommand.PushTransform(
                originX = ox,
                originY = oy,
                translateX = activeTransform.translateX,
                translateY = activeTransform.translateY,
                scaleX = activeTransform.scaleX,
                scaleY = activeTransform.scaleY,
                rotateDeg = activeTransform.rotateDeg
            )
        }
        if (opacityPushed) {
            out += RenderCommand.PushOpacity(activeOpacity)
        }
        buildRenderCommands(ctx, out)
        if (opacityPushed) {
            out += RenderCommand.PopOpacity
        }
        if (transformPushed) {
            out += RenderCommand.PopTransform
        }
    }

    /** Handles a click; return true when consumed. */
    open fun handleClick(event: MouseClickEvent): Boolean = false

    /** Dispatches a click through this node and its subtree. */
    fun dispatchClick(event: MouseClickEvent): Boolean {
        return dispatchClickInternal(this, event, AffineTransform2D.IDENTITY)
    }

    /** Returns true if the mouse event is within current bounds. */
    fun hovered(event: MouseEvent): Boolean {
        return isHitTestVisible() && containsGlobalPoint(event.mouseX, event.mouseY)
    }

    fun isHitTestVisible(): Boolean {
        return !dragHitTestHidden && display != Display.None
    }

    /** Applies event handlers from [ComponentProps] to this node. */
    fun applyHandlers(props: ComponentProps) {
        this@DOMNode.styleId = props.id
        this@DOMNode.setClassNames(props.className)
        if (props.classes.isNotEmpty()) {
            val before = this@DOMNode.styleClasses.size
            this@DOMNode.styleClasses.addAll(props.classes)
            if (this@DOMNode.styleClasses.size != before) {
                StyleEngine.markSelectorStateChanged(this@DOMNode)
            }
        }
        this@DOMNode.styleDisabled = props.disabled
        this@DOMNode.draggable = props.draggable
        this@DOMNode.droppable = props.droppable ||
                props.onDragEnter != null ||
                props.onDragOver != null ||
                props.onDragLeave != null ||
                props.onDrop != null
        this@DOMNode.dragPreviewMode = props.dragPreviewMode
        this@DOMNode.hideSourceWhileDragging = props.hideSourceWhileDragging
        this@DOMNode.dragPreviewBuilder = props.dragPreview
        this@DOMNode.dragPlaceholderBuilder = props.dragPlaceholder
        this@DOMNode.refTarget = props.ref
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
        val parsed = parseClassNames(value)
        if (styleClasses == parsed) return
        styleClasses.clear()
        styleClasses.addAll(parsed)
        StyleEngine.markSelectorStateChanged(this)
    }

    fun addClass(name: String) {
        val normalized = name.trim()
        if (normalized.isNotEmpty()) {
            if (styleClasses.add(normalized)) {
                StyleEngine.markSelectorStateChanged(this)
            }
        }
    }

    fun setHoveredState(value: Boolean) {
        val normalized = value && !styleDisabled
        if (styleHovered == normalized) return
        styleHovered = normalized
        StyleEngine.markPseudoStateChanged(this)
    }

    fun setActiveState(value: Boolean) {
        val normalized = value && !styleDisabled
        if (styleActive == normalized) return
        styleActive = normalized
        StyleEngine.markPseudoStateChanged(this)
    }

    fun setFocusedState(value: Boolean) {
        val normalized = value && !styleDisabled
        if (styleFocused == normalized) return
        styleFocused = normalized
        StyleEngine.markPseudoStateChanged(this)
    }

    /**
     * Indicates whether this node should receive pointer drag capture when pressed.
     * Default behavior enables capture for nodes with explicit external onMouseDrag handlers.
     */
    open fun shouldCapturePointerDrag(mouseX: Int, mouseY: Int): Boolean {
        return onMouseDrag != null && containsGlobalPoint(mouseX, mouseY)
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
        display = template.display
        flexDirection = template.flexDirection
        justifyContent = template.justifyContent
        alignItems = template.alignItems
        justifyItems = template.justifyItems
        gap = template.gap
        flexGrow = template.flexGrow
        flexShrink = template.flexShrink
        flexBasis = template.flexBasis
        gridColumns = template.gridColumns
        gridRows = template.gridRows
        gridAutoFlow = template.gridAutoFlow
        gridColumnSpan = template.gridColumnSpan
        gridRowSpan = template.gridRowSpan
        textWrap = template.textWrap
        textFormatting = template.textFormatting
        fontWeight = template.fontWeight
        fontStyle = template.fontStyle
        textDecoration = template.textDecoration
        textObfuscated = template.textObfuscated
        fontId = template.fontId
        fontSize = template.fontSize
        transform = template.transform
        transformOrigin = template.transformOrigin
        opacity = template.opacity
        transitionSpec = template.transitionSpec
        animationSpecs = template.animationSpecs
        styleId = template.styleId
        styleClasses.clear()
        styleClasses.addAll(template.styleClasses)
        inlineStyleDeclarations = copyStyleDecls(template.inlineStyleDeclarations)
        styleDisabled = template.styleDisabled
        draggable = template.draggable
        droppable = template.droppable
        dragPreviewMode = template.dragPreviewMode
        hideSourceWhileDragging = template.hideSourceWhileDragging
        dragPreviewBuilder = template.dragPreviewBuilder
        dragPlaceholderBuilder = template.dragPlaceholderBuilder
        refTarget = template.refTarget
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
        markRenderCommandsDirty()
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
            fontId = defaultFontId(),
            fontSize = defaultFontSize(),
            fontWeight = fontWeight,
            fontStyle = fontStyle,
            textDecoration = textDecoration,
            obfuscated = textObfuscated,
            width = width,
            height = height,
            align = align,
            display = display,
            flexDirection = flexDirection,
            justifyContent = justifyContent,
            alignItems = alignItems,
            justifyItems = justifyItems,
            gap = gap,
            flexGrow = flexGrow,
            flexShrink = flexShrink,
            flexBasis = flexBasis,
            gridColumns = gridColumns,
            gridRows = gridRows,
            gridAutoFlow = gridAutoFlow,
            gridColumnSpan = gridColumnSpan,
            gridRowSpan = gridRowSpan,
            textWrap = textWrap,
            textFormatting = textFormatting,
            transform = transform,
            transformOrigin = transformOrigin,
            opacity = opacity
        )
        styleDefaultsSnapshot = computed
        return computed
    }

    internal fun applyComputedStyle(style: ComputedStyle): NodeStyleApplyResult {
        val previous = appliedComputedStyle
        if (previous == style) {
            StyleAnimationEngine.onComputedStyleApplied(this, previous, style)
            return NodeStyleApplyResult(
                visualDirty = false,
                layoutDirty = false
            )
        }
        margin = style.margin
        padding = style.padding
        border = Border.all(style.borderWidth, style.borderColor)
        borderRadius = style.borderRadius
        width = style.width
        height = style.height
        align = style.align
        display = style.display
        flexDirection = style.flexDirection
        justifyContent = style.justifyContent
        alignItems = style.alignItems
        justifyItems = style.justifyItems
        gap = style.gap
        flexGrow = style.flexGrow
        flexShrink = style.flexShrink
        flexBasis = style.flexBasis
        gridColumns = style.gridColumns
        gridRows = style.gridRows
        gridAutoFlow = style.gridAutoFlow
        gridColumnSpan = style.gridColumnSpan
        gridRowSpan = style.gridRowSpan
        textWrap = style.textWrap
        textFormatting = style.textFormatting
        fontWeight = style.fontWeight
        fontStyle = style.fontStyle
        textDecoration = style.textDecoration
        textObfuscated = style.obfuscated
        fontId = style.fontId
        fontSize = style.fontSize
        transform = style.transform
        transformOrigin = style.transformOrigin
        opacity = style.opacity
        applyBackgroundColor(style.backgroundColor)
        applyBackgroundImage(style.backgroundImage)
        baseForegroundColor = style.foregroundColor
        applyForegroundColor(style.foregroundColor)
        applyFontId(style.fontId)
        applyFontSize(style.fontSize)
        appliedComputedStyle = style
        StyleAnimationEngine.onComputedStyleApplied(this, previous, style)
        markRenderCommandsDirty()

        if (previous == null) {
            return NodeStyleApplyResult(
                visualDirty = true,
                layoutDirty = true
            )
        }
        val layoutDirty = previous.margin != style.margin ||
                previous.padding != style.padding ||
                previous.borderWidth != style.borderWidth ||
                previous.borderColor != style.borderColor ||
                previous.borderRadius != style.borderRadius ||
                previous.width != style.width ||
                previous.height != style.height ||
                previous.align != style.align ||
                previous.display != style.display ||
                previous.flexDirection != style.flexDirection ||
                previous.justifyContent != style.justifyContent ||
                previous.alignItems != style.alignItems ||
                previous.justifyItems != style.justifyItems ||
                previous.gap != style.gap ||
                previous.flexGrow != style.flexGrow ||
                previous.flexShrink != style.flexShrink ||
                previous.flexBasis != style.flexBasis ||
                previous.gridColumns != style.gridColumns ||
                previous.gridRows != style.gridRows ||
                previous.gridAutoFlow != style.gridAutoFlow ||
                previous.gridColumnSpan != style.gridColumnSpan ||
                previous.gridRowSpan != style.gridRowSpan ||
                previous.textWrap != style.textWrap ||
                previous.textFormatting != style.textFormatting ||
                previous.fontWeight != style.fontWeight ||
                previous.fontStyle != style.fontStyle ||
                previous.textDecoration != style.textDecoration ||
                previous.obfuscated != style.obfuscated ||
                previous.fontId != style.fontId ||
                previous.fontSize != style.fontSize
        return NodeStyleApplyResult(
            visualDirty = true,
            layoutDirty = layoutDirty
        )
    }

    internal fun appliedComputedStyleSnapshot(): ComputedStyle? = appliedComputedStyle

    internal fun applyAnimationVisuals(transform: UiTransform?, opacity: Float?, color: Int?): Boolean {
        val normalizedOpacity = opacity?.coerceIn(0f, 1f)
        val changed =
            animatedTransform != transform ||
                animatedOpacity != normalizedOpacity ||
                animatedColor != color
        animatedTransform = transform
        animatedOpacity = normalizedOpacity
        animatedColor = color
        applyForegroundColor(animatedColor ?: baseForegroundColor)
        if (changed) {
            markRenderCommandsDirty()
        }
        return changed
    }

    internal fun renderCommandsSignature(nowMs: Long): Long {
        var result = renderCommandsRevision
        result = 31L * result + bounds.hashCode().toLong()
        result = 31L * result + if (dragRenderHidden) 1L else 0L
        result = 31L * result + display.ordinal.toLong()
        result = 31L * result + effectiveTransform().hashCode().toLong()
        result = 31L * result + java.lang.Float.floatToIntBits(effectiveOpacity()).toLong()
        result = 31L * result + volatileRenderCommandsSignature(nowMs)
        return result
    }

    protected open fun volatileRenderCommandsSignature(nowMs: Long): Long = 0L

    protected fun markRenderCommandsDirty() {
        renderCommandsRevision += 1L
    }

    fun effectiveTransform(): UiTransform = animatedTransform ?: transform

    fun effectiveOpacity(): Float = (animatedOpacity ?: opacity).coerceIn(0f, 1f)

    fun animationColorOverride(): Int? = animatedColor

    fun localTransformMatrix(): AffineTransform2D {
        val active = effectiveTransform()
        if (active.isIdentity()) return AffineTransform2D.IDENTITY

        val ox = bounds.x + bounds.width * transformOrigin.originX
        val oy = bounds.y + bounds.height * transformOrigin.originY
        val origin = AffineTransform2D.translation(ox, oy)
        val originBack = AffineTransform2D.translation(-ox, -oy)
        val translate = AffineTransform2D.translation(active.translateX, active.translateY)
        val rotate = AffineTransform2D.rotation(active.rotateDeg)
        val scale = AffineTransform2D.scale(active.scaleX, active.scaleY)
        return translate.times(origin).times(rotate).times(scale).times(originBack)
    }

    fun worldTransformMatrix(): AffineTransform2D {
        val chain = ArrayList<DOMNode>(8)
        var current: DOMNode? = this
        while (current != null) {
            chain += current
            current = current.parent
        }
        chain.reverse()
        var result = AffineTransform2D.IDENTITY
        chain.forEach { node ->
            result = result.times(node.localTransformMatrix())
        }
        return result
    }

    fun containsGlobalPoint(x: Int, y: Int): Boolean {
        val inverse = worldTransformMatrix().inverseOrNull() ?: return false
        val local = inverse.transform(x.toFloat(), y.toFloat())
        return bounds.contains(local.first, local.second)
    }

    protected open fun defaultBackgroundColor(): Int? = null

    protected open fun defaultBackgroundImage(): String? = styledBackgroundImage

    protected open fun defaultForegroundColor(): Int = DsglColors.TEXT

    protected open fun defaultFontId(): String? = FontRegistry.DEFAULT_FONT_ID

    protected open fun defaultFontSize(): Int? = null

    protected open fun applyBackgroundColor(value: Int?) {}

    protected open fun applyBackgroundImage(value: String?) {
        styledBackgroundImage = value
    }

    protected open fun applyForegroundColor(value: Int) {}

    protected open fun applyFontId(value: String?) {}

    protected open fun applyFontSize(value: Int?) {}

    protected fun resolveFontSize(ctx: UiMeasureContext): Int {
        return ctx.fontHeight(fontId, fontSize).coerceAtLeast(1)
    }

    protected fun parseTextForFormatting(rawText: String): ParsedText {
        return MinecraftFormattingParser.parse(rawText, textFormatting)
    }

    protected fun baseTextStyleFlags(): TextStyleFlags {
        return TextStyleFlags(
            bold = fontWeight == FontWeight.Bold,
            italic = fontStyle == FontStyle.Italic,
            underline = textDecoration == TextDecoration.Underline ||
                    textDecoration == TextDecoration.UnderlineStrikethrough,
            strikethrough = textDecoration == TextDecoration.Strikethrough ||
                    textDecoration == TextDecoration.UnderlineStrikethrough,
            obfuscated = textObfuscated
        )
    }

    protected fun measureText(ctx: UiMeasureContext, text: String): Int {
        val parsed = parseTextForFormatting(text)
        val plainText = parsed.plainText
        val base = ctx.measureText(plainText, fontId, fontSize)
        val extraBold = TextStyleMetrics.boldExtraPxForRange(
            plainText = plainText,
            spans = parsed.spans,
            baseFlags = baseTextStyleFlags()
        )
        return base + extraBold
    }

    protected fun drawTextCommand(
        text: String,
        x: Int,
        y: Int,
        color: Int,
        styleSpans: List<RenderCommand.TextStyleSpan> = emptyList()
    ): RenderCommand.DrawText {
        val baseFlags = baseTextStyleFlags()
        return RenderCommand.DrawText(
            text = text,
            x = x,
            y = y,
            color = color,
            fontId = fontId,
            fontSize = fontSize,
            textFormatting = textFormatting,
            bold = baseFlags.bold,
            italic = baseFlags.italic,
            underline = baseFlags.underline,
            strikethrough = baseFlags.strikethrough,
            obfuscated = baseFlags.obfuscated,
            textStyleSpans = styleSpans,
            sourceKey = key?.toString()
        )
    }

    protected fun contentX(): Int = bounds.x + border.left + padding.left

    protected fun contentY(): Int = bounds.y + border.top + padding.top

    protected fun contentWidth(): Int =
        (bounds.width - border.horizontal - padding.horizontal).coerceAtLeast(0)

    protected fun contentHeight(): Int =
        (bounds.height - border.vertical - padding.vertical).coerceAtLeast(0)

    /**
     * Optional scroll offsets exposed for tooling overlays (e.g., inspector).
     */
    open fun inspectorScrollOffset(): Pair<Int, Int>? = null

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

    private fun copyStyleDecls(source: StyleDeclarations): StyleDeclarations {
        return StyleDeclarations(
            values = linkedMapOf<StyleProperty, StyleExpression>().apply {
                putAll(source.values)
            },
            importantProperties = linkedSetOf<StyleProperty>().apply {
                addAll(source.importantProperties)
            }
        )
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
