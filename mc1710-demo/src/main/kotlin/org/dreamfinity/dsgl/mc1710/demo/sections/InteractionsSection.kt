package org.dreamfinity.dsgl.mc1710.demo.sections

import org.dreamfinity.dsgl.core.UiScope
import org.dreamfinity.dsgl.core.dom.elements.InputType
import org.dreamfinity.dsgl.core.event.KeyCodes
import org.dreamfinity.dsgl.core.style.Display
import org.dreamfinity.dsgl.core.style.FlexDirection
import org.dreamfinity.dsgl.mc1710.demo.ShowcaseWindow
import org.dreamfinity.dsgl.mc1710.demo.support.DEMO_MUTED
import org.dreamfinity.dsgl.mc1710.demo.support.DEMO_SURFACE_ALT

fun UiScope.interactionsSection(window: ShowcaseWindow, contentWidth: Int, contentHeight: Int) {
    div({
        key = "section.interactions"
        style = {
            width = contentWidth.px
            height = contentHeight.px
            gap = 4.px
            display = Display.Flex
            flexDirection = FlexDirection.Column
        }
    }) {
        text("Mouse zone wires all mouse hooks. Key fields wire key hooks.")
        text("Event Inspector will show bubbling and cancellation details.", {
            style = { color = DEMO_MUTED }
        })

        div({
            key = "interactions.mouse.zone"
            onMouseEnter = { event ->
                if (window.markInteractionZoneEntered()) {
                    window.mouseEnterCount += 1
                    window.logHook("onMouseEnter", event)
                }
            }
            onMouseLeave = { event ->
                if (window.markInteractionZoneLeft()) {
                    window.mouseLeaveCount += 1
                    window.logHook("onMouseLeave", event)
                }
            }
            onMouseOver = { event ->
                window.mouseOverCount += 1
                if (window.sampledMouseOverEvent()) {
                    window.logHook("onMouseOver", event, "sampled")
                }
            }
            onMouseMove = { event ->
                window.mouseMoveCount += 1
                if (window.sampledMouseMoveEvent()) {
                    window.logHook("onMouseMove", event, "sampled")
                }
            }
            onMouseDown = { event ->
                window.mouseDownCount += 1
                window.logHook("onMouseDown", event)
            }
            onMouseUp = { event ->
                window.mouseUpCount += 1
                window.logHook("onMouseUp", event)
            }
            onMouseClick = { event ->
                window.mouseClickCount += 1
                window.logHook("onMouseClick", event)
            }
            onMouseDrag = { event ->
                window.mouseDragCount += 1
                window.logHook("onMouseDrag", event)
            }
            onMouseWheel = { event ->
                window.mouseWheelCount += 1
                window.logHook("onMouseWheel", event)
            }
            style = {
                width = (contentWidth - 8).px
                height = 52.px
                padding = 4.px
                backgroundColor = DEMO_SURFACE_ALT
                border(1.px, 0xFF6E7A89.toInt())
            }
        }) {
            text("Move, click, drag and wheel here")
            text(
                "E${window.mouseEnterCount} L${window.mouseLeaveCount} O${window.mouseOverCount} M${window.mouseMoveCount} D${window.mouseDownCount}/${window.mouseUpCount} C${window.mouseClickCount} G${window.mouseDragCount} W${window.mouseWheelCount}",
                { style = { color = DEMO_MUTED } }
            )
        }

        div({
            style = {
                gap = 4.px
                display = Display.Flex
                flexDirection = FlexDirection.Row
            }
        }) {
            input(
                InputType.Text(placeholder = "onKeyDown/onKeyUp"), {
                    key = "interactions.key.downUp"
                    style = { width = ((contentWidth / 2) - 6).px }
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
                InputType.Text(placeholder = "onKeyPressed/onKeyReleased"), {
                    key = "interactions.key.aliases"
                    style = { width = ((contentWidth / 2) - 6).px }
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
            "Key counters: down=${window.keyDownCount} up=${window.keyUpCount} pressed=${window.keyPressedCount} released=${window.keyReleasedCount} enter=${window.enterActionCount}",
            { style = { color = DEMO_MUTED } }
        )

        div({
            style = {
                gap = 4.px
                display = Display.Flex
                flexDirection = FlexDirection.Row
            }
        }) {
            button(
                if (window.cancellationEnabled) "Cancel child click: ON" else "Cancel child click: OFF",
                {
                    style = { width = 126.px }
                    onMouseClick = {
                        window.cancellationEnabled = !window.cancellationEnabled
                        window.appendInfo("Interactions: cancellation=${window.cancellationEnabled}")
                    }
                }
            )
            text(
                "Parent=${window.cancellationParentHits} Child=${window.cancellationChildHits}",
                { style = { color = DEMO_MUTED } }
            )
        }

        div({
            key = "interactions.bubble.parent"
            onMouseClick = { event ->
                window.cancellationParentHits += 1
                window.logHook("parent.onMouseClick", event)
            }
            style = {
                width = (contentWidth - 8).px
                padding = 3.px
                backgroundColor = 0xFF353D46.toInt()
                border(1.px, 0xFF708090.toInt())
            }
        }) {
            text("Parent click area")
            div({
                key = "interactions.bubble.child"
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
                }
                style = {
                    width = 118.px
                    padding = 3.px
                    backgroundColor = 0xFF4D5560.toInt()
                    border(1.px, 0xFF9AA5B1.toInt())
                }
            }) {
                text("Child area")
            }
        }
    }
}

