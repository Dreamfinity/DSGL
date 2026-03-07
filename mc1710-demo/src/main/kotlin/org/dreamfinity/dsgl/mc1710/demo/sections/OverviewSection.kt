package org.dreamfinity.dsgl.mc1710.demo.sections

import org.dreamfinity.dsgl.core.UiScope
import org.dreamfinity.dsgl.core.style.Display
import org.dreamfinity.dsgl.core.style.FlexDirection
import org.dreamfinity.dsgl.mc1710.demo.ShowcaseWindow
import org.dreamfinity.dsgl.mc1710.demo.support.CapabilityChecklistCatalog
import org.dreamfinity.dsgl.mc1710.demo.support.CapabilityGroup
import org.dreamfinity.dsgl.mc1710.demo.support.DEMO_MUTED
import org.dreamfinity.dsgl.mc1710.demo.support.DEMO_OK

fun UiScope.overviewSection(window: ShowcaseWindow, contentWidth: Int, contentHeight: Int) {
    div({
        key = "section.overview"
        style = { width = contentWidth.px
            height = contentHeight.px
            gap = 4.px

            display = Display.Flex
            flexDirection = FlexDirection.Column }
    }) {
        text("Use left navigation to open each capability group.")
        text("Event Inspector and Checklist stay visible while switching sections.", {
            style = { color = DEMO_MUTED }
        })
        text("Stylesheets: <gameDir>/dsgl/styles/*.dss (manual reload).", {
            style = { color = DEMO_MUTED }
        })
        text("Open the Stylesheets section for full selector/pseudo-state/variables showcase.", {
            style = { color = DEMO_MUTED }
        })
        text("Press F6 to force stylesheet reload and rebuild after file edits.", {
            style = { color = DEMO_MUTED }
        })

        text("Manual invalidates: ${window.manualInvalidateCount} (last=${window.lastManualReason})", {
            style = { color = DEMO_MUTED }
        })
        text("Auto state rebuild counter: ${window.autoRebuildCounter}", {
            style = { color = DEMO_MUTED }
        })

        div({
            style = {
                gap = 4.px
                display = Display.Flex
                flexDirection = FlexDirection.Row
            }
        }) {
            button("Auto state +1", {
                style = { width = 90.px }
                onMouseClick = {
                    window.bumpAutoRebuildCounter()
                    window.appendInfo("Overview: state-driven rebuild")
                }
            })
            button("Manual invalidate", {
                style = { width = 96.px }
                onMouseClick = {
                    window.requestManualInvalidate("overview button")
                    window.appendInfo("Overview: manual invalidate requested")
                }
            })
        }

        text("Checklist groups", { style = { color = DEMO_OK } })
        CapabilityGroup.entries.forEach { group ->
            val required = CapabilityChecklistCatalog.required.filter { it.group == group }.size
            val implemented = window.implementedCapabilities.count { it.group == group }
            val ok = implemented == required
            text(
                "${group.title}: $implemented/$required",
                { style = { color = if (ok) DEMO_OK else 0xFFE06A6A.toInt() } }
            )
        }
    }
}


