package org.dreamfinity.dsgl.mcForge1710.demo.support

import org.dreamfinity.dsgl.core.DsglColors
import org.dreamfinity.dsgl.core.dsl.UiScope
import org.dreamfinity.dsgl.core.dsl.button
import org.dreamfinity.dsgl.core.dsl.div
import org.dreamfinity.dsgl.core.dsl.text
import org.dreamfinity.dsgl.core.style.Display
import org.dreamfinity.dsgl.core.style.FlexDirection

fun UiScope.renderEventInspectorPanel(
    eventLogs: List<EventLogEntry>,
    maxEventLogs: Int,
    visibleEventLines: Int,
    onClearLogs: () -> Unit
) {
    div({
        key = "panel.eventInspector"
        style = {
            display = Display.Flex
            flexDirection = FlexDirection.Column
            flexGrow = 1f
            gap = 4.px
            padding = 20.px
            backgroundColor = DEMO_SURFACE_ALT
            color = DsglColors.TEXT
            border { width = 1.px; color = DsglColors.BORDER }
        }

    }) {
        div({
            style = {
                display = Display.Flex
                flexDirection = FlexDirection.Row
            }
        }) {
            text("Event Inspector")
            button("Clear", {
                style = { }
                onMouseClick = { onClearLogs() }
            })
        }
        text("Stored: ${eventLogs.size}/$maxEventLogs", { style = { color = DEMO_MUTED } })
        if (eventLogs.isEmpty()) {
            text("No events yet. Interact with any demo area.", { style = { color = DEMO_MUTED } })
        } else {
            eventLogs
                .take(visibleEventLines)
                .forEach { entry ->
                    text("#${entry.sequence} ${entry.line}", { style = { color = entry.color } })
                }
        }
    }
}

fun UiScope.renderChecklistPanel(
    implementedCapabilities: Set<CapabilityId>,
    checklistPage: Int,
    checklistPageSize: Int,
    onSetChecklistPage: (Int) -> Unit,
    onMoveChecklistPage: (Int) -> Unit
) {
    val required = CapabilityChecklistCatalog.required
    val pageSize = checklistPageSize
    val pageCount = ((required.size + pageSize - 1) / pageSize).coerceAtLeast(1)
    val safePage = checklistPage.coerceIn(0, pageCount - 1)
    if (safePage != checklistPage) {
        onSetChecklistPage(safePage)
    }
    val from = safePage * pageSize
    val to = minOf(required.size, from + pageSize)
    val pageItems = required.subList(from, to)

    div({
        key = "panel.capabilityChecklist"
        style = {
            display = Display.Flex
            flexDirection = FlexDirection.Column
            flexGrow = 1f
            padding = 20.px
            gap = 4.px
            backgroundColor = DEMO_SURFACE_ALT
            color = DsglColors.TEXT
            border { width = 1.px; color = DsglColors.BORDER }
        }

    }) {
        text("Capability Checklist")
        div({
            style = {
                display = Display.Flex
                flexDirection = FlexDirection.Row
            }
        }) {
            button("<", {
                onMouseClick = { onMoveChecklistPage(-1) }
            })
            text("Page ${safePage + 1}/$pageCount", { style = { color = DEMO_MUTED } })
            button(">", {
                onMouseClick = { onMoveChecklistPage(1) }
            })
        }
        pageItems.forEach { capability ->
            val ok = implementedCapabilities.contains(capability)
            text("${if (ok) "[OK]" else "[MISS]"} ${capability.label}", {
                style = { color = if (ok) DEMO_OK else DEMO_ERR }
            })
        }
        val missing = required.count { !implementedCapabilities.contains(it) }
        text("Missing: $missing / ${required.size}", { style = { color = if (missing == 0) DEMO_OK else DEMO_ERR } })
    }
}

