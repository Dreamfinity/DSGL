package org.dreamfinity.dsgl.core.dom

import org.dreamfinity.dsgl.core.ComponentProps
import org.dreamfinity.dsgl.core.DsglColors
import org.dreamfinity.dsgl.core.StyleScope
import org.dreamfinity.dsgl.core.animation.AnimationSpec
import org.dreamfinity.dsgl.core.animation.StyleAnimationEngine
import org.dreamfinity.dsgl.core.animation.TransitionSpec
import org.dreamfinity.dsgl.core.debug.ScrollPerformanceCounters
import org.dreamfinity.dsgl.core.dnd.*
import org.dreamfinity.dsgl.core.dom.layout.*
import org.dreamfinity.dsgl.core.event.*
import org.dreamfinity.dsgl.core.font.FontRegistry
import org.dreamfinity.dsgl.core.ref.ElementHandle
import org.dreamfinity.dsgl.core.ref.RefTarget
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.style.*
import org.dreamfinity.dsgl.core.text.MinecraftFormattingParser
import org.dreamfinity.dsgl.core.text.ParsedText
import org.dreamfinity.dsgl.core.text.TextStyleFlags
import org.dreamfinity.dsgl.core.text.TextStyleMetrics
import org.dreamfinity.dsgl.core.dom.text.ResolvedTextMetrics
import kotlin.math.roundToInt

data class NodeStyleApplyResult(
    val visualDirty: Boolean,
    val layoutDirty: Boolean
)

data class ScrollAxisState(
    val overflow: Overflow,
    val scrollContainer: Boolean,
    val clipsToViewport: Boolean,
    val scrollbarPresent: Boolean,
    val scrollbarGutter: Int
)

data class ScrollContainerState(
    val baseViewportRect: Rect,
    val viewportRect: Rect,
    val contentExtent: Size,
    val scrollX: Int,
    val scrollY: Int,
    val maxScrollX: Int,
    val maxScrollY: Int,
    val horizontalScrollbarGutter: Int,
    val verticalScrollbarGutter: Int,
    val axisX: ScrollAxisState,
    val axisY: ScrollAxisState
)

data class ScrollAnimationDebugState(
    val targetX: Int,
    val targetY: Int,
    val displayedX: Double,
    val displayedY: Double,
    val resolvedX: Int,
    val resolvedY: Int
)

data class ScrollSessionSnapshot(
    val targetX: Int,
    val targetY: Int,
    val displayedX: Double,
    val displayedY: Double,
    val resolvedX: Int,
    val resolvedY: Int,
    val dragSession: ScrollbarDragSessionDebugState?
)

data class ScrollbarDragSessionDebugState(
    val verticalAxis: Boolean,
    val trackStartPx: Int,
    val trackLengthPx: Int,
    val thumbLengthPx: Int,
    val maxThumbTravelPx: Int,
    val maxScroll: Int,
    val grabOffsetPx: Int,
    val initialResolvedScroll: Int
)

data class ScrollbarVisualAxis(
    val trackRect: Rect,
    val thumbRect: Rect,
    val maxScroll: Int,
    val scrollOffset: Int
)

data class ScrollbarVisualState(
    val horizontal: ScrollbarVisualAxis?,
    val vertical: ScrollbarVisualAxis?
)

private data class ScrollbarResolution(
    val horizontalPresent: Boolean,
    val verticalPresent: Boolean,
    val horizontalGutter: Int,
    val verticalGutter: Int,
    val viewportWidth: Int,
    val viewportHeight: Int
)

private data class NativeFontMetricsPx(
    val lineHeightPx: Int,
    val ascenderPx: Float,
    val descenderPx: Float
)

private data class ScrollbarDragSession(
    val axis: ScrollbarAxis,
    val trackStartPx: Int,
    val trackLengthPx: Int,
    val thumbLengthPx: Int,
    val maxThumbTravelPx: Int,
    val maxScroll: Int,
    val grabOffsetPx: Int,
    val initialResolvedScroll: Int
)

private enum class ScrollbarAxis {
    Horizontal,
    Vertical
}

/**
 * Base class for all DOM nodes in the retained UI tree.
 *
 * Nodes are measured, laid out, and then converted into [RenderCommand]s by the host.
 */
