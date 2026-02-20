package org.dreamfinity.dsgl.core.event

import org.dreamfinity.dsgl.core.DsglColors
import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.render.RenderCommand
import kotlin.math.exp

/**
 * Core drag-and-drop orchestrator with HTML-like event semantics.
 */
object DragManager {
    private const val DRAG_THRESHOLD_SQUARED_DISTANCE = 16
    private const val GHOST_SMOOTHING_COEF = 32.0
    private const val DRAG_TICK_SEC = 0.05
    private const val OVER_TICK_SEC = 0.04

    private data class PendingDrag(
        val sourceNode: DOMNode,
        val sourceKey: Any?,
        val sourceClass: Class<out DOMNode>,
        val startX: Int,
        val startY: Int
    )

    private data class ActiveDrag(
        var sourceNode: DOMNode,
        val sourceKey: Any?,
        val sourceClass: Class<out DOMNode>,
        val dataTransfer: DataTransfer,
        var cursorX: Int,
        var cursorY: Int,
        var ghostX: Double,
        var ghostY: Double,
        var dropTargetNode: DOMNode? = null,
        var dropTargetKey: Any? = null,
        var dropTargetClass: Class<out DOMNode>? = null,
        var dropAccepted: Boolean = false,
        var dragTickAccum: Double = 0.0,
        var overTickAccum: Double = 0.0
    )

    private var pendingDrag: PendingDrag? = null
    private var activeDrag: ActiveDrag? = null

    val isDragging: Boolean
        get() = activeDrag != null

    val isPointerCaptured: Boolean
        get() = pendingDrag != null || activeDrag != null

    fun onMouseDown(_root: DOMNode, target: DOMNode?, event: MouseDownEvent) {
        if (event.mouseButton != MouseButton.LEFT) {
            pendingDrag = null
            return
        }
        if (activeDrag != null) return
        pendingDrag = resolveDraggableSource(target)?.let { source ->
            PendingDrag(
                sourceNode = source,
                sourceKey = source.key,
                sourceClass = source.javaClass,
                startX = event.mouseX,
                startY = event.mouseY
            )
        }
    }

    fun onMouseMove(root: DOMNode, mouseX: Int, mouseY: Int) {
        val pending = pendingDrag
        if (pending != null && activeDrag == null) {
            val dx = mouseX - pending.startX
            val dy = mouseY - pending.startY
            if ((dx * dx) + (dy * dy) >= DRAG_THRESHOLD_SQUARED_DISTANCE) {
                tryStartDrag(root, pending, mouseX, mouseY)
            }
        }

        val active = activeDrag ?: return
        val moved = active.cursorX != mouseX || active.cursorY != mouseY
        active.cursorX = mouseX
        active.cursorY = mouseY
        if (moved) {
            updateDropTarget(root, active, dispatchOver = true)
            dispatchDragEvent(active)
        }
    }

    fun onMouseUp(root: DOMNode, event: MouseUpEvent): Boolean {
        if (event.mouseButton != MouseButton.LEFT) return false

        val active = activeDrag
        if (active == null) {
            pendingDrag = null
            return false
        }

        active.cursorX = event.mouseX
        active.cursorY = event.mouseY
        updateDropTarget(root, active, dispatchOver = true)

        val acceptedTarget = if (active.dropAccepted) active.dropTargetNode else null
        var didDrop = false
        var dropTargetKey: Any? = null
        if (acceptedTarget != null) {
            val dropEvent = DropEvent(
                x = active.cursorX,
                y = active.cursorY,
                dragSourceKey = active.sourceKey,
                transfer = active.dataTransfer
            )
            dropEvent.target = acceptedTarget
            EventBus.post(dropEvent)
            dropTargetKey = acceptedTarget.key
            didDrop = true
            if (dropEvent.dropAccepted) {
                active.dropAccepted = true
            }
        } else {
            active.dataTransfer.dropEffect = DropEffect.NONE
        }

        dispatchDragEnd(active, didDrop, dropTargetKey)
        pendingDrag = null
        activeDrag = null
        return true
    }

    fun onFrame(root: DOMNode, dtSeconds: Double) {
        if (activeDrag == null) return
        val safeDt = dtSeconds.coerceAtLeast(0.0)
        rebindAfterReconcile(root)
        val rebound = activeDrag ?: return

        val alpha = 1.0 - exp(-GHOST_SMOOTHING_COEF * safeDt)
        rebound.ghostX += (rebound.cursorX.toDouble() - rebound.ghostX) * alpha
        rebound.ghostY += (rebound.cursorY.toDouble() - rebound.ghostY) * alpha

        rebound.dragTickAccum += safeDt
        if (rebound.dragTickAccum >= DRAG_TICK_SEC) {
            rebound.dragTickAccum = 0.0
            dispatchDragEvent(rebound)
        }

        rebound.overTickAccum += safeDt
        if (rebound.overTickAccum >= OVER_TICK_SEC) {
            rebound.overTickAccum = 0.0
            updateDropTarget(root, rebound, dispatchOver = true)
        }
    }

    fun appendOverlayCommands(
        root: DOMNode,
        ctx: UiMeasureContext,
        viewportWidth: Int,
        viewportHeight: Int,
        out: MutableList<RenderCommand>
    ) {
        val active = activeDrag ?: return
        if (viewportWidth <= 0 || viewportHeight <= 0) return

        val preview = active.dataTransfer.currentDragImageSpec()?.let { spec ->
            findByKey(root, spec.nodeKey)?.let { node -> node to spec }
        }

        out.add(RenderCommand.PushClip(0, 0, viewportWidth, viewportHeight))
        if (preview != null) {
            val previewNode = preview.first
            val spec = preview.second
            val dx = (active.ghostX - spec.offsetX - previewNode.bounds.x).toInt()
            val dy = (active.ghostY - spec.offsetY - previewNode.bounds.y).toInt()
            val previewCommands = ArrayList<RenderCommand>(32)
            previewNode.buildRenderCommands(ctx, previewCommands)
            previewCommands.forEach { cmd ->
                out.add(shiftCommand(cmd, dx, dy))
            }
        } else {
            drawDefaultGhost(active, ctx, out)
        }
        out.add(RenderCommand.PopClip)
    }

    fun rebindAfterReconcile(root: DOMNode) {
        val active = activeDrag ?: return

        val reboundSource = findByKeyAndClass(root, active.sourceKey, active.sourceClass)
        if (reboundSource == null) {
            finishDragWithoutDrop(active)
            return
        }
        active.sourceNode = reboundSource

        val dropClass = active.dropTargetClass
        val reboundTarget = if (active.dropTargetKey != null && dropClass != null) {
            findByKeyAndClass(root, active.dropTargetKey, dropClass)
        } else {
            null
        }
        active.dropTargetNode = reboundTarget
        if (reboundTarget == null) {
            active.dropTargetKey = null
            active.dropTargetClass = null
            active.dropAccepted = false
            active.dataTransfer.dropEffect = DropEffect.NONE
        }
    }

    fun cancelActiveDrag() {
        val active = activeDrag ?: run {
            pendingDrag = null
            return
        }
        finishDragWithoutDrop(active)
    }

    private fun tryStartDrag(root: DOMNode, pending: PendingDrag, mouseX: Int, mouseY: Int) {
        val source = findByKeyAndClass(root, pending.sourceKey, pending.sourceClass) ?: pending.sourceNode
        val transfer = DataTransfer()
        val startEvent = DragStartEvent(
            x = mouseX,
            y = mouseY,
            dragSourceKey = source.key,
            transfer = transfer
        )
        startEvent.target = source
        EventBus.post(startEvent)
        if (startEvent.cancelled) {
            pendingDrag = null
            return
        }

        val startedDrag = ActiveDrag(
            sourceNode = source,
            sourceKey = source.key,
            sourceClass = source.javaClass,
            dataTransfer = transfer,
            cursorX = mouseX,
            cursorY = mouseY,
            ghostX = mouseX.toDouble(),
            ghostY = mouseY.toDouble()
        )
        activeDrag = startedDrag
        pendingDrag = null
        updateDropTarget(root, startedDrag, dispatchOver = true)
        dispatchDragEvent(startedDrag)
    }

    private fun resolveDraggableSource(start: DOMNode?): DOMNode? {
        var current = start
        while (current != null) {
            if (current.draggable && !current.styleDisabled) return current
            current = current.parent
        }
        return null
    }

    private fun updateDropTarget(root: DOMNode, active: ActiveDrag, dispatchOver: Boolean) {
        val resolvedTarget = resolveDropTarget(root, active.cursorX, active.cursorY)
        if (!isSameNode(active.dropTargetNode, resolvedTarget)) {
            active.dropTargetNode?.let { prev ->
                val leaveEvent = DragLeaveEvent(
                    x = active.cursorX,
                    y = active.cursorY,
                    dragSourceKey = active.sourceKey,
                    transfer = active.dataTransfer
                )
                leaveEvent.target = prev
                EventBus.post(leaveEvent)
            }
            active.dropTargetNode = resolvedTarget
            active.dropTargetKey = resolvedTarget?.key
            active.dropTargetClass = resolvedTarget?.javaClass
            active.dropAccepted = false
            active.dataTransfer.dropEffect = DropEffect.NONE
            resolvedTarget?.let { next ->
                val enterEvent = DragEnterEvent(
                    x = active.cursorX,
                    y = active.cursorY,
                    dragSourceKey = active.sourceKey,
                    transfer = active.dataTransfer
                )
                enterEvent.target = next
                EventBus.post(enterEvent)
            }
        }

        if (!dispatchOver) return
        val currentTarget = active.dropTargetNode ?: run {
            active.dropAccepted = false
            active.dataTransfer.dropEffect = DropEffect.NONE
            return
        }

        val overEvent = DragOverEvent(
            x = active.cursorX,
            y = active.cursorY,
            dragSourceKey = active.sourceKey,
            transfer = active.dataTransfer
        )
        overEvent.target = currentTarget
        EventBus.post(overEvent)

        val accepted = overEvent.dropAccepted || overEvent.cancelled
        active.dropAccepted = accepted
        if (accepted) {
            active.dataTransfer.dropEffect = normalizeDropEffect(
                requested = active.dataTransfer.dropEffect,
                allowed = active.dataTransfer.effectAllowed
            )
        } else {
            active.dataTransfer.dropEffect = DropEffect.NONE
        }
    }

    private fun resolveDropTarget(root: DOMNode, mouseX: Int, mouseY: Int): DOMNode? {
        val chain = collectHoverChain(root, mouseX, mouseY)
        for (index in chain.size - 1 downTo 0) {
            val node = chain[index]
            if (node.droppable && !node.styleDisabled) {
                return node
            }
        }
        return null
    }

    private fun dispatchDragEvent(active: ActiveDrag) {
        val source = active.sourceNode
        val dragEvent = DragEvent(
            x = active.cursorX,
            y = active.cursorY,
            dragSourceKey = active.sourceKey,
            transfer = active.dataTransfer
        )
        dragEvent.target = source
        EventBus.post(dragEvent)
    }

    private fun dispatchDragEnd(active: ActiveDrag, didDrop: Boolean, dropTargetKey: Any?) {
        val source = active.sourceNode
        val event = DragEndEvent(
            x = active.cursorX,
            y = active.cursorY,
            dragSourceKey = active.sourceKey,
            transfer = active.dataTransfer,
            didDrop = didDrop,
            finalDropEffect = if (didDrop) active.dataTransfer.dropEffect else DropEffect.NONE,
            dropTargetKey = dropTargetKey
        )
        event.target = source
        EventBus.post(event)
    }

    private fun finishDragWithoutDrop(active: ActiveDrag) {
        dispatchDragEnd(active, didDrop = false, dropTargetKey = null)
        pendingDrag = null
        activeDrag = null
    }

    private fun normalizeDropEffect(requested: DropEffect, allowed: EffectAllowed): DropEffect {
        if (requested != DropEffect.NONE && isDropEffectAllowed(requested, allowed)) {
            return requested
        }
        return when (allowed) {
            EffectAllowed.NONE -> DropEffect.NONE
            EffectAllowed.COPY -> DropEffect.COPY
            EffectAllowed.MOVE -> DropEffect.MOVE
            EffectAllowed.LINK -> DropEffect.LINK
            EffectAllowed.COPY_LINK -> DropEffect.COPY
            EffectAllowed.COPY_MOVE -> DropEffect.MOVE
            EffectAllowed.LINK_MOVE -> DropEffect.MOVE
            EffectAllowed.ALL -> DropEffect.MOVE
        }
    }

    private fun isDropEffectAllowed(effect: DropEffect, allowed: EffectAllowed): Boolean {
        return when (allowed) {
            EffectAllowed.NONE -> false
            EffectAllowed.COPY -> effect == DropEffect.COPY
            EffectAllowed.MOVE -> effect == DropEffect.MOVE
            EffectAllowed.LINK -> effect == DropEffect.LINK
            EffectAllowed.COPY_LINK -> effect == DropEffect.COPY || effect == DropEffect.LINK
            EffectAllowed.COPY_MOVE -> effect == DropEffect.COPY || effect == DropEffect.MOVE
            EffectAllowed.LINK_MOVE -> effect == DropEffect.LINK || effect == DropEffect.MOVE
            EffectAllowed.ALL -> effect != DropEffect.NONE
        }
    }

    private fun drawDefaultGhost(
        active: ActiveDrag,
        ctx: UiMeasureContext,
        out: MutableList<RenderCommand>
    ) {
        val label = active.dataTransfer.getData("text/plain") ?: "drag"
        val x = (active.ghostX - 10.0).toInt()
        val y = (active.ghostY - 10.0).toInt()
        val width = (ctx.measureText(label) + 12).coerceAtLeast(48)
        val height = (ctx.fontHeight + 8).coerceAtLeast(14)
        out.add(RenderCommand.DrawRect(x, y, width, height, 0xCC222222.toInt()))
        out.add(RenderCommand.DrawRect(x, y, width, 1, 0xFF7F8C99.toInt()))
        out.add(RenderCommand.DrawRect(x, y + height - 1, width, 1, 0xFF7F8C99.toInt()))
        out.add(RenderCommand.DrawRect(x, y, 1, height, 0xFF7F8C99.toInt()))
        out.add(RenderCommand.DrawRect(x + width - 1, y, 1, height, 0xFF7F8C99.toInt()))
        out.add(RenderCommand.DrawText(label, x + 6, y + 4, DsglColors.WHITE))
    }

    private fun shiftCommand(command: RenderCommand, dx: Int, dy: Int): RenderCommand {
        return when (command) {
            is RenderCommand.DrawRect -> command.copy(
                x = command.x + dx,
                y = command.y + dy
            )

            is RenderCommand.DrawText -> command.copy(
                x = command.x + dx,
                y = command.y + dy
            )

            is RenderCommand.DrawImage -> command.copy(
                x = command.x + dx,
                y = command.y + dy
            )

            is RenderCommand.DrawItemStack -> command.copy(
                x = command.x + dx,
                y = command.y + dy
            )

            is RenderCommand.PushClip -> command.copy(
                x = command.x + dx,
                y = command.y + dy
            )

            RenderCommand.PopClip -> RenderCommand.PopClip
        }
    }

    private fun isSameNode(prev: DOMNode?, current: DOMNode?): Boolean {
        if (prev === current) return true
        if (prev == null || current == null) return false
        val prevKey = prev.key
        val currKey = current.key
        if (prevKey != null || currKey != null) {
            return prevKey != null &&
                currKey != null &&
                prevKey == currKey &&
                prev.javaClass == current.javaClass
        }
        return false
    }

    private fun findByKey(root: DOMNode, key: Any): DOMNode? {
        if (root.key == key) return root
        for (child in root.children) {
            val found = findByKey(child, key)
            if (found != null) {
                return found
            }
        }
        return null
    }

    private fun findByKeyAndClass(root: DOMNode, key: Any?, cls: Class<out DOMNode>): DOMNode? {
        if (key == null) return null
        if (root.key == key && root.javaClass == cls) return root
        for (child in root.children) {
            val found = findByKeyAndClass(child, key, cls)
            if (found != null) {
                return found
            }
        }
        return null
    }
}
