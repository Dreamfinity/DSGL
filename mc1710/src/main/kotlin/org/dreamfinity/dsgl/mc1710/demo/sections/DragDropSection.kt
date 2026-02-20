package org.dreamfinity.dsgl.mc1710.demo.sections

import org.dreamfinity.dsgl.core.ButtonProps
import org.dreamfinity.dsgl.core.ComponentProps
import org.dreamfinity.dsgl.core.DynamicTextProps
import org.dreamfinity.dsgl.core.TextProps
import org.dreamfinity.dsgl.core.UiScope
import org.dreamfinity.dsgl.core.event.DropEffect
import org.dreamfinity.dsgl.mc1710.demo.ShowcaseWindow
import org.dreamfinity.dsgl.mc1710.demo.support.DEMO_MUTED

private const val ITEMS_PANEL_PERCENT = 58
private const val MIN_ITEMS_PANEL_WIDTH = 110
private const val MIN_TARGETS_PANEL_WIDTH = 88
private const val DROP_ZONE_HEIGHT = 34
private const val COLOR_HIGHLIGHT_DELTA = 20

private const val ZONE_REORDER = "reorder"
private const val ZONE_TRASH = "trash"
private const val ZONE_COPY = "copy"

fun UiScope.renderDragDropSection(window: ShowcaseWindow, contentWidth: Int, contentHeight: Int) {
    val itemsPanelWidth = ((contentWidth * ITEMS_PANEL_PERCENT) / 100).coerceAtLeast(MIN_ITEMS_PANEL_WIDTH)
    val targetsPanelWidth = (contentWidth - itemsPanelWidth - 8).coerceAtLeast(MIN_TARGETS_PANEL_WIDTH)

    column(
        ComponentProps(
            key = "section.dragDrop",
            width = contentWidth,
            height = contentHeight,
            gap = 4
        )
    ) {
        text(TextProps("Drag cards into zones. Drop is allowed only when dragover accepts it."))
        dynamicText(
            DynamicTextProps {
                "active=${window.dndActiveItem} hoverZone=${window.dndHoverZone} effect=${window.dndDropEffect}"
            }.apply { color = DEMO_MUTED }
        )
        dynamicText(
            DynamicTextProps {
                "types=${window.dndTransferTypes} ticks=${window.dndDragTickCount} action=${window.dndLastAction}"
            }.apply { color = DEMO_MUTED }
        )

        row(
            ComponentProps(
                key = "dnd.row.main",
                width = contentWidth,
                height = (contentHeight - 44).coerceAtLeast(90),
                gap = 6
            )
        ) {
            column(
                ComponentProps(
                    key = "dnd.items.panel",
                    width = itemsPanelWidth,
                    gap = 3,
                    padding = 3,
                    backgroundColor = 0xFF2D333B.toInt(),
                    style = { border(1, 0xFF6B7785.toInt()) }
                )
            ) {
                text(TextProps("Draggable source list"))
                if (window.dndItems.isEmpty()) {
                    text(TextProps("No items left. Use copy zone first.").apply { color = DEMO_MUTED })
                } else {
                    window.dndItems.forEach { item ->
                        div(
                            ComponentProps(
                                key = "dnd.item.${item.id}",
                                width = itemsPanelWidth - 10,
                                padding = 3,
                                draggable = true,
                                backgroundColor = 0xFF3C4B5A.toInt(),
                                onDragStart = { event -> window.handleDndStart(item, event) },
                                onDrag = { event -> window.handleDndDrag(event) },
                                onDragEnd = { event -> window.handleDndEnd(event) },
                                style = { border(1, 0xFF7F8FA0.toInt()) }
                            )
                        ) {
                            text(TextProps(item.label))
                            text(TextProps("id=${item.id}").apply { color = DEMO_MUTED })
                        }
                    }
                }
            }

            column(
                ComponentProps(
                    key = "dnd.targets.panel",
                    width = targetsPanelWidth,
                    gap = 4
                )
            ) {
                renderDropZone(
                    window = window,
                    key = "dnd.zone.reorder",
                    title = "Reorder (move)",
                    zoneId = ZONE_REORDER,
                    color = 0xFF314B3A.toInt(),
                    effect = DropEffect.MOVE
                )
                renderDropZone(
                    window = window,
                    key = "dnd.zone.trash",
                    title = "Trash (delete)",
                    zoneId = ZONE_TRASH,
                    color = 0xFF5A3434.toInt(),
                    effect = DropEffect.MOVE
                )
                renderDropZone(
                    window = window,
                    key = "dnd.zone.copy",
                    title = "Copy Bin",
                    zoneId = ZONE_COPY,
                    color = 0xFF354A5A.toInt(),
                    effect = DropEffect.COPY
                )
                button(
                    ButtonProps("Reset Items").apply {
                        width = targetsPanelWidth
                        onMouseClick = {
                            window.resetDndItems("button")
                        }
                    }
                )
            }
        }
    }
}

private fun UiScope.renderDropZone(
    window: ShowcaseWindow,
    key: Any,
    title: String,
    zoneId: String,
    color: Int,
    effect: DropEffect
) {
    val highlighted = window.dndHoverZone == zoneId
    div(
        ComponentProps(
            key = key,
            width = null,
            height = DROP_ZONE_HEIGHT,
            padding = 4,
            droppable = true,
            backgroundColor = if (highlighted) lighten(color, COLOR_HIGHLIGHT_DELTA) else color,
            onDragEnter = { event ->
                window.dndHoverZone = zoneId
                window.logHook("dnd.$zoneId.onDragEnter", event)
            },
            onDragOver = { event ->
                window.handleDndOver(zoneId, effect, event)
            },
            onDragLeave = { event ->
                if (window.dndHoverZone == zoneId) {
                    window.dndHoverZone = "none"
                }
                window.logHook("dnd.$zoneId.onDragLeave", event)
            },
            onDrop = { event ->
                window.handleDndDrop(zoneId, event)
            },
            style = { border(1, 0xFF8A94A2.toInt()) }
        )
    ) {
        text(TextProps(title))
    }
}

private fun lighten(color: Int, delta: Int): Int {
    val a = (color ushr 24) and 0xFF
    val r = ((color ushr 16) and 0xFF) + delta
    val g = ((color ushr 8) and 0xFF) + delta
    val b = (color and 0xFF) + delta
    return (a shl 24) or ((r.coerceAtMost(255)) shl 16) or ((g.coerceAtMost(255)) shl 8) or b.coerceAtMost(255)
}
