package org.dreamfinity.dsgl.mc1710.demo.sections

import org.dreamfinity.dsgl.core.UiScope
import org.dreamfinity.dsgl.core.style.Display
import org.dreamfinity.dsgl.core.style.FlexDirection
import org.dreamfinity.dsgl.core.hooks.useState
import org.dreamfinity.dsgl.mc1710.demo.support.CapabilityId
import org.dreamfinity.dsgl.mc1710.demo.support.CapabilityChecklistCatalog
import org.dreamfinity.dsgl.mc1710.demo.support.CapabilityGroup
import org.dreamfinity.dsgl.mc1710.demo.support.DEMO_MUTED
import org.dreamfinity.dsgl.mc1710.demo.support.DEMO_OK

fun UiScope.overviewSection(
    implementedCapabilities: Set<CapabilityId>,
    onManualInvalidate: (String) -> Unit,
    onInfo: (String) -> Unit
) {
    var manualInvalidateCount by useState(0)
    var lastManualReason by useState("none")
    var autoRebuildCounter by useState(0)

    div({
        key = "section.overview"
        style = {
            gap = 4.px
            display = Display.Flex
            flexDirection = FlexDirection.Column
        }
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
        text("Press F10 to toggle the draggable overlay panel panel demo (text + button + image).", {
            style = { color = DEMO_MUTED }
        })

        text("Manual invalidates: $manualInvalidateCount (last=$lastManualReason)", {
            style = { color = DEMO_MUTED }
        })
        text("Auto state rebuild counter: $autoRebuildCounter", {
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
                onMouseClick = {
                    autoRebuildCounter += 1
                    onInfo("Overview: state-driven rebuild")
                }
            })
            button("Manual invalidate", {
                onMouseClick = {
                    val reason = "overview button"
                    manualInvalidateCount += 1
                    lastManualReason = reason
                    onManualInvalidate(reason)
                    onInfo("Overview: manual invalidate requested")
                }
            })
        }

        text("Checklist groups", { style = { color = DEMO_OK } })
        CapabilityGroup.entries.forEach { group ->
            val required = CapabilityChecklistCatalog.required.filter { it.group == group }.size
            val implemented = implementedCapabilities.count { it.group == group }
            val ok = implemented == required
            text(
                "${group.title}: $implemented/$required",
                { style = { color = if (ok) DEMO_OK else 0xFFE06A6A.toInt() } }
            )
        }
    }
}

