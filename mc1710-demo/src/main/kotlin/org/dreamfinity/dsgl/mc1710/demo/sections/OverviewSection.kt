package org.dreamfinity.dsgl.mc1710.demo.sections

import org.dreamfinity.dsgl.core.ButtonProps
import org.dreamfinity.dsgl.core.ComponentProps
import org.dreamfinity.dsgl.core.TextProps
import org.dreamfinity.dsgl.core.UiScope
import org.dreamfinity.dsgl.mc1710.demo.ShowcaseWindow
import org.dreamfinity.dsgl.mc1710.demo.support.CapabilityChecklistCatalog
import org.dreamfinity.dsgl.mc1710.demo.support.CapabilityGroup
import org.dreamfinity.dsgl.mc1710.demo.support.DEMO_MUTED
import org.dreamfinity.dsgl.mc1710.demo.support.DEMO_OK

fun UiScope.renderOverviewSection(window: ShowcaseWindow, contentWidth: Int, contentHeight: Int) {
    div(
        ComponentProps(
            key = "section.overview",
            width = contentWidth,
            height = contentHeight,
            gap = 4
        ).asFlexColumn()
    ) {
        text(TextProps("Use left navigation to open each capability group."))
        text(TextProps("Event Inspector and Checklist stay visible while switching sections.").apply {
            color = DEMO_MUTED
        })
        text(TextProps("Stylesheets: <gameDir>/dsgl/styles/*.dss (manual reload).").apply {
            color = DEMO_MUTED
        })
        text(TextProps("Open the Stylesheets section for full selector/pseudo-state/variables showcase.").apply {
            color = DEMO_MUTED
        })
        text(TextProps("Press F6 to force stylesheet reload and rebuild after file edits.").apply {
            color = DEMO_MUTED
        })

        text(
            value = {
                "Manual invalidates: ${window.manualInvalidateCount} (last=${window.lastManualReason})"
            }
        ) {
            color = DEMO_MUTED
        }
        text(
            value = {
                "Auto state rebuild counter: ${window.autoRebuildCounter}"
            }
        ) {
            color = DEMO_MUTED
        }

        div(ComponentProps(gap = 4).asFlexRow()) {
            button(
                ButtonProps("Auto state +1").apply {
                    width = 90
                    onMouseClick = {
                        window.bumpAutoRebuildCounter()
                        window.appendInfo("Overview: state-driven rebuild")
                    }
                }
            )
            button(
                ButtonProps("Manual invalidate").apply {
                    width = 96
                    onMouseClick = {
                        window.requestManualInvalidate("overview button")
                        window.appendInfo("Overview: manual invalidate requested")
                    }
                }
            )
        }

        text(TextProps("Checklist groups").apply { color = DEMO_OK })
        CapabilityGroup.values().forEach { group ->
            val required = CapabilityChecklistCatalog.required.filter { it.group == group }.size
            val implemented = window.implementedCapabilities.count { it.group == group }
            val ok = implemented == required
            text(
                TextProps("${group.title}: $implemented/$required")
                    .apply { color = if (ok) DEMO_OK else 0xFFE06A6A.toInt() }
            )
        }
    }
}
