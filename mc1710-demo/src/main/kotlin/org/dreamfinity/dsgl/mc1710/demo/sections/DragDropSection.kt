package org.dreamfinity.dsgl.mc1710.demo.sections

import org.dreamfinity.dsgl.core.UiScope
import org.dreamfinity.dsgl.core.dnd.*
import org.dreamfinity.dsgl.core.style.Display
import org.dreamfinity.dsgl.core.style.FlexDirection
import org.dreamfinity.dsgl.mc1710.demo.ShowcaseWindow
import org.dreamfinity.dsgl.mc1710.demo.ShowcaseWindow.DndDemoItem
import org.dreamfinity.dsgl.mc1710.demo.support.DEMO_MUTED

private const val LEFT_PANEL_PERCENT = 58
private const val MIN_LEFT_WIDTH = 72
private const val MIN_RIGHT_WIDTH = 64
private const val PANELS_GAP = 6
private const val CARD_SIZE = 40
private const val BOX_HEIGHT = 52
private const val BOX_CARD_SIZE = 28
private const val HIGHLIGHT_DELTA = 22

fun UiScope.dragNDropSection(window: ShowcaseWindow, contentWidth: Int, contentHeight: Int) {
    val splitAllowed = contentWidth >= (MIN_LEFT_WIDTH + MIN_RIGHT_WIDTH + PANELS_GAP)
    val availableWidth = (contentWidth - PANELS_GAP).coerceAtLeast(0)
    val leftIdeal = (availableWidth * LEFT_PANEL_PERCENT) / 100
    val leftWidth = if (splitAllowed) {
        leftIdeal.coerceIn(MIN_LEFT_WIDTH, availableWidth - MIN_RIGHT_WIDTH)
    } else {
        contentWidth.coerceAtLeast(0)
    }
    val rightWidth = if (splitAllowed) {
        availableWidth - leftWidth
    } else {
        contentWidth.coerceAtLeast(0)
    }
    val monitor = DndSystem.monitor()

    div({
        key = "section.dragDrop"
        style = {
            width = contentWidth.px
            height = contentHeight.px
            gap = 4.px
            display = Display.Flex
            flexDirection = FlexDirection.Column
        }
    }) {
        text("Drag preview modes: ORIGINAL (detached source) and GHOST (overlay preview).")
        text({
            val mode = monitor.mode?.name ?: "none"
            value =
                "active=${window.dndActiveItem} mode=$mode effect=${window.dndDropEffect} hover=${window.dndHoverZone}"
            style = { color = DEMO_MUTED }
        })
        text("types=${window.dndTransferTypes} dragTicks=${window.dndDragTickCount} action=${window.dndLastAction}", {
            style = { color = DEMO_MUTED }
        })
        text(
            "debug active=${monitor.sourceKey ?: "none"} over=${window.dndDebugOverId} container=${window.dndDebugOverContainerId}",
            { style = { color = DEMO_MUTED } }
        )
        text(
            "candidates=${window.dndDebugCandidatesCount} insert=${window.dndDebugInsertPosition} excludeActive=${window.dndDebugExcludesActiveCard}",
            { style = { color = DEMO_MUTED } }
        )

        div({
            style = {
                gap = 3.px
                display = Display.Flex
                flexDirection = FlexDirection.Row
            }
        }) {
            button(if (window.dndGhostEnabled) "Ghost ON" else "Ghost OFF", {
                onMouseClick = {
                    window.dndGhostEnabled = !window.dndGhostEnabled
                    window.appendInfo("DnD ghost=${window.dndGhostEnabled}")
                }
            })
            button(if (window.dndHideSourceWhileDragging) "Hide ON" else "Hide OFF", {
                onMouseClick = {
                    window.dndHideSourceWhileDragging = !window.dndHideSourceWhileDragging
                    window.appendInfo("DnD hideSource=${window.dndHideSourceWhileDragging}")
                }
            })
            button("Reset state", {
                onMouseClick = { window.resetDndItems("toolbar") }
            })
            button("Reset logs", {
                onMouseClick = { window.clearEventLogs() }
            })
        }
        div({
            style = {
                gap = 3.px
                display = Display.Flex
                flexDirection = FlexDirection.Row
            }
        }) {
            button("k-", {
                style = { width = 26.px }
                onMouseClick = { window.updateDndSmoothing(-4.0) }
            })
            button("k+", {
                style = { width = 26.px }
                onMouseClick = { window.updateDndSmoothing(4.0) }
            })
            text("smoothing k=${"%.1f".format(window.dndSmoothFactor)}", { style = { color = DEMO_MUTED } })
        }
        if (splitAllowed) {
            div({
                key = "dnd.row.main"
                style = {
                    width = (leftWidth + rightWidth + PANELS_GAP).px
                    height = ((contentHeight - 70).coerceAtLeast(96)).px
                    gap = PANELS_GAP.px

                    display = Display.Flex
                    flexDirection = FlexDirection.Row
                }
            }) {
                originalModeReorder(window, leftWidth)
                renderGhostModeBoxes(window, rightWidth)
            }
        } else {
            div({
                key = "dnd.col.main"
                style = {
                    width = (contentWidth.coerceAtLeast(0)).px
                    height = ((contentHeight - 70).coerceAtLeast(96)).px
                    gap = 4.px
                    display = Display.Flex
                    flexDirection = FlexDirection.Column
                }
            }) {
                originalModeReorder(window, contentWidth.coerceAtLeast(0))
                renderGhostModeBoxes(window, contentWidth.coerceAtLeast(0))
            }
        }
    }
}

