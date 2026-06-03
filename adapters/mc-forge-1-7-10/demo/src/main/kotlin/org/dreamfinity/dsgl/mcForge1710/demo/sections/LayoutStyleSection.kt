package org.dreamfinity.dsgl.mcForge1710.demo.sections

import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dsl.*
import org.dreamfinity.dsgl.core.event.Event
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.event.MouseDragEvent
import org.dreamfinity.dsgl.core.hooks.ref.useRef
import org.dreamfinity.dsgl.core.hooks.useState
import org.dreamfinity.dsgl.core.style.Display
import org.dreamfinity.dsgl.core.style.FlexDirection
import org.dreamfinity.dsgl.mcForge1710.demo.support.DEMO_MUTED
import org.dreamfinity.dsgl.mcForge1710.demo.support.DEMO_SURFACE_ALT
import kotlin.math.abs

fun UiScope.layoutStyleSection(onInfo: (String) -> Unit, onLogHook: (String, Event, String?) -> Unit) {
    var styleUseMargin by useState(true)
    var styleUsePadding by useState(true)
    var styleUseBorder by useState(true)
    var styleLargeGap by useState(false)
    var styleFixedSize by useState(false)
    var stackLayerEnabled by useState(true)
    var stackLayerX by useState(8)
    var stackLayerY by useState(92)
    var stackLayerDragging by useState(false)
    var stackLayerClicks by useState(0)

    val stackLayerDragAnchorXRef by useRef(0)
    val stackLayerDragAnchorYRef by useRef(0)
    val stackLayerDragMovedRef by useRef(false)

    val demoGap = if (styleLargeGap) 10 else 3
    val fixedSize = if (styleFixedSize) 24 else null
    val stackLayerWidth = 148
    val stackLayerHeight = 26

    div({
        key = "section.layoutStyle.stack"
        overlapChildren = true
        style = {
            width = 100.percent
            gap = 0.px
        }
    }) {
        div({
            key = "section.layoutStyle"
            style = {
                gap = 4.px
                display = Display.Flex
                flexDirection = FlexDirection.Column
            }
        }) {
            text(
                "Toggle values and click boxes to verify row/column behavior.",
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
                    if (styleLargeGap) "Gap: Large" else "Gap: Compact",
                    {
                        onMouseClick = {
                            styleLargeGap = !styleLargeGap
                            onInfo("Layout: gap=${if (styleLargeGap) "large" else "compact"}")
                        }
                    },
                )
                button(if (styleFixedSize) "Size: Fixed" else "Size: Auto", {
                    onMouseClick = {
                        styleFixedSize = !styleFixedSize
                        onInfo("Layout: fixedSize=$styleFixedSize")
                    }
                })
            }

            div({
                style = {
                    key = "layout.row.demo"
                    gap = demoGap.px
                    display = Display.Flex
                    flexDirection = FlexDirection.Row
                }
            }) {
                repeat(3) { index ->
                    div({
                        key = "layout.row.box.$index"
                        onMouseClick = { event ->
                            onLogHook("layout.row.onMouseClick", event, "box=$index")
                        }
                        style = {
                            width = fixedSize?.px
                            height = fixedSize?.px
                            padding = 2.px
                            backgroundColor = 0xFF3A4A5A.toInt()
                            border {
                                width = 1.px
                                color = 0xFF5E89B5.toInt()
                            }
                        }
                    }) {
                        text("R${index + 1}")
                    }
                }
            }

            div({
                style = {
                    key = "layout.column.demo"
                    gap = demoGap.px
                    display = Display.Flex
                    flexDirection = FlexDirection.Column
                }
            }) {
                repeat(3) { index ->
                    div({
                        key = "layout.column.box.$index"
                        onMouseClick = { event ->
                            onLogHook("layout.column.onMouseClick", event, "box=$index")
                        }
                        style = {
                            width = if (styleFixedSize) 72.px else null
                            padding = 2.px
                            backgroundColor = 0xFF43404F.toInt()
                            border {
                                width = 1.px
                                color = 0xFF786AA6.toInt()
                            }
                        }
                    }) {
                        text("Column box ${index + 1}")
                    }
                }
            }

            div({
                style = {
                    gap = 4.px
                    display = Display.Flex
                    flexDirection = FlexDirection.Row
                }
            }) {
                button(if (styleUseMargin) "Margin ON" else "Margin OFF", {
                    onMouseClick = { styleUseMargin = !styleUseMargin }
                })
                button(if (styleUsePadding) "Padding ON" else "Padding OFF", {
                    onMouseClick = { styleUsePadding = !styleUsePadding }
                })
                button(if (styleUseBorder) "Border ON" else "Border OFF", {
                    onMouseClick = { styleUseBorder = !styleUseBorder }
                })
            }

            div({
                key = "layout.style.target"
                onMouseClick = { event ->
                    onLogHook("layout.style.onMouseClick", event, null)
                }
                style = {
                    width = 100.percent
                    backgroundColor = DEMO_SURFACE_ALT
                    if (styleUseMargin) {
                        margin {
                            top = 4.px
                            right = 0.px
                            bottom = 0.px
                            left = 8.px
                        }
                    }
                    if (styleUsePadding) padding { all(4.px) }
                    if (styleUseBorder) {
                        border {
                            width = 1.px
                            color = 0xFF90A4AE.toInt()
                        }
                    }
                }
            }) {
                text("Style target (margin/padding/border)")
                text(
                    "margin=$styleUseMargin padding=$styleUsePadding border=$styleUseBorder",
                    { style = { color = DEMO_MUTED } },
                )
            }

            div({
                style = {
                    gap = 4.px
                    display = Display.Flex
                    flexDirection = FlexDirection.Row
                }
            }) {
                button(if (stackLayerEnabled) "Stack Layer ON" else "Stack Layer OFF", {
                    onMouseClick = {
                        stackLayerEnabled = !stackLayerEnabled
                        onInfo("Layout: stackLayer=$stackLayerEnabled")
                    }
                })
                button("Reset Stack Layer", {
                    onMouseClick = {
                        stackLayerX = 8
                        stackLayerY = 92
                        stackLayerDragging = false
                        stackLayerDragMovedRef.current = false
                        onInfo("Layout: stack layer reset")
                    }
                })
                text(
                    "Stack layer: $stackLayerX,$stackLayerY clicks=$stackLayerClicks",
                    { style = { color = DEMO_MUTED } },
                )
            }
        }

        if (stackLayerEnabled) {
            div({
                key = "layout.stack.layer"
                onMouseDown = onMouseDown@{ event ->
                    if (event.mouseButton != MouseButton.LEFT) return@onMouseDown
                    val stackLayerNode = findNodeInPath(event.target, "layout.stack.layer") ?: return@onMouseDown
                    stackLayerDragging = true
                    stackLayerDragAnchorXRef.current =
                        (event.mouseX - stackLayerNode.bounds.x).coerceIn(
                            0,
                            stackLayerNode.bounds.width
                                .coerceAtLeast(1),
                        )
                    stackLayerDragAnchorYRef.current =
                        (event.mouseY - stackLayerNode.bounds.y).coerceIn(
                            0,
                            stackLayerNode.bounds.height
                                .coerceAtLeast(1),
                        )
                    stackLayerDragMovedRef.current = false
                }
                onMouseDrag = { event ->
                    updateStackLayerDrag(
                        event = event,
                        stackLayerWidth = stackLayerWidth,
                        stackLayerHeight = stackLayerHeight,
                        isDragging = stackLayerDragging,
                        currentX = stackLayerX,
                        currentY = stackLayerY,
                        anchorX = stackLayerDragAnchorXRef.current ?: 0,
                        anchorY = stackLayerDragAnchorYRef.current ?: 0,
                    ) { nextX, nextY, moved ->
                        if (moved) {
                            stackLayerDragMovedRef.current = true
                        }
                        if (nextX != stackLayerX) {
                            stackLayerX = nextX
                        }
                        if (nextY != stackLayerY) {
                            stackLayerY = nextY
                        }
                    }
                }
                onMouseUp = onMouseUp@{ event ->
                    if (!stackLayerDragging) return@onMouseUp
                    if (event.mouseButton == MouseButton.LEFT && !(stackLayerDragMovedRef.current ?: false)) {
                        stackLayerClicks += 1
                        onLogHook("stackLayer.onMouseClick", event, "stackLayerClicks=$stackLayerClicks")
                    }
                    stackLayerDragging = false
                    stackLayerDragMovedRef.current = false
                }
                style = {
                    width = stackLayerWidth.px
                    height = stackLayerHeight.px
                    backgroundColor = 0xCC5A3131.toInt()
                    margin {
                        top = stackLayerY.px
                        right = 0.px
                        bottom = 0.px
                        left = stackLayerX.px
                    }
                    padding { all(4.px) }
                    border {
                        width = 1.px
                        color = 0xFF8D4848.toInt()
                    }
                }
            }) {
                text(
                    if (stackLayerDragging) "Stack layer (dragging...)" else "Stack layer (drag me)",
                    { style = { color = 0xFFF5F7FA.toInt() } },
                )
            }
        }
    }
}

private fun updateStackLayerDrag(
    event: MouseDragEvent,
    stackLayerWidth: Int,
    stackLayerHeight: Int,
    isDragging: Boolean,
    currentX: Int,
    currentY: Int,
    anchorX: Int,
    anchorY: Int,
    onUpdate: (nextX: Int, nextY: Int, moved: Boolean) -> Unit,
) {
    if (!isDragging) return
    val stackNode = findNodeInPath(event.target, "section.layoutStyle.stack") ?: return
    val currentMouseX = event.lastMouseX + event.dx
    val currentMouseY = event.lastMouseY + event.dy
    val maxX = (stackNode.bounds.width - stackLayerWidth - 2).coerceAtLeast(0)
    val maxY = (stackNode.bounds.height - stackLayerHeight - 2).coerceAtLeast(0)
    val nextX = (currentMouseX - stackNode.bounds.x - anchorX).coerceIn(0, maxX)
    val nextY = (currentMouseY - stackNode.bounds.y - anchorY).coerceIn(0, maxY)
    val moved = abs(nextX - currentX) > 0 || abs(nextY - currentY) > 0
    onUpdate(nextX, nextY, moved)
}

private fun findNodeInPath(start: DOMNode?, key: Any): DOMNode? {
    var current = start
    while (current != null) {
        if (current.key == key) return current
        current = current.parent
    }
    return null
}
