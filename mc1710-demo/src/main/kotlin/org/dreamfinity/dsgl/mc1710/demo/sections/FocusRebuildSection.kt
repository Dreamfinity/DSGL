package org.dreamfinity.dsgl.mc1710.demo.sections

import org.dreamfinity.dsgl.core.UiScope
import org.dreamfinity.dsgl.core.dom.elements.InputType
import org.dreamfinity.dsgl.core.event.KeyCodes
import org.dreamfinity.dsgl.mc1710.demo.ShowcaseWindow
import org.dreamfinity.dsgl.mc1710.demo.support.DEMO_MUTED

fun UiScope.focusRebuildSection(window: ShowcaseWindow, contentWidth: Int, contentHeight: Int) {
    div({
        key = "section.focusRebuild"
        width = contentWidth
        height = contentHeight
        gap = 4
        asFlexColumn()
    }
    ) {
        text("Stable key focus test: focus first field, press Enter to rebuild, keep typing.")
        text("Unstable key field changes key version and demonstrates focus/key instability.", {
            color = DEMO_MUTED
        })

        text(
            "renderPasses=${window.renderPasses} autoState=${window.autoRebuildCounter} manualInvalidates=${window.manualInvalidateCount}",
            { color = DEMO_MUTED }
        )
        text(
            "stableEnterRebuilds=${window.focusStableEnterRebuilds} unstableKeyVersion=${window.focusKeyVersion}",
            { color = DEMO_MUTED }
        )

        input(
            InputType.Text(
                value = window.focusStableValue,
                placeholder = "Stable key input (press Enter to rebuild)"
            ),
            {
                key = "focus.stable.input"
                width = contentWidth - 10
                onKeyDown = { event ->
                    if (event.keyCode == KeyCodes.ENTER) {
                        window.focusStableEnterRebuilds += 1
                        window.requestManualInvalidate("stable input Enter")
                        window.logHook("focus.stable.onKeyDown", event, "manual rebuild")
                    } else {
                        window.focusStableValue =
                            window.applyTextMutation(window.focusStableValue, event, maxLength = 28)
                        window.logHook("focus.stable.onKeyDown", event)
                    }
                }
                onKeyUp = { event ->
                    window.logHook("focus.stable.onKeyUp", event)
                }
            }
        )

        input(
            InputType.Text(
                value = window.focusUnstableValue,
                placeholder = "Unstable key input"
            ),
            {
                key = "focus.unstable.input.${window.focusKeyVersion}"
                width = contentWidth - 10
                onKeyDown = { event ->
                    window.focusUnstableValue =
                        window.applyTextMutation(window.focusUnstableValue, event, maxLength = 28)
                    window.logHook("focus.unstable.onKeyDown", event)
                }
            }
        )

        div({ gap = 4; asFlexRow() }) {
            button("Auto state +1", {
                width = 80
                onMouseClick = {
                    window.bumpAutoRebuildCounter()
                    window.appendInfo("Focus/Rebuild: state counter increment")
                }
            })
            button("Manual invalidate", {
                width = 96
                onMouseClick = {
                    window.requestManualInvalidate("focus section button")
                    window.appendInfo("Focus/Rebuild: manual invalidate button")
                }
            })
        }

        div({ gap = 4; asFlexRow() }) {
            button("Bump unstable key", {
                width = 94
                onMouseClick = {
                    window.bumpFocusVersion()
                    window.requestManualInvalidate("unstable key version changed")
                    window.appendInfo("Focus/Rebuild: unstable key version=${window.focusKeyVersion}")
                }
            })
            text(
                "lastManualReason=${window.lastManualReason}",
                { color = DEMO_MUTED }
            )
        }
    }
}