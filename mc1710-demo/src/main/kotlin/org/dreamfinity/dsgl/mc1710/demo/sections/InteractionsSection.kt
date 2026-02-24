package org.dreamfinity.dsgl.mc1710.demo.sections

import org.dreamfinity.dsgl.core.*
import org.dreamfinity.dsgl.core.dom.elements.InputType
import org.dreamfinity.dsgl.core.event.KeyCodes
import org.dreamfinity.dsgl.mc1710.demo.ShowcaseWindow
import org.dreamfinity.dsgl.mc1710.demo.support.DEMO_MUTED
import org.dreamfinity.dsgl.mc1710.demo.support.DEMO_SURFACE_ALT

fun UiScope.renderInteractionsSection(window: ShowcaseWindow, contentWidth: Int, contentHeight: Int) {
    div(
        ComponentProps(
            key = "section.interactions",
            width = contentWidth,
            height = contentHeight,
            gap = 4
        ).asFlexColumn()
    ) {
        text(TextProps("Mouse zone wires all mouse hooks. Key fields wire key hooks."))
        text(TextProps("Event Inspector will show bubbling and cancellation details.").apply {
            color = DEMO_MUTED
        })

        div(
            ComponentProps(
                key = "interactions.mouse.zone",
                width = contentWidth - 8,
                height = 52,
                padding = 4,
                backgroundColor = DEMO_SURFACE_ALT,
                onMouseEnter = { event ->
                    if (window.markInteractionZoneEntered()) {
                        window.mouseEnterCount += 1
                        window.logHook("onMouseEnter", event)
                    }
                },
                onMouseLeave = { event ->
                    if (window.markInteractionZoneLeft()) {
                        window.mouseLeaveCount += 1
                        window.logHook("onMouseLeave", event)
                    }
                },
                onMouseOver = { event ->
                    window.mouseOverCount += 1
                    if (window.sampledMouseOverEvent()) {
                        window.logHook("onMouseOver", event, "sampled")
                    }
                },
                onMouseMove = { event ->
                    window.mouseMoveCount += 1
                    if (window.sampledMouseMoveEvent()) {
                        window.logHook("onMouseMove", event, "sampled")
                    }
                },
                onMouseDown = { event ->
                    window.mouseDownCount += 1
                    window.logHook("onMouseDown", event)
                },
                onMouseUp = { event ->
                    window.mouseUpCount += 1
                    window.logHook("onMouseUp", event)
                },
                onMouseClick = { event ->
                    window.mouseClickCount += 1
                    window.logHook("onMouseClick", event)
                },
                onMouseDrag = { event ->
                    window.mouseDragCount += 1
                    window.logHook("onMouseDrag", event)
                },
                onMouseWheel = { event ->
                    window.mouseWheelCount += 1
                    window.logHook("onMouseWheel", event)
                },
                style = {
                    border(1, 0xFF6E7A89.toInt())
                }
            )
        ) {
            text(TextProps("Move, click, drag and wheel here"))
            text(
                TextProps {
                    "E${window.mouseEnterCount} L${window.mouseLeaveCount} O${window.mouseOverCount} M${window.mouseMoveCount} D${window.mouseDownCount}/${window.mouseUpCount} C${window.mouseClickCount} G${window.mouseDragCount} W${window.mouseWheelCount}"
                }.apply { color = DEMO_MUTED }
            )
        }

        div(ComponentProps(gap = 4).asFlexRow()) {
            input(
                InputProps(InputType.Text(placeholder = "onKeyDown/onKeyUp")).apply {
                    key = "interactions.key.downUp"
                    width = (contentWidth / 2) - 6
                    onKeyDown = { event ->
                        window.keyDownCount += 1
                        if (event.keyCode == KeyCodes.ENTER) {
                            window.enterActionCount += 1
                            window.logHook("onKeyDown", event, "enterAction")
                        } else {
                            window.logHook("onKeyDown", event)
                        }
                    }
                    onKeyUp = { event ->
                        window.keyUpCount += 1
                        window.logHook("onKeyUp", event)
                    }
                }
            )
            input(
                InputProps(InputType.Text(placeholder = "onKeyPressed/onKeyReleased")).apply {
                    key = "interactions.key.aliases"
                    width = (contentWidth / 2) - 6
                    onKeyPressed = { event ->
                        window.keyPressedCount += 1
                        window.logHook("onKeyPressed", event)
                    }
                    onKeyReleased = { event ->
                        window.keyReleasedCount += 1
                        window.logHook("onKeyReleased", event)
                    }
                }
            )
        }

        text(
            TextProps {
                "Key counters: down=${window.keyDownCount} up=${window.keyUpCount} pressed=${window.keyPressedCount} released=${window.keyReleasedCount} enter=${window.enterActionCount}"
            }.apply { color = DEMO_MUTED }
        )

        div(ComponentProps(gap = 4).asFlexRow()) {
            button(
                ButtonProps(if (window.cancellationEnabled) "Cancel child click: ON" else "Cancel child click: OFF").apply {
                    width = 126
                    onMouseClick = {
                        window.cancellationEnabled = !window.cancellationEnabled
                        window.appendInfo("Interactions: cancellation=${window.cancellationEnabled}")
                    }
                }
            )
            text(
                TextProps {
                    "Parent=${window.cancellationParentHits} Child=${window.cancellationChildHits}"
                }.apply { color = DEMO_MUTED }
            )
        }

        div(
            ComponentProps(
                key = "interactions.bubble.parent",
                width = contentWidth - 8,
                padding = 3,
                backgroundColor = 0xFF353D46.toInt(),
                onMouseClick = { event ->
                    window.cancellationParentHits += 1
                    window.logHook("parent.onMouseClick", event)
                },
                style = { border(1, 0xFF708090.toInt()) }
            )
        ) {
            text(TextProps("Parent click area"))
            div(
                ComponentProps(
                    key = "interactions.bubble.child",
                    width = 118,
                    padding = 3,
                    backgroundColor = 0xFF4D5560.toInt(),
                    onMouseClick = { event ->
                        window.cancellationChildHits += 1
                        if (window.cancellationEnabled) {
                            event.cancelled = true
                        }
                        window.logHook(
                            "child.onMouseClick",
                            event,
                            if (window.cancellationEnabled) "cancelled=true" else "cancelled=false"
                        )
                    },
                    style = { border(1, 0xFF9AA5B1.toInt()) }
                )
            ) {
                text(TextProps("Child area"))
            }
        }
    }
}