package org.dreamfinity.dsgl.core.dom

import org.dreamfinity.dsgl.core.style.CssLength
import org.dreamfinity.dsgl.core.style.PositionMode
import org.dreamfinity.dsgl.core.style.StyleProperty

internal object PositionedLayoutModel {
    data class RootStackingContextId(
        val rootNode: DOMNode
    )

    enum class StackingParticipantKind {
        LocalNode,
        ChildContext
    }

    data class StackingParticipant(
        val node: DOMNode,
        val logicalParent: DOMNode,
        val sourceDomOrder: Int,
        val priority: OrderingPriority,
        val kind: StackingParticipantKind,
        val createsChildContextHint: Boolean,
        val rootContextPromotionTarget: RootStackingContextId?
    )

    data class StackingContext(
        val id: RootStackingContextId,
        val ownerNode: DOMNode,
        val rootNode: DOMNode,
        val participants: List<StackingParticipant>
    )

    data class OffsetPrecedenceResolution(
        val sourceProperty: StyleProperty?,
        val value: CssLength?
    )

    data class OrderingPriority(
        val positionedBucket: Int,
        val zIndex: Int,
        val domOrder: Int
    )

    data class ChildEntry(
        val node: DOMNode,
        val priority: OrderingPriority
    )

    fun isPositioned(node: DOMNode): Boolean {
        return node.position != PositionMode.Static
    }

    private fun effectiveOrderingZIndex(node: DOMNode): Int {
        return if (isPositioned(node)) node.zIndex else 0
    }

    fun orderingPriority(node: DOMNode, domOrder: Int): OrderingPriority {
        return OrderingPriority(
            positionedBucket = if (isPositioned(node)) 1 else 0,
            zIndex = effectiveOrderingZIndex(node),
            domOrder = domOrder
        )
    }

    fun rootStackingScope(node: DOMNode): DOMNode {
        var current = node
        while (current.parent != null) {
            current = current.parent!!
        }
        return current
    }

    fun sharesRootStackingScope(first: DOMNode, second: DOMNode): Boolean {
        return rootStackingScope(first) === rootStackingScope(second)
    }

    fun rootStackingContextId(node: DOMNode): RootStackingContextId {
        return RootStackingContextId(rootNode = rootStackingScope(node))
    }

    fun stackingContextScaffold(owner: DOMNode): StackingContext {
        val root = rootStackingScope(owner)
        val contextId = RootStackingContextId(rootNode = root)
        val participants = if (owner.parent == null) {
            rootContextParticipants(owner, contextId)
        } else {
            localContextParticipants(owner)
        }
        return StackingContext(
            id = contextId,
            ownerNode = owner,
            rootNode = root,
            participants = participants
        )
    }

    fun containingBlockForAbsolute(node: DOMNode): DOMNode {
        var current = node.parent
        while (current != null) {
            if (isPositioned(current)) {
                return current
            }
            current = current.parent
        }
        return rootStackingScope(node)
    }

    fun fixedViewportRoot(node: DOMNode): DOMNode {
        return rootStackingScope(node)
    }

    private fun localContextParticipants(owner: DOMNode): List<StackingParticipant> {
        return owner.children.withIndex()
            .filter { indexed -> indexed.value.position != PositionMode.Fixed }
            .map { indexed ->
                val child = indexed.value
                val createsChildContextHint = createsChildStackingContextHint(child)
                StackingParticipant(
                    node = child,
                    logicalParent = owner,
                    sourceDomOrder = indexed.index,
                    priority = orderingPriority(child, indexed.index),
                    kind = if (createsChildContextHint) {
                        StackingParticipantKind.ChildContext
                    } else {
                        StackingParticipantKind.LocalNode
                    },
                    createsChildContextHint = createsChildContextHint,
                    rootContextPromotionTarget = null
                )
            }
    }

    private fun rootContextParticipants(root: DOMNode, contextId: RootStackingContextId): List<StackingParticipant> {
        val globalDomOrder = buildGlobalDomOrderMap(root)
        val localParticipants = root.children.withIndex()
            .filter { indexed -> indexed.value.position != PositionMode.Fixed }
            .map { indexed ->
                val child = indexed.value
                val domOrder = globalDomOrder[child] ?: indexed.index
                val createsChildContextHint = createsChildStackingContextHint(child)
                StackingParticipant(
                    node = child,
                    logicalParent = root,
                    sourceDomOrder = domOrder,
                    priority = orderingPriority(child, domOrder),
                    kind = if (createsChildContextHint) {
                        StackingParticipantKind.ChildContext
                    } else {
                        StackingParticipantKind.LocalNode
                    },
                    createsChildContextHint = createsChildContextHint,
                    rootContextPromotionTarget = null
                )
            }
        val promotedFixedParticipants = collectPromotedFixedNodes(root)
            .map { fixed ->
                val domOrder = globalDomOrder[fixed] ?: Int.MAX_VALUE
                StackingParticipant(
                    node = fixed,
                    logicalParent = fixed.parent ?: root,
                    sourceDomOrder = domOrder,
                    priority = orderingPriority(fixed, domOrder),
                    kind = StackingParticipantKind.ChildContext,
                    createsChildContextHint = true,
                    rootContextPromotionTarget = contextId
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

    private fun createsChildStackingContextHint(node: DOMNode): Boolean {
        if (node.parent == null) return true
        if (node.position == PositionMode.Fixed) return true
        if (isPositioned(node) && node.zIndex != 0) return true
        return false
    }

    fun resolveHorizontalOffset(left: CssLength?, right: CssLength?): OffsetPrecedenceResolution {
        return when {
            left != null -> OffsetPrecedenceResolution(StyleProperty.LEFT, left)
            right != null -> OffsetPrecedenceResolution(StyleProperty.RIGHT, right)
            else -> OffsetPrecedenceResolution(null, null)
        }
    }

    fun resolveVerticalOffset(top: CssLength?, bottom: CssLength?): OffsetPrecedenceResolution {
        return when {
            top != null -> OffsetPrecedenceResolution(StyleProperty.TOP, top)
            bottom != null -> OffsetPrecedenceResolution(StyleProperty.BOTTOM, bottom)
            else -> OffsetPrecedenceResolution(null, null)
        }
    }

    fun orderedChildrenForPaint(parent: DOMNode): List<DOMNode> {
        val participants = stackingContextScaffold(parent).participants
        if (participants.size <= 1) {
            return participants.map { it.node }
        }
        val hasPositioned = participants.any { isPositioned(it.node) }
        if (!hasPositioned) {
            return participants
                .sortedBy { it.priority.domOrder }
                .map { it.node }
        }

        return participants
            .map { participant -> ChildEntry(participant.node, participant.priority) }
            .sortedWith(
                compareBy(
                    { it.priority.positionedBucket },
                    { it.priority.zIndex },
                    { it.priority.domOrder }
                )
            )
            .map { it.node }
    }

    fun orderedChildrenForHitTesting(parent: DOMNode): List<DOMNode> {
        return orderedChildrenForPaint(parent).asReversed()
    }
}

