package org.dreamfinity.dsgl.mc1710.demo.support

import org.dreamfinity.dsgl.core.ButtonProps
import org.dreamfinity.dsgl.core.ComponentProps
import org.dreamfinity.dsgl.core.TextProps
import org.dreamfinity.dsgl.core.UiScope
import org.dreamfinity.dsgl.mc1710.demo.ShowcaseWindow

fun UiScope.renderEventInspectorPanel(window: ShowcaseWindow, width: Int, height: Int) {
    div(
        panelProps(
            key = "panel.eventInspector",
            width = width,
            height = height,
            backgroundColor = DEMO_SURFACE_ALT
        )
    ) {
        div(ComponentProps().asFlexRow()) {
            text(TextProps("Event Inspector"))
            button(
                ButtonProps("Clear").apply {
                    this.width = 44
                    onMouseClick = { window.clearEventLogs() }
                }
            )
        }
        text(
            TextProps {
                "Stored: ${window.eventLogs.size}/${window.maxEventLogs}"
            }.apply {
                color = DEMO_MUTED
            }
        )
        if (window.eventLogs.isEmpty()) {
            text(
                TextProps("No events yet. Interact with any demo area.")
                    .apply { color = DEMO_MUTED }
            )
        } else {
            window.eventLogs
                .take(window.visibleEventLines)
                .forEach { entry ->
                    text(
                        TextProps("#${entry.sequence} ${entry.line}")
                            .apply { color = entry.color }
                    )
                }
        }
    }
}

fun UiScope.renderChecklistPanel(window: ShowcaseWindow, width: Int, height: Int) {
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

    div(
        panelProps(
            key = "panel.capabilityChecklist",
            width = width,
            height = height,
            backgroundColor = DEMO_SURFACE_ALT
        )
    ) {
        text(TextProps("Capability Checklist"))
        div(ComponentProps().asFlexRow()) {
            button(
                ButtonProps("<").apply {
                    this.width = 22
                    onMouseClick = { window.moveChecklistPage(-1) }
                }
            )
            text(
                TextProps {
                    "Page ${window.checklistPage + 1}/$pageCount"
                }.apply { color = DEMO_MUTED }
            )
            button(
                ButtonProps(">").apply {
                    this.width = 22
                    onMouseClick = { window.moveChecklistPage(1) }
                }
            )
        }
        pageItems.forEach { capability ->
            val ok = implemented.contains(capability)
            text(
                TextProps("${if (ok) "[OK]" else "[MISS]"} ${capability.label}")
                    .apply { color = if (ok) DEMO_OK else DEMO_ERR }
            )
        }
        val missing = required.count { !implemented.contains(it) }
        text(
            TextProps {
                "Missing: $missing / ${required.size}"
            }.apply {
                color = if (missing == 0) DEMO_OK else DEMO_ERR
            }
        )
    }
}