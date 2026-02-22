package org.dreamfinity.dsgl.mc1710.demo.sections

import org.dreamfinity.dsgl.core.ButtonProps
import org.dreamfinity.dsgl.core.ComponentProps
import org.dreamfinity.dsgl.core.DynamicTextProps
import org.dreamfinity.dsgl.core.ItemStackProps
import org.dreamfinity.dsgl.core.TextProps
import org.dreamfinity.dsgl.core.UiScope
import org.dreamfinity.dsgl.core.event.DragEndEvent
import org.dreamfinity.dsgl.core.event.DragEvent
import org.dreamfinity.dsgl.core.event.DragManager
import org.dreamfinity.dsgl.core.event.DragLeaveEvent
import org.dreamfinity.dsgl.core.event.DragOverEvent
import org.dreamfinity.dsgl.core.event.DragPreviewMode
import org.dreamfinity.dsgl.core.event.DragStartEvent
import org.dreamfinity.dsgl.core.event.DropEvent
import org.dreamfinity.dsgl.mc1710.demo.ShowcaseWindow.DndDemoItem
import org.dreamfinity.dsgl.mc1710.demo.ShowcaseWindow
import org.dreamfinity.dsgl.mc1710.demo.support.DEMO_MUTED

private const val LEFT_PANEL_PERCENT = 58
private const val MIN_LEFT_WIDTH = 72
private const val MIN_RIGHT_WIDTH = 64
private const val PANELS_GAP = 6
private const val CARD_SIZE = 40
private const val BOX_HEIGHT = 52
private const val BOX_CARD_SIZE = 28
private const val HIGHLIGHT_DELTA = 22

fun UiScope.renderDragDropSection(window: ShowcaseWindow, contentWidth: Int, contentHeight: Int) {
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
    val monitor = DragManager.monitor()

    column(
        ComponentProps(
            key = "section.dragDrop",
            width = contentWidth,
            height = contentHeight,
            gap = 4
        )
    ) {
        text(TextProps("Drag preview modes: ORIGINAL (detached source) and GHOST (overlay preview)."))
        dynamicText(
            DynamicTextProps {
                val mode = monitor.mode?.name ?: "none"
                "active=${window.dndActiveItem} mode=$mode effect=${window.dndDropEffect} hover=${window.dndHoverZone}"
            }.apply { color = DEMO_MUTED }
        )
        dynamicText(
            DynamicTextProps {
                "types=${window.dndTransferTypes} dragTicks=${window.dndDragTickCount} action=${window.dndLastAction}"
            }.apply { color = DEMO_MUTED }
        )
        dynamicText(
            DynamicTextProps {
                "debug active=${monitor.sourceKey ?: "none"} over=${window.dndDebugOverId} container=${window.dndDebugOverContainerId}"
            }.apply { color = DEMO_MUTED }
        )
        dynamicText(
            DynamicTextProps {
                "candidates=${window.dndDebugCandidatesCount} insert=${window.dndDebugInsertPosition} excludeActive=${window.dndDebugExcludesActiveCard}"
            }.apply { color = DEMO_MUTED }
        )

        row(ComponentProps(gap = 3)) {
            button(
                ButtonProps(if (window.dndGhostEnabled) "Ghost ON" else "Ghost OFF").apply {
                    onMouseClick = {
                        window.dndGhostEnabled = !window.dndGhostEnabled
                        window.appendInfo("DnD ghost=${window.dndGhostEnabled}")
                    }
                }
            )
            button(
                ButtonProps(if (window.dndHideSourceWhileDragging) "Hide ON" else "Hide OFF").apply {
                    onMouseClick = {
                        window.dndHideSourceWhileDragging = !window.dndHideSourceWhileDragging
                        window.appendInfo("DnD hideSource=${window.dndHideSourceWhileDragging}")
                    }
                }
            )
            button(
                ButtonProps("Reset state").apply {
                    onMouseClick = { window.resetDndItems("toolbar") }
                }
            )
            button(
                ButtonProps("Reset logs").apply {
                    onMouseClick = { window.clearEventLogs() }
                }
            )
        }
        row(ComponentProps(gap = 3)) {
            button(
                ButtonProps("k-").apply {
                    width = 26
                    onMouseClick = { window.updateDndSmoothing(-4.0) }
                }
            )
            button(
                ButtonProps("k+").apply {
                    width = 26
                    onMouseClick = { window.updateDndSmoothing(4.0) }
                }
            )
            text(TextProps("smoothing k=${"%.1f".format(window.dndSmoothFactor)}").apply { color = DEMO_MUTED })
        }
        if (splitAllowed) {
            row(
                ComponentProps(
                    key = "dnd.row.main",
                    width = leftWidth + rightWidth + PANELS_GAP,
                    height = (contentHeight - 70).coerceAtLeast(96),
                    gap = PANELS_GAP
                )
            ) {
                renderOriginalModeReorder(window, leftWidth)
                renderGhostModeBoxes(window, rightWidth)
            }
        } else {
            column(
                ComponentProps(
                    key = "dnd.col.main",
                    width = contentWidth.coerceAtLeast(0),
                    height = (contentHeight - 70).coerceAtLeast(96),
                    gap = 4
                )
            ) {
                renderOriginalModeReorder(window, contentWidth.coerceAtLeast(0))
                renderGhostModeBoxes(window, contentWidth.coerceAtLeast(0))
            }
        }
    }
}

