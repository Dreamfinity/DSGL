package org.dreamfinity.dsgl.core.dnd.internal

import org.dreamfinity.dsgl.core.DsglColors
import org.dreamfinity.dsgl.core.dnd.*
import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.event.*
import org.dreamfinity.dsgl.core.render.RenderCommand
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.exp

/**
 * Core drag-and-drop orchestrator.
 */
object DefaultDndEngine : DndEngine {
    private const val DRAG_THRESHOLD_SQUARED_DISTANCE = 16
    private const val DRAG_TICK_SEC = 0.05
    private const val OVER_TICK_SEC = 0.04
    private const val MIN_SMOOTHING_K = 0.0
    private const val MAX_SMOOTHING_K = 96.0

    private data class PendingDrag(
        val sourceNode: DOMNode,
        val sourceKey: Any?,
        val sourceClass: Class<out DOMNode>,
        val startX: Int,
        val startY: Int
    )

    private data class ActiveSession(
        var sourceNode: DOMNode,
        val sourceKey: Any?,
        val sourceClass: Class<out DOMNode>,
        val dataTransfer: DataTransfer,
        val previewMode: DragPreviewMode,
        val sourceHiddenDuringDrag: Boolean,
        val placeholderWidth: Int,
        val placeholderHeight: Int,
        val placeholderBuilder: (PlaceholderScope.() -> Unit)?,
        val previewBuilder: (DragPreviewScope.() -> Unit)?,
        var cursorX: Int,
        var cursorY: Int,
        var previewX: Double,
        var previewY: Double,
        var previewOffsetX: Int,
        var previewOffsetY: Int,
        var dropTargetNode: DOMNode? = null,
        var dropTargetKey: Any? = null,
        var dropTargetClass: Class<out DOMNode>? = null,
        var dropAccepted: Boolean = false,
        var collisionCandidateCount: Int = 0,
        var sourceExcludedFromHitTest: Boolean = true,
        var dragTickAccum: Double = 0.0,
        var overTickAccum: Double = 0.0
    )

    private var pendingDrag: PendingDrag? = null
    private var activeDrag: ActiveSession? = null
    private var smoothingFactor: Double = 26.0
    private val monitorListeners: LinkedHashMap<Long, DndMonitorListener> = linkedMapOf()
    private val monitorTokenGenerator: AtomicLong = AtomicLong(1L)

    override val isDragging: Boolean
        get() = activeDrag != null

    override val isPointerCaptured: Boolean
        get() = pendingDrag != null || activeDrag != null

    override fun setSmoothingFactor(value: Double) {
        smoothingFactor = value.coerceIn(MIN_SMOOTHING_K, MAX_SMOOTHING_K)
    }

    override fun getSmoothingFactor(): Double = smoothingFactor

    override fun monitor(nodeKey: Any?): DndMonitorState {
        val active = activeDrag ?: return DndMonitorState(
            isDragging = false,
            sourceKey = null,
            cursorX = 0,
            cursorY = 0,
            previewX = 0.0,
            previewY = 0.0,
            mode = null,
            overKey = null,
            collisionCandidates = 0,
            sourceExcludedFromHitTest = true
        )
        val draggingThisSource = nodeKey != null && nodeKey == active.sourceKey
        return DndMonitorState(
            isDragging = nodeKey == null || draggingThisSource,
            sourceKey = active.sourceKey,
            cursorX = active.cursorX,
            cursorY = active.cursorY,
            previewX = active.previewX,
            previewY = active.previewY,
            mode = active.previewMode,
            overKey = active.dropTargetKey,
            collisionCandidates = active.collisionCandidateCount,
            sourceExcludedFromHitTest = active.sourceExcludedFromHitTest
        )
    }

    override fun isDraggingNode(nodeKey: Any?): Boolean {
        val active = activeDrag ?: return false
        if (nodeKey == null) return false
        return nodeKey == active.sourceKey
    }

    override fun activeDrag(): ActiveDrag? {
        val active = activeDrag ?: return null
        return toApiActiveDrag(active)
    }

    override fun subscribe(listener: DndMonitorListener): AutoCloseable {
        val token = monitorTokenGenerator.getAndIncrement()
        monitorListeners[token] = listener
        return AutoCloseable {
            monitorListeners.remove(token)
        }
    }

    override fun onMouseDown(_root: DOMNode, target: DOMNode?, event: MouseDownEvent) {
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

    override fun onMouseMove(root: DOMNode, mouseX: Int, mouseY: Int) {
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
            notifyDragMove(active)
        }
    }

    override fun onMouseUp(root: DOMNode, event: MouseUpEvent): Boolean {
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
        } else {
            active.dataTransfer.dropEffect = DropEffect.NONE
        }

        finishDrag(active, didDrop, dropTargetKey)
        return true
    }

    override fun onFrame(root: DOMNode, dtSeconds: Double) {
        if (activeDrag == null) return
        val safeDt = dtSeconds.coerceAtLeast(0.0)
        rebindAfterReconcile(root)
        val active = activeDrag ?: return

        val alpha = if (smoothingFactor <= 0.0) {
            1.0
        } else {
            1.0 - exp(-smoothingFactor * safeDt)
        }
        active.previewX += (active.cursorX.toDouble() - active.previewX) * alpha
        active.previewY += (active.cursorY.toDouble() - active.previewY) * alpha

        active.dragTickAccum += safeDt
        if (active.dragTickAccum >= DRAG_TICK_SEC) {
            active.dragTickAccum = 0.0
            dispatchDragEvent(active)
        }

        active.overTickAccum += safeDt
        if (active.overTickAccum >= OVER_TICK_SEC) {
            active.overTickAccum = 0.0
            updateDropTarget(root, active, dispatchOver = true)
        }
    }

    override fun appendPlaceholderCommands(out: MutableList<RenderCommand>) {
        val active = activeDrag ?: return
        if (active.previewMode != DragPreviewMode.ORIGINAL) return

        val source = active.sourceNode
        val width = active.placeholderWidth.coerceAtLeast(0)
        val height = active.placeholderHeight.coerceAtLeast(0)
        if (width <= 0 || height <= 0) return
        val bounds = Rect(source.bounds.x, source.bounds.y, width, height)
        val scope = PlaceholderScope()
        active.placeholderBuilder?.invoke(scope)
        out.addAll(scope.buildCommands(bounds))
    }

    override fun appendOverlayCommands(
        root: DOMNode,
        ctx: UiMeasureContext,
        viewportWidth: Int,
        viewportHeight: Int,
        out: MutableList<RenderCommand>
    ) {
        val active = activeDrag ?: return
        if (viewportWidth <= 0 || viewportHeight <= 0) return

        out.add(RenderCommand.PushClip(0, 0, viewportWidth, viewportHeight))
        when (active.previewMode) {
            DragPreviewMode.ORIGINAL -> appendOriginalPreviewCommands(active, ctx, out)
            DragPreviewMode.GHOST -> appendGhostPreviewCommands(root, active, ctx, out)
        }
        out.add(RenderCommand.PopClip)
    }

    override fun rebindAfterReconcile(root: DOMNode) {
        val active = activeDrag ?: return

        val reboundSource = findByKeyAndClass(root, active.sourceKey, active.sourceClass)
        if (reboundSource == null) {
            notifyDragCancel(active)
            finishDrag(active, didDrop = false, dropTargetKey = null)
            return
        }

        if (active.sourceNode !== reboundSource) {
            clearSourceHiddenFlags(active.sourceNode)
            active.sourceNode = reboundSource
            applySourceHiddenFlags(active)
        } else {
            applySourceHiddenFlags(active)
        }

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

    override fun cancelActiveDrag() {
        val active = activeDrag ?: run {
            pendingDrag = null
            return
        }
        notifyDragCancel(active)
        finishDrag(active, didDrop = false, dropTargetKey = null)
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

        val sourceBounds = source.bounds
        val defaultOffsetX = (mouseX - sourceBounds.x).coerceIn(0, sourceBounds.width.coerceAtLeast(1))
        val defaultOffsetY = (mouseY - sourceBounds.y).coerceIn(0, sourceBounds.height.coerceAtLeast(1))
        val previewSpec = transfer.currentDragImageSpec()
        val previewOffsetX = previewSpec?.offsetX ?: defaultOffsetX
        val previewOffsetY = previewSpec?.offsetY ?: defaultOffsetY
        val hideSource = source.dragPreviewMode == DragPreviewMode.ORIGINAL || source.hideSourceWhileDragging

        val startedDrag = ActiveSession(
            sourceNode = source,
            sourceKey = source.key,
            sourceClass = source.javaClass,
            dataTransfer = transfer,
            previewMode = source.dragPreviewMode,
            sourceHiddenDuringDrag = hideSource,
            placeholderWidth = sourceBounds.width,
            placeholderHeight = sourceBounds.height,
            placeholderBuilder = source.dragPlaceholderBuilder,
            previewBuilder = source.dragPreviewBuilder,
            cursorX = mouseX,
            cursorY = mouseY,
            previewX = mouseX.toDouble(),
            previewY = mouseY.toDouble(),
            previewOffsetX = previewOffsetX,
            previewOffsetY = previewOffsetY
        )
        activeDrag = startedDrag
        pendingDrag = null
        applySourceHiddenFlags(startedDrag)
        updateDropTarget(root, startedDrag, dispatchOver = true)
        dispatchDragEvent(startedDrag)
        notifyDragStart(startedDrag)
    }

    private fun resolveDraggableSource(start: DOMNode?): DOMNode? {
        var current = start
        while (current != null) {
            if (current.draggable && !current.styleDisabled) return current
            current = current.parent
        }
        return null
    }

    private fun updateDropTarget(root: DOMNode, active: ActiveSession, dispatchOver: Boolean) {
        val resolvedTarget = resolveDropTarget(root, active, active.cursorX, active.cursorY)
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
        notifyDragOver(active)
    }

    private fun resolveDropTarget(root: DOMNode, active: ActiveSession, mouseX: Int, mouseY: Int): DOMNode? {
        val chain = collectHoverChain(root, mouseX, mouseY)
        val candidates = ArrayList<DOMNode>(chain.size)
        var excludedSource = false
        for (index in chain.indices) {
            val node = chain[index]
            if (!node.droppable || node.styleDisabled) continue
            if (node === active.sourceNode) {
                excludedSource = true
                continue
            }
            if (active.sourceKey != null &&
                node.key == active.sourceKey &&
                node.javaClass == active.sourceClass
            ) {
                excludedSource = true
                continue
            }
            candidates.add(node)
        }
        active.collisionCandidateCount = candidates.size
        active.sourceExcludedFromHitTest = excludedSource || active.sourceExcludedFromHitTest
        return selectDropTargetCandidate(candidates, active.dropTargetNode)
    }

    internal fun selectDropTargetCandidate(candidates: List<DOMNode>, previousTarget: DOMNode?): DOMNode? {
        val deepest = candidates.lastOrNull() ?: return null
        if (previousTarget != null && isSameNode(previousTarget, deepest)) {
            return deepest
        }
        return deepest
    }

    private fun dispatchDragEvent(active: ActiveSession) {
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

    private fun dispatchDragEnd(active: ActiveSession, didDrop: Boolean, dropTargetKey: Any?) {
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

    private fun finishDrag(active: ActiveSession, didDrop: Boolean, dropTargetKey: Any?) {
        clearSourceHiddenFlags(active.sourceNode)
        dispatchDragEnd(active, didDrop, dropTargetKey)
        notifyDragEnd(active, didDrop, dropTargetKey)
        pendingDrag = null
        activeDrag = null
    }

    private fun applySourceHiddenFlags(active: ActiveSession) {
        if (!active.sourceHiddenDuringDrag) return
        active.sourceNode.dragRenderHidden = true
        active.sourceNode.dragHitTestHidden = true
    }

    private fun clearSourceHiddenFlags(source: DOMNode?) {
        if (source == null) return
        source.dragRenderHidden = false
        source.dragHitTestHidden = false
    }

    private fun appendOriginalPreviewCommands(
        active: ActiveSession,
        ctx: UiMeasureContext,
        out: MutableList<RenderCommand>
    ) {
        val source = active.sourceNode
        val dx = (active.previewX - active.previewOffsetX - source.bounds.x).toInt()
        val dy = (active.previewY - active.previewOffsetY - source.bounds.y).toInt()
        val previewCommands = ArrayList<RenderCommand>(32)
        source.buildRenderCommands(ctx, previewCommands)
        previewCommands.forEach { cmd ->
            out.add(shiftCommand(cmd, dx, dy))
        }
    }

    private fun appendGhostPreviewCommands(
        root: DOMNode,
        active: ActiveSession,
        ctx: UiMeasureContext,
        out: MutableList<RenderCommand>
    ) {
        if (!active.dataTransfer.ghostVisible) return
        val anchorX = (active.previewX - active.previewOffsetX).toInt()
        val anchorY = (active.previewY - active.previewOffsetY).toInt()
        val sourceBounds = active.sourceNode.bounds

        val customBuilder = active.previewBuilder
        if (customBuilder != null) {
            val scope = DragPreviewScope(
                dataTransfer = active.dataTransfer,
                sourceBounds = sourceBounds,
                anchorX = anchorX,
                anchorY = anchorY
            )
            customBuilder.invoke(scope)
            out.addAll(scope.build())
            return
        }

        val previewSpec = active.dataTransfer.currentDragImageSpec()
        val previewNode = previewSpec?.let { spec ->
            findByKey(root, spec.nodeKey)
        }
        if (previewNode != null && previewSpec != null) {
            val dx = (active.previewX - previewSpec.offsetX - previewNode.bounds.x).toInt()
            val dy = (active.previewY - previewSpec.offsetY - previewNode.bounds.y).toInt()
            val previewCommands = ArrayList<RenderCommand>(32)
            previewNode.buildRenderCommands(ctx, previewCommands)
            previewCommands.forEach { cmd ->
                out.add(shiftCommand(cmd, dx, dy))
            }
            return
        }

        drawDefaultGhost(active, ctx, out)
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
        active: ActiveSession,
        ctx: UiMeasureContext,
        out: MutableList<RenderCommand>
    ) {
        val label = active.dataTransfer.getData("text/plain") ?: "drag"
        val x = (active.previewX - active.previewOffsetX).toInt()
        val y = (active.previewY - active.previewOffsetY).toInt()
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

            is RenderCommand.DrawColorField -> command.copy(
                x = command.x + dx,
                y = command.y + dy
            )

            is RenderCommand.DrawHueBar -> command.copy(
                x = command.x + dx,
                y = command.y + dy
            )

            is RenderCommand.DrawAlphaBar -> command.copy(
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

            is RenderCommand.CaptureScreenRegion -> command.copy(
                sourceX = command.sourceX + dx,
                sourceY = command.sourceY + dy
            )

            is RenderCommand.DrawCapturedScreenRegion -> command.copy(
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
            is RenderCommand.PushTransform -> command.copy(
                originX = command.originX + dx,
                originY = command.originY + dy
            )
            RenderCommand.PopTransform -> RenderCommand.PopTransform
            is RenderCommand.PushOpacity -> command
            RenderCommand.PopOpacity -> RenderCommand.PopOpacity
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

    private fun notifyDragStart(active: ActiveSession) {
        val snapshot = toApiActiveDrag(active)
        monitorListeners.values.forEach { listener ->
            listener.onDragStart(snapshot)
        }
    }

    private fun notifyDragMove(active: ActiveSession) {
        val snapshot = toApiActiveDrag(active)
        monitorListeners.values.forEach { listener ->
            listener.onDragMove(snapshot, active.dropTargetKey)
        }
    }

    private fun notifyDragOver(active: ActiveSession) {
        val snapshot = toApiActiveDrag(active)
        monitorListeners.values.forEach { listener ->
            listener.onDragOver(snapshot, active.dropTargetKey)
        }
    }

    private fun notifyDragEnd(active: ActiveSession, didDrop: Boolean, dropTargetKey: Any?) {
        val snapshot = toApiActiveDrag(active)
        val effect = if (didDrop) active.dataTransfer.dropEffect else DropEffect.NONE
        monitorListeners.values.forEach { listener ->
            listener.onDragEnd(snapshot, dropTargetKey, effect)
        }
    }

    private fun notifyDragCancel(active: ActiveSession) {
        val snapshot = toApiActiveDrag(active)
        monitorListeners.values.forEach { listener ->
            listener.onDragCancel(snapshot)
        }
    }

    private fun toApiActiveDrag(active: ActiveSession): ActiveDrag {
        val id = active.dataTransfer.getData(DND_DATA_ID_MIME)
        val type = active.dataTransfer.getData(DND_DATA_TYPE_MIME)
        return ActiveDrag(
            id = id,
            type = type,
            sourceKey = active.sourceKey,
            overKey = active.dropTargetKey,
            data = DndSystem.payload(id),
            cursorX = active.cursorX,
            cursorY = active.cursorY,
            transform = Transform(
                x = active.previewX - active.cursorX.toDouble(),
                y = active.previewY - active.cursorY.toDouble()
            ),
            dropEffect = active.dataTransfer.dropEffect,
            dataTransfer = active.dataTransfer
        )
    }
}