private fun UiScope.originalModeReorder(window: ShowcaseWindow, panelWidth: Int) {
    val monitor = DndSystem.monitor()
    val draggedId = extractCardIdFromDragKey(monitor.sourceKey)
    val previewOrder = window.resolveLanePreviewOrder(monitor.sourceKey)
    div({
        key = "dnd.original.panel"
        style = {
            width = panelWidth.px
            gap = 3.px
            padding = 3.px
            backgroundColor = 0xFF2D333B.toInt()
            border(1.px, 0xFF6B7785.toInt())
            display = Display.Flex
            flexDirection = FlexDirection.Column
        }

    }) {
        text("ORIGINAL mode: reorder list")
        text("Detached source follows cursor; slot uses placeholder.", { style = { color = DEMO_MUTED } })
        if (previewOrder.isEmpty()) {
            text("No items available", { style = { color = DEMO_MUTED } })
        } else {
            val laneCardSize = CARD_SIZE.coerceAtMost((panelWidth - 12).coerceAtLeast(24))
            val laneDroppable = window.useDroppable(
                id = "lane",
                nodeKey = "dnd.lane.column",
                accepts = { active -> !active.id.isNullOrBlank() },
                onDragOver = { event, _ -> window.handleDndLaneOver(event) },
                onDragLeave = { _, _ -> window.clearLaneReorderHoverState() },
                onDrop = { event, _ -> window.handleDndLaneDrop(event) }
            )
            div({
                key = "dnd.lane.column"
                style = {
                    gap = 2.px
                    backgroundColor = if (window.isLaneAppendHighlighted()) 0x2A9EC4E3 else 0x00000000
                    border(
                        1.px,
                        if (window.isLaneAppendHighlighted()) 0xFF9EC4E3.toInt() else 0x44405058
                    )
                    display = Display.Flex
                    flexDirection = FlexDirection.Column
                }

                applyDroppable(laneDroppable)
            }) {
                previewOrder.forEach { item ->
                    val indicator = window.laneIndicatorForCard(item.id, monitor.sourceKey)
                    val isDraggedItem = draggedId != null && draggedId == item.id
                    val sortable = window.useSortable(
                        id = item.id,
                        nodeKey = "dnd.lane.card.${item.id}",
                        containerId = "lane",
                        items = previewOrder.map { it.id },
                        data = item,
                        previewMode = DragPreviewMode.ORIGINAL,
                        hideSourceWhileDragging = true
                    )
                    cardWithItem(
                        item = item,
                        cardKey = "dnd.lane.card.${item.id}",
                        cardSize = laneCardSize,
                        sortable = sortable,
                        draggableEnabled = !isDraggedItem,
                        highlighted = indicator != ShowcaseWindow.DndLaneIndicator.NONE,
                        insertionIndicator = indicator,
                        extraListeners = DndListeners(
                            onDragStart = { event -> window.handleDndStart(item, event) },
                            onDrag = { event -> window.handleDndDrag(event) },
                            onDragEnd = { event -> window.handleDndEnd(event) },
                            onDragOver = if (isDraggedItem) null else { event ->
                                val insertAfter = resolveInsertAfter(event)
                                window.handleDndCardReorderOver(item.id, insertAfter, event)
                            },
                            onDrop = if (isDraggedItem) null else { event ->
                                val insertAfter = resolveInsertAfter(event)
                                window.handleDndCardReorderDrop(item.id, insertAfter, event)
                            }
                        )
                    )
                }
                if (window.shouldShowLaneAppendGap(monitor.sourceKey)) {
                    div({
                        key = "dnd.lane.append.gap"
                        style = {
                            width = laneCardSize.px
                            height = laneCardSize.px
                            backgroundColor = 0x2A9EC4E3
                            border(1.px, 0xFF9EC4E3.toInt())
                            borderRadius(3.px)
                        }
                    }) {
                        text("APPEND", { style = { color = 0xFFD3E8FB.toInt() } })
                    }
                }
            }
            text({
                val source = monitor.sourceKey ?: "none"
                value = "dragSource=$source previewCount=${previewOrder.size}"
                style = { color = DEMO_MUTED }
            })
            if (window.dndBoxes.values.any { it.isNotEmpty() }) {
                text("Tip: drag cards from boxes back to lane to reorder them.", { style = { color = DEMO_MUTED } }
                )
            }
        }
    }
}

private fun UiScope.renderGhostModeBoxes(window: ShowcaseWindow, ghostWidth: Int) {
    div({
        key = "dnd.ghost.panel"
        style = {
            width = ghostWidth.px
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
            window = window,
            boxKey = "dnd.box.a",
            title = "Box A",
            boxId = "box-a",
            color = 0xFF314B3A.toInt(),
            cards = window.bucketCards("box-a"),
            boxWidth = ghostWidth
        )
        dropBox(
            window = window,
            boxKey = "dnd.box.b",
            title = "Box B",
            boxId = "box-b",
            color = 0xFF5A3434.toInt(),
            cards = window.bucketCards("box-b"),
            boxWidth = ghostWidth
        )
        dropBox(
            window = window,
            boxKey = "dnd.box.c",
            title = "Box C",
            boxId = "box-c",
            color = 0xFF354A5A.toInt(),
            cards = window.bucketCards("box-c"),
            boxWidth = ghostWidth
        )

        button("Reset DnD", {
            onMouseClick = { window.resetDndItems("button") }
            style = {
                width = (ghostWidth - 8).px
                backgroundImage("file://demo/local_showcase.png")
            }
        })
    }
}

private fun UiScope.cardWithItem(
    item: DndDemoItem,
    cardKey: Any,
    cardSize: Int,
    draggable: Draggable? = null,
    sortable: Sortable? = null,
    draggableEnabled: Boolean = true,
    highlighted: Boolean,
    insertionIndicator: ShowcaseWindow.DndLaneIndicator = ShowcaseWindow.DndLaneIndicator.NONE,
    extraListeners: DndListeners = DndListeners()
) {
    val draggingThis = sortable?.isDragging ?: draggable?.isDragging ?: DndSystem.monitor(cardKey).isDragging
    val accent = itemAccentColor(item.id)
    val base = itemBaseColor(item.id)
    val insertionGap = (cardSize + 2).coerceAtLeast(24)
    div({
        key = cardKey
        dragPlaceholder = {
            fillColor = 0x44333F4D
            borderColor = accent
            borderWidth = 1
        }
        style = {
            width = cardSize.px
            height = cardSize.px
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
                ShowcaseWindow.DndLaneIndicator.BEFORE -> margin(insertionGap.px, 0.px, 0.px, 0.px)
                ShowcaseWindow.DndLaneIndicator.AFTER -> margin(0.px, 0.px, insertionGap.px, 0.px)
                ShowcaseWindow.DndLaneIndicator.NONE -> Unit
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
                width = (cardSize - 8).px
                height = 3.px
                backgroundColor = lighten(accent, 12)
                borderRadius(2.px)
            }
        })
        itemStack(item.stack, {
            size = (cardSize / 2).coerceAtLeast(16)
            key = "dnd.stack.${item.id}"
            style = {
                width = ((cardSize - 12).coerceAtLeast(18)).px
                border(1.px, 0x553A4452)
                backgroundColor(0x2219222B)
            }
        })
        text(item.label, {
            style = { color = if (draggingThis) 0xFFFFFFFF.toInt() else 0xFFEAF2FD.toInt() }
        })
    }
}

private fun UiScope.dropBox(
    window: ShowcaseWindow,
    boxKey: Any,
    title: String,
    boxId: String,
    color: Int,
    cards: List<DndDemoItem>,
    boxWidth: Int
) {
    val highlighted = window.dndHoverZone == boxId
    val dropDescriptor = window.useDroppable(
        id = boxId,
        nodeKey = boxKey,
        accepts = { active -> !active.id.isNullOrBlank() },
        onDragEnter = { event, _ ->
            window.dndHoverZone = boxId
            window.logHook("dnd.$boxId.onDragEnter", event)
        },
        onDragOver = { event, _ -> window.handleDndBoxOver(boxId, event) },
        onDragLeave = { event, _ ->
            if (window.dndHoverZone == boxId) {
                window.dndHoverZone = "none"
            }
            window.logHook("dnd.$boxId.onDragLeave", event)
        },
        onDrop = { event, _ -> window.handleDndBoxDrop(boxId, event) }
    )
    div({
        key = boxKey
        style = {
            width = null
            height = BOX_HEIGHT.px
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
            val rowBudget = (boxWidth - 20).coerceAtLeast(1)
            val maxVisibleCards = ((rowBudget + 2) / (BOX_CARD_SIZE + 2)).coerceAtLeast(1)
            div({
                style = {
                    key = "$boxKey.cards"
                    gap = 2.px
                    display = Display.Flex
                    flexDirection = FlexDirection.Row
                }
            }) {
                cards.take(maxVisibleCards).forEach { item ->
                    val draggable = window.useDraggable(
                        id = item.id,
                        nodeKey = "dnd.box.$boxId.card.${item.id}",
                        type = "card",
                        data = item,
                        previewMode = DragPreviewMode.GHOST,
                        hideSourceWhileDragging = window.dndHideSourceWhileDragging,
                        onDragStart = { event -> window.handleDndStart(item, event) },
                        onDrag = { event -> window.handleDndDrag(event) },
                        onDragEnd = { event -> window.handleDndEnd(event) }
                    )
                    cardWithItem(
                        item = item,
                        cardKey = "dnd.box.$boxId.card.${item.id}",
                        cardSize = BOX_CARD_SIZE,
                        draggable = draggable,
                        highlighted = false,
                        extraListeners = DndListeners()
                    )
                }
                if (cards.size > maxVisibleCards) {
                    text("+${cards.size - maxVisibleCards}", { style = { this.color = DEMO_MUTED } })
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

