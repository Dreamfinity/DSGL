package org.dreamfinity.dsgl.core.overlay.input

import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.elements.RangeInputNode
import org.dreamfinity.dsgl.core.dom.elements.SingleLineInputNode
import org.dreamfinity.dsgl.core.dom.elements.TextAreaNode
import org.dreamfinity.dsgl.core.dom.layout.AffineTransform2D
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.event.EventBus
import org.dreamfinity.dsgl.core.event.FocusManager
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.event.MouseClickEvent
import org.dreamfinity.dsgl.core.event.MouseDragEvent
import org.dreamfinity.dsgl.core.event.MouseDownEvent
import org.dreamfinity.dsgl.core.event.MouseEnterEvent
import org.dreamfinity.dsgl.core.event.MouseLeaveEvent
import org.dreamfinity.dsgl.core.event.MouseMoveEvent
import org.dreamfinity.dsgl.core.event.MouseOverEvent
import org.dreamfinity.dsgl.core.event.MouseUpEvent
import org.dreamfinity.dsgl.core.event.MouseWheelEvent
import org.dreamfinity.dsgl.core.event.KeyboardKeyDownEvent

class LayerDomInputRouter(
    private val rootProvider: () -> DOMNode?
) {
    private val hoverChain: MutableList<DOMNode> = ArrayList()
    private var hoverTarget: DOMNode? = null
    private var dragCaptureTarget: DOMNode? = null
    private var dragCaptureKey: Any? = null
    private var dragCaptureClass: Class<out DOMNode>? = null
    private var dragCaptureFocusKey: Any? = null
    private var activeTarget: DOMNode? = null
    private var pressedButton: MouseButton? = null
    private var draggedSincePress: Boolean = false
    private var lastMoveX: Int = Int.MIN_VALUE
    private var lastMoveY: Int = Int.MIN_VALUE

    fun handleMouseMove(mouseX: Int, mouseY: Int): Boolean {
        val root = rootProvider() ?: run {
            clear()
            return false
        }
        restoreDragCapture(root)
        val prevX = if (lastMoveX == Int.MIN_VALUE) mouseX else lastMoveX
        val prevY = if (lastMoveY == Int.MIN_VALUE) mouseY else lastMoveY
        val dx = mouseX - prevX
        val dy = mouseY - prevY
        updateHoverLocal(root, hoverChain, mouseX, mouseY, dx, dy)
        hoverTarget = hoverChain.lastOrNull()

        if (dragCaptureTarget != null && hasFocusChangedSinceCapture()) {
            releaseDragCapture()
        }

        if (dx != 0 || dy != 0) {
            val move = MouseMoveEvent(mouseX, mouseY, prevX, prevY)
            move.target = dragCaptureTarget ?: hoverTarget
            EventBus.post(move)
            val button = pressedButton
            if (button != null) {
                draggedSincePress = true
                val drag = MouseDragEvent(prevX, prevY, dx, dy, button)
                drag.target = dragCaptureTarget ?: hoverTarget
                EventBus.post(drag)
                dragCaptureTarget?.continuePointerCapture(
                    mouseX = mouseX,
                    mouseY = mouseY,
                    mouseDX = dx,
                    mouseDY = dy,
                    button = button
                )
            }
        }
        lastMoveX = mouseX
        lastMoveY = mouseY
        return dragCaptureTarget != null || hoverTarget != null
    }

    fun handleMouseDown(mouseX: Int, mouseY: Int, button: MouseButton): Boolean {
        val root = rootProvider() ?: run {
            clear()
            return false
        }
        restoreDragCapture(root)
        updateHoverLocal(root, hoverChain, mouseX, mouseY, 0, 0)
        hoverTarget = hoverChain.lastOrNull()
        val target = hoverTarget ?: return false

        val down = MouseDownEvent(mouseX, mouseY, button)
        down.target = target
        EventBus.post(down)

        if (button == MouseButton.LEFT) {
            setActiveTarget(target)
            val capture = resolveDragCaptureTarget(target, mouseX, mouseY)
            if (capture != null) {
                setDragCapture(capture)
                capture.beginPointerCapture(mouseX, mouseY, button)
            } else {
                releaseDragCapture()
            }
        }
        pressedButton = button
        draggedSincePress = false
        lastMoveX = mouseX
        lastMoveY = mouseY
        return true
    }

    fun handleMouseUp(mouseX: Int, mouseY: Int, button: MouseButton): Boolean {
        val root = rootProvider() ?: run {
            clear()
            return false
        }
        restoreDragCapture(root)
        updateHoverLocal(root, hoverChain, mouseX, mouseY, 0, 0)
        hoverTarget = hoverChain.lastOrNull()
        val releaseTarget = dragCaptureTarget ?: hoverTarget ?: activeTarget
        val hadCapture = dragCaptureTarget != null
        val pressed = pressedButton
        if (pressed != button && releaseTarget == null) {
            return false
        }

        val up = MouseUpEvent(mouseX, mouseY, button)
        up.target = releaseTarget
        EventBus.post(up)
        dragCaptureTarget?.endPointerCapture(mouseX, mouseY, button)
        if (!hadCapture && !draggedSincePress && pressed == button) {
            val click = MouseClickEvent(mouseX, mouseY, button)
            click.target = hoverTarget
            EventBus.post(click)
        }

        pressedButton = null
        draggedSincePress = false
        clearActiveTarget()
        releaseDragCapture()
        lastMoveX = mouseX
        lastMoveY = mouseY
        return releaseTarget != null || pressed == button
    }

    fun handleMouseWheel(mouseX: Int, mouseY: Int, delta: Int): Boolean {
        if (delta == 0) return false
        val root = rootProvider() ?: run {
            clear()
            return false
        }
        restoreDragCapture(root)
        updateHoverLocal(root, hoverChain, mouseX, mouseY, 0, 0)
        hoverTarget = hoverChain.lastOrNull()
        val target = resolveWheelTarget() ?: return false
        val wheel = MouseWheelEvent(mouseX, mouseY, delta)
        wheel.target = target
        EventBus.post(wheel)
        if (wheel.cancelled) return true
        return bubbleGenericWheel(target, mouseX, mouseY, delta)
    }

    fun handleKeyDown(keyCode: Int, keyChar: Char): Boolean {
        val root = rootProvider() ?: run {
            clear()
            return false
        }
        val focused = FocusManager.focusedNode() ?: return false
        if (!isSameOrAncestor(root, focused)) return false
        val event = KeyboardKeyDownEvent(keyChar = keyChar, keyCode = keyCode)
        event.target = focused
        EventBus.post(event)
        return true
    }

    fun clear() {
        clearHoverChainStates()
        hoverTarget = null
        releaseDragCapture()
        clearActiveTarget()
        pressedButton = null
        draggedSincePress = false
        lastMoveX = Int.MIN_VALUE
        lastMoveY = Int.MIN_VALUE
    }

    private fun resolveDragCaptureTarget(start: DOMNode?, mouseX: Int, mouseY: Int): DOMNode? {
        var current = start
        while (current != null) {
            when (current) {
                is RangeInputNode -> return current
                is SingleLineInputNode -> if (current.shouldCaptureTextSelectionDrag(mouseX, mouseY)) return current
                is TextAreaNode -> if (current.shouldCaptureAnyDrag(mouseX, mouseY)) return current
            }
            if (current.shouldCapturePointerDrag(mouseX, mouseY)) {
                return current
            }
            current = current.parent
        }
        return null
    }

    private fun setDragCapture(target: DOMNode) {
        dragCaptureTarget = target
        dragCaptureKey = target.key
        dragCaptureClass = target.javaClass
        dragCaptureFocusKey = FocusManager.focusedNode()?.key
    }

    private fun releaseDragCapture() {
        dragCaptureTarget?.cancelPointerCapture()
        RangeInputNode.clearActiveDrag()
        SingleLineInputNode.clearActiveDrag()
        TextAreaNode.clearActiveDrag()
        dragCaptureTarget = null
        dragCaptureKey = null
        dragCaptureClass = null
        dragCaptureFocusKey = null
    }

    private fun restoreDragCapture(root: DOMNode) {
        if (dragCaptureTarget == null) return
        val key = dragCaptureKey
        val cls = dragCaptureClass
        if (cls == null) {
            releaseDragCapture()
            return
        }
        if (key == null) {
            val captured = dragCaptureTarget
            if (captured != null && captured.javaClass == cls) {
                if (pressedButton != null) {
                    return
                }
                if (isSameOrAncestor(root, captured)) {
                    return
                }
            }
            releaseDragCapture()
            return
        }
        val restored = findByKeyAndClass(root, key, cls)
        if (restored != null) {
            dragCaptureTarget = restored
            return
        }
        releaseDragCapture()
    }

    private fun findByKeyAndClass(node: DOMNode, key: Any, cls: Class<out DOMNode>): DOMNode? {
        if (node.key == key && node.javaClass == cls) return node
        node.children.forEach { child ->
            val found = findByKeyAndClass(child, key, cls)
            if (found != null) return found
        }
        return null
    }

    private fun hasFocusChangedSinceCapture(): Boolean {
        if (dragCaptureFocusKey == null) return false
        val currentFocusKey = FocusManager.focusedNode()?.key
        return currentFocusKey != dragCaptureFocusKey
    }

    private fun setActiveTarget(target: DOMNode?) {
        if (target?.styleDisabled == true) return
        if (activeTarget === target) return
        activeTarget?.setActiveState(false)
        activeTarget = target
        activeTarget?.setActiveState(true)
    }

    private fun clearActiveTarget() {
        activeTarget?.setActiveState(false)
        activeTarget = null
    }

    private fun resolveWheelTarget(): DOMNode? {
        val hovered = hoverTarget
        if (hovered != null) return hovered
        val focused = FocusManager.focusedNode()
        return if (focused is TextAreaNode) focused else null
    }

    private fun bubbleGenericWheel(target: DOMNode, mouseX: Int, mouseY: Int, delta: Int): Boolean {
        var current: DOMNode? = target
        while (current != null) {
            if (current.handleGenericWheel(mouseX, mouseY, delta)) {
                return true
            }
            current = current.parent
        }
        return false
    }

    private fun isSameOrAncestor(candidate: DOMNode, node: DOMNode?): Boolean {
        var current = node
        while (current != null) {
            if (current === candidate) return true
            current = current.parent
        }
        return false
    }

    private fun clearHoverChainStates() {
        hoverChain.forEach { node ->
            node.setHoveredState(false)
        }
        hoverChain.clear()
    }

    private fun collectHoverChainLocal(
        root: DOMNode,
        mouseX: Int,
        mouseY: Int,
        parentTransform: AffineTransform2D,
        parentInputClipRect: Rect?,
        out: MutableList<DOMNode>
    ): Boolean {
        if (root.styleDisabled) return false
        if (!root.isHitTestVisible()) return false
        if (!root.isPointInsideInputClip(mouseX, mouseY, parentInputClipRect)) return false
        val worldTransform = parentTransform.times(root.localTransformMatrix())
        val inverse = worldTransform.inverseOrNull() ?: return false
        val local = inverse.transform(mouseX.toFloat(), mouseY.toFloat())
        if (!root.bounds.contains(local.first, local.second)) return false
        val childInputClipRect = root.inputClipRectForChildren(parentInputClipRect)
        out.add(root)
        root.orderedChildrenForHitTestingTraversal().forEach { child ->
            if (
                collectHoverChainLocal(
                    root = child,
                    mouseX = mouseX,
                    mouseY = mouseY,
                    parentTransform = worldTransform,
                    parentInputClipRect = childInputClipRect,
                    out = out
                )
            ) {
                return true
            }
        }
        if (isTargetCandidate(root, mouseX, mouseY)) return true
        out.removeAt(out.lastIndex)
        return false
    }

    private fun isTargetCandidate(node: DOMNode, mouseX: Int, mouseY: Int): Boolean {
        if (node.focusable) return true
        if (
            node.onMouseDown != null ||
            node.onMouseUp != null ||
            node.onMouseClick != null ||
            node.onMouseDrag != null ||
            node.onMouseWheel != null ||
            node.onMouseMove != null ||
            node.onKeyDown != null ||
            node.onKeyUp != null
        ) {
            return true
        }
        if (node.shouldCapturePointerDrag(mouseX, mouseY)) return true
        if (node.hasGenericWheelHandling(mouseX, mouseY)) return true
        return EventBus.hasInputListeners(node)
    }

    private fun updateHoverLocal(
        root: DOMNode,
        prevHoverChain: MutableList<DOMNode>,
        mouseX: Int,
        mouseY: Int,
        mouseDX: Int,
        mouseDY: Int
    ) {
        val currHoverChain = ArrayList<DOMNode>(prevHoverChain.size + 4)
        collectHoverChainLocal(
            root = root,
            mouseX = mouseX,
            mouseY = mouseY,
            parentTransform = AffineTransform2D.IDENTITY,
            parentInputClipRect = null,
            out = currHoverChain
        )
        val minSize = minOf(prevHoverChain.size, currHoverChain.size)
        var commonPrefixLen = 0
        while (
            commonPrefixLen < minSize &&
            isSameHoverNodeLocal(prevHoverChain[commonPrefixLen], currHoverChain[commonPrefixLen])
        ) {
            commonPrefixLen++
        }
        for (i in prevHoverChain.size - 1 downTo commonPrefixLen) {
            prevHoverChain[i].setHoveredState(false)
            postMouseLeaveEventLocal(prevHoverChain[i], mouseX, mouseY)
        }
        for (i in commonPrefixLen until currHoverChain.size) {
            currHoverChain[i].setHoveredState(true)
            postMouseEnterEventLocal(currHoverChain[i], mouseX, mouseY)
        }
        for (i in 0 until commonPrefixLen) {
            currHoverChain[i].setHoveredState(true)
        }
        if (mouseDX != 0 || mouseDY != 0) {
            for (i in 0 until currHoverChain.size) {
                postMouseOverEventLocal(currHoverChain[i], mouseX, mouseY)
            }
        }
        prevHoverChain.clear()
        prevHoverChain.addAll(currHoverChain)
    }

    private fun isSameHoverNodeLocal(prev: DOMNode, curr: DOMNode): Boolean {
        if (prev === curr) return true
        val prevKey = prev.key
        val currKey = curr.key
        if (prevKey != null || currKey != null) {
            return prevKey != null &&
                currKey != null &&
                prevKey == currKey &&
                prev.javaClass == curr.javaClass
        }
        if (prev.parent == null && curr.parent == null) {
            return prev.javaClass == curr.javaClass
        }
        return false
    }

    private fun postMouseEnterEventLocal(target: DOMNode, mouseX: Int, mouseY: Int) {
        val event = MouseEnterEvent(mouseX, mouseY)
        event.target = target
        EventBus.post(event)
        target.onmouseenter?.invoke(event)
    }

    private fun postMouseLeaveEventLocal(target: DOMNode, mouseX: Int, mouseY: Int) {
        val event = MouseLeaveEvent(mouseX, mouseY)
        event.target = target
        EventBus.post(event)
        target.onmouseleave?.invoke(event)
    }

    private fun postMouseOverEventLocal(target: DOMNode, mouseX: Int, mouseY: Int) {
        val event = MouseOverEvent(mouseX, mouseY)
        event.target = target
        EventBus.post(event)
        target.onmouseover?.invoke(event)
    }
}