private fun UiScope.renderOriginalModeReorder(window: ShowcaseWindow, width: Int) {
    val monitor = DragManager.monitor()
    val draggedId = extractCardIdFromDragKey(monitor.sourceKey)
    val previewOrder = window.resolveLanePreviewOrder(monitor.sourceKey)
    column(
        ComponentProps(
            key = "dnd.original.panel",
            width = width,
            gap = 3,
            padding = 3,
            backgroundColor = 0xFF2D333B.toInt(),
            style = { border(1, 0xFF6B7785.toInt()) }
        )
    ) {
        text(TextProps("ORIGINAL mode: reorder list"))
        text(TextProps("Detached source follows cursor; slot uses placeholder.").apply { color = DEMO_MUTED })
        if (previewOrder.isEmpty()) {
            text(TextProps("No items available").apply { color = DEMO_MUTED })
        } else {
            val laneCardSize = CARD_SIZE.coerceAtMost((width - 12).coerceAtLeast(24))
            column(
                ComponentProps(
                    gap = 2,
                    key = "dnd.lane.column",
                    droppable = true,
                    backgroundColor = if (window.isLaneAppendHighlighted()) 0x2A9EC4E3 else 0x00000000,
                    onDragOver = { event -> window.handleDndLaneOver(event) },
                    onDragLeave = { _: DragLeaveEvent ->
                        window.clearLaneReorderHoverState()
                    },
                    onDrop = { event -> window.handleDndLaneDrop(event) },
                    style = {
                        border(
                            1,
                            if (window.isLaneAppendHighlighted()) 0xFF9EC4E3.toInt() else 0x44405058
                        )
                    }
                )
            ) {
                previewOrder.forEach { item ->
                    val indicator = window.laneIndicatorForCard(item.id, monitor.sourceKey)
                    val isDraggedItem = draggedId != null && draggedId == item.id
                    renderCard(
                        item = item,
                        key = "dnd.lane.card.${item.id}",
                        size = laneCardSize,
                        previewMode = DragPreviewMode.ORIGINAL,
                        hideSourceWhileDragging = true,
                        draggableEnabled = !isDraggedItem,
                        highlighted = indicator != ShowcaseWindow.DndLaneIndicator.NONE,
                        insertionIndicator = indicator,
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
                }
                if (window.shouldShowLaneAppendGap(monitor.sourceKey)) {
                    div(
                        ComponentProps(
                            key = "dnd.lane.append.gap",
                            width = laneCardSize,
                            height = laneCardSize,
                            backgroundColor = 0x2A9EC4E3,
                            style = {
                                border(1, 0xFF9EC4E3.toInt())
                                borderRadius(3)
                            }
                        )
                    ) {
                        text(TextProps("APPEND").apply { color = 0xFFD3E8FB.toInt() })
                    }
                }
            }
            dynamicText(
                DynamicTextProps {
                    val source = monitor.sourceKey ?: "none"
                    "dragSource=$source previewCount=${previewOrder.size}"
                }.apply { color = DEMO_MUTED }
            )
            if (window.dndBoxes.values.any { it.isNotEmpty() }) {
                text(
                    TextProps("Tip: drag cards from boxes back to lane to reorder them.")
                        .apply { color = DEMO_MUTED }
                )
            }
        }
    }
}

private fun UiScope.renderGhostModeBoxes(window: ShowcaseWindow, width: Int) {
    column(
        ComponentProps(
            key = "dnd.ghost.panel",
            width = width,
            gap = 4,
            padding = 3,
            backgroundColor = 0xFF2D333B.toInt(),
            style = { border(1, 0xFF6B7785.toInt()) }
        )
    ) {
        text(TextProps("Buckets: drop card to move it into a box"))
        text(TextProps("Ghost toggle applies to drag previews in this panel.").apply { color = DEMO_MUTED })

        renderDropBox(
            window = window,
            key = "dnd.box.a",
            title = "Box A",
            boxId = "box-a",
            color = 0xFF314B3A.toInt(),
            cards = window.bucketCards("box-a"),
            width = width
        )
        renderDropBox(
            window = window,
            key = "dnd.box.b",
            title = "Box B",
            boxId = "box-b",
            color = 0xFF5A3434.toInt(),
            cards = window.bucketCards("box-b"),
            width = width
        )
        renderDropBox(
            window = window,
            key = "dnd.box.c",
            title = "Box C",
            boxId = "box-c",
            color = 0xFF354A5A.toInt(),
            cards = window.bucketCards("box-c"),
            width = width
        )

        button(
            ButtonProps("Reset DnD").apply {
                this.width = width - 8
                onMouseClick = { window.resetDndItems("button") }
                style = { backgroundImage("file://demo/local_showcase.png") }
            }
        )
    }
}

private fun UiScope.renderCard(
    item: DndDemoItem,
    key: Any,
    size: Int,
    previewMode: DragPreviewMode,
    hideSourceWhileDragging: Boolean,
    draggableEnabled: Boolean = true,
    highlighted: Boolean,
    insertionIndicator: ShowcaseWindow.DndLaneIndicator = ShowcaseWindow.DndLaneIndicator.NONE,
    onDragStart: ((DragStartEvent) -> Unit)? = null,
    onDrag: ((DragEvent) -> Unit)? = null,
    onDragEnd: ((DragEndEvent) -> Unit)? = null,
    onDragOver: ((DragOverEvent) -> Unit)? = null,
    onDrop: ((DropEvent) -> Unit)? = null
) {
    val draggingThis = DragManager.isDraggingNode(key)
    val accent = itemAccentColor(item.id)
    val base = itemBaseColor(item.id)
    val insertionGap = (size + 2).coerceAtLeast(24)
    div(
        ComponentProps(
            key = key,
            width = size,
            height = size,
            padding = 2,
            gap = 1,
            draggable = draggableEnabled,
            droppable = onDragOver != null || onDrop != null,
            dragPreviewMode = previewMode,
            hideSourceWhileDragging = hideSourceWhileDragging,
            dragPlaceholder = {
                fillColor = 0x44333F4D
                borderColor = accent
                borderWidth = 1
            },
            backgroundColor = when {
                draggingThis -> lighten(base, HIGHLIGHT_DELTA + 8)
                highlighted -> lighten(base, HIGHLIGHT_DELTA)
                else -> base
            },
            onDragStart = onDragStart,
            onDrag = onDrag,
            onDragEnd = onDragEnd,
            onDragOver = onDragOver,
            onDrop = onDrop,
            style = {
                border(1, accent)
                borderRadius(3)
                when (insertionIndicator) {
                    ShowcaseWindow.DndLaneIndicator.BEFORE -> margin(insertionGap, 0, 0, 0)
                    ShowcaseWindow.DndLaneIndicator.AFTER -> margin(0, 0, insertionGap, 0)
                    ShowcaseWindow.DndLaneIndicator.NONE -> Unit
                }
            }
        )
    ) {
        div(
            ComponentProps(
                key = "$key.accent",
                width = size - 8,
                height = 3,
                backgroundColor = lighten(accent, 12)
            ).apply {
                style = { borderRadius(2) }
            }
        )
        itemStack(
            ItemStackProps(item.stack, size = (size / 2).coerceAtLeast(16)).apply {
                this.key = "dnd.stack.${item.id}"
                this.width = (size - 12).coerceAtLeast(18)
                style = {
                    border(1, 0x553A4452)
                    backgroundColor(0x2219222B)
                }
            }
        )
        text(TextProps(item.label).apply {
            color = if (draggingThis) 0xFFFFFFFF.toInt() else 0xFFEAF2FD.toInt()
        })
    }
}

private fun UiScope.renderDropBox(
    window: ShowcaseWindow,
    key: Any,
    title: String,
    boxId: String,
    color: Int,
    cards: List<DndDemoItem>,
    width: Int
) {
    val highlighted = window.dndHoverZone == boxId
    div(
        ComponentProps(
            key = key,
            width = null,
            height = BOX_HEIGHT,
            padding = 4,
            gap = 2,
            droppable = true,
            backgroundColor = if (highlighted) lighten(color, HIGHLIGHT_DELTA) else color,
            onDragEnter = { event ->
                window.dndHoverZone = boxId
                window.logHook("dnd.$boxId.onDragEnter", event)
            },
            onDragOver = { event -> window.handleDndBoxOver(boxId, event) },
            onDragLeave = { event ->
                if (window.dndHoverZone == boxId) {
                    window.dndHoverZone = "none"
                }
                window.logHook("dnd.$boxId.onDragLeave", event)
            },
            onDrop = { event -> window.handleDndBoxDrop(boxId, event) },
            style = {
                border(1, 0xFF8A94A2.toInt())
                borderRadius(3)
            }
        )
    ) {
        text(TextProps("$title (${cards.size})"))
        if (cards.isEmpty()) {
            text(TextProps("Drop here").apply { this.color = DEMO_MUTED })
        } else {
            val rowBudget = (width - 20).coerceAtLeast(1)
            val maxVisibleCards = ((rowBudget + 2) / (BOX_CARD_SIZE + 2)).coerceAtLeast(1)
            row(ComponentProps(gap = 2, key = "$key.cards")) {
                cards.take(maxVisibleCards).forEach { item ->
                    renderCard(
                        item = item,
                        key = "dnd.box.$boxId.card.${item.id}",
                        size = BOX_CARD_SIZE,
                        previewMode = DragPreviewMode.GHOST,
                        hideSourceWhileDragging = window.dndHideSourceWhileDragging,
                        highlighted = false,
                        onDragStart = { event -> window.handleDndStart(item, event) },
                        onDrag = { event -> window.handleDndDrag(event) },
                        onDragEnd = { event -> window.handleDndEnd(event) }
                    )
                }
                if (cards.size > maxVisibleCards) {
                    text(TextProps("+${cards.size - maxVisibleCards}").apply { this.color = DEMO_MUTED })
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
