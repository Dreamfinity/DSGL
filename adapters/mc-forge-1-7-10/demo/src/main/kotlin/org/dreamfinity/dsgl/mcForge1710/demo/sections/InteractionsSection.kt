package org.dreamfinity.dsgl.mcForge1710.demo.sections

import org.dreamfinity.dsgl.core.dom.elements.InputType
import org.dreamfinity.dsgl.core.dsl.*
import org.dreamfinity.dsgl.core.event.Event
import org.dreamfinity.dsgl.core.event.KeyCodes
import org.dreamfinity.dsgl.core.hooks.ref.useRef
import org.dreamfinity.dsgl.core.hooks.useState
import org.dreamfinity.dsgl.core.style.Display
import org.dreamfinity.dsgl.core.style.FlexDirection
import org.dreamfinity.dsgl.mcForge1710.demo.support.DEMO_MUTED
import org.dreamfinity.dsgl.mcForge1710.demo.support.DEMO_SURFACE_ALT

fun UiScope.interactionsSection(onInfo: (String) -> Unit, onLogHook: (String, Event, String?) -> Unit) {
    var mouseEnterCount by useState(0)
    var mouseLeaveCount by useState(0)
    var mouseOverCount by useState(0)
    var mouseMoveCount by useState(0)
    var mouseDownCount by useState(0)
    var mouseUpCount by useState(0)
    var mouseClickCount by useState(0)
    var mouseDragCount by useState(0)
    var mouseWheelCount by useState(0)
    var keyDownCount by useState(0)
    var keyUpCount by useState(0)
    var keyPressedCount by useState(0)
    var keyReleasedCount by useState(0)
    var enterActionCount by useState(0)
    var cancellationEnabled by useState(true)
    var cancellationParentHits by useState(0)
    var cancellationChildHits by useState(0)

    val mouseOverSamplesRef by useRef(0)
    val mouseMoveSamplesRef by useRef(0)
    val interactionZoneInsideRef by useRef(false)

    fun sampledMouseOverEvent(): Boolean {
        val next = (mouseOverSamplesRef.current ?: 0) + 1
        mouseOverSamplesRef.current = next
        return next % 6 == 0
    }

    fun sampledMouseMoveEvent(): Boolean {
        val next = (mouseMoveSamplesRef.current ?: 0) + 1
        mouseMoveSamplesRef.current = next
        return next % 8 == 0
    }

    fun markInteractionZoneEntered(): Boolean {
        val inside = interactionZoneInsideRef.current ?: false
        if (inside) return false
        interactionZoneInsideRef.current = true
        return true
    }

    fun markInteractionZoneLeft(): Boolean {
        val inside = interactionZoneInsideRef.current ?: false
        if (!inside) return false
        interactionZoneInsideRef.current = false
        return true
    }

    div({
        key = "section.interactions"
        style = {
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
                if (markInteractionZoneEntered()) {
                    mouseEnterCount += 1
                    onLogHook("onMouseEnter", event, null)
                }
            }
            onMouseLeave = { event ->
                if (markInteractionZoneLeft()) {
                    mouseLeaveCount += 1
                    onLogHook("onMouseLeave", event, null)
                }
            }
            onMouseOver = { event ->
                mouseOverCount += 1
                if (sampledMouseOverEvent()) {
                    onLogHook("onMouseOver", event, "sampled")
                }
            }
            onMouseMove = { event ->
                mouseMoveCount += 1
                if (sampledMouseMoveEvent()) {
                    onLogHook("onMouseMove", event, "sampled")
                }
            }
            onMouseDown = { event ->
                mouseDownCount += 1
                onLogHook("onMouseDown", event, null)
            }
            onMouseUp = { event ->
                mouseUpCount += 1
                onLogHook("onMouseUp", event, null)
            }
            onMouseClick = { event ->
                mouseClickCount += 1
                onLogHook("onMouseClick", event, null)
            }
            onMouseDrag = { event ->
                mouseDragCount += 1
                onLogHook("onMouseDrag", event, null)
            }
            onMouseWheel = { event ->
                mouseWheelCount += 1
                onLogHook("onMouseWheel", event, null)
            }
            style = {
                width = 100.percent
                height = 52.px
                padding = 4.px
                backgroundColor = DEMO_SURFACE_ALT
                border {
                    width = 1.px
                    color = 0xFF6E7A89.toInt()
                }
            }
        }) {
            text("Move, click, drag and wheel here")
            text(
                "E$mouseEnterCount L$mouseLeaveCount O$mouseOverCount M$mouseMoveCount D$mouseDownCount/$mouseUpCount C$mouseClickCount G$mouseDragCount W$mouseWheelCount",
                { style = { color = DEMO_MUTED } },
            )
        }

        div({
            style = {
                gap = 4.px
                display = Display.Flex
                flexDirection = FlexDirection.Column
            }
        }) {
            input(
                InputType.Text(placeholder = "onKeyDown/onKeyUp"),
                {
                    key = "interactions.key.downUp"
                    style = { width = 100.percent }
                    onKeyDown = { event ->
                        keyDownCount += 1
                        if (event.keyCode == KeyCodes.ENTER) {
                            enterActionCount += 1
                            onLogHook("onKeyDown", event, "enterAction")
                        } else {
                            onLogHook("onKeyDown", event, null)
                        }
                    }
                    onKeyUp = { event ->
                        keyUpCount += 1
                        onLogHook("onKeyUp", event, null)
                    }
                },
            )
            input(
                InputType.Text(placeholder = "onKeyPressed/onKeyReleased"),
                {
                    key = "interactions.key.aliases"
                    style = { width = 100.percent }
                    onKeyPressed = { event ->
                        keyPressedCount += 1
                        onLogHook("onKeyPressed", event, null)
                    }
                    onKeyReleased = { event ->
                        keyReleasedCount += 1
                        onLogHook("onKeyReleased", event, null)
                    }
                },
            )
        }

        text(
            "Key counters: down=$keyDownCount up=$keyUpCount pressed=$keyPressedCount released=$keyReleasedCount enter=$enterActionCount",
            { style = { color = DEMO_MUTED } },
        )

        div({
            style = {
                gap = 4.px
                display = Display.Flex
                flexDirection = FlexDirection.Row
            }
        }) {
            button(
                if (cancellationEnabled) "Cancel child click: ON" else "Cancel child click: OFF",
                {
                    onMouseClick = {
                        cancellationEnabled = !cancellationEnabled
                        onInfo("Interactions: cancellation=$cancellationEnabled")
                    }
                },
            )
            text(
                "Parent=$cancellationParentHits Child=$cancellationChildHits",
                { style = { color = DEMO_MUTED } },
            )
        }

        div({
            key = "interactions.bubble.parent"
            onMouseClick = { event ->
                cancellationParentHits += 1
                onLogHook("parent.onMouseClick", event, null)
            }
            style = {
                width = 100.percent
                padding = 3.px
                backgroundColor = 0xFF353D46.toInt()
                border {
                    width = 1.px
                    color = 0xFF708090.toInt()
                }
            }
        }) {
            text("Parent click area")
            div({
                key = "interactions.bubble.child"
                onMouseClick = { event ->
                    cancellationChildHits += 1
                    if (cancellationEnabled) {
                        event.cancelled = true
                    }
                    onLogHook(
                        "child.onMouseClick",
                        event,
                        if (cancellationEnabled) "cancelled=true" else "cancelled=false",
                    )
                }
                style = {
                    padding = 3.px
                    backgroundColor = 0xFF4D5560.toInt()
                    border {
                        width = 1.px
                        color = 0xFF9AA5B1.toInt()
                    }
                }
            }) {
                text("Child area")
            }
        }
    }
}
