package org.dreamfinity.dsgl.core.overlay

import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.render.RenderCommand

internal data class PortalEntryId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "Portal entry id must not be blank." }
    }
}

internal data class PortalEntryOrder(
    val zIndex: Int,
    val sequence: Int = 0,
) : Comparable<PortalEntryOrder> {
    override fun compareTo(other: PortalEntryOrder): Int =
        compareValuesBy(this, other, PortalEntryOrder::zIndex, PortalEntryOrder::sequence)
}

internal data class PortalEntryBounds(
    val viewportBounds: Rect,
    val entryBounds: Rect,
) {
    init {
        require(viewportBounds.width > 0 && viewportBounds.height > 0) {
            "Portal viewport bounds must be explicit and non-empty."
        }
        require(entryBounds.width > 0 && entryBounds.height > 0) {
            "Portal entry bounds must be explicit and non-empty."
        }
    }
}

internal data class PortalEntryPlacement(
    val anchorBounds: Rect?,
    val bounds: PortalEntryBounds,
)

internal enum class PortalDismissPolicy {
    None,
    OutsidePointerDown,
    EscapeOrOutsidePointerDown,
}

internal enum class PortalBackdropPolicy {
    None,
    ConsumeOutsidePointerDown,
}

internal enum class PortalInsidePointerPolicy {
    PassThrough,
    ConsumePointerDown,
}

internal enum class PortalPointerContainmentPolicy {
    DomOrEntryBounds,
    ProtectedBoundsOnly,
}

internal enum class PortalInputPolicy {
    None,
    DomOnly,
    ManualOnly,
    ManualThenDomFallback,
}

internal enum class PortalFocusPolicy {
    Preserve,
    RequestFocus,
    TrapFocus,
}

internal enum class PortalLifecyclePolicy {
    Manual,
    CloseOnUnmount,
}

internal enum class PortalPointerRegion {
    InsideEntry,
    OutsideEntry,
}

internal data class PortalPointerPolicyResult(
    val entry: PortalEntry,
    val region: PortalPointerRegion,
    val shouldClose: Boolean,
    val consumed: Boolean,
)

internal data class PortalEntryState(
    val id: PortalEntryId,
    val ownerToken: Any,
    val surface: ScreenDomainSurface,
    val order: PortalEntryOrder,
    var dismissPolicy: PortalDismissPolicy = PortalDismissPolicy.None,
    val inputPolicy: PortalInputPolicy = PortalInputPolicy.DomOnly,
    val focusPolicy: PortalFocusPolicy = PortalFocusPolicy.Preserve,
    var backdropPolicy: PortalBackdropPolicy = PortalBackdropPolicy.None,
    val insidePointerPolicy: PortalInsidePointerPolicy = PortalInsidePointerPolicy.PassThrough,
    val pointerContainmentPolicy: PortalPointerContainmentPolicy = PortalPointerContainmentPolicy.DomOrEntryBounds,
    val lifecyclePolicy: PortalLifecyclePolicy = PortalLifecyclePolicy.CloseOnUnmount,
) {
    var active: Boolean = false
        internal set
    var placement: PortalEntryPlacement? = null
        internal set
    var dismissAction: (() -> Unit)? = null
    private var protectedBounds: List<Rect> = emptyList()

    fun activate(placement: PortalEntryPlacement) {
        this.placement = placement
        active = true
    }

    fun deactivate() {
        active = false
        placement = null
        protectedBounds = emptyList()
    }

    fun updateProtectedBounds(bounds: List<Rect>) {
        protectedBounds = bounds.filter { it.width > 0 && it.height > 0 }
    }

    fun containsPointer(mouseX: Int, mouseY: Int, node: DOMNode?): Boolean {
        if (pointerContainmentPolicy == PortalPointerContainmentPolicy.ProtectedBoundsOnly) {
            return protectedBounds.any { it.contains(mouseX, mouseY) }
        }
        if (node?.containsGlobalPoint(mouseX, mouseY) == true) return true
        val activePlacement = placement ?: return false
        if (activePlacement.bounds.entryBounds
                .contains(mouseX, mouseY)
        ) {
            return true
        }
        return protectedBounds.any { it.contains(mouseX, mouseY) }
    }

    fun dismiss(entry: PortalEntry) {
        dismissAction?.invoke() ?: entry.close()
    }
}

internal data class PortalFrameContext(
    val viewportBounds: Rect,
) {
    init {
        require(viewportBounds.width > 0 && viewportBounds.height > 0) {
            "Portal frame viewport bounds must be explicit and non-empty."
        }
    }
}

@Suppress("TooManyFunctions")
internal interface PortalEntry {
    val state: PortalEntryState
    val node: DOMNode?

    fun onInputFrame(context: PortalFrameContext) = Unit

    fun render(ctx: UiMeasureContext, width: Int, height: Int) = Unit

    fun paint(ctx: UiMeasureContext): List<RenderCommand> = emptyList()

    fun clearRefs() = Unit

    fun close() {
        state.deactivate()
    }

    fun handleMouseMove(mouseX: Int, mouseY: Int): Boolean = false

    fun handleMouseDown(mouseX: Int, mouseY: Int, button: MouseButton): Boolean = false

    fun handleMouseUp(mouseX: Int, mouseY: Int, button: MouseButton): Boolean = false

    fun handleMouseWheel(mouseX: Int, mouseY: Int, delta: Int): Boolean = false

    fun handleKeyDown(keyCode: Int, keyChar: Char): Boolean = false

    fun handleKeyUp(keyCode: Int, keyChar: Char): Boolean = false
}

internal class PortalHost(
    val surface: ScreenDomainSurface,
) {
    private val entriesById: LinkedHashMap<PortalEntryId, PortalEntry> = LinkedHashMap()

    fun register(entry: PortalEntry) {
        require(entry.state.surface == surface) {
            "Portal entry ${entry.state.id.value} belongs to ${entry.state.surface}, not $surface."
        }
        require(entriesById.putIfAbsent(entry.state.id, entry) == null) {
            "Portal entry ${entry.state.id.value} is already registered."
        }
    }

    fun unregister(id: PortalEntryId): Boolean {
        val entry = entriesById.remove(id) ?: return false
        entry.clearRefs()
        entry.close()
        return true
    }

    fun entriesInPaintOrder(): List<PortalEntry> =
        entriesById.values
            .filter { it.state.active }
            .sortedBy { it.state.order }

    fun entriesInInputOrder(): List<PortalEntry> = entriesInPaintOrder().asReversed()

    fun onInputFrame(context: PortalFrameContext) {
        entriesById.values.forEach { it.onInputFrame(context) }
    }

    fun render(ctx: UiMeasureContext, width: Int, height: Int) {
        entriesInPaintOrder().forEach { it.render(ctx, width, height) }
    }

    fun paint(ctx: UiMeasureContext): List<RenderCommand> = entriesInPaintOrder().flatMap { it.paint(ctx) }

    fun clearRefs() {
        entriesById.values.forEach { entry ->
            entry.clearRefs()
            if (entry.state.lifecyclePolicy == PortalLifecyclePolicy.CloseOnUnmount) {
                entry.close()
            } else {
                entry.state.deactivate()
            }
        }
        entriesById.clear()
    }

    fun dispatchInput(handler: (PortalEntry) -> Boolean): Boolean = entriesInInputOrder().any(handler)
}

internal fun PortalHost.handleOutsidePointerDownPolicy(mouseX: Int, mouseY: Int): Boolean {
    val result = evaluateOutsidePointerDown(mouseX, mouseY) ?: return false
    if (result.shouldClose) {
        result.entry.state
            .dismiss(result.entry)
    }
    return result.consumed
}

internal fun PortalHost.evaluateOutsidePointerDown(mouseX: Int, mouseY: Int): PortalPointerPolicyResult? =
    entriesInInputOrder().firstNotNullOfOrNull { entry ->
        if (entry.state.containsPointer(mouseX, mouseY, entry.node)) {
            return@firstNotNullOfOrNull PortalPointerPolicyResult(
                entry = entry,
                region = PortalPointerRegion.InsideEntry,
                shouldClose = false,
                consumed = entry.state.insidePointerPolicy == PortalInsidePointerPolicy.ConsumePointerDown,
            )
        }
        val shouldClose =
            entry.state.dismissPolicy == PortalDismissPolicy.OutsidePointerDown ||
                entry.state.dismissPolicy == PortalDismissPolicy.EscapeOrOutsidePointerDown
        val consumed = shouldClose || entry.state.backdropPolicy == PortalBackdropPolicy.ConsumeOutsidePointerDown
        if (!consumed) {
            return@firstNotNullOfOrNull null
        }
        PortalPointerPolicyResult(
            entry = entry,
            region = PortalPointerRegion.OutsideEntry,
            shouldClose = shouldClose,
            consumed = true,
        )
    }

internal data class DomainSurfacePortalHostAdapter(
    val surfaceHost: DomainSurfaceHost,
) {
    val surface: ScreenDomainSurface = surfaceHost.surface
}
