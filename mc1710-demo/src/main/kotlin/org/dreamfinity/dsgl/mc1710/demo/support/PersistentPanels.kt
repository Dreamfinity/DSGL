package org.dreamfinity.dsgl.mc1710.demo.support

import org.dreamfinity.dsgl.core.DsglColors
import org.dreamfinity.dsgl.core.UiScope
import org.dreamfinity.dsgl.mc1710.demo.ShowcaseWindow

fun UiScope.renderEventInspectorPanel(window: ShowcaseWindow, panelWidth: Int, panelHeight: Int) {
    div({
        key = "panel.eventInspector"
        width = panelWidth
        height = panelHeight
        gap = 4
        backgroundColor = DEMO_SURFACE_ALT
        color = DsglColors.TEXT
        style = { border(1, DsglColors.BORDER) }
        asFlexColumn()
    }) {
        div({ asFlexRow() }) {
            text("Event Inspector")
            button("Clear", {
                this.width = 44
                onMouseClick = { window.clearEventLogs() }
            })
        }
        text("Stored: ${window.eventLogs.size}/${window.maxEventLogs}", { color = DEMO_MUTED })
        if (window.eventLogs.isEmpty()) {
            text("No events yet. Interact with any demo area.", { color = DEMO_MUTED })
        } else {
            window.eventLogs
                .take(window.visibleEventLines)
                .forEach { entry ->
                    text("#${entry.sequence} ${entry.line}", { color = entry.color })
                }
        }
    }
}

fun UiScope.renderChecklistPanel(window: ShowcaseWindow, panelWidth: Int, panelHeight: Int) {
    val required = CapabilityChecklistCatalog.required
    val implemented = window.implementedCapabilities
    val pageSize = window.checklistPageSize
    val pageCount = ((required.size + pageSize - 1) / pageSize).coerceAtLeast(1)
    val safePage = window.checklistPage.coerceIn(0, pageCount - 1)
    if (safePage != window.checklistPage) {
        window.checklistPage = safePage
    }
    val from = safePage * pageSize
    val to = minOf(required.size, from + pageSize)
    val pageItems = required.subList(from, to)

    div({
        key = "panel.capabilityChecklist"
        width = panelWidth
        height = panelHeight
        gap = 4
        backgroundColor = DEMO_SURFACE_ALT
        color = DsglColors.TEXT
        style = { border(1, DsglColors.BORDER) }
        asFlexColumn()
    }) {
        text("Capability Checklist")
        div({ asFlexRow() }) {
            button("<", {
                width = 22
                onMouseClick = { window.moveChecklistPage(-1) }
            })
            text("Page ${window.checklistPage + 1}/$pageCount", { color = DEMO_MUTED })
            button(">", {
                this.width = 22
                onMouseClick = { window.moveChecklistPage(1) }
            })
        }
        pageItems.forEach { capability ->
            val ok = implemented.contains(capability)
            text("${if (ok) "[OK]" else "[MISS]"} ${capability.label}", { color = if (ok) DEMO_OK else DEMO_ERR })
        }
        val missing = required.count { !implemented.contains(it) }
        text("Missing: $missing / ${required.size}", { color = if (missing == 0) DEMO_OK else DEMO_ERR })
    }
}