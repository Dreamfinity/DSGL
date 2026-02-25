package org.dreamfinity.dsgl.mc1710.demo.sections

import org.dreamfinity.dsgl.core.ButtonProps
import org.dreamfinity.dsgl.core.ComponentProps
import org.dreamfinity.dsgl.core.TextProps
import org.dreamfinity.dsgl.core.InputProps
import org.dreamfinity.dsgl.core.UiScope
import org.dreamfinity.dsgl.core.dom.elements.InputType
import org.dreamfinity.dsgl.core.event.KeyCodes
import org.dreamfinity.dsgl.mc1710.demo.ShowcaseWindow
import org.dreamfinity.dsgl.mc1710.demo.support.DEMO_MUTED

fun UiScope.renderFocusRebuildSection(window: ShowcaseWindow, contentWidth: Int, contentHeight: Int) {
    div(
        ComponentProps(
            key = "section.focusRebuild",
            width = contentWidth,
            height = contentHeight,
            gap = 4
        ).asFlexColumn()
    ) {
        text(TextProps("Stable key focus test: focus first field, press Enter to rebuild, keep typing."))
        text(TextProps("Unstable key field changes key version and demonstrates focus/key instability.").apply {
            color = DEMO_MUTED
        })

        text(
            TextProps {
                "renderPasses=${window.renderPasses} autoState=${window.autoRebuildCounter} manualInvalidates=${window.manualInvalidateCount}"
            }.apply { color = DEMO_MUTED }
        )
        text(
            TextProps {
                "stableEnterRebuilds=${window.focusStableEnterRebuilds} unstableKeyVersion=${window.focusKeyVersion}"
            }.apply { color = DEMO_MUTED }
        )

        input(
            InputProps(
                InputType.Text(
                    value = window.focusStableValue,
                    placeholder = "Stable key input (press Enter to rebuild)"
                )
            ).apply {
                key = "focus.stable.input"
                width = contentWidth - 10
                onKeyDown = { event ->
                    if (event.keyCode == KeyCodes.ENTER) {
                        window.focusStableEnterRebuilds += 1
                        window.requestManualInvalidate("stable input Enter")
                        window.logHook("focus.stable.onKeyDown", event, "manual rebuild")
                    } else {
                        window.focusStableValue = window.applyTextMutation(window.focusStableValue, event, maxLength = 28)
                        window.logHook("focus.stable.onKeyDown", event)
                    }
                }
                onKeyUp = { event ->
                    window.logHook("focus.stable.onKeyUp", event)
                }
            }
        )

        input(
            InputProps(
                InputType.Text(
                    value = window.focusUnstableValue,
                    placeholder = "Unstable key input"
                )
            ).apply {
                key = "focus.unstable.input.${window.focusKeyVersion}"
                width = contentWidth - 10
                onKeyDown = { event ->
                    window.focusUnstableValue = window.applyTextMutation(window.focusUnstableValue, event, maxLength = 28)
                    window.logHook("focus.unstable.onKeyDown", event)
                }
            }
        )

        div(ComponentProps(gap = 4).asFlexRow()) {
            button(
                ButtonProps("Auto state +1").apply {
                    width = 80
                    onMouseClick = {
                        window.bumpAutoRebuildCounter()
                        window.appendInfo("Focus/Rebuild: state counter increment")
                    }
                }
            )
            button(
                ButtonProps("Manual invalidate").apply {
                    width = 96
                    onMouseClick = {
                        window.requestManualInvalidate("focus section button")
                        window.appendInfo("Focus/Rebuild: manual invalidate button")
                    }
                }
            )
        }

        div(ComponentProps(gap = 4).asFlexRow()) {
            button(
                ButtonProps("Bump unstable key").apply {
                    width = 94
                    onMouseClick = {
                        window.bumpFocusVersion()
                        window.requestManualInvalidate("unstable key version changed")
                        window.appendInfo("Focus/Rebuild: unstable key version=${window.focusKeyVersion}")
                    }
                }
            )
            text(
                TextProps {
                    "lastManualReason=${window.lastManualReason}"
                }.apply { color = DEMO_MUTED }
            )
        }
    }
}