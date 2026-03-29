package org.dreamfinity.dsgl.mc1710.demo.sections

import net.minecraft.init.Items
import net.minecraft.item.ItemStack
import org.dreamfinity.dsgl.core.UiScope
import org.dreamfinity.dsgl.core.dnd.*
import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.event.Event
import org.dreamfinity.dsgl.core.style.AlignItems
import org.dreamfinity.dsgl.core.style.Display
import org.dreamfinity.dsgl.core.style.FlexDirection
import org.dreamfinity.dsgl.core.style.JustifyItems
import org.dreamfinity.dsgl.core.hooks.useEffect
import org.dreamfinity.dsgl.core.hooks.useState
import org.dreamfinity.dsgl.mc1710.McItemStackRef
import org.dreamfinity.dsgl.mc1710.demo.support.DEMO_MUTED

private const val HIGHLIGHT_DELTA = 22

private data class DndDemoItem(
    val id: String,
    val label: String,
    val stack: McItemStackRef
)

private enum class DndLaneIndicator {
    NONE,
    BEFORE,
    AFTER
}

private data class DndSectionState(
    val items: List<DndDemoItem> = defaultDndItems(),
    val hoverZone: String = "none",
    val lastAction: String = "none",
    val transferTypes: String = "-",
    val dropEffect: String = "none",
    val activeItem: String = "none",
    val dragTickCount: Int = 0,
    val ghostEnabled: Boolean = true,
    val hideSourceWhileDragging: Boolean = false,
    val smoothFactor: Double = 26.0,
    val reorderHoverTargetId: String? = null,
    val reorderHoverInsertAfter: Boolean = false,
    val reorderHoverLaneAppend: Boolean = false,
    val debugOverId: String = "none",
    val debugOverContainerId: String = "none",
    val debugCandidatesCount: Int = 0,
    val debugInsertPosition: String = "none",
    val debugExcludesActiveCard: Boolean = true,
    val boxes: Map<String, List<DndDemoItem>> = linkedMapOf(
        "box-a" to emptyList(),
        "box-b" to emptyList(),
        "box-c" to emptyList()
    )
)

private data class LaneHoverIntent(
    val targetId: String?,
    val insertAfter: Boolean,
    val append: Boolean
)

