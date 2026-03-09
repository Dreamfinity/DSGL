package org.dreamfinity.dsgl.core.dom.reconcile

import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.elements.*

data class DomReconcileResult(
    val root: DOMNode,
    val detachedRoots: List<DOMNode>,
    val reusedNodes: Int,
    val insertedNodes: Int,
    val removedNodes: Int,
    val replacedSubtrees: Int
)

private data class ChildIdentity(
    val key: Any,
    val type: Class<out DOMNode>
)

private class Counters {
    var reused: Int = 0
    var inserted: Int = 0
    var removed: Int = 0
    var replaced: Int = 0
}

object DomReconciler {
    fun reconcile(currentRoot: DOMNode, nextRoot: DOMNode): DomReconcileResult {
        val detached = ArrayList<DOMNode>(8)
        val counters = Counters()
        val mergedRoot = reconcileNodeOrReplace(
            current = currentRoot,
            template = nextRoot,
            detached = detached,
            counters = counters
        )
        return DomReconcileResult(
            root = mergedRoot,
            detachedRoots = detached,
            reusedNodes = counters.reused,
            insertedNodes = counters.inserted,
            removedNodes = counters.removed,
            replacedSubtrees = counters.replaced
        )
    }

    private fun reconcileNodeOrReplace(
        current: DOMNode,
        template: DOMNode,
        detached: MutableList<DOMNode>,
        counters: Counters
    ): DOMNode {
        if (!canReuse(current, template)) {
            counters.replaced += 1
            counters.removed += countSubtree(current)
            counters.inserted += countSubtree(template)
            detached.add(current)
            return template
        }

        counters.reused += 1
        syncNode(current, template)
        reconcileChildren(current, template, detached, counters)
        return current
    }

    private fun reconcileChildren(
        current: DOMNode,
        template: DOMNode,
        detached: MutableList<DOMNode>,
        counters: Counters
    ) {
        if (current.children.isEmpty() && template.children.isEmpty()) return

        val oldChildren = current.children.toList()
        val consumed = BooleanArray(oldChildren.size)
        val keyed = HashMap<ChildIdentity, ArrayDeque<Int>>()
        oldChildren.forEachIndexed { index, child ->
            val childKey = child.key
            if (childKey != null) {
                val identity = ChildIdentity(childKey, child.javaClass)
                keyed.getOrPut(identity) { ArrayDeque() }.addLast(index)
            }
        }

        val reconciledChildren = ArrayList<DOMNode>(template.children.size)
        template.children.forEachIndexed { index, templateChild ->
            val matchIndex = findMatchIndex(index, templateChild, oldChildren, consumed, keyed)
            if (matchIndex == null) {
                templateChild.parent = current
                reconciledChildren.add(templateChild)
                counters.inserted += countSubtree(templateChild)
            } else {
                consumed[matchIndex] = true
                val merged = reconcileNodeOrReplace(
                    current = oldChildren[matchIndex],
                    template = templateChild,
                    detached = detached,
                    counters = counters
                )
                merged.parent = current
                reconciledChildren.add(merged)
            }
        }

        oldChildren.forEachIndexed { index, child ->
            if (!consumed[index]) {
                detached.add(child)
                counters.removed += countSubtree(child)
            }
        }

        current.children.clear()
        current.children.addAll(reconciledChildren)
    }

    private fun findMatchIndex(
        index: Int,
        template: DOMNode,
        oldChildren: List<DOMNode>,
        consumed: BooleanArray,
        keyed: Map<ChildIdentity, ArrayDeque<Int>>
    ): Int? {
        val templateKey = template.key
        if (templateKey != null) {
            val identity = ChildIdentity(templateKey, template.javaClass)
            val queue = keyed[identity] ?: return null
            while (queue.isNotEmpty()) {
                val candidate = queue.removeFirst()
                if (!consumed[candidate]) {
                    return candidate
                }
            }
            return null
        }

        if (index < oldChildren.size) {
            val candidate = oldChildren[index]
            if (!consumed[index] && candidate.key == null && canReuse(candidate, template)) {
                return index
            }
        }

        for (candidateIndex in oldChildren.indices) {
            if (consumed[candidateIndex]) continue
            val candidate = oldChildren[candidateIndex]
            if (candidate.key != null) continue
            if (canReuse(candidate, template)) {
                return candidateIndex
            }
        }
        return null
    }

    private fun canReuse(current: DOMNode, template: DOMNode): Boolean {
        if (current.javaClass != template.javaClass) return false
        if (current.key != template.key) return false
        return !isReplaceOnlyType(current)
    }

    private fun isReplaceOnlyType(node: DOMNode): Boolean {
        return node is CheckboxGroupNode ||
                node is NumberInputNode ||
                node is DateInputNode ||
                node is RangeInputNode
    }

    private fun syncNode(current: DOMNode, template: DOMNode) {
        current.syncBaseFrom(template)
        when (current) {
            is ContainerNode if template is ContainerNode -> {
                current.backgroundColor = template.backgroundColor
                current.stackLayout = template.stackLayout
            }

            is TextNode if template is TextNode -> {
                current.color = template.color
                current.syncSourceFrom(template)
            }

            is ButtonNode if template is ButtonNode -> {
                current.text = template.text
                current.textColor = template.textColor
                current.backgroundColor = template.backgroundColor
                current.syncClickFrom(template)
            }

            is ImageNode if template is ImageNode -> {
                current.url = template.url
                current.imageWidth = template.imageWidth
                current.imageHeight = template.imageHeight
            }

            is ItemStackNode if template is ItemStackNode -> {
                current.stack = template.stack
                current.size = template.size
                current.rotYDeg = template.rotYDeg
                current.rotXDeg = template.rotXDeg
            }

            is SingleLineInputNode if template is SingleLineInputNode -> {
                current.syncEditablePropsFrom(template)
            }

            is TextAreaNode if template is TextAreaNode -> {
                current.syncEditablePropsFrom(template)
            }

            is RadioGroupNode if template is RadioGroupNode -> {
                current.variants = template.variants
                current.selectedId = template.selectedId
                current.textColor = template.textColor
                current.boxColor = template.boxColor
                current.dotColor = template.dotColor
            }

            is SelectNode if template is SelectNode -> {
                current.syncFrom(template)
            }

            is ToggleNode if template is ToggleNode -> {
                current.syncFrom(template)
            }

            is ColorPickerInlineNode if template is ColorPickerInlineNode -> {
                current.syncFrom(template)
            }

            is ColorPickerPopupPaneNode if template is ColorPickerPopupPaneNode -> {
                current.syncFrom(template)
            }
        }
    }

    private fun countSubtree(node: DOMNode): Int {
        var total = 1
        node.children.forEach { child ->
            total += countSubtree(child)
        }
        return total
    }
}
