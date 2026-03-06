package org.dreamfinity.dsgl.mc1710.demo.sections

import org.dreamfinity.dsgl.core.UiScope
import org.dreamfinity.dsgl.mc1710.demo.ShowcaseWindow
import org.dreamfinity.dsgl.mc1710.demo.support.CapabilityChecklistCatalog
import org.dreamfinity.dsgl.mc1710.demo.support.CapabilityGroup
import org.dreamfinity.dsgl.mc1710.demo.support.DEMO_MUTED
import org.dreamfinity.dsgl.mc1710.demo.support.DEMO_OK

fun UiScope.overviewSection(window: ShowcaseWindow, contentWidth: Int, contentHeight: Int) {
    div({
        key = "section.overview"
        width = contentWidth
        height = contentHeight
        gap = 4
        asFlexColumn()
    }) {
        text("Use left navigation to open each capability group.")
        text("Event Inspector and Checklist stay visible while switching sections.", {
            color = DEMO_MUTED
        })
        text("Stylesheets: <gameDir>/dsgl/styles/*.dss (manual reload).", {
            color = DEMO_MUTED
        })
        text("Open the Stylesheets section for full selector/pseudo-state/variables showcase.", {
            color = DEMO_MUTED
        })
        text("Press F6 to force stylesheet reload and rebuild after file edits.", {
            color = DEMO_MUTED
        })

        text("Manual invalidates: ${window.manualInvalidateCount} (last=${window.lastManualReason})", {
            color = DEMO_MUTED
        })
        text("Auto state rebuild counter: ${window.autoRebuildCounter}", {
            color = DEMO_MUTED
        })

        div({ gap = 4; asFlexRow() }) {
            button("Auto state +1", {
                width = 90
                onMouseClick = {
                    window.bumpAutoRebuildCounter()
                    window.appendInfo("Overview: state-driven rebuild")
                }
            })
            button("Manual invalidate", {
                width = 96
                onMouseClick = {
                    window.requestManualInvalidate("overview button")
                    window.appendInfo("Overview: manual invalidate requested")
                }
            })
        }

        text("Checklist groups", { color = DEMO_OK })
        CapabilityGroup.entries.forEach { group ->
            val required = CapabilityChecklistCatalog.required.filter { it.group == group }.size
            val implemented = window.implementedCapabilities.count { it.group == group }
            val ok = implemented == required
            text("${group.title}: $implemented/$required", { color = if (ok) DEMO_OK else 0xFFE06A6A.toInt() }
            )
        }
    }
}