fun UiScope.dragNDropSection(
    onInfo: (String) -> Unit,
    onClearLogs: () -> Unit,
    onLogHook: (String, Event, String?) -> Unit
) {
    var state by useState(DndSectionState())

    fun logHook(name: String, event: Event, note: String? = null) = onLogHook(name, event, note)

    fun clearLaneReorderHover() {
        state = state.copy(
            reorderHoverTargetId = null,
            reorderHoverInsertAfter = false,
            reorderHoverLaneAppend = false
        )
    }

    fun resetDndItems(source: String) {
        state = DndSectionState(
            items = defaultDndItems(),
            smoothFactor = state.smoothFactor
        )
        onInfo("DnD demo list reset by $source")
    }

    fun updateDndSmoothing(delta: Double) {
        val next = (state.smoothFactor + delta).coerceIn(0.0, 96.0)
        state = state.copy(smoothFactor = next)
        DndSystem.setSmoothingFactor(next)
        onInfo("DnD smoothing k=${"%.1f".format(next)}")
    }

    fun extractCard(cardId: String): Triple<DndDemoItem, List<DndDemoItem>, LinkedHashMap<String, MutableList<DndDemoItem>>>? {
        val lane = state.items.toMutableList()
        val boxes = linkedMapOf<String, MutableList<DndDemoItem>>().apply {
            state.boxes.forEach { (key, list) ->
                this[key] = list.toMutableList()
            }
        }
        val laneIndex = lane.indexOfFirst { it.id == cardId }
        if (laneIndex >= 0) {
            val card = lane.removeAt(laneIndex)
            return Triple(card, lane, boxes)
        }
        boxes.forEach { (_, list) ->
            val boxIndex = list.indexOfFirst { it.id == cardId }
            if (boxIndex >= 0) {
                val card = list.removeAt(boxIndex)
                return Triple(card, lane, boxes)
            }
        }
        return null
    }

    fun wouldLaneReorderChange(
        draggedId: String,
        targetId: String?,
        insertAfter: Boolean?,
        dropOnLane: Boolean
    ): Boolean {
        val laneIds = state.items.map { it.id }.toMutableList()
        val sourceIndex = laneIds.indexOf(draggedId)
        if (sourceIndex < 0) return true
        val removed = laneIds.removeAt(sourceIndex)
        val targetIndex = targetId?.let { laneIds.indexOf(it) } ?: -1
        val destinationIndex = when {
            dropOnLane || targetId == null -> laneIds.size
            targetIndex < 0 -> laneIds.size
            insertAfter == true -> (targetIndex + 1).coerceAtMost(laneIds.size)
            else -> targetIndex.coerceIn(0, laneIds.size)
        }
        laneIds.add(destinationIndex, removed)
        return laneIds != state.items.map { it.id }
    }

    fun commitLaneReorderDrop(
        draggedId: String,
        targetId: String?,
        insertAfter: Boolean?,
        dropOnLane: Boolean
    ): Boolean {
        val extracted = extractCard(draggedId) ?: return false
        val card = extracted.first
        val lane = extracted.second.toMutableList()
        val boxes = extracted.third
        val targetIndex = targetId?.let { id -> lane.indexOfFirst { it.id == id } } ?: -1
        val insertIndex = when {
            dropOnLane || targetId == null || targetIndex < 0 -> lane.size
            insertAfter == true -> (targetIndex + 1).coerceAtMost(lane.size)
            else -> targetIndex.coerceIn(0, lane.size)
        }
        lane.add(insertIndex, card)
        if (!dropOnLane && !wouldLaneReorderChange(draggedId, targetId, insertAfter, dropOnLane)) {
            return false
        }
        state = state.copy(
            items = lane,
            boxes = boxes.mapValuesTo(linkedMapOf()) { (_, value) -> value.toList() }
        )
        return true
    }

    fun moveCardToBox(cardId: String, boxId: String): Boolean {
        val extracted = extractCard(cardId) ?: return false
        val lane = extracted.second.toMutableList()
        val boxes = extracted.third
        val target = boxes.getOrPut(boxId) { mutableListOf() }
        target.add(extracted.first)
        state = state.copy(
            items = lane,
            boxes = boxes.mapValuesTo(linkedMapOf()) { (_, value) -> value.toList() }
        )
        return true
    }

    fun handleDndStart(item: DndDemoItem, event: DragStartEvent) {
        val sourceBounds = event.target?.bounds
        val offsetX = if (sourceBounds != null) {
            (event.mouseX - sourceBounds.x).coerceIn(0, sourceBounds.width.coerceAtLeast(1))
        } else {
            0
        }
        val offsetY = if (sourceBounds != null) {
            (event.mouseY - sourceBounds.y).coerceIn(0, sourceBounds.height.coerceAtLeast(1))
        } else {
            0
        }
        event.dataTransfer.setData("text/plain", item.label)
        event.dataTransfer.setData("application/x-dsgl-item-id", item.id)
        event.dataTransfer.effectAllowed = EffectAllowed.COPY_MOVE
        event.dataTransfer.dropEffect = DropEffect.MOVE
        if (!state.ghostEnabled) {
            event.dataTransfer.hideGhost()
        }
        val sourceKey = event.target?.key?.toString()
        if (!sourceKey.isNullOrBlank()) {
            event.dataTransfer.setDragImage(sourceKey, offsetX, offsetY)
        }
        clearLaneReorderHover()
        state = state.copy(
            activeItem = item.label,
            transferTypes = event.dataTransfer.types.sorted().joinToString(",").ifBlank { "-" },
            dropEffect = event.dataTransfer.dropEffect.name.lowercase(),
            lastAction = "dragstart ${item.label}",
            debugOverId = "none",
            debugOverContainerId = "none",
            debugCandidatesCount = 0,
            debugInsertPosition = "none",
            debugExcludesActiveCard = true
        )
        val mode = event.target?.dragPreviewMode?.name?.lowercase() ?: "unknown"
        logHook("dnd.onDragStart", event, "item=${item.id} mode=$mode")
    }

    fun handleDndDrag(event: DragEvent) {
        val tick = state.dragTickCount + 1
        state = state.copy(
            dragTickCount = tick,
            transferTypes = event.dataTransfer.types.sorted().joinToString(",").ifBlank { "-" },
            dropEffect = event.dataTransfer.dropEffect.name.lowercase()
        )
        if (tick % 5 == 0) {
            logHook("dnd.onDrag", event, "tick=$tick")
        }
    }

    fun laneCards(laneNode: DOMNode?, excludedCardId: String?): List<Pair<String, DOMNode>> {
        if (laneNode == null) return emptyList()
        return laneNode.children
            .mapNotNull { child ->
                val id = extractCardIdFromDragKey(child.key) ?: return@mapNotNull null
                if (excludedCardId != null && id == excludedCardId) return@mapNotNull null
                id to child
            }
            .sortedBy { (_, node) -> node.bounds.y }
    }

    fun resolveLaneIntentFromMouse(
        laneNode: DOMNode?,
        mouseY: Int,
        excludedCardId: String?
    ): LaneHoverIntent {
        val cards = laneCards(laneNode, excludedCardId)
        if (cards.isEmpty()) return LaneHoverIntent(targetId = null, insertAfter = false, append = true)
        val lastCard = cards.last().second
        if (mouseY >= lastCard.bounds.y + lastCard.bounds.height + 4) {
            return LaneHoverIntent(targetId = null, insertAfter = false, append = true)
        }
        val target = cards.minByOrNull { (_, node) ->
            kotlin.math.abs(mouseY - (node.bounds.y + (node.bounds.height / 2)))
        } ?: return LaneHoverIntent(targetId = null, insertAfter = false, append = true)
        val splitY = target.second.bounds.y + (target.second.bounds.height / 2)
        return LaneHoverIntent(targetId = target.first, insertAfter = mouseY >= splitY, append = false)
    }

    fun laneCandidateCount(laneNode: DOMNode?, excludedCardId: String?): Int {
        return laneCards(laneNode, excludedCardId).size
    }

    fun handleDndLaneOver(event: DragOverEvent) {
        val laneNode = event.target
        val draggedId = event.dataTransfer.getData("application/x-dsgl-item-id")
        val intent = resolveLaneIntentFromMouse(laneNode, event.mouseY, draggedId)
        state = state.copy(
            reorderHoverLaneAppend = intent.append,
            reorderHoverTargetId = intent.targetId,
            reorderHoverInsertAfter = intent.insertAfter,
            debugOverContainerId = "lane",
            debugOverId = intent.targetId ?: "append",
            debugCandidatesCount = laneCandidateCount(laneNode, draggedId),
            debugInsertPosition = when {
                intent.append -> "append"
                intent.insertAfter -> "after"
                else -> "before"
            },
            debugExcludesActiveCard = true,
            dropEffect = event.dataTransfer.dropEffect.name.lowercase()
        )
        event.acceptDrop(DropEffect.MOVE)
    }

    fun handleDndLaneDrop(event: DropEvent) {
        val draggedId = event.dataTransfer.getData("application/x-dsgl-item-id") ?: return
        val laneNode = event.target
        val intent = resolveLaneIntentFromMouse(laneNode, event.mouseY, draggedId)
        val moved = commitLaneReorderDrop(
            draggedId = draggedId,
            targetId = intent.targetId,
            insertAfter = if (intent.append) null else intent.insertAfter,
            dropOnLane = intent.append
        )
        if (moved) {
            onInfo(
                "Lane drop: drag=$draggedId target=${intent.targetId ?: "lane"} pos=${
                    if (intent.append) "append" else if (intent.insertAfter) "after" else "before"
                }"
            )
        }
        clearLaneReorderHover()
        state = state.copy(
            dropEffect = event.dataTransfer.dropEffect.name.lowercase(),
            debugOverId = "none",
            debugOverContainerId = "none",
            debugInsertPosition = "none"
        )
        logHook(
            "dnd.reorder.lane.onDrop",
            event,
            "dragged=$draggedId target=${intent.targetId ?: "lane"} append=${intent.append}"
        )
    }

    fun handleDndCardReorderOver(targetCardId: String, insertAfter: Boolean, event: DragOverEvent) {
        val draggedId = event.dataTransfer.getData("application/x-dsgl-item-id")
        if (draggedId != null && draggedId == targetCardId) return
        val laneNode = event.target?.parent
        state = state.copy(
            reorderHoverTargetId = targetCardId,
            reorderHoverInsertAfter = insertAfter,
            reorderHoverLaneAppend = false,
            debugOverContainerId = "lane",
            debugOverId = targetCardId,
            debugCandidatesCount = laneCandidateCount(laneNode, draggedId),
            debugInsertPosition = if (insertAfter) "after" else "before",
            debugExcludesActiveCard = true,
            dropEffect = event.dataTransfer.dropEffect.name.lowercase()
        )
        event.acceptDrop(DropEffect.MOVE)
        event.cancelled = true
    }

    fun handleDndCardReorderDrop(targetCardId: String, insertAfter: Boolean, event: DropEvent) {
        val draggedId = event.dataTransfer.getData("application/x-dsgl-item-id") ?: return
        if (draggedId == targetCardId) return
        val moved = commitLaneReorderDrop(draggedId, targetCardId, insertAfter, dropOnLane = false)
        if (moved) {
            onInfo("Card drop: drag=$draggedId target=$targetCardId pos=${if (insertAfter) "after" else "before"}")
        }
        clearLaneReorderHover()
        state = state.copy(
            debugOverId = "none",
            debugOverContainerId = "none",
            debugInsertPosition = "none",
            dropEffect = event.dataTransfer.dropEffect.name.lowercase()
        )
        event.cancelled = true
        logHook(
            "dnd.reorder.card.onDrop",
            event,
            "dragged=$draggedId target=$targetCardId pos=${if (insertAfter) "after" else "before"}"
        )
    }

    fun handleDndBoxOver(boxId: String, event: DragOverEvent) {
        clearLaneReorderHover()
        state = state.copy(
            hoverZone = boxId,
            debugOverId = boxId,
            debugOverContainerId = "box:$boxId",
            debugInsertPosition = "drop",
            debugCandidatesCount = 1,
            debugExcludesActiveCard = true,
            dropEffect = event.dataTransfer.dropEffect.name.lowercase()
        )
        event.acceptDrop(DropEffect.MOVE)
    }

    fun handleDndBoxDrop(boxId: String, event: DropEvent) {
        val draggedId = event.dataTransfer.getData("application/x-dsgl-item-id") ?: return
        val moved = moveCardToBox(draggedId, boxId)
        if (moved) {
            state = state.copy(hoverZone = boxId, lastAction = "moved $draggedId to $boxId")
        }
        state = state.copy(dropEffect = event.dataTransfer.dropEffect.name.lowercase())
        logHook("dnd.$boxId.onDrop", event, "dragged=$draggedId")
    }

    fun handleDndEnd(event: DragEndEvent) {
        clearLaneReorderHover()
        state = state.copy(
            hoverZone = "none",
            dropEffect = event.finalDropEffect.name.lowercase(),
            lastAction = "dragend drop=${event.didDrop} effect=${event.finalDropEffect.name.lowercase()}",
            activeItem = "none",
            debugOverId = "none",
            debugOverContainerId = "none",
            debugCandidatesCount = 0,
            debugInsertPosition = "none"
        )
        logHook("dnd.onDragEnd", event, "drop=${event.didDrop}")
    }

    fun laneIndicatorForCard(cardId: String, sourceKey: Any?): DndLaneIndicator {
        if (state.reorderHoverLaneAppend) return DndLaneIndicator.NONE
        if (state.reorderHoverTargetId != cardId) return DndLaneIndicator.NONE
        val draggedId = extractCardIdFromDragKey(sourceKey) ?: return DndLaneIndicator.NONE
        val wouldChange = wouldLaneReorderChange(
            draggedId = draggedId,
            targetId = cardId,
            insertAfter = state.reorderHoverInsertAfter,
            dropOnLane = false
        )
        if (!wouldChange) return DndLaneIndicator.NONE
        return if (state.reorderHoverInsertAfter) DndLaneIndicator.AFTER else DndLaneIndicator.BEFORE
    }

    fun shouldShowLaneAppendGap(sourceKey: Any?): Boolean {
        if (!state.reorderHoverLaneAppend) return false
        val draggedId = extractCardIdFromDragKey(sourceKey) ?: return true
        if (!wouldLaneReorderChange(draggedId, targetId = null, insertAfter = null, dropOnLane = true)) return false
        val index = state.items.indexOfFirst { it.id == draggedId }
        if (index < 0) return true
        return index != state.items.lastIndex
    }

    useEffect(state.smoothFactor) {
        DndSystem.setSmoothingFactor(state.smoothFactor)
    }

    useDragDropMonitor(
        DragDropMonitorCallbacks(
            onDragMove = { active, over ->
                state = state.copy(
                    activeItem = active.id ?: active.sourceKey?.toString() ?: "none",
                    debugOverContainerId = if (over == null) "none" else "target",
                    debugOverId = over?.toString() ?: "none"
                )
            },
            onDragOver = { active, over ->
                state = state.copy(
                    dropEffect = active.dropEffect.name.lowercase(),
                    debugOverId = over?.toString() ?: "none"
                )
            },
            onDragEnd = { _, _, effect ->
                state = state.copy(
                    dropEffect = effect.name.lowercase(),
                    debugOverId = "none",
                    debugOverContainerId = "none"
                )
            },
            onDragCancel = {
                state = state.copy(
                    debugOverId = "none",
                    debugOverContainerId = "none"
                )
            }
        )
    )

    val monitor = DndSystem.monitor()

    div({
        key = "section.dragDrop"
        style = {
            gap = 4.px
            display = Display.Flex
            flexDirection = FlexDirection.Column
        }
    }) {
        text("Drag preview modes: ORIGINAL (detached source) and GHOST (overlay preview).")
        text(
            "active=${state.activeItem} mode=${monitor.mode?.name ?: "none"} effect=${state.dropEffect} hover=${state.hoverZone}",
            { style = { color = DEMO_MUTED } }
        )
        text("types=${state.transferTypes} dragTicks=${state.dragTickCount} action=${state.lastAction}", {
            style = { color = DEMO_MUTED }
        })
        text(
            "debug active=${monitor.sourceKey ?: "none"} over=${state.debugOverId} container=${state.debugOverContainerId}",
            { style = { color = DEMO_MUTED } }
        )
        text(
            "candidates=${state.debugCandidatesCount} insert=${state.debugInsertPosition} excludeActive=${state.debugExcludesActiveCard}",
            { style = { color = DEMO_MUTED } }
        )
        div({
            style = {
                gap = 3.px
                display = Display.Flex
                flexDirection = FlexDirection.Row
            }
        }) {
            button(if (state.ghostEnabled) "Ghost ON" else "Ghost OFF", {
                onMouseClick = {
                    val next = !state.ghostEnabled
                    state = state.copy(ghostEnabled = next)
                    onInfo("DnD ghost=$next")
                }
            })
            button(if (state.hideSourceWhileDragging) "Hide ON" else "Hide OFF", {
                onMouseClick = {
                    val next = !state.hideSourceWhileDragging
                    state = state.copy(hideSourceWhileDragging = next)
                    onInfo("DnD hideSource=$next")
                }
            })
            button("Reset state", { onMouseClick = { resetDndItems("toolbar") } })
            button("Reset logs", { onMouseClick = { onClearLogs() } })
        }

        div({
            style = {
                gap = 3.px
                display = Display.Flex
                flexDirection = FlexDirection.Row
            }
        }) {
            button("k-", { onMouseClick = { updateDndSmoothing(-4.0) } })
            button("k+", { onMouseClick = { updateDndSmoothing(4.0) } })
            text("smoothing k=${"%.1f".format(state.smoothFactor)}", { style = { color = DEMO_MUTED } })
        }

        div({
            key = "dnd.main"
            style = {
                display = Display.Flex
                flexDirection = FlexDirection.Row
                gap = 6.px
                alignItems = AlignItems.Stretch
            }
        }) {
            originalModeReorder(
                state = state,
                sourceKey = monitor.sourceKey,
                onStart = ::handleDndStart,
                onDrag = ::handleDndDrag,
                onEnd = ::handleDndEnd,
                onLaneOver = ::handleDndLaneOver,
                onLaneLeave = ::clearLaneReorderHover,
                onLaneDrop = ::handleDndLaneDrop,
                onCardOver = ::handleDndCardReorderOver,
                onCardDrop = ::handleDndCardReorderDrop,
                laneIndicatorForCard = ::laneIndicatorForCard,
                shouldShowLaneAppendGap = ::shouldShowLaneAppendGap
            )
            renderGhostModeBoxes(
                state = state,
                onStart = ::handleDndStart,
                onDrag = ::handleDndDrag,
                onEnd = ::handleDndEnd,
                onBoxOver = ::handleDndBoxOver,
                onBoxDrop = ::handleDndBoxDrop,
                onHoverZone = { boxId -> state = state.copy(hoverZone = boxId) },
                onLogHook = ::logHook,
                onReset = { resetDndItems("button") }
            )
        }
    }
}

private fun UiScope.originalModeReorder(
    state: DndSectionState,
    sourceKey: Any?,
    onStart: (DndDemoItem, DragStartEvent) -> Unit,
    onDrag: (DragEvent) -> Unit,
    onEnd: (DragEndEvent) -> Unit,
    onLaneOver: (DragOverEvent) -> Unit,
    onLaneLeave: () -> Unit,
    onLaneDrop: (DropEvent) -> Unit,
    onCardOver: (String, Boolean, DragOverEvent) -> Unit,
    onCardDrop: (String, Boolean, DropEvent) -> Unit,
    laneIndicatorForCard: (String, Any?) -> DndLaneIndicator,
    shouldShowLaneAppendGap: (Any?) -> Boolean
) {
    val draggedId = extractCardIdFromDragKey(sourceKey)

    div({
        key = "dnd.original.panel"
        style = {
            minWidth = 200.px
            gap = 3.px
            padding = 3.px
            backgroundColor = 0xFF2D333B.toInt()
            border(1.px, 0xFF6B7785.toInt())
            display = Display.Flex
            flexDirection = FlexDirection.Column
            flexGrow = 1.0f
        }
    }) {
        text("ORIGINAL mode: reorder list")
        text("Detached source follows cursor; slot uses placeholder.", { style = { color = DEMO_MUTED } })
        val laneDroppable = useDroppable(
            id = "lane",
            nodeKey = "dnd.lane.column",
            accepts = { active -> !active.id.isNullOrBlank() },
            onDragOver = { event, _ -> onLaneOver(event) },
            onDragLeave = { _, _ -> onLaneLeave() },
            onDrop = { event, _ -> onLaneDrop(event) }
        )
        div({
            style = {
                display = Display.Flex
                flexDirection = FlexDirection.Column
                justifyItems = JustifyItems.Center
                alignItems = AlignItems.Center
                gap = 4.px
            }
        }) {
            div({
                key = "dnd.lane.column"
                style = {
                    gap = 6.px
                    backgroundColor = if (state.reorderHoverLaneAppend) 0x2A9EC4E3 else 0x00000000
                    border(1.px, if (state.reorderHoverLaneAppend) 0xFF9EC4E3.toInt() else 0x44405058)
                    display = Display.Flex
                    flexDirection = FlexDirection.Column
                }
                applyDroppable(laneDroppable)
            }) {
                if (state.items.isEmpty()) {
                    text("No items available - drop here", { style = { color = DEMO_MUTED } })
                }

                state.items.forEach { item ->
                    val indicator = laneIndicatorForCard(item.id, sourceKey)
                    val isDraggedItem = draggedId != null && draggedId == item.id
                    val sortable = useSortable(
                        id = item.id,
                        nodeKey = "dnd.lane.card.${item.id}",
                        containerId = "lane",
                        items = state.items.map { it.id },
                        data = item,
                        previewMode = DragPreviewMode.ORIGINAL,
                        hideSourceWhileDragging = true
                    )

                    cardWithItem(
                        item = item,
                        cardKey = "dnd.lane.card.${item.id}",
                        sortable = sortable,
                        draggableEnabled = !isDraggedItem,
                        highlighted = indicator != DndLaneIndicator.NONE,
                        insertionIndicator = indicator,
                        extraListeners = DndListeners(
                            onDragStart = { event -> onStart(item, event) },
                            onDrag = { event -> onDrag(event) },
                            onDragEnd = { event -> onEnd(event) },
                            onDragOver = if (isDraggedItem) null else { event ->
                                onCardOver(item.id, resolveInsertAfter(event), event)
                            },
                            onDrop = if (isDraggedItem) null else { event ->
                                onCardDrop(item.id, resolveInsertAfter(event), event)
                            }
                        )
                    )
                }

                if (shouldShowLaneAppendGap(sourceKey)) {
                    div({
                        key = "dnd.lane.append.gap"
                        style = {
                            backgroundColor = 0x2A9EC4E3
                            border(1.px, 0xFF9EC4E3.toInt())
                            borderRadius(3.px)
                            display = Display.Flex
                            flexDirection = FlexDirection.Column
                            alignItems = AlignItems.Center
                        }
                    }) {
                        text("APPEND", { style = { color = 0xFFD3E8FB.toInt() } })
                    }
                }
            }
        }
    }
}

private fun UiScope.renderGhostModeBoxes(
    state: DndSectionState,
    onStart: (DndDemoItem, DragStartEvent) -> Unit,
    onDrag: (DragEvent) -> Unit,
    onEnd: (DragEndEvent) -> Unit,
    onBoxOver: (String, DragOverEvent) -> Unit,
    onBoxDrop: (String, DropEvent) -> Unit,
    onHoverZone: (String) -> Unit,
    onLogHook: (String, Event, String?) -> Unit,
    onReset: () -> Unit
) {
    div({
        key = "dnd.ghost.panel"
        style = {
            minWidth = 200.px
            flexGrow = 1.0f
            gap = 4.px
            padding = 3.px
            backgroundColor = 0xFF2D333B.toInt()
            border(1.px, 0xFF6B7785.toInt())
            display = Display.Flex
            flexDirection = FlexDirection.Column
        }
    }) {
        text("Buckets: drop card to move it into a box")
        text("Ghost toggle applies to drag previews in this panel.", { style = { color = DEMO_MUTED } })

        dropBox(
            state = state,
            boxKey = "dnd.box.a",
            title = "Box A",
            boxId = "box-a",
            color = 0xFF314B3A.toInt(),
            cards = state.boxes["box-a"].orEmpty(),
            onStart = onStart,
            onDrag = onDrag,
            onEnd = onEnd,
            onBoxOver = onBoxOver,
            onBoxDrop = onBoxDrop,
            onHoverZone = onHoverZone,
            onLogHook = onLogHook
        )
        dropBox(
            state = state,
            boxKey = "dnd.box.b",
            title = "Box B",
            boxId = "box-b",
            color = 0xFF5A3434.toInt(),
            cards = state.boxes["box-b"].orEmpty(),
            onStart = onStart,
            onDrag = onDrag,
            onEnd = onEnd,
            onBoxOver = onBoxOver,
            onBoxDrop = onBoxDrop,
            onHoverZone = onHoverZone,
            onLogHook = onLogHook
        )
        dropBox(
            state = state,
            boxKey = "dnd.box.c",
            title = "Box C",
            boxId = "box-c",
            color = 0xFF354A5A.toInt(),
            cards = state.boxes["box-c"].orEmpty(),
            onStart = onStart,
            onDrag = onDrag,
            onEnd = onEnd,
            onBoxOver = onBoxOver,
            onBoxDrop = onBoxDrop,
            onHoverZone = onHoverZone,
            onLogHook = onLogHook
        )

        button("Reset DnD", { onMouseClick = { onReset() } })
    }
}

private fun UiScope.cardWithItem(
    item: DndDemoItem,
    cardKey: Any,
    draggable: Draggable? = null,
    sortable: Sortable? = null,
    draggableEnabled: Boolean = true,
    highlighted: Boolean,
    insertionIndicator: DndLaneIndicator = DndLaneIndicator.NONE,
    extraListeners: DndListeners = DndListeners()
) {
    val draggingThis = sortable?.isDragging ?: draggable?.isDragging ?: DndSystem.monitor(cardKey).isDragging
    val accent = itemAccentColor(item.id)
    val base = itemBaseColor(item.id)
    val insertionGap = 24
    div({
        key = cardKey
        dragPlaceholder = {
            fillColor = 0x44333F4D
            borderColor = accent
            borderWidth = 1
        }
        style = {
            display = Display.Flex
            flexDirection = FlexDirection.Column
            alignItems = AlignItems.Center
            padding = 2.px
            gap = 1.px
            backgroundColor = when {
                draggingThis -> lighten(base, HIGHLIGHT_DELTA + 8)
                highlighted -> lighten(base, HIGHLIGHT_DELTA)
                else -> base
            }
            border(1.px, accent)
            borderRadius(3.px)
            when (insertionIndicator) {
                DndLaneIndicator.BEFORE -> margin(insertionGap.px, 0.px, 0.px, 0.px)
                DndLaneIndicator.AFTER -> margin(0.px, 0.px, insertionGap.px, 0.px)
                DndLaneIndicator.NONE -> Unit
            }
        }
        when {
            sortable != null -> applySortable(sortable)
            draggable != null -> applyDraggable(draggable)
            else -> this.draggable = draggableEnabled
        }
        if (!draggableEnabled) {
            this.draggable = false
        }
        applyDndListeners(extraListeners)
    }) {
        div({
            key = "$cardKey.accent"
            style = {
                backgroundColor = lighten(accent, 12)
                borderRadius(2.px)
            }
        })
        itemStack(item.stack, {
            key = "dnd.stack.${item.id}"
            style = {
                border(1.px, 0x553A4452)
                backgroundColor = 0x2219222B
            }
        })
        text(item.label, {
            style = { color = if (draggingThis) 0xFFFFFFFF.toInt() else 0xFFEAF2FD.toInt() }
        })
    }
}

private fun UiScope.dropBox(
    state: DndSectionState,
    boxKey: Any,
    title: String,
    boxId: String,
    color: Int,
    cards: List<DndDemoItem>,
    onStart: (DndDemoItem, DragStartEvent) -> Unit,
    onDrag: (DragEvent) -> Unit,
    onEnd: (DragEndEvent) -> Unit,
    onBoxOver: (String, DragOverEvent) -> Unit,
    onBoxDrop: (String, DropEvent) -> Unit,
    onHoverZone: (String) -> Unit,
    onLogHook: (String, Event, String?) -> Unit
) {
    val highlighted = state.hoverZone == boxId
    val dropDescriptor = useDroppable(
        id = boxId,
        nodeKey = boxKey,
        accepts = { active -> !active.id.isNullOrBlank() },
        onDragEnter = { event, _ ->
            onHoverZone(boxId)
            onLogHook("dnd.$boxId.onDragEnter", event, null)
        },
        onDragOver = { event, _ -> onBoxOver(boxId, event) },
        onDragLeave = { event, _ ->
            if (highlighted) {
                onHoverZone("none")
            }
            onLogHook("dnd.$boxId.onDragLeave", event, null)
        },
        onDrop = { event, _ -> onBoxDrop(boxId, event) }
    )
    div({
        key = boxKey
        style = {
            padding = 4.px
            gap = 2.px
            backgroundColor = if (highlighted) lighten(color, HIGHLIGHT_DELTA) else color
            border(1.px, 0xFF8A94A2.toInt())
            borderRadius(3.px)
        }
        applyDroppable(dropDescriptor)
    }) {
        text("$title (${cards.size})")
        if (cards.isEmpty()) {
            text("Drop here", { style = { this.color = DEMO_MUTED } })
        } else {
            div({
                style = {
                    key = "$boxKey.cards"
                    gap = 2.px
                    display = Display.Flex
                    flexDirection = FlexDirection.Row
                }
            }) {
                cards.take(5).forEach { item ->
                    val draggable = useDraggable(
                        id = item.id,
                        nodeKey = "dnd.box.$boxId.card.${item.id}",
                        type = "card",
                        data = item,
                        previewMode = DragPreviewMode.GHOST,
                        hideSourceWhileDragging = state.hideSourceWhileDragging,
                        onDragStart = { event -> onStart(item, event) },
                        onDrag = { event -> onDrag(event) },
                        onDragEnd = { event -> onEnd(event) }
                    )
                    cardWithItem(
                        item = item,
                        cardKey = "dnd.box.$boxId.card.${item.id}",
                        draggable = draggable,
                        highlighted = false
                    )
                }
                if (cards.size > 5) {
                    text("+${cards.size - 5}", { style = { this.color = DEMO_MUTED } })
                }
            }
        }
    }
}

private fun extractCardIdFromDragKey(sourceKey: Any?): String? {
    val key = sourceKey as? String ?: return null
    val marker = ".card."
    val markerIndex = key.indexOf(marker)
    if (markerIndex < 0) return null
    return key.substring(markerIndex + marker.length).takeIf { it.isNotBlank() }
}

private fun itemBaseColor(itemId: String): Int {
    return when (itemId) {
        "apple" -> 0xFF355841.toInt()
        "bread" -> 0xFF68543A.toInt()
        "carrot" -> 0xFF6A4A2B.toInt()
        "diamond" -> 0xFF315A70.toInt()
        else -> 0xFF3C4B5A.toInt()
    }
}

private fun itemAccentColor(itemId: String): Int {
    return when (itemId) {
        "apple" -> 0xFF7BCEA0.toInt()
        "bread" -> 0xFFE7BE79.toInt()
        "carrot" -> 0xFFFFB46E.toInt()
        "diamond" -> 0xFF8ED2FF.toInt()
        else -> 0xFF9BB0C4.toInt()
    }
}

private fun lighten(color: Int, delta: Int): Int {
    val a = (color ushr 24) and 0xFF
    val r = ((color ushr 16) and 0xFF) + delta
    val g = ((color ushr 8) and 0xFF) + delta
    val b = (color and 0xFF) + delta
    return (a shl 24) or ((r.coerceAtMost(255)) shl 16) or ((g.coerceAtMost(255)) shl 8) or b.coerceAtMost(255)
}

private fun resolveInsertAfter(event: DragOverEvent): Boolean {
    val target = event.target ?: return false
    val splitY = target.bounds.y + (target.bounds.height / 2)
    return event.mouseY >= splitY
}

private fun resolveInsertAfter(event: DropEvent): Boolean {
    val target = event.target ?: return false
    val splitY = target.bounds.y + (target.bounds.height / 2)
    return event.mouseY >= splitY
}

private fun defaultDndItems(): List<DndDemoItem> = listOf(
    DndDemoItem(
        id = "apple",
        label = "Apple",
        stack = McItemStackRef(ItemStack(Items.apple, 1, 0))
    ),
    DndDemoItem(
        id = "bread",
        label = "Bread",
        stack = McItemStackRef(ItemStack(Items.bread, 1, 0))
    ),
    DndDemoItem(
        id = "carrot",
        label = "Carrot",
        stack = McItemStackRef(ItemStack(Items.carrot, 1, 0))
    ),
    DndDemoItem(
        id = "diamond",
        label = "Diamond",
        stack = McItemStackRef(ItemStack(Items.diamond, 1, 0))
    )
)