abstract class DOMNode(
    var key: Any? = null
) {
    companion object {
        const val NORMAL_LINE_HEIGHT_MULTIPLIER: Float = 1.2f

        private val includeChildrenInRenderPass: ThreadLocal<Boolean> =
            ThreadLocal.withInitial { true }
        private val inheritedChildRenderClipStack: ThreadLocal<MutableList<Rect?>> =
            ThreadLocal.withInitial { ArrayList() }

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

        private inline fun <T> withInheritedChildRenderClipRect(clipRect: Rect?, block: () -> T): T {
            val stack = inheritedChildRenderClipStack.get()
            stack.add(clipRect)
            return try {
                block()
            } finally {
                stack.removeAt(stack.lastIndex)
            }
        }

        private fun currentInheritedChildRenderClipRect(): Rect? {
            val stack = inheritedChildRenderClipStack.get()
            return stack.lastOrNull()
        }
    }

    val children: MutableList<DOMNode> = mutableListOf()
    var parent: DOMNode? = null
    var bounds: Rect = Rect(0, 0, 0, 0)
    var width: Int? = null
    var height: Int? = null
    var minWidth: Int? = null
    var minHeight: Int? = null
    var maxWidth: Int? = null
    var maxHeight: Int? = null
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
                styleOpen = false
                if (FocusManager.isFocused(this)) {
                    FocusManager.clearFocus()
                }
            }
        }
    var position: PositionMode = PositionMode.Static
        set(value) {
            if (field == value) return
            field = value
            markRenderCommandsDirty()
        }
    var zIndex: Int = 0
        set(value) {
            if (field == value) return
            field = value
            markRenderCommandsDirty()
        }
    var overflow: Overflow = Overflow.Visible
        set(value) {
            if (field == value && overflowX == value && overflowY == value) return
            field = value
            overflowX = value
            overflowY = value
            markRenderCommandsDirty()
        }
    var overflowX: Overflow = Overflow.Visible
        set(value) {
            if (field == value) return
            field = value
            markRenderCommandsDirty()
        }
    var overflowY: Overflow = Overflow.Visible
        set(value) {
            if (field == value) return
            field = value
            markRenderCommandsDirty()
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
    var styleOpen: Boolean = false
        private set
    var styleDisabled: Boolean = false
        set(value) {
            val changed = field != value
            field = value
            if (value) {
                styleHovered = false
                styleActive = false
                styleFocused = false
                styleOpen = false
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
    private var marginStyleValue: LengthInsets = LengthInsets.fromInsets(margin)
    private var paddingStyleValue: LengthInsets = LengthInsets.fromInsets(padding)
    private var borderWidthStyleValue: CssLength =
        CssLength.px(maxOf(border.top, border.right, border.bottom, border.left))
    private var borderRadiusStyleValue: CssLength = CssLength.px(borderRadius)
    private var widthStyleValue: CssLength? = null
    private var heightStyleValue: CssLength? = null
    private var minWidthStyleValue: CssLength? = null
    private var minHeightStyleValue: CssLength? = null
    private var maxWidthStyleValue: CssLength? = null
    private var maxHeightStyleValue: CssLength? = null
    private var leftStyleValue: CssLength? = null
    private var topStyleValue: CssLength? = null
    private var rightStyleValue: CssLength? = null
    private var bottomStyleValue: CssLength? = null
    private var relativeVisualOffsetXPx: Int = 0
    private var relativeVisualOffsetYPx: Int = 0
    private var gapStyleValue: CssLength = CssLength.px(gap)
    private var flexBasisStyleValue: CssLength? = null
    private var borderColorStyleValue: Int = border.color
    private var baseForegroundColor: Int = DsglColors.TEXT
    private var animatedTransform: UiTransform? = null
    private var animatedOpacity: Float? = null
    private var animatedColor: Int? = null
    private var scrollOffsetTargetX: Int = 0
    private var scrollOffsetTargetY: Int = 0
    private var scrollOffsetDisplayedX: Double = 0.0
    private var scrollOffsetDisplayedY: Double = 0.0
    private var scrollOffsetResolvedX: Int = 0
    private var scrollOffsetResolvedY: Int = 0
    private var scrollLayoutDirty: Boolean = false
    private var contentLayoutScrollX: Int = 0
    private var contentLayoutScrollY: Int = 0
    private var activeScrollbarDragAxis: ScrollbarAxis? = null
    private var scrollbarDragSession: ScrollbarDragSession? = null

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
    internal fun resolveLayoutStyleValues(
        ctx: UiMeasureContext,
        parentContentWidth: Int?,
        parentContentHeight: Int?
    ) {
        if (appliedComputedStyle == null) {
            return
        }
        val context = lengthResolveContext(ctx, parentContentWidth, parentContentHeight)
        margin = marginStyleValue.resolveToInsets(context)
        padding = paddingStyleValue.resolveToInsets(context)
        val borderWidthPx = borderWidthStyleValue
            .resolvePx(context, LengthPercentBase.ContainerWidth)
            .roundToInt()
            .coerceAtLeast(0)
        border = Border.all(borderWidthPx, borderColorStyleValue)
        borderRadius = borderRadiusStyleValue
            .resolvePx(context, LengthPercentBase.ContainerWidth)
            .roundToInt()
            .coerceAtLeast(0)
        val resolvedWidth = widthStyleValue
            ?.resolvePx(context, LengthPercentBase.ContainerWidth)
            ?.roundToInt()
            ?.coerceAtLeast(0)
        val resolvedHeight = heightStyleValue
            ?.resolvePx(context, LengthPercentBase.ContainerHeight)
            ?.roundToInt()
            ?.coerceAtLeast(0)
        val resolvedMinWidth = minWidthStyleValue
            ?.resolvePx(context, LengthPercentBase.ContainerWidth)
            ?.roundToInt()
            ?.coerceAtLeast(0)
        val resolvedMinHeight = minHeightStyleValue
            ?.resolvePx(context, LengthPercentBase.ContainerHeight)
            ?.roundToInt()
            ?.coerceAtLeast(0)
        val resolvedMaxWidth = maxWidthStyleValue
            ?.resolvePx(context, LengthPercentBase.ContainerWidth)
            ?.roundToInt()
            ?.coerceAtLeast(0)
        val resolvedMaxHeight = maxHeightStyleValue
            ?.resolvePx(context, LengthPercentBase.ContainerHeight)
            ?.roundToInt()
            ?.coerceAtLeast(0)
        minWidth = resolvedMinWidth
        minHeight = resolvedMinHeight
        maxWidth = resolvedMaxWidth
        maxHeight = resolvedMaxHeight
        width = resolvedWidth?.let {
            clampContentLengthToConstraints(
                length = it,
                minConstraint = resolvedMinWidth,
                maxConstraint = resolvedMaxWidth
            )
        }
        height = resolvedHeight?.let {
            clampContentLengthToConstraints(
                length = it,
                minConstraint = resolvedMinHeight,
                maxConstraint = resolvedMaxHeight
            )
        }
        gap = gapStyleValue
            .resolvePx(context, LengthPercentBase.ContainerWidth)
            .roundToInt()
            .coerceAtLeast(0)
        flexBasis = flexBasisStyleValue
            ?.resolvePx(context, LengthPercentBase.ContainerWidth)
            ?.roundToInt()
            ?.coerceAtLeast(0)

        val resolvedRelativeOffsetX = resolveRelativeVisualOffsetXPx(context)
        val resolvedRelativeOffsetY = resolveRelativeVisualOffsetYPx(context)
        if (relativeVisualOffsetXPx != resolvedRelativeOffsetX || relativeVisualOffsetYPx != resolvedRelativeOffsetY) {
            relativeVisualOffsetXPx = resolvedRelativeOffsetX
            relativeVisualOffsetYPx = resolvedRelativeOffsetY
            markRenderCommandsDirty()
        }
    }

    private fun resolveRelativeVisualOffsetXPx(context: LengthResolveContext): Int {
        if (position != PositionMode.Relative) return 0
        val resolution = PositionedLayoutModel.resolveHorizontalOffset(left = leftStyleValue, right = rightStyleValue)
        val value = resolution.value ?: return 0
        val magnitude = value.resolvePx(context, LengthPercentBase.ContainerWidth).roundToInt()
        return when (resolution.sourceProperty) {
            StyleProperty.LEFT -> magnitude
            StyleProperty.RIGHT -> -magnitude
            else -> 0
        }
    }

    private fun resolveRelativeVisualOffsetYPx(context: LengthResolveContext): Int {
        if (position != PositionMode.Relative) return 0
        val resolution = PositionedLayoutModel.resolveVerticalOffset(top = topStyleValue, bottom = bottomStyleValue)
        val value = resolution.value ?: return 0
        val magnitude = value.resolvePx(context, LengthPercentBase.ContainerHeight).roundToInt()
        return when (resolution.sourceProperty) {
            StyleProperty.TOP -> magnitude
            StyleProperty.BOTTOM -> -magnitude
            else -> 0
        }
    }

    private fun resolveStickyVisualOffsetsPx(): Pair<Int, Int> {
        ScrollPerformanceCounters.incrementStickyResolutionCalls()
        if (position != PositionMode.Sticky) return 0 to 0
        val offsetX = resolveStickyHorizontalVisualOffsetXPx()
        val offsetY = resolveStickyVerticalVisualOffsetYPx()
        return offsetX to offsetY
    }

    private fun resolveStickyHorizontalVisualOffsetXPx(): Int {
        ScrollPerformanceCounters.incrementStickyHorizontalResolutionCalls()
        val insetResolution = stickyHorizontalInsetResolutionContract()
        if (!insetResolution.active) return 0

        val referenceScrollContainer = stickyReferenceScrollContainerHorizontal()
        val viewportRect = referenceScrollContainer.scrollContainerState().viewportRect
        val containingBlockRect = stickyContainingBlockForPositioningRect()
        val offsetContext = positioningOffsetResolveContext(viewportRect)
        val insetLength = insetResolution.value ?: return 0
        val insetPx = insetLength.resolvePx(offsetContext, LengthPercentBase.ContainerWidth).roundToInt()

        return StickyLayoutModel.resolveHorizontalVisualOffsetPx(
            baseX = bounds.x,
            nodeWidth = bounds.width.coerceAtLeast(0),
            viewportRect = viewportRect,
            containingBlockRect = containingBlockRect,
            insetResolution = insetResolution,
            insetPx = insetPx
        )
    }

    private fun resolveStickyVerticalVisualOffsetYPx(): Int {
        ScrollPerformanceCounters.incrementStickyVerticalResolutionCalls()
        val insetResolution = stickyVerticalInsetResolutionContract()
        if (!insetResolution.active) return 0

        val referenceScrollContainer = stickyReferenceScrollContainerVertical()
        val viewportRect = referenceScrollContainer.scrollContainerState().viewportRect
        val containingBlockRect = stickyContainingBlockForPositioningRect()
        val offsetContext = positioningOffsetResolveContext(viewportRect)
        val insetLength = insetResolution.value ?: return 0
        val insetPx = insetLength.resolvePx(offsetContext, LengthPercentBase.ContainerHeight).roundToInt()

        return StickyLayoutModel.resolveVerticalVisualOffsetPx(
            baseY = bounds.y,
            nodeHeight = bounds.height.coerceAtLeast(0),
            viewportRect = viewportRect,
            containingBlockRect = containingBlockRect,
            insetResolution = insetResolution,
            insetPx = insetPx
        )
    }

    private fun stickyContainingBlockForPositioningRect(): Rect {
        val containing = stickyContainingBlockForPositioning()
        return Rect(
            x = containing.bounds.x,
            y = containing.bounds.y,
            width = containing.bounds.width.coerceAtLeast(0),
            height = containing.bounds.height.coerceAtLeast(0)
        )
    }

    internal fun resolveFlexBasisForAxis(
        ctx: UiMeasureContext,
        parentContentWidth: Int?,
        parentContentHeight: Int?,
        axis: FlexDirection
    ): Int? {
        val context = lengthResolveContext(ctx, parentContentWidth, parentContentHeight)
        val percentBase = if (axis == FlexDirection.Row) {
            LengthPercentBase.ContainerWidth
        } else {
            LengthPercentBase.ContainerHeight
        }
        return flexBasisStyleValue
            ?.resolvePx(context, percentBase)
            ?.roundToInt()
            ?.coerceAtLeast(0)
    }

    private fun lengthResolveContext(
        ctx: UiMeasureContext,
        parentContentWidth: Int?,
        parentContentHeight: Int?
    ): LengthResolveContext {
        val rootFontSizePx = rootNode().resolveComputedFontSizePx().toFloat()
        val inheritedFontSizePx = (
                parent?.resolveComputedFontSizePx()
                    ?: resolveComputedFontSizePx()
                ).toFloat()
        val currentFontSizePx = resolveComputedFontSizePx().toFloat()
        return LengthResolveContext(
            viewportWidthPx = StyleEngine.viewportWidthPx().toFloat(),
            viewportHeightPx = StyleEngine.viewportHeightPx().toFloat(),
            containingBlockWidthPx = parentContentWidth?.toFloat(),
            containingBlockHeightPx = parentContentHeight?.toFloat(),
            rootFontSizePx = rootFontSizePx,
            currentFontSizePx = currentFontSizePx,
            inheritedFontSizePx = inheritedFontSizePx
        )
    }

    private fun rootNode(): DOMNode {
        var current: DOMNode = this
        while (current.parent != null) {
            current = current.parent!!
        }
        return current
    }


    internal fun participatesInPositionedOrderingModel(): Boolean {
        return PositionedLayoutModel.isPositioned(this)
    }

    internal fun rootStackingScopeForPositioning(): DOMNode {
        return PositionedLayoutModel.rootStackingScope(this)
    }

    internal fun sharesRootStackingScopeForPositioning(other: DOMNode): Boolean {
        return PositionedLayoutModel.sharesRootStackingScope(this, other)
    }

    internal fun rootStackingContextIdentityForPositioning(): PositionedLayoutModel.RootStackingContextId {
        return PositionedLayoutModel.rootStackingContextId(this)
    }

    internal fun stackingContextScaffoldForTraversalOwner(): PositionedLayoutModel.StackingContext {
        return PositionedLayoutModel.stackingContextScaffold(this)
    }

    internal fun containingBlockForAbsolutePositioning(): DOMNode {
        return PositionedLayoutModel.containingBlockForAbsolute(this)
    }

    internal fun fixedViewportRootForPositioning(): DOMNode {
        return PositionedLayoutModel.fixedViewportRoot(this)
    }

    internal fun stickyReferenceScrollContainerVertical(): DOMNode {
        return StickyLayoutModel.nearestStickyScrollContainerVertical(this)
    }

    internal fun stickyReferenceScrollContainerHorizontal(): DOMNode {
        return StickyLayoutModel.nearestStickyScrollContainerHorizontal(this)
    }

    internal fun stickyContainingBlockForPositioning(): DOMNode {
        return StickyLayoutModel.stickyContainingBlock(this)
    }

    internal fun stickyHorizontalInsetResolutionContract(): StickyLayoutModel.StickyHorizontalInsetResolution {
        return StickyLayoutModel.resolveHorizontalInsets(
            left = leftStyleValue,
            right = rightStyleValue
        )
    }

    internal fun stickyVerticalInsetResolutionContract(): StickyLayoutModel.StickyInsetResolution {
        return StickyLayoutModel.resolveVerticalInsets(
            top = topStyleValue,
            bottom = bottomStyleValue
        )
    }

    internal fun stickyPositionedGeometryIntegrationPoint(): StickyLayoutModel.PositionedGeometryIntegrationPoint {
        return StickyLayoutModel.positionedGeometryIntegrationPoint()
    }

    internal fun isRemovedFromNormalFlowForPositioning(): Boolean {
        return position == PositionMode.Absolute || position == PositionMode.Fixed
    }

    internal fun resolveAbsoluteLayoutRect(
        ctx: UiMeasureContext,
        desiredX: Int,
        desiredY: Int,
        desiredWidth: Int,
        desiredHeight: Int
    ): Rect {
        if (position != PositionMode.Absolute) {
            return Rect(
                x = desiredX,
                y = desiredY,
                width = desiredWidth.coerceAtLeast(0),
                height = desiredHeight.coerceAtLeast(0)
            )
        }

        val containingBlockRect = absoluteContainingBlockRect()
        val offsetContext = positioningOffsetResolveContext(ctx, containingBlockRect)
        val resolvedX = resolvePositionedX(
            context = offsetContext,
            containerRect = containingBlockRect,
            desiredX = desiredX,
            desiredWidth = desiredWidth
        )
        val resolvedY = resolvePositionedY(
            context = offsetContext,
            containerRect = containingBlockRect,
            desiredY = desiredY,
            desiredHeight = desiredHeight
        )
        return Rect(
            x = resolvedX,
            y = resolvedY,
            width = desiredWidth.coerceAtLeast(0),
            height = desiredHeight.coerceAtLeast(0)
        )
    }

    internal fun resolveFixedLayoutRect(
        ctx: UiMeasureContext,
        desiredX: Int,
        desiredY: Int,
        desiredWidth: Int,
        desiredHeight: Int
    ): Rect {
        if (position != PositionMode.Fixed) {
            return Rect(
                x = desiredX,
                y = desiredY,
                width = desiredWidth.coerceAtLeast(0),
                height = desiredHeight.coerceAtLeast(0)
            )
        }

        val viewportRect = fixedViewportAnchorRect()
        val offsetContext = positioningOffsetResolveContext(ctx, viewportRect)
        val resolvedX = resolvePositionedX(
            context = offsetContext,
            containerRect = viewportRect,
            desiredX = desiredX,
            desiredWidth = desiredWidth
        )
        val resolvedY = resolvePositionedY(
            context = offsetContext,
            containerRect = viewportRect,
            desiredY = desiredY,
            desiredHeight = desiredHeight
        )
        return Rect(
            x = resolvedX,
            y = resolvedY,
            width = desiredWidth.coerceAtLeast(0),
            height = desiredHeight.coerceAtLeast(0)
        )
    }

    internal fun orderedChildrenForPaintTraversal(): List<DOMNode> {
        return PositionedLayoutModel.orderedChildrenForPaint(this)
    }

    internal fun orderedChildrenForHitTestingTraversal(): List<DOMNode> {
        return PositionedLayoutModel.orderedChildrenForHitTesting(this)
    }

    internal fun clampMeasuredOuterSize(size: Size): Size {
        val extrasWidth = (padding.horizontal + border.horizontal).coerceAtLeast(0)
        val extrasHeight = (padding.vertical + border.vertical).coerceAtLeast(0)
        val contentWidth = (size.width - extrasWidth).coerceAtLeast(0)
        val contentHeight = (size.height - extrasHeight).coerceAtLeast(0)
        val clampedContentWidth = clampContentLengthToConstraints(
            length = contentWidth,
            minConstraint = minWidth,
            maxConstraint = maxWidth
        )
        val clampedContentHeight = clampContentLengthToConstraints(
            length = contentHeight,
            minConstraint = minHeight,
            maxConstraint = maxHeight
        )
        return Size(
            width = clampedContentWidth + extrasWidth,
            height = clampedContentHeight + extrasHeight
        )
    }

    private fun clampContentLengthToConstraints(
        length: Int,
        minConstraint: Int?,
        maxConstraint: Int?
    ): Int {
        val normalizedMin = minConstraint?.coerceAtLeast(0)
        val normalizedMax = maxConstraint?.coerceAtLeast(0)
        val effectiveMax = when {
            normalizedMin != null && normalizedMax != null -> normalizedMax.coerceAtLeast(normalizedMin)
            else -> normalizedMax
        }
        var result = length.coerceAtLeast(0)
        if (normalizedMin != null && result < normalizedMin) {
            result = normalizedMin
        }
        if (effectiveMax != null && result > effectiveMax) {
            result = effectiveMax
        }
        return result
    }

    private fun absoluteContainingBlockRect(): Rect {
        val containing = containingBlockForAbsolutePositioning()
        val state = containing.scrollContainerState()
        return Rect(
            x = state.viewportRect.x - state.scrollX,
            y = state.viewportRect.y - state.scrollY,
            width = state.viewportRect.width.coerceAtLeast(0),
            height = state.viewportRect.height.coerceAtLeast(0)
        )
    }

    internal fun fixedViewportClipRectForPromotedParticipation(): Rect {
        val root = fixedViewportRootForPositioning()
        val state = root.scrollContainerState()
        return Rect(
            x = state.viewportRect.x,
            y = state.viewportRect.y,
            width = state.viewportRect.width.coerceAtLeast(0),
            height = state.viewportRect.height.coerceAtLeast(0)
        )
    }

    private fun fixedViewportAnchorRect(): Rect {
        return fixedViewportClipRectForPromotedParticipation()
    }

    private fun positioningOffsetResolveContext(
        @Suppress("UNUSED_PARAMETER") ctx: UiMeasureContext,
        containerRect: Rect
    ): LengthResolveContext {
        return positioningOffsetResolveContext(containerRect)
    }

    private fun positioningOffsetResolveContext(containerRect: Rect): LengthResolveContext {
        val rootFontSizePx = rootNode().resolveComputedFontSizePx().toFloat()
        val inheritedFontSizePx = (parent?.resolveComputedFontSizePx() ?: resolveComputedFontSizePx()).toFloat()
        val currentFontSizePx = resolveComputedFontSizePx().toFloat()
        return LengthResolveContext(
            viewportWidthPx = StyleEngine.viewportWidthPx().toFloat(),
            viewportHeightPx = StyleEngine.viewportHeightPx().toFloat(),
            containingBlockWidthPx = containerRect.width.coerceAtLeast(0).toFloat(),
            containingBlockHeightPx = containerRect.height.coerceAtLeast(0).toFloat(),
            rootFontSizePx = rootFontSizePx,
            currentFontSizePx = currentFontSizePx,
            inheritedFontSizePx = inheritedFontSizePx
        )
    }

    private fun resolvePositionedX(
        context: LengthResolveContext,
        containerRect: Rect,
        desiredX: Int,
        desiredWidth: Int
    ): Int {
        val resolution = PositionedLayoutModel.resolveHorizontalOffset(left = leftStyleValue, right = rightStyleValue)
        val value = resolution.value ?: return desiredX
        val magnitude = value.resolvePx(context, LengthPercentBase.ContainerWidth).roundToInt()
        return when (resolution.sourceProperty) {
            StyleProperty.LEFT -> containerRect.x + magnitude
            StyleProperty.RIGHT -> containerRect.x + containerRect.width - desiredWidth - magnitude
            else -> desiredX
        }
    }

    private fun resolvePositionedY(
        context: LengthResolveContext,
        containerRect: Rect,
        desiredY: Int,
        desiredHeight: Int
    ): Int {
        val resolution = PositionedLayoutModel.resolveVerticalOffset(top = topStyleValue, bottom = bottomStyleValue)
        val value = resolution.value ?: return desiredY
        val magnitude = value.resolvePx(context, LengthPercentBase.ContainerHeight).roundToInt()
        return when (resolution.sourceProperty) {
            StyleProperty.TOP -> containerRect.y + magnitude
            StyleProperty.BOTTOM -> containerRect.y + containerRect.height - desiredHeight - magnitude
            else -> desiredY
        }
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
            resetContentLayoutScroll()
            return
        }
        val next = Rect(x, y, width, height)
        if (bounds != next) {
            bounds = next
            markRenderCommandsDirty()
        }
        val scrollState = scrollContainerState()
        val layoutScrollX = scrollState.scrollX
        val layoutScrollY = scrollState.scrollY
        val layoutContentX = scrollState.viewportRect.x - layoutScrollX
        val layoutContentY = scrollState.viewportRect.y - layoutScrollY
        val availableOuterWidth = scrollState.viewportRect.width
        val availableOuterHeight = scrollState.viewportRect.height
        children.forEach { child ->
            if (child.display == Display.None) return@forEach
            child.resolveLayoutStyleValues(
                ctx = ctx,
                parentContentWidth = availableOuterWidth,
                parentContentHeight = availableOuterHeight
            )
            val childSize = child.clampMeasuredOuterSize(child.measureForLayout(ctx, availableOuterWidth))
            val childX = layoutContentX + child.margin.left
            val childY = layoutContentY + child.margin.top
            child.render(ctx, childX, childY, childSize.width, childSize.height)
        }
        setContentLayoutScroll(layoutScrollX, layoutScrollY)
        scrollContainerState()
    }

    /** Appends render commands for this node and its children. */
    open fun buildRenderCommands(ctx: UiMeasureContext, out: MutableList<RenderCommand>) {
        val scrollState = scrollContainerState()
        appendScrollbarCommands(out, scrollState)
        if (!isChildrenRenderPassEnabled()) return
        val localClipRect = overflowViewportRect()
        val inheritedClipRect = currentInheritedChildRenderClipRect()
        if (localClipRect != null) {
            val effectiveClipRect = if (inheritedClipRect != null) {
                inheritedClipRect.intersection(localClipRect) ?: return
            } else {
                localClipRect
            }
            if (effectiveClipRect.width <= 0 || effectiveClipRect.height <= 0) {
                return
            }
            out += RenderCommand.PushClip(
                x = effectiveClipRect.x,
                y = effectiveClipRect.y,
                width = effectiveClipRect.width.coerceAtLeast(0),
                height = effectiveClipRect.height.coerceAtLeast(0)
            )
            withInheritedChildRenderClipRect(effectiveClipRect) {
                orderedChildrenForPaintTraversal().forEach { child ->
                    child.appendRenderCommands(ctx, out)
                }
            }
            out += RenderCommand.PopClip
            return
        }
        withInheritedChildRenderClipRect(inheritedClipRect) {
            orderedChildrenForPaintTraversal().forEach { child ->
                child.appendRenderCommands(ctx, out)
            }
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
        return dispatchClickInternal(
            element = this,
            event = event,
            parentTransform = AffineTransform2D.IDENTITY,
            parentInputClipRect = null
        )
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

    fun setOpenState(value: Boolean) {
        val normalized = value && !styleDisabled
        if (styleOpen == normalized) return
        styleOpen = normalized
        StyleEngine.markPseudoStateChanged(this)
    }

    /**
     * Indicates whether this node should receive pointer drag capture when pressed.
     * Default behavior enables capture for nodes with explicit external onMouseDrag handlers.
     */
    open fun shouldCapturePointerDrag(mouseX: Int, mouseY: Int): Boolean {
        if (onMouseDrag != null && containsGlobalPoint(mouseX, mouseY)) {
            return true
        }
        return resolveScrollbarDragAxisAt(mouseX, mouseY) != null
    }

    open fun beginPointerCapture(mouseX: Int, mouseY: Int, button: MouseButton) {
        if (styleDisabled || button != MouseButton.LEFT) return
        beginScrollbarPointerDrag(mouseX, mouseY)
    }

    open fun continuePointerCapture(
        mouseX: Int,
        mouseY: Int,
        mouseDX: Int,
        mouseDY: Int,
        button: MouseButton
    ) {
        if (styleDisabled || button != MouseButton.LEFT) return
        updateScrollbarPointerDrag(mouseX, mouseY)
    }

    open fun endPointerCapture(mouseX: Int, mouseY: Int, button: MouseButton) {
        if (button == MouseButton.LEFT) {
            val draggedAxis = activeScrollbarDragAxis
            val wasDragging = draggedAxis != null
            activeScrollbarDragAxis = null
            scrollbarDragSession = null
            if (wasDragging) {
                settleReleasedDragAxis(draggedAxis)
                markRenderCommandsDirty()
            }
        }
    }

    open fun cancelPointerCapture() {
        val draggedAxis = activeScrollbarDragAxis
        val wasDragging = draggedAxis != null
        activeScrollbarDragAxis = null
        scrollbarDragSession = null
        if (wasDragging) {
            settleReleasedDragAxis(draggedAxis)
            markRenderCommandsDirty()
        }
    }

    private fun settleReleasedDragAxis(axis: ScrollbarAxis?) {
        when (axis) {
            ScrollbarAxis.Vertical -> {
                val settled = scrollOffsetResolvedY.coerceAtLeast(0)
                if (scrollOffsetDisplayedY != settled.toDouble()) {
                    scrollOffsetDisplayedY = settled.toDouble()
                }
                if (scrollOffsetTargetY != settled) {
                    scrollOffsetTargetY = settled
                }
            }

            ScrollbarAxis.Horizontal -> {
                val settled = scrollOffsetResolvedX.coerceAtLeast(0)
                if (scrollOffsetDisplayedX != settled.toDouble()) {
                    scrollOffsetDisplayedX = settled.toDouble()
                }
                if (scrollOffsetTargetX != settled) {
                    scrollOffsetTargetX = settled
                }
            }

            null -> Unit
        }
    }

    open fun handleGenericWheel(mouseX: Int, mouseY: Int, delta: Int): Boolean {
        if (styleDisabled || delta == 0) return false
        if (!containsGlobalPoint(mouseX, mouseY)) return false
        val state = scrollContainerState()
        if (!state.axisX.scrollContainer && !state.axisY.scrollContainer) return false

        val direction = if (delta > 0) -1 else 1
        val steps = (kotlin.math.abs(delta) / 120).coerceAtLeast(1)
        val amount = direction * steps * wheelScrollStepPx().coerceAtLeast(1)
        val horizontalIntent = KeyModifiers.shiftDown
        if (horizontalIntent) {
            if (state.maxScrollX <= 0) return false
            val currentTargetX = scrollOffsetTargetX.coerceIn(0, state.maxScrollX)
            val nextX = (currentTargetX + amount).coerceIn(0, state.maxScrollX)
            if (nextX == currentTargetX) return false
            applyScrollTargets(nextX, scrollOffsetTargetY, immediate = false)
            return true
        }

        if (state.maxScrollY <= 0) return false
        val currentTargetY = scrollOffsetTargetY.coerceIn(0, state.maxScrollY)
        val nextY = (currentTargetY + amount).coerceIn(0, state.maxScrollY)
        if (nextY == currentTargetY) return false
        applyScrollTargets(scrollOffsetTargetX, nextY, immediate = false)
        return true
    }

    open fun hasGenericWheelHandling(mouseX: Int, mouseY: Int): Boolean {
        if (styleDisabled) return false
        if (!containsGlobalPoint(mouseX, mouseY)) return false
        val state = scrollContainerState()
        return state.maxScrollY > 0 || state.maxScrollX > 0
    }

    internal fun syncBaseFrom(template: DOMNode) {
        key = template.key
        width = template.width
        height = template.height
        minWidth = template.minWidth
        minHeight = template.minHeight
        maxWidth = template.maxWidth
        maxHeight = template.maxHeight
        margin = template.margin
        padding = template.padding
        border = template.border
        borderRadius = template.borderRadius
        align = template.align
        display = template.display
        position = template.position
        zIndex = template.zIndex
        overflow = template.overflow
        overflowX = template.overflowX
        overflowY = template.overflowY
        flexDirection = template.flexDirection
        justifyContent = template.justifyContent
        alignItems = template.alignItems
        justifyItems = template.justifyItems
        gap = template.gap
        flexGrow = template.flexGrow
        flexShrink = template.flexShrink
        flexBasis = template.flexBasis
        marginStyleValue = template.marginStyleValue
        paddingStyleValue = template.paddingStyleValue
        borderWidthStyleValue = template.borderWidthStyleValue
        borderRadiusStyleValue = template.borderRadiusStyleValue
        widthStyleValue = template.widthStyleValue
        heightStyleValue = template.heightStyleValue
        minWidthStyleValue = template.minWidthStyleValue
        minHeightStyleValue = template.minHeightStyleValue
        maxWidthStyleValue = template.maxWidthStyleValue
        maxHeightStyleValue = template.maxHeightStyleValue
        leftStyleValue = template.leftStyleValue
        topStyleValue = template.topStyleValue
        rightStyleValue = template.rightStyleValue
        bottomStyleValue = template.bottomStyleValue
        relativeVisualOffsetXPx = template.relativeVisualOffsetXPx
        relativeVisualOffsetYPx = template.relativeVisualOffsetYPx
        gapStyleValue = template.gapStyleValue
        flexBasisStyleValue = template.flexBasisStyleValue
        borderColorStyleValue = template.borderColorStyleValue
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
        val defaultFontSize = defaultFontSize()
        val computed = ComputedStyleDefaults(
            margin = LengthInsets.fromInsets(margin),
            padding = LengthInsets.fromInsets(padding),
            backgroundColor = defaultBackgroundColor(),
            backgroundImage = defaultBackgroundImage(),
            borderColor = border.color,
            borderWidth = CssLength.px(maxOf(border.top, border.right, border.bottom, border.left)),
            borderRadius = CssLength.px(borderRadius),
            foregroundColor = defaultForegroundColor(),
            fontId = defaultFontId(),
            fontSize = defaultFontSize,
            fontSizeValue = defaultFontSize?.let { CssLength.px(it) },
            fontWeight = fontWeight,
            fontStyle = fontStyle,
            textDecoration = textDecoration,
            obfuscated = textObfuscated,
            width = width?.let { CssLength.px(it) },
            height = height?.let { CssLength.px(it) },
            minWidth = minWidth?.let { CssLength.px(it) },
            minHeight = minHeight?.let { CssLength.px(it) },
            maxWidth = maxWidth?.let { CssLength.px(it) },
            maxHeight = maxHeight?.let { CssLength.px(it) },
            align = align,
            display = display,
            position = position,
            left = leftStyleValue,
            top = topStyleValue,
            right = rightStyleValue,
            bottom = bottomStyleValue,
            zIndex = zIndex,
            flexDirection = flexDirection,
            justifyContent = justifyContent,
            alignItems = alignItems,
            justifyItems = justifyItems,
            gap = CssLength.px(gap),
            flexGrow = flexGrow,
            flexShrink = flexShrink,
            flexBasis = flexBasis?.let { CssLength.px(it) },
            gridColumns = gridColumns,
            gridRows = gridRows,
            gridAutoFlow = gridAutoFlow,
            gridColumnSpan = gridColumnSpan,
            gridRowSpan = gridRowSpan,
            overflow = overflow,
            overflowX = overflowX,
            overflowY = overflowY,
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
        marginStyleValue = style.margin
        paddingStyleValue = style.padding
        borderWidthStyleValue = style.borderWidth
        borderRadiusStyleValue = style.borderRadius
        widthStyleValue = style.width
        heightStyleValue = style.height
        minWidthStyleValue = style.minWidth
        minHeightStyleValue = style.minHeight
        maxWidthStyleValue = style.maxWidth
        maxHeightStyleValue = style.maxHeight
        leftStyleValue = style.left
        topStyleValue = style.top
        rightStyleValue = style.right
        bottomStyleValue = style.bottom
        gapStyleValue = style.gap
        flexBasisStyleValue = style.flexBasis
        borderColorStyleValue = style.borderColor
        align = style.align
        display = style.display
        position = style.position
        zIndex = style.zIndex
        overflow = style.overflow
        overflowX = style.overflowX
        overflowY = style.overflowY
        flexDirection = style.flexDirection
        justifyContent = style.justifyContent
        alignItems = style.alignItems
        justifyItems = style.justifyItems
        flexGrow = style.flexGrow
        flexShrink = style.flexShrink
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
                previous.minWidth != style.minWidth ||
                previous.minHeight != style.minHeight ||
                previous.maxWidth != style.maxWidth ||
                previous.maxHeight != style.maxHeight ||
                previous.align != style.align ||
                previous.display != style.display ||
                previous.position != style.position ||
                previous.left != style.left ||
                previous.top != style.top ||
                previous.right != style.right ||
                previous.bottom != style.bottom ||
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
                previous.overflowX != style.overflowX ||
                previous.overflowY != style.overflowY ||
                previous.textWrap != style.textWrap ||
                previous.textFormatting != style.textFormatting ||
                previous.fontWeight != style.fontWeight ||
                previous.fontStyle != style.fontStyle ||
                previous.textDecoration != style.textDecoration ||
                previous.obfuscated != style.obfuscated ||
                previous.fontId != style.fontId ||
                previous.fontSize != style.fontSize ||
                previous.lineHeight != style.lineHeight
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
        result = 31L * result + position.ordinal.toLong()
        result = 31L * result + zIndex.toLong()
        result = 31L * result + overflow.ordinal.toLong()
        result = 31L * result + overflowX.ordinal.toLong()
        result = 31L * result + overflowY.ordinal.toLong()
        result = 31L * result + scrollOffsetTargetX.toLong()
        result = 31L * result + scrollOffsetTargetY.toLong()
        result = 31L * result + scrollOffsetResolvedX.toLong()
        result = 31L * result + scrollOffsetResolvedY.toLong()
        result = 31L * result + effectiveTransform().hashCode().toLong()
        result = 31L * result + java.lang.Float.floatToIntBits(effectiveOpacity()).toLong()
        result = 31L * result + volatileRenderCommandsSignature(nowMs)
        return result
    }

    protected open fun volatileRenderCommandsSignature(nowMs: Long): Long = 0L

    protected fun markRenderCommandsDirty() {
        renderCommandsRevision += 1L
    }

    fun effectiveTransform(): UiTransform {
        val base = animatedTransform ?: transform
        val relativeOffsetX = if (position == PositionMode.Relative) relativeVisualOffsetXPx else 0
        val relativeOffsetY = if (position == PositionMode.Relative) relativeVisualOffsetYPx else 0
        val (stickyOffsetX, stickyOffsetY) = resolveStickyVisualOffsetsPx()
        if (relativeOffsetX == 0 && relativeOffsetY == 0 && stickyOffsetX == 0 && stickyOffsetY == 0) {
            return base
        }
        return base.copy(
            translateX = base.translateX + relativeOffsetX.toFloat() + stickyOffsetX.toFloat(),
            translateY = base.translateY + relativeOffsetY.toFloat() + stickyOffsetY.toFloat()
        )
    }

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
        if (!isPointInsideEffectiveAncestorClip(x, y)) {
            return false
        }
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

    protected fun resolveComputedFontSizePx(): Int {
        return (appliedComputedStyleSnapshot()?.fontSize ?: fontSize ?: 16).coerceAtLeast(1)
    }

    protected fun resolveEffectiveLineHeight(ctx: UiMeasureContext): Int {
        return resolveTextMetrics(ctx).lineHeightPx
    }

    protected fun resolveEffectiveLineTopLeading(ctx: UiMeasureContext): Int {
        return resolveTextMetrics(ctx).topLeadingPx.roundToInt().coerceAtLeast(0)
    }

    protected fun resolveEffectiveAscenderPx(ctx: UiMeasureContext): Float {
        return resolveTextMetrics(ctx).ascenderPx
    }

    protected fun resolveEffectiveDescenderPx(ctx: UiMeasureContext): Float {
        return resolveTextMetrics(ctx).descenderPx
    }

    protected fun resolveTextMetrics(ctx: UiMeasureContext): ResolvedTextMetrics {
        val fontSizePx = resolveComputedFontSizePx().coerceAtLeast(1)
        val nativeMetrics = resolveNativeFontMetrics(ctx, fontSizePx)
        val fallbackFontHeightPx = resolveFontSize(ctx).coerceAtLeast(1)
        val fallbackNormalLineHeightPx = (fallbackFontHeightPx * NORMAL_LINE_HEIGHT_MULTIPLIER)
            .roundToInt()
            .coerceAtLeast(fallbackFontHeightPx)
            .coerceAtLeast(1)

        val nativeLineHeightPx = nativeMetrics?.lineHeightPx ?: fallbackNormalLineHeightPx
        val ascenderPx = nativeMetrics?.ascenderPx ?: (fallbackFontHeightPx * 0.8f)
        val descenderPx = nativeMetrics?.descenderPx
            ?: (nativeLineHeightPx - ascenderPx).coerceAtLeast(0f)

        val computedLineHeight =
            when (val computedLineHeight = appliedComputedStyleSnapshot()?.lineHeight ?: LineHeightValue.Normal) {
                LineHeightValue.Normal -> nativeLineHeightPx
                is LineHeightValue.Length -> {
                    val currentFontSizePx = fontSizePx.toFloat()
                    val context = LengthResolveContext(
                        rootFontSizePx = currentFontSizePx,
                        currentFontSizePx = currentFontSizePx,
                        inheritedFontSizePx = currentFontSizePx
                    )
                    computedLineHeight.value
                        .resolvePx(context, LengthPercentBase.CurrentFontSize)
                        .roundToInt()
                        .coerceAtLeast(1)
                }
            }

        val extraLeadingPx = (computedLineHeight - nativeLineHeightPx).coerceAtLeast(0).toFloat()
        val topLeadingPx = extraLeadingPx / 2f
        val bottomLeadingPx = extraLeadingPx - topLeadingPx
        return ResolvedTextMetrics(
            fontSizePx = fontSizePx,
            lineHeightPx = computedLineHeight,
            nativeLineHeightPx = nativeLineHeightPx,
            ascenderPx = ascenderPx,
            descenderPx = descenderPx,
            topLeadingPx = topLeadingPx,
            bottomLeadingPx = bottomLeadingPx
        )
    }

    private fun resolveNativeFontMetrics(ctx: UiMeasureContext, fontSizePx: Int): NativeFontMetricsPx? {
        val metrics = ctx.fontLineMetrics(fontId, fontSizePx) ?: return null
        if (metrics.emSize <= 0f || metrics.lineHeightEm <= 0f) return null
        val scalePx = fontSizePx / metrics.emSize
        val lineHeightPx = kotlin.math.ceil(metrics.lineHeightEm * scalePx).toInt().coerceAtLeast(1)
        val ascenderPx = (metrics.ascenderEm * scalePx).coerceAtLeast(0f)
        val descenderPx = kotlin.math.abs(metrics.descenderEm * scalePx)
        return NativeFontMetricsPx(
            lineHeightPx = lineHeightPx,
            ascenderPx = ascenderPx,
            descenderPx = descenderPx
        )
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
        val metrics = resolveTextMetrics(ctx)
        val parsed = parseTextForFormatting(text)
        val plainText = parsed.plainText
        val base = ctx.measureText(plainText, fontId, metrics.fontSizePx)
        val extraBold = TextStyleMetrics.boldExtraPxForRange(
            plainText = plainText,
            spans = parsed.spans,
            baseFlags = baseTextStyleFlags()
        )
        return base + extraBold
    }

    protected fun drawTextCommand(
        ctx: UiMeasureContext,
        text: String,
        x: Int,
        y: Int,
        color: Int,
        styleSpans: List<RenderCommand.TextStyleSpan> = emptyList()
    ): RenderCommand.DrawText {
        val metrics = resolveTextMetrics(ctx)
        val baseFlags = baseTextStyleFlags()
        return RenderCommand.DrawText(
            text = text,
            x = x,
            y = y,
            color = color,
            fontId = fontId,
            fontSize = metrics.fontSizePx,
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

    protected fun viewportContentX(): Int {
        return scrollContainerState().viewportRect.x
    }

    protected fun viewportContentY(): Int {
        return scrollContainerState().viewportRect.y
    }

    protected fun viewportContentWidth(): Int {
        return scrollContainerState().viewportRect.width
    }

    protected fun viewportContentHeight(): Int {
        return scrollContainerState().viewportRect.height
    }

    protected fun setContentLayoutScroll(scrollX: Int, scrollY: Int) {
        contentLayoutScrollX = scrollX
        contentLayoutScrollY = scrollY
    }

    protected fun resetContentLayoutScroll() {
        setContentLayoutScroll(0, 0)
    }

    protected fun childContentOriginX(): Int {
        val state = scrollContainerState()
        setContentLayoutScroll(state.scrollX, state.scrollY)
        return state.viewportRect.x - state.scrollX
    }

    protected fun childContentOriginY(): Int {
        val state = scrollContainerState()
        setContentLayoutScroll(state.scrollX, state.scrollY)
        return state.viewportRect.y - state.scrollY
    }

    fun setScrollOffsets(scrollX: Int, scrollY: Int) {
        applyScrollTargets(scrollX = scrollX, scrollY = scrollY, immediate = true)
    }

    internal fun advanceScrollAnimationsRecursively(dtSeconds: Double): Boolean {
        var changed = advanceScrollAnimation(dtSeconds)
        children.forEach { child ->
            if (child.advanceScrollAnimationsRecursively(dtSeconds)) {
                changed = true
            }
        }
        return changed
    }

    internal fun consumeScrollLayoutDirtyRecursively(): Boolean {
        var dirty = scrollLayoutDirty
        scrollLayoutDirty = false
        children.forEach { child ->
            if (child.consumeScrollLayoutDirtyRecursively()) {
                dirty = true
            }
        }
        return dirty
    }

    internal fun debugScrollAnimationState(): ScrollAnimationDebugState {
        return ScrollAnimationDebugState(
            targetX = scrollOffsetTargetX,
            targetY = scrollOffsetTargetY,
            displayedX = scrollOffsetDisplayedX,
            displayedY = scrollOffsetDisplayedY,
            resolvedX = scrollOffsetResolvedX,
            resolvedY = scrollOffsetResolvedY
        )
    }

    internal fun captureScrollSessionSnapshot(): ScrollSessionSnapshot {
        val animation = debugScrollAnimationState()
        return ScrollSessionSnapshot(
            targetX = animation.targetX.coerceAtLeast(0),
            targetY = animation.targetY.coerceAtLeast(0),
            displayedX = if (animation.displayedX.isFinite()) animation.displayedX.coerceAtLeast(0.0) else 0.0,
            displayedY = if (animation.displayedY.isFinite()) animation.displayedY.coerceAtLeast(0.0) else 0.0,
            resolvedX = animation.resolvedX.coerceAtLeast(0),
            resolvedY = animation.resolvedY.coerceAtLeast(0),
            dragSession = debugScrollbarDragSession()
        )
    }

    internal fun restoreScrollSessionSnapshot(snapshot: ScrollSessionSnapshot?) {
        if (snapshot == null) return
        var changed = false

        val nextTargetX = snapshot.targetX.coerceAtLeast(0)
        val nextTargetY = snapshot.targetY.coerceAtLeast(0)
        val nextDisplayedX = if (snapshot.displayedX.isFinite()) snapshot.displayedX.coerceAtLeast(0.0) else 0.0
        val nextDisplayedY = if (snapshot.displayedY.isFinite()) snapshot.displayedY.coerceAtLeast(0.0) else 0.0
        val nextResolvedX = snapshot.resolvedX.coerceAtLeast(0)
        val nextResolvedY = snapshot.resolvedY.coerceAtLeast(0)

        if (scrollOffsetTargetX != nextTargetX) {
            scrollOffsetTargetX = nextTargetX
            changed = true
        }
        if (scrollOffsetTargetY != nextTargetY) {
            scrollOffsetTargetY = nextTargetY
            changed = true
        }
        if (scrollOffsetDisplayedX != nextDisplayedX) {
            scrollOffsetDisplayedX = nextDisplayedX
            changed = true
        }
        if (scrollOffsetDisplayedY != nextDisplayedY) {
            scrollOffsetDisplayedY = nextDisplayedY
            changed = true
        }
        if (scrollOffsetResolvedX != nextResolvedX) {
            scrollOffsetResolvedX = nextResolvedX
            scrollLayoutDirty = true
            changed = true
        }
        if (scrollOffsetResolvedY != nextResolvedY) {
            scrollOffsetResolvedY = nextResolvedY
            scrollLayoutDirty = true
            changed = true
        }
        if (contentLayoutScrollX != nextResolvedX) {
            contentLayoutScrollX = nextResolvedX
            changed = true
        }
        if (contentLayoutScrollY != nextResolvedY) {
            contentLayoutScrollY = nextResolvedY
            changed = true
        }

        val drag = snapshot.dragSession
        if (drag == null) {
            if (activeScrollbarDragAxis != null || scrollbarDragSession != null) {
                activeScrollbarDragAxis = null
                scrollbarDragSession = null
                changed = true
            }
        } else {
            val nextAxis = if (drag.verticalAxis) ScrollbarAxis.Vertical else ScrollbarAxis.Horizontal
            val nextSession = ScrollbarDragSession(
                axis = nextAxis,
                trackStartPx = drag.trackStartPx,
                trackLengthPx = drag.trackLengthPx,
                thumbLengthPx = drag.thumbLengthPx,
                maxThumbTravelPx = drag.maxThumbTravelPx,
                maxScroll = drag.maxScroll.coerceAtLeast(0),
                grabOffsetPx = drag.grabOffsetPx.coerceAtLeast(0),
                initialResolvedScroll = drag.initialResolvedScroll.coerceAtLeast(0)
            )
            if (activeScrollbarDragAxis != nextAxis || scrollbarDragSession != nextSession) {
                activeScrollbarDragAxis = nextAxis
                scrollbarDragSession = nextSession
                changed = true
            }
        }

        if (changed) {
            markRenderCommandsDirty()
        }
    }

    private fun applyScrollTargets(scrollX: Int, scrollY: Int, immediate: Boolean) {
        val normalizedX = scrollX.coerceAtLeast(0)
        val normalizedY = scrollY.coerceAtLeast(0)
        var changed = false
        if (scrollOffsetTargetX != normalizedX) {
            scrollOffsetTargetX = normalizedX
            changed = true
        }
        if (scrollOffsetTargetY != normalizedY) {
            scrollOffsetTargetY = normalizedY
            changed = true
        }
        if (immediate) {
            val immediateDisplayX = normalizedX.toDouble()
            val immediateDisplayY = normalizedY.toDouble()
            if (scrollOffsetDisplayedX != immediateDisplayX) {
                scrollOffsetDisplayedX = immediateDisplayX
                changed = true
            }
            if (scrollOffsetDisplayedY != immediateDisplayY) {
                scrollOffsetDisplayedY = immediateDisplayY
                changed = true
            }
            if (changed) {
                scrollLayoutDirty = true
            }
        }
        if (changed) {
            markRenderCommandsDirty()
        }
    }

    private fun advanceScrollAnimation(dtSeconds: Double): Boolean {
        val state = scrollContainerState()
        val normalizedDt = dtSeconds.coerceAtLeast(0.0)
        var changed = false
        changed = advanceScrollAnimationAxis(
            scrollContainer = state.axisX.scrollContainer,
            maxScroll = state.maxScrollX,
            vertical = false,
            dtSeconds = normalizedDt
        ) || changed
        changed = advanceScrollAnimationAxis(
            scrollContainer = state.axisY.scrollContainer,
            maxScroll = state.maxScrollY,
            vertical = true,
            dtSeconds = normalizedDt
        ) || changed
        return changed
    }

    private fun advanceScrollAnimationAxis(
        scrollContainer: Boolean,
        maxScroll: Int,
        vertical: Boolean,
        dtSeconds: Double
    ): Boolean {
        if (!scrollContainer) {
            return normalizeScrollAxisForNonScrollable(vertical)
        }

        val clampedTarget = targetScrollAxis(vertical).coerceIn(0, maxScroll)
        if (isScrollbarAxisActivelyDragged(vertical)) {
            val clamped = resolvedScrollAxis(vertical).coerceIn(0, maxScroll)
            var changed = false
            val directDisplayed = clamped.toDouble()
            if (displayedScrollAxis(vertical) != directDisplayed) {
                setDisplayedScrollAxis(vertical, directDisplayed)
                changed = true
            }
            if (resolvedScrollAxis(vertical) != clamped) {
                setResolvedScrollAxis(vertical, clamped)
                scrollLayoutDirty = true
                markRenderCommandsDirty()
                changed = true
            }
            if (targetScrollAxis(vertical) != clamped) {
                setTargetScrollAxis(vertical, clamped)
                markRenderCommandsDirty()
                changed = true
            }
            return changed
        }

        val currentDisplayed = displayedScrollAxis(vertical).coerceIn(0.0, maxScroll.toDouble())
        val normalizedDisplayed = if (currentDisplayed.isFinite()) currentDisplayed else 0.0
        var changed = false
        if (normalizedDisplayed != displayedScrollAxis(vertical)) {
            setDisplayedScrollAxis(vertical, normalizedDisplayed)
            changed = true
        }

        val target = clampedTarget.toDouble()
        val nextDisplayed = if (dtSeconds <= 0.0) {
            normalizedDisplayed
        } else {
            val delta = target - normalizedDisplayed
            if (kotlin.math.abs(delta) <= wheelScrollSnapThresholdPx()) {
                target
            } else {
                val alpha = 1.0 - kotlin.math.exp(-wheelScrollSmoothingPerSecond() * dtSeconds)
                val eased = normalizedDisplayed + delta * alpha.coerceIn(0.0, 1.0)
                if (kotlin.math.abs(target - eased) <= wheelScrollSnapThresholdPx()) target else eased
            }
        }.coerceIn(0.0, maxScroll.toDouble())
        if (nextDisplayed != displayedScrollAxis(vertical)) {
            setDisplayedScrollAxis(vertical, nextDisplayed)
            changed = true
        }

        val resolved = nextDisplayed.roundToInt().coerceIn(0, maxScroll)
        if (resolved != resolvedScrollAxis(vertical)) {
            setResolvedScrollAxis(vertical, resolved)
            scrollLayoutDirty = true
            markRenderCommandsDirty()
            changed = true
        }
        return changed
    }

    private fun normalizeScrollAxisForNonScrollable(vertical: Boolean): Boolean {
        var changed = false
        if (targetScrollAxis(vertical) != 0) {
            setTargetScrollAxis(vertical, 0)
            changed = true
        }
        if (displayedScrollAxis(vertical) != 0.0) {
            setDisplayedScrollAxis(vertical, 0.0)
            changed = true
        }
        if (resolvedScrollAxis(vertical) != 0) {
            setResolvedScrollAxis(vertical, 0)
            scrollLayoutDirty = true
            changed = true
        }
        if (changed) {
            markRenderCommandsDirty()
        }
        return changed
    }

    private fun targetScrollAxis(vertical: Boolean): Int {
        return if (vertical) scrollOffsetTargetY else scrollOffsetTargetX
    }

    private fun isScrollbarAxisActivelyDragged(vertical: Boolean): Boolean {
        return when (activeScrollbarDragAxis) {
            ScrollbarAxis.Vertical -> vertical
            ScrollbarAxis.Horizontal -> !vertical
            null -> false
        }
    }

    private fun displayedScrollAxis(vertical: Boolean): Double {
        return if (vertical) scrollOffsetDisplayedY else scrollOffsetDisplayedX
    }

    private fun resolvedScrollAxis(vertical: Boolean): Int {
        return if (vertical) scrollOffsetResolvedY else scrollOffsetResolvedX
    }

    private fun setTargetScrollAxis(vertical: Boolean, value: Int) {
        if (vertical) {
            scrollOffsetTargetY = value
        } else {
            scrollOffsetTargetX = value
        }
    }

    private fun setDisplayedScrollAxis(vertical: Boolean, value: Double) {
        if (vertical) {
            scrollOffsetDisplayedY = value
        } else {
            scrollOffsetDisplayedX = value
        }
    }

    private fun setResolvedScrollAxis(vertical: Boolean, value: Int) {
        if (vertical) {
            scrollOffsetResolvedY = value
        } else {
            scrollOffsetResolvedX = value
        }
    }

    fun scrollContainerState(): ScrollContainerState {
        ScrollPerformanceCounters.incrementScrollContainerStateCalls()
        val baseViewportRect = Rect(
            x = contentX(),
            y = contentY(),
            width = contentWidth(),
            height = contentHeight()
        )
        val contentExtent = computeContentExtent(
            contentOriginX = baseViewportRect.x,
            contentOriginY = baseViewportRect.y,
            layoutScrollX = contentLayoutScrollX,
            layoutScrollY = contentLayoutScrollY
        )
        val scrollbarResolution = resolveScrollbarResolution(
            overflowX = overflowX,
            overflowY = overflowY,
            contentExtent = contentExtent,
            baseViewportWidth = baseViewportRect.width,
            baseViewportHeight = baseViewportRect.height
        )
        val viewportRect = Rect(
            x = baseViewportRect.x,
            y = baseViewportRect.y,
            width = scrollbarResolution.viewportWidth,
            height = scrollbarResolution.viewportHeight
        )
        val axisX = axisStateForOverflow(
            overflowMode = overflowX,
            scrollbarPresent = scrollbarResolution.horizontalPresent,
            scrollbarGutter = scrollbarResolution.horizontalGutter
        )
        val axisY = axisStateForOverflow(
            overflowMode = overflowY,
            scrollbarPresent = scrollbarResolution.verticalPresent,
            scrollbarGutter = scrollbarResolution.verticalGutter
        )
        val maxScrollX = if (axisX.scrollContainer) {
            (contentExtent.width - viewportRect.width).coerceAtLeast(0)
        } else {
            0
        }
        val maxScrollY = if (axisY.scrollContainer) {
            (contentExtent.height - viewportRect.height).coerceAtLeast(0)
        } else {
            0
        }
        if (axisX.scrollContainer) {
            var axisChanged = false
            val axisDragged = isScrollbarAxisActivelyDragged(vertical = false)
            val clampedDisplayedX = scrollOffsetDisplayedX.coerceIn(0.0, maxScrollX.toDouble())
            if (axisDragged && scrollOffsetDisplayedX != clampedDisplayedX) {
                scrollOffsetDisplayedX = clampedDisplayedX
                axisChanged = true
            }
            if (axisDragged) {
                val clampedTargetX = scrollOffsetTargetX.coerceIn(0, maxScrollX)
                if (scrollOffsetTargetX != clampedTargetX) {
                    scrollOffsetTargetX = clampedTargetX
                    axisChanged = true
                }
            }
            val resolvedX = clampedDisplayedX.roundToInt().coerceIn(0, maxScrollX)
            if (resolvedX != scrollOffsetResolvedX) {
                scrollOffsetResolvedX = resolvedX
                scrollLayoutDirty = true
                axisChanged = true
            }
            if (axisChanged) {
                markRenderCommandsDirty()
            }
        } else {
            normalizeScrollAxisForNonScrollable(vertical = false)
        }
        if (axisY.scrollContainer) {
            var axisChanged = false
            val axisDragged = isScrollbarAxisActivelyDragged(vertical = true)
            val clampedDisplayedY = scrollOffsetDisplayedY.coerceIn(0.0, maxScrollY.toDouble())
            if (axisDragged && scrollOffsetDisplayedY != clampedDisplayedY) {
                scrollOffsetDisplayedY = clampedDisplayedY
                axisChanged = true
            }
            if (axisDragged) {
                val clampedTargetY = scrollOffsetTargetY.coerceIn(0, maxScrollY)
                if (scrollOffsetTargetY != clampedTargetY) {
                    scrollOffsetTargetY = clampedTargetY
                    axisChanged = true
                }
            }
            val resolvedY = clampedDisplayedY.roundToInt().coerceIn(0, maxScrollY)
            if (resolvedY != scrollOffsetResolvedY) {
                scrollOffsetResolvedY = resolvedY
                scrollLayoutDirty = true
                axisChanged = true
            }
            if (axisChanged) {
                markRenderCommandsDirty()
            }
        } else {
            normalizeScrollAxisForNonScrollable(vertical = true)
        }
        return ScrollContainerState(
            baseViewportRect = baseViewportRect,
            viewportRect = viewportRect,
            contentExtent = contentExtent,
            scrollX = scrollOffsetResolvedX,
            scrollY = scrollOffsetResolvedY,
            maxScrollX = maxScrollX,
            maxScrollY = maxScrollY,
            horizontalScrollbarGutter = scrollbarResolution.horizontalGutter,
            verticalScrollbarGutter = scrollbarResolution.verticalGutter,
            axisX = axisX,
            axisY = axisY
        )
    }

    private fun axisStateForOverflow(
        overflowMode: Overflow,
        scrollbarPresent: Boolean,
        scrollbarGutter: Int
    ): ScrollAxisState {
        val scrollContainer = overflowMode != Overflow.Visible
        val supportsScrollbar = overflowMode == Overflow.Scroll || overflowMode == Overflow.Auto
        return ScrollAxisState(
            overflow = overflowMode,
            scrollContainer = scrollContainer,
            clipsToViewport = scrollContainer,
            scrollbarPresent = supportsScrollbar && scrollbarPresent,
            scrollbarGutter = if (supportsScrollbar && scrollbarPresent) scrollbarGutter.coerceAtLeast(0) else 0
        )
    }

    protected open fun scrollbarThicknessPx(): Int = 6

    protected open fun minScrollbarThumbSizePx(): Int = 8

    protected open fun wheelScrollStepPx(): Int = 18

    protected open fun wheelScrollSmoothingPerSecond(): Double = 20.0

    protected open fun wheelScrollSnapThresholdPx(): Double = 0.35

    protected open fun scrollbarTrackColor(): Int = 0x55303030

    protected open fun scrollbarThumbColor(): Int = 0xAA9AA5B1.toInt()

    protected open fun scrollbarThumbActiveColor(): Int = 0xCCB7C3D1.toInt()

    private fun resolveScrollbarResolution(
        overflowX: Overflow,
        overflowY: Overflow,
        contentExtent: Size,
        baseViewportWidth: Int,
        baseViewportHeight: Int
    ): ScrollbarResolution {
        val thickness = scrollbarThicknessPx().coerceAtLeast(0)
        var horizontalPresent = overflowX == Overflow.Scroll
        var verticalPresent = overflowY == Overflow.Scroll
        repeat(3) {
            val viewportWidth = (baseViewportWidth - if (verticalPresent) thickness else 0).coerceAtLeast(0)
            val viewportHeight = (baseViewportHeight - if (horizontalPresent) thickness else 0).coerceAtLeast(0)
            val nextHorizontal = when (overflowX) {
                Overflow.Visible, Overflow.Hidden -> false
                Overflow.Scroll -> true
                Overflow.Auto -> contentExtent.width > viewportWidth
            }
            val nextVertical = when (overflowY) {
                Overflow.Visible, Overflow.Hidden -> false
                Overflow.Scroll -> true
                Overflow.Auto -> contentExtent.height > viewportHeight
            }
            if (nextHorizontal == horizontalPresent && nextVertical == verticalPresent) {
                return ScrollbarResolution(
                    horizontalPresent = nextHorizontal,
                    verticalPresent = nextVertical,
                    horizontalGutter = if (nextHorizontal) thickness else 0,
                    verticalGutter = if (nextVertical) thickness else 0,
                    viewportWidth = viewportWidth,
                    viewportHeight = viewportHeight
                )
            }
            horizontalPresent = nextHorizontal
            verticalPresent = nextVertical
        }
        val viewportWidth = (baseViewportWidth - if (verticalPresent) thickness else 0).coerceAtLeast(0)
        val viewportHeight = (baseViewportHeight - if (horizontalPresent) thickness else 0).coerceAtLeast(0)
        return ScrollbarResolution(
            horizontalPresent = horizontalPresent,
            verticalPresent = verticalPresent,
            horizontalGutter = if (horizontalPresent) thickness else 0,
            verticalGutter = if (verticalPresent) thickness else 0,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight
        )
    }

    private fun scrollbarVisualState(state: ScrollContainerState = scrollContainerState()): ScrollbarVisualState {
        val verticalTrack = if (state.axisY.scrollbarPresent && state.verticalScrollbarGutter > 0) {
            Rect(
                x = state.viewportRect.x + state.viewportRect.width,
                y = state.viewportRect.y,
                width = state.verticalScrollbarGutter.coerceAtLeast(1),
                height = state.viewportRect.height.coerceAtLeast(0)
            )
        } else {
            null
        }
        val horizontalTrack = if (state.axisX.scrollbarPresent && state.horizontalScrollbarGutter > 0) {
            Rect(
                x = state.viewportRect.x,
                y = state.viewportRect.y + state.viewportRect.height,
                width = state.viewportRect.width.coerceAtLeast(0),
                height = state.horizontalScrollbarGutter.coerceAtLeast(1)
            )
        } else {
            null
        }
        val vertical = buildScrollbarVisualAxis(
            trackRect = verticalTrack,
            viewportExtent = state.viewportRect.height,
            contentExtent = state.contentExtent.height,
            scrollOffset = displayedScrollAxis(vertical = true).coerceIn(0.0, state.maxScrollY.toDouble()),
            maxScroll = state.maxScrollY,
            verticalAxis = true
        )
        val horizontal = buildScrollbarVisualAxis(
            trackRect = horizontalTrack,
            viewportExtent = state.viewportRect.width,
            contentExtent = state.contentExtent.width,
            scrollOffset = displayedScrollAxis(vertical = false).coerceIn(0.0, state.maxScrollX.toDouble()),
            maxScroll = state.maxScrollX,
            verticalAxis = false
        )
        return ScrollbarVisualState(
            horizontal = horizontal,
            vertical = vertical
        )
    }

    private fun buildScrollbarVisualAxis(
        trackRect: Rect?,
        viewportExtent: Int,
        contentExtent: Int,
        scrollOffset: Double,
        maxScroll: Int,
        verticalAxis: Boolean
    ): ScrollbarVisualAxis? {
        val track = trackRect ?: return null
        val trackExtent = if (verticalAxis) track.height else track.width
        if (trackExtent <= 0) return null

        val minThumb = minScrollbarThumbSizePx().coerceAtLeast(1)
        val rawThumbExtent = if (contentExtent <= 0 || viewportExtent <= 0) {
            trackExtent
        } else {
            val ratio = viewportExtent.toFloat() / contentExtent.toFloat()
            (trackExtent.toFloat() * ratio).roundToInt()
        }
        val thumbExtent = rawThumbExtent.coerceIn(minThumb.coerceAtMost(trackExtent), trackExtent)
        val thumbTravel = (trackExtent - thumbExtent).coerceAtLeast(0)
        val thumbOffset = if (maxScroll <= 0 || thumbTravel <= 0) {
            0
        } else {
            val ratio = (scrollOffset.coerceIn(0.0, maxScroll.toDouble()) / maxScroll.toDouble()).toFloat()
            (ratio * thumbTravel.toFloat()).roundToInt().coerceIn(0, thumbTravel)
        }
        val thumbRect = if (verticalAxis) {
            Rect(track.x, track.y + thumbOffset, track.width, thumbExtent)
        } else {
            Rect(track.x + thumbOffset, track.y, thumbExtent, track.height)
        }
        return ScrollbarVisualAxis(
            trackRect = track,
            thumbRect = thumbRect,
            maxScroll = maxScroll.coerceAtLeast(0),
            scrollOffset = scrollOffset.roundToInt().coerceAtLeast(0)
        )
    }

    private fun appendScrollbarCommands(out: MutableList<RenderCommand>, state: ScrollContainerState) {
        val visuals = scrollbarVisualState(state)
        visuals.vertical?.let { axis ->
            out += RenderCommand.DrawRect(
                x = axis.trackRect.x,
                y = axis.trackRect.y,
                width = axis.trackRect.width,
                height = axis.trackRect.height,
                color = scrollbarTrackColor()
            )
            val thumbColor = if (activeScrollbarDragAxis == ScrollbarAxis.Vertical) {
                scrollbarThumbActiveColor()
            } else {
                scrollbarThumbColor()
            }
            out += RenderCommand.DrawRect(
                x = axis.thumbRect.x,
                y = axis.thumbRect.y,
                width = axis.thumbRect.width,
                height = axis.thumbRect.height,
                color = thumbColor
            )
        }
        visuals.horizontal?.let { axis ->
            out += RenderCommand.DrawRect(
                x = axis.trackRect.x,
                y = axis.trackRect.y,
                width = axis.trackRect.width,
                height = axis.trackRect.height,
                color = scrollbarTrackColor()
            )
            val thumbColor = if (activeScrollbarDragAxis == ScrollbarAxis.Horizontal) {
                scrollbarThumbActiveColor()
            } else {
                scrollbarThumbColor()
            }
            out += RenderCommand.DrawRect(
                x = axis.thumbRect.x,
                y = axis.thumbRect.y,
                width = axis.thumbRect.width,
                height = axis.thumbRect.height,
                color = thumbColor
            )
        }
    }

    private fun resolveScrollbarDragAxisAt(mouseX: Int, mouseY: Int): ScrollbarAxis? {
        if (!containsGlobalPoint(mouseX, mouseY)) return null
        val visuals = scrollbarVisualState()
        if (visuals.vertical?.trackRect?.contains(mouseX, mouseY) == true) {
            return ScrollbarAxis.Vertical
        }
        if (visuals.horizontal?.trackRect?.contains(mouseX, mouseY) == true) {
            return ScrollbarAxis.Horizontal
        }
        return null
    }

    private fun beginScrollbarPointerDrag(mouseX: Int, mouseY: Int): Boolean {
        val visuals = scrollbarVisualState()
        val vertical = visuals.vertical
        if (vertical != null && vertical.trackRect.contains(mouseX, mouseY)) {
            activeScrollbarDragAxis = ScrollbarAxis.Vertical
            scrollbarDragSession = beginScrollbarDragSession(
                axis = ScrollbarAxis.Vertical,
                visual = vertical,
                pointerPx = mouseY,
                pointerInsideThumb = vertical.thumbRect.contains(mouseX, mouseY)
            )
            markRenderCommandsDirty()
            updateScrollbarPointerDrag(mouseX, mouseY)
            return true
        }

        val horizontal = visuals.horizontal
        if (horizontal != null && horizontal.trackRect.contains(mouseX, mouseY)) {
            activeScrollbarDragAxis = ScrollbarAxis.Horizontal
            scrollbarDragSession = beginScrollbarDragSession(
                axis = ScrollbarAxis.Horizontal,
                visual = horizontal,
                pointerPx = mouseX,
                pointerInsideThumb = horizontal.thumbRect.contains(mouseX, mouseY)
            )
            markRenderCommandsDirty()
            updateScrollbarPointerDrag(mouseX, mouseY)
            return true
        }
        return false
    }

    private fun beginScrollbarDragSession(
        axis: ScrollbarAxis,
        visual: ScrollbarVisualAxis,
        pointerPx: Int,
        pointerInsideThumb: Boolean
    ): ScrollbarDragSession {
        val track = visual.trackRect
        val thumb = visual.thumbRect
        val trackLengthPx = if (axis == ScrollbarAxis.Vertical) {
            track.height.coerceAtLeast(0)
        } else {
            track.width.coerceAtLeast(0)
        }
        val thumbLengthPx = if (axis == ScrollbarAxis.Vertical) {
            thumb.height.coerceAtLeast(1)
        } else {
            thumb.width.coerceAtLeast(1)
        }
        val trackStartPx = if (axis == ScrollbarAxis.Vertical) track.y else track.x
        val thumbStartPx = if (axis == ScrollbarAxis.Vertical) thumb.y else thumb.x
        val maxThumbTravelPx = (trackLengthPx - thumbLengthPx).coerceAtLeast(0)
        val grabOffsetPx = if (pointerInsideThumb) {
            (pointerPx - thumbStartPx).coerceIn(0, (thumbLengthPx - 1).coerceAtLeast(0))
        } else {
            (thumbLengthPx / 2).coerceAtLeast(0)
        }
        val initialResolvedScroll = if (axis == ScrollbarAxis.Vertical) {
            resolvedScrollAxis(vertical = true)
        } else {
            resolvedScrollAxis(vertical = false)
        }
        return ScrollbarDragSession(
            axis = axis,
            trackStartPx = trackStartPx,
            trackLengthPx = trackLengthPx,
            thumbLengthPx = thumbLengthPx,
            maxThumbTravelPx = maxThumbTravelPx,
            maxScroll = visual.maxScroll.coerceAtLeast(0),
            grabOffsetPx = grabOffsetPx,
            initialResolvedScroll = initialResolvedScroll
        )
    }

    private fun updateScrollbarPointerDrag(mouseX: Int, mouseY: Int) {
        val session = scrollbarDragSession ?: return
        val pointerAxisPx = if (session.axis == ScrollbarAxis.Vertical) mouseY else mouseX
        val desiredThumbStartPx = if (session.maxThumbTravelPx <= 0) {
            0
        } else {
            (pointerAxisPx - session.trackStartPx - session.grabOffsetPx).coerceIn(0, session.maxThumbTravelPx)
        }
        val desiredScroll = if (session.maxThumbTravelPx <= 0 || session.maxScroll <= 0) {
            0
        } else {
            val ratio = desiredThumbStartPx.toDouble() / session.maxThumbTravelPx.toDouble()
            (ratio * session.maxScroll.toDouble()).roundToInt().coerceIn(0, session.maxScroll)
        }

        var nextScrollX = resolvedScrollAxis(vertical = false)
        var nextScrollY = resolvedScrollAxis(vertical = true)
        if (session.axis == ScrollbarAxis.Vertical) {
            nextScrollY = desiredScroll
        } else {
            nextScrollX = desiredScroll
        }
        applyDragScrollOffsets(
            scrollX = nextScrollX,
            scrollY = nextScrollY
        )
    }

    private fun applyDragScrollOffsets(
        scrollX: Int,
        scrollY: Int
    ) {
        val resolvedX = scrollX.coerceAtLeast(0)
        val resolvedY = scrollY.coerceAtLeast(0)
        val clampedX = resolvedX.toDouble()
        val clampedY = resolvedY.toDouble()
        var changed = false
        if (scrollOffsetDisplayedX != clampedX) {
            scrollOffsetDisplayedX = clampedX
            changed = true
        }
        if (scrollOffsetDisplayedY != clampedY) {
            scrollOffsetDisplayedY = clampedY
            changed = true
        }
        if (scrollOffsetResolvedX != resolvedX) {
            scrollOffsetResolvedX = resolvedX
            scrollLayoutDirty = true
            changed = true
        }
        if (scrollOffsetResolvedY != resolvedY) {
            scrollOffsetResolvedY = resolvedY
            scrollLayoutDirty = true
            changed = true
        }
        if (scrollOffsetTargetX != resolvedX) {
            scrollOffsetTargetX = resolvedX
            changed = true
        }
        if (scrollOffsetTargetY != resolvedY) {
            scrollOffsetTargetY = resolvedY
            changed = true
        }
        if (changed) {
            markRenderCommandsDirty()
        }
    }

    private fun computeContentExtent(
        contentOriginX: Int,
        contentOriginY: Int,
        layoutScrollX: Int,
        layoutScrollY: Int
    ): Size {
        var maxWidth = 0
        var maxHeight = 0
        children.forEach { child ->
            if (child.display == Display.None) return@forEach
            if (child.isRemovedFromNormalFlowForPositioning()) return@forEach
            val outerStartX = child.bounds.x - child.margin.left
            val outerStartY = child.bounds.y - child.margin.top
            val outerEndX = outerStartX + child.bounds.width + child.margin.horizontal
            val outerEndY = outerStartY + child.bounds.height + child.margin.vertical
            val normalizedOuterEndX = outerEndX + layoutScrollX
            val normalizedOuterEndY = outerEndY + layoutScrollY
            maxWidth = maxOf(maxWidth, (normalizedOuterEndX - contentOriginX).coerceAtLeast(0))
            maxHeight = maxOf(maxHeight, (normalizedOuterEndY - contentOriginY).coerceAtLeast(0))
        }
        return Size(maxWidth, maxHeight)
    }

    open fun overflowViewportRect(): Rect? {
        val state = scrollContainerState()
        val clipX = state.axisX.clipsToViewport
        val clipY = state.axisY.clipsToViewport
        if (!clipX && !clipY) return null
        val root = rootNode()
        return Rect(
            x = if (clipX) state.viewportRect.x else root.bounds.x,
            y = if (clipY) state.viewportRect.y else root.bounds.y,
            width = if (clipX) state.viewportRect.width.coerceAtLeast(0) else root.bounds.width.coerceAtLeast(0),
            height = if (clipY) state.viewportRect.height.coerceAtLeast(0) else root.bounds.height.coerceAtLeast(0)
        )
    }

    fun inputClipRectForChildren(parentClipRect: Rect?): Rect? {
        val localClipRect = overflowViewportRect()
        return when {
            parentClipRect != null && localClipRect != null -> parentClipRect.intersection(localClipRect)
            localClipRect != null -> localClipRect
            else -> parentClipRect
        }
    }

    fun isPointInsideInputClip(pointX: Int, pointY: Int, clipRect: Rect?): Boolean {
        if (clipRect == null) {
            return true
        }
        return clipRect.contains(pointX, pointY)
    }

    private fun isPointInsideEffectiveAncestorClip(pointX: Int, pointY: Int): Boolean {
        if (position == PositionMode.Fixed) {
            return fixedViewportClipRectForPromotedParticipation().contains(pointX, pointY)
        }
        val effectiveClip = effectiveAncestorOverflowClipRect() ?: return true
        return effectiveClip.contains(pointX, pointY)
    }

    internal fun effectiveAncestorOverflowClipRect(): Rect? {
        var current: DOMNode? = parent
        var effectiveRect: Rect? = null
        while (current != null) {
            val clipRect = current.overflowViewportRect()
            if (clipRect != null) {
                effectiveRect = if (effectiveRect == null) {
                    clipRect
                } else {
                    effectiveRect.intersection(clipRect) ?: return Rect(0, 0, 0, 0)
                }
            }
            current = current.parent
        }
        return effectiveRect
    }

    open fun inspectorScrollOffset(): Pair<Int, Int>? {
        val state = scrollContainerState()
        return if (state.axisX.scrollContainer || state.axisY.scrollContainer) {
            state.scrollX to state.scrollY
        } else {
            null
        }
    }

    internal fun debugScrollbarVisualState(): ScrollbarVisualState {
        return scrollbarVisualState()
    }

    internal fun debugScrollbarDragSession(): ScrollbarDragSessionDebugState? {
        val session = scrollbarDragSession ?: return null
        return ScrollbarDragSessionDebugState(
            verticalAxis = session.axis == ScrollbarAxis.Vertical,
            trackStartPx = session.trackStartPx,
            trackLengthPx = session.trackLengthPx,
            thumbLengthPx = session.thumbLengthPx,
            maxThumbTravelPx = session.maxThumbTravelPx,
            maxScroll = session.maxScroll,
            grabOffsetPx = session.grabOffsetPx,
            initialResolvedScroll = session.initialResolvedScroll
        )
    }

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

