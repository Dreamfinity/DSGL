package org.dreamfinity.dsgl.mc1710.demo.sections

import org.dreamfinity.dsgl.core.UiScope
import org.dreamfinity.dsgl.core.dom.elements.InputType
import org.dreamfinity.dsgl.core.event.Event
import org.dreamfinity.dsgl.core.event.KeyCodes
import org.dreamfinity.dsgl.core.event.KeyInput
import org.dreamfinity.dsgl.core.event.KeyModifiers
import org.dreamfinity.dsgl.core.event.KeyboardKeyDownEvent
import org.dreamfinity.dsgl.core.style.Display
import org.dreamfinity.dsgl.core.style.FlexDirection
import org.dreamfinity.dsgl.core.useState
import org.dreamfinity.dsgl.mc1710.demo.support.DEMO_MUTED

fun UiScope.focusRebuildSection(
    renderPasses: Int,
    onManualInvalidate: (String) -> Unit,
    onInfo: (String) -> Unit,
    onLogHook: (String, Event, String?) -> Unit
) {
    var focusStableValue by useState("")
    var focusUnstableValue by useState("")
    var focusStableEnterRebuilds by useState(0)
    var focusKeyVersion by useState(0)
    var autoRebuildCounter by useState(0)
    var manualInvalidateCount by useState(0)
    var lastManualReason by useState("none")

    fun requestLocalManualInvalidate(reason: String) {
        manualInvalidateCount += 1
        lastManualReason = reason
        onManualInvalidate(reason)
    }

    div({
        key = "section.focusRebuild"
        style = {
            gap = 4.px
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
            "renderPasses=$renderPasses autoState=$autoRebuildCounter manualInvalidates=$manualInvalidateCount",
            { style = { color = DEMO_MUTED } }
        )
        text(
            "stableEnterRebuilds=$focusStableEnterRebuilds unstableKeyVersion=$focusKeyVersion",
            { style = { color = DEMO_MUTED } }
        )

        input(
            InputType.Text(
                value = focusStableValue,
                placeholder = "Stable key input (press Enter to rebuild)"
            ),
            {
                key = "focus.stable.input"
                style = { width = 100.percent }
                onKeyDown = { event ->
                    if (event.keyCode == KeyCodes.ENTER) {
                        focusStableEnterRebuilds += 1
                        requestLocalManualInvalidate("stable input Enter")
                        onLogHook("focus.stable.onKeyDown", event, "manual rebuild")
                    } else {
                        focusStableValue = applyTextMutation(focusStableValue, event, maxLength = 28)
                        onLogHook("focus.stable.onKeyDown", event, null)
                    }
                }
                onKeyUp = { event ->
                    onLogHook("focus.stable.onKeyUp", event, null)
                }
            }
        )

        input(
            InputType.Text(
                value = focusUnstableValue,
                placeholder = "Unstable key input"
            ),
            {
                key = "focus.unstable.input.$focusKeyVersion"
                style = { width = 100.percent }
                onKeyDown = { event ->
                    focusUnstableValue = applyTextMutation(focusUnstableValue, event, maxLength = 28)
                    onLogHook("focus.unstable.onKeyDown", event, null)
                }
            }
        )

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
                    onInfo("Focus/Rebuild: state counter increment")
                }
            })
            button("Manual invalidate", {
                onMouseClick = {
                    requestLocalManualInvalidate("focus section button")
                    onInfo("Focus/Rebuild: manual invalidate button")
                }
            })
        }

        div({
            style = {
                gap = 4.px
                display = Display.Flex
                flexDirection = FlexDirection.Row
            }
        }) {
            button("Bump unstable key", {
                onMouseClick = {
                    focusKeyVersion += 1
                    requestLocalManualInvalidate("unstable key version changed")
                    onInfo("Focus/Rebuild: unstable key version=$focusKeyVersion")
                }
            })
            text(
                "lastManualReason=$lastManualReason",
                { style = { color = DEMO_MUTED } }
            )
        }
    }
}

private fun applyTextMutation(
    current: String,
    event: KeyboardKeyDownEvent,
    allowedChars: String? = null,
    maxLength: Int? = null
): String {
    if (event.keyCode == KeyCodes.BACKSPACE) {
        if (current.isEmpty()) return current
        return current.dropLast(1)
    }

    var ch = event.keyChar
    if (ch < ' ' || ch.code == 127) return current
    ch = KeyInput.applyShift(ch, KeyModifiers.shiftDown)
    if (allowedChars != null && !allowedChars.contains(ch)) return current
    val next = current + ch
    if (maxLength != null && next.length > maxLength) return current
    return next
}

