package org.dreamfinity.dsgl.mc1710.demo.sections

import org.dreamfinity.dsgl.core.UiScope
import org.dreamfinity.dsgl.core.dom.elements.InputType
import org.dreamfinity.dsgl.core.event.KeyCodes
import org.dreamfinity.dsgl.core.style.Display
import org.dreamfinity.dsgl.core.style.FlexDirection
import org.dreamfinity.dsgl.mc1710.demo.ShowcaseWindow
import org.dreamfinity.dsgl.mc1710.demo.support.DEMO_MUTED

fun UiScope.focusRebuildSection(window: ShowcaseWindow, contentWidth: Int, contentHeight: Int) {
    div({
        key = "section.focusRebuild"
        style = {
            width = contentWidth
            height = contentHeight
            gap = 4
            display = Display.Flex
            flexDirection = FlexDirection.Column
        }
    }
    ) {
        text("Stable key focus test: focus first field, press Enter to rebuild, keep typing.")
        text("Unstable key field changes key version and demonstrates focus/key instability.", {
            style = { color = DEMO_MUTED }
        })

        text(
            "renderPasses=${window.renderPasses} autoState=${window.autoRebuildCounter} manualInvalidates=${window.manualInvalidateCount}",
            { style = { color = DEMO_MUTED } }
        )
        text(
            "stableEnterRebuilds=${window.focusStableEnterRebuilds} unstableKeyVersion=${window.focusKeyVersion}",
            { style = { color = DEMO_MUTED } }
        )

        input(
            InputType.Text(
                value = window.focusStableValue,
                placeholder = "Stable key input (press Enter to rebuild)"
            ),
            {
                key = "focus.stable.input"
                style = { width = contentWidth - 10 }
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
                style = { width = contentWidth - 10 }
                onKeyDown = { event ->
                    window.focusUnstableValue =
                        window.applyTextMutation(window.focusUnstableValue, event, maxLength = 28)
                    window.logHook("focus.unstable.onKeyDown", event)
                }
            }
        )

        div({
            style = {
                gap = 4
                display = Display.Flex
                flexDirection = FlexDirection.Row
            }
        }) {
            button("Auto state +1", {
                style = { width = 80 }
                onMouseClick = {
                    window.bumpAutoRebuildCounter()
                    window.appendInfo("Focus/Rebuild: state counter increment")
                }
            })
            button("Manual invalidate", {
                style = { width = 96 }
                onMouseClick = {
                    window.requestManualInvalidate("focus section button")
                    window.appendInfo("Focus/Rebuild: manual invalidate button")
                }
            })
        }

        div({
            style = {
                gap = 4
                display = Display.Flex
                flexDirection = FlexDirection.Row
            }
        }) {
            button("Bump unstable key", {
                style = { width = 94 }
                onMouseClick = {
                    window.bumpFocusVersion()
                    window.requestManualInvalidate("unstable key version changed")
                    window.appendInfo("Focus/Rebuild: unstable key version=${window.focusKeyVersion}")
                }
            })
            text(
                "lastManualReason=${window.lastManualReason}",
                { style = { color = DEMO_MUTED } }
            )
        }
    }
}