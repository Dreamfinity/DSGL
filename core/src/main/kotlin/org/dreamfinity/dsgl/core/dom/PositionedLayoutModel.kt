package org.dreamfinity.dsgl.core.dom

import org.dreamfinity.dsgl.core.style.CssLength
import org.dreamfinity.dsgl.core.style.PositionMode
import org.dreamfinity.dsgl.core.style.StyleProperty

internal object PositionedLayoutModel {
    private fun isLayoutRuntimePositionedMode(mode: PositionMode): Boolean =
        when (mode) {
            PositionMode.Relative,
            PositionMode.Absolute,
            PositionMode.Fixed,
            -> true
            PositionMode.Static,
            PositionMode.Sticky,
            -> false
        }

    private fun isOrderingPositionedMode(mode: PositionMode): Boolean =
        when (mode) {
            PositionMode.Static -> false
            PositionMode.Relative,
            PositionMode.Absolute,
            PositionMode.Fixed,
            PositionMode.Sticky,
            -> true
        }

    data class RootStackingContextId(
        val rootNode: DOMNode,
    )

    enum class StackingParticipantKind {
        LocalNode,
        ChildContext,
    }

    data class StackingParticipant(
        val node: DOMNode,
        val logicalParent: DOMNode,
        val sourceDomOrder: Int,
        val priority: OrderingPriority,
        val kind: StackingParticipantKind,
        val createsChildContextHint: Boolean,
        val rootContextPromotionTarget: RootStackingContextId?,
    )

    data class StackingContext(
        val id: RootStackingContextId,
        val ownerNode: DOMNode,
        val rootNode: DOMNode,
        val participants: List<StackingParticipant>,
    )

    data class OffsetPrecedenceResolution(
        val sourceProperty: StyleProperty?,
        val value: CssLength?,
    )

    data class OrderingPriority(
        val positionedBucket: Int,
        val zIndex: Int,
        val domOrder: Int,
    )

    data class ChildEntry(
        val node: DOMNode,
        val priority: OrderingPriority,
    )

    fun isPositioned(node: DOMNode): Boolean = isOrderingPositionedMode(node.position)

    private fun effectiveOrderingZIndex(node: DOMNode): Int = if (isPositioned(node)) node.zIndex else 0

    fun orderingPriority(node: DOMNode, domOrder: Int): OrderingPriority =
        OrderingPriority(
            positionedBucket = if (isPositioned(node)) 1 else 0,
            zIndex = effectiveOrderingZIndex(node),
            domOrder = domOrder,
        )

    fun rootStackingScope(node: DOMNode): DOMNode {
        var current = node
        while (current.parent != null) {
            current = current.parent!!
        }
        return current
    }

    fun sharesRootStackingScope(first: DOMNode, second: DOMNode): Boolean =
        rootStackingScope(first) === rootStackingScope(second)

    fun rootStackingContextId(node: DOMNode): RootStackingContextId =
        RootStackingContextId(rootNode = rootStackingScope(node))

    fun matchesChildContextTrigger(node: DOMNode): Boolean = isOrderingPositionedMode(node.position) && node.zIndex != 0

    fun stackingContextScaffold(owner: DOMNode): StackingContext {
        val root = rootStackingScope(owner)
        val contextId = RootStackingContextId(rootNode = root)
        val participants =
            if (owner.parent == null) {
                rootContextParticipants(owner, contextId)
            } else {
                localContextParticipants(owner)
            }
        return StackingContext(
            id = contextId,
            ownerNode = owner,
            rootNode = root,
            participants = participants,
        )
    }

    fun containingBlockForAbsolute(node: DOMNode): DOMNode {
        var current = node.parent
        while (current != null) {
            if (isLayoutRuntimePositionedMode(current.position)) {
                return current
            }
            current = current.parent
        }
        return rootStackingScope(node)
    }

    fun fixedViewportRoot(node: DOMNode): DOMNode = rootStackingScope(node)

    private fun createsChildContextForLocalParticipation(node: DOMNode): Boolean {
        if (node.position == PositionMode.Fixed) {
            return false
        }
        return matchesChildContextTrigger(node)
    }

    private fun localContextParticipants(owner: DOMNode): List<StackingParticipant> {
        val children = owner.children
        val participants = ArrayList<StackingParticipant>(children.size)
        for ((index, element) in children.withIndex()) {
            val child = element
            if (child.position == PositionMode.Fixed) {
                continue
            }
            val createsChildContextHint = createsChildContextForLocalParticipation(child)
            participants +=
                StackingParticipant(
                    node = child,
                    logicalParent = owner,
                    sourceDomOrder = index,
                    priority = orderingPriority(child, index),
                    kind =
                        if (createsChildContextHint) {
                            StackingParticipantKind.ChildContext
                        } else {
                            StackingParticipantKind.LocalNode
                        },
                    createsChildContextHint = createsChildContextHint,
                    rootContextPromotionTarget = null,
                )
        }
        return participants
    }

    private fun rootContextParticipants(root: DOMNode, contextId: RootStackingContextId): List<StackingParticipant> {
        val globalDomOrder = buildGlobalDomOrderMap(root)
        val localParticipants =
            root.children
                .withIndex()
                .filter { indexed -> indexed.value.position != PositionMode.Fixed }
                .map { indexed ->
                    val child = indexed.value
                    val domOrder = globalDomOrder[child] ?: indexed.index
                    val createsChildContextHint = createsChildContextForLocalParticipation(child)
                    StackingParticipant(
                        node = child,
                        logicalParent = root,
                        sourceDomOrder = domOrder,
                        priority = orderingPriority(child, domOrder),
                        kind =
                            if (createsChildContextHint) {
                                StackingParticipantKind.ChildContext
                            } else {
                                StackingParticipantKind.LocalNode
                            },
                        createsChildContextHint = createsChildContextHint,
                        rootContextPromotionTarget = null,
                    )
                }
        val promotedFixedParticipants =
            collectPromotedFixedNodes(root)
                .map { fixed ->
                    val domOrder = globalDomOrder[fixed] ?: Int.MAX_VALUE
                    StackingParticipant(
                        node = fixed,
                        logicalParent = fixed.parent ?: root,
                        sourceDomOrder = domOrder,
                        priority = orderingPriority(fixed, domOrder),
                        kind = StackingParticipantKind.ChildContext,
                        createsChildContextHint = true,
                        rootContextPromotionTarget = contextId,
                    )
                }
        return localParticipants + promotedFixedParticipants
    }

    private fun collectPromotedFixedNodes(root: DOMNode): List<DOMNode> {
        val out = ArrayList<DOMNode>()

        fun visit(node: DOMNode) {
            node.children.forEach { child ->
                if (child.position == PositionMode.Fixed) {
                    out += child
                }
                visit(child)
            }
        }
        visit(root)
        return out
    }

    private fun buildGlobalDomOrderMap(root: DOMNode): Map<DOMNode, Int> {
        val order = LinkedHashMap<DOMNode, Int>()
        var cursor = 0

        fun visit(node: DOMNode) {
            node.children.forEach { child ->
                order[child] = cursor
                cursor += 1
                visit(child)
            }
        }
        visit(root)
        return order
    }

    fun resolveHorizontalOffset(left: CssLength?, right: CssLength?): OffsetPrecedenceResolution =
        when {
            left != null -> OffsetPrecedenceResolution(StyleProperty.LEFT, left)
            right != null -> OffsetPrecedenceResolution(StyleProperty.RIGHT, right)
            else -> OffsetPrecedenceResolution(null, null)
        }

    fun resolveVerticalOffset(top: CssLength?, bottom: CssLength?): OffsetPrecedenceResolution =
        when {
            top != null -> OffsetPrecedenceResolution(StyleProperty.TOP, top)
            bottom != null -> OffsetPrecedenceResolution(StyleProperty.BOTTOM, bottom)
            else -> OffsetPrecedenceResolution(null, null)
        }

    private val paintOrderComparator: Comparator<StackingParticipant> =
        compareBy(
            { it.priority.positionedBucket },
            { it.priority.zIndex },
            { it.priority.domOrder },
        )

    fun orderedParticipantsForPaint(owner: DOMNode): List<StackingParticipant> {
        val participants = stackingContextScaffold(owner).participants
        if (participants.size <= 1) {
            return participants
        }
        var hasPositioned = false
        for (index in participants.indices) {
            if (isPositioned(participants[index].node)) {
                hasPositioned = true
                break
            }
        }
        if (!hasPositioned) {
            // Participants are built in DOM order; without positioned nodes the sort is a no-op.
            return participants
        }

        return participants.sortedWith(paintOrderComparator)
    }

    fun orderedParticipantsForHitTesting(owner: DOMNode): List<StackingParticipant> =
        orderedParticipantsForPaint(owner).asReversed()

    fun orderedChildrenForPaint(parent: DOMNode): List<DOMNode> {
        // Fast path for the per-frame chunk traversal: non-root owners with no positioned
        // children paint in plain DOM order, and Fixed children (the only ones filtered out
        // of local participation) are positioned by definition. Callers iterate read-only.
        if (parent.parent != null && !hasPositionedChild(parent)) {
            return parent.children
        }
        return orderedParticipantsForPaint(parent).map { it.node }
    }

    private fun hasPositionedChild(parent: DOMNode): Boolean {
        val children = parent.children
        for (index in children.indices) {
            if (isPositioned(children[index])) {
                return true
            }
        }
        return false
    }

    fun orderedChildrenForHitTesting(parent: DOMNode): List<DOMNode> =
        orderedParticipantsForHitTesting(parent).map {
            it.node
        }
}
