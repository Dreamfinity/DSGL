package org.dreamfinity.dsgl.mc1710.demo.sections

import org.dreamfinity.dsgl.core.UiScope
import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.event.Event
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.event.MouseDragEvent
import org.dreamfinity.dsgl.core.hooks.ref.useRef
import org.dreamfinity.dsgl.core.style.Display
import org.dreamfinity.dsgl.core.style.FlexDirection
import org.dreamfinity.dsgl.core.hooks.useState
import org.dreamfinity.dsgl.mc1710.demo.support.DEMO_MUTED
import org.dreamfinity.dsgl.mc1710.demo.support.DEMO_SURFACE_ALT
import kotlin.math.abs

fun UiScope.layoutStyleSection(
    onInfo: (String) -> Unit,
    onLogHook: (String, Event, String?) -> Unit
) {
    var styleUseMargin by useState(true)
    var styleUsePadding by useState(true)
    var styleUseBorder by useState(true)
    var styleLargeGap by useState(false)
    var styleFixedSize by useState(false)
    var stackOverlayEnabled by useState(true)
    var layoutOverlayX by useState(8)
    var layoutOverlayY by useState(92)
    var layoutOverlayDragging by useState(false)
    var overlayClicks by useState(0)

    val overlayDragAnchorXRef by useRef(0)
    val overlayDragAnchorYRef by useRef(0)
    val overlayDragMovedRef by useRef(false)

    val demoGap = if (styleLargeGap) 10 else 3
    val fixedSize = if (styleFixedSize) 24 else null
    val overlayWidth = 148
    val overlayHeight = 26

    overlay({
        key = "section.layoutStyle.stack"
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
                    if (styleLargeGap) "Gap: Large" else "Gap: Compact",
                    {
                        onMouseClick = {
                            styleLargeGap = !styleLargeGap
                            onInfo("Layout: gap=${if (styleLargeGap) "large" else "compact"}")
                        }
                    })
                button(
                    if (styleFixedSize) "Size: Fixed" else "Size: Auto",
                    {
                        onMouseClick = {
                            styleFixedSize = !styleFixedSize
                            onInfo("Layout: fixedSize=$styleFixedSize")
                        }
                    }
                )
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
                            border(1.px, 0xFF5E89B5.toInt())
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
                            border(1.px, 0xFF786AA6.toInt())
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
                button(
                    if (styleUseMargin) "Margin ON" else "Margin OFF",
                    {
                        onMouseClick = { styleUseMargin = !styleUseMargin }
                    }
                )
                button(
                    if (styleUsePadding) "Padding ON" else "Padding OFF",
                    {
                        onMouseClick = { styleUsePadding = !styleUsePadding }
                    }
                )
                button(
                    if (styleUseBorder) "Border ON" else "Border OFF",
                    {
                        onMouseClick = { styleUseBorder = !styleUseBorder }
                    }
                )
            }

            div({
                key = "layout.style.target"
                onMouseClick = { event ->
                    onLogHook("layout.style.onMouseClick", event, null)
                }
                style = {
                    width = 100.percent
                    backgroundColor = DEMO_SURFACE_ALT
                    if (styleUseMargin) margin(4.px, 0.px, 0.px, 8.px)
                    if (styleUsePadding) padding(4.px)
                    if (styleUseBorder) border(1.px, 0xFF90A4AE.toInt())
                }
            }) {
                text("Style target (margin/padding/border)")
                text(
                    "margin=$styleUseMargin padding=$styleUsePadding border=$styleUseBorder",
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
                button(
                    if (stackOverlayEnabled) "Stack Overlay ON" else "Stack Overlay OFF",
                    {
                        onMouseClick = {
                            stackOverlayEnabled = !stackOverlayEnabled
                            onInfo("Layout: stackOverlay=$stackOverlayEnabled")
                        }
                    }
                )
                button("Reset Overlay", {
                    onMouseClick = {
                        layoutOverlayX = 8
                        layoutOverlayY = 92
                        layoutOverlayDragging = false
                        overlayDragMovedRef.current = false
                        onInfo("Layout: overlay reset")
                    }
                })
                text(
                    "Overlay: $layoutOverlayX,$layoutOverlayY clicks=$overlayClicks",
                    { style = { color = DEMO_MUTED } }
                )
            }
        }

        if (stackOverlayEnabled) {
            div({
                key = "layout.stack.overlay"
                onMouseDown = onMouseDown@{ event ->
                    if (event.mouseButton != MouseButton.LEFT) return@onMouseDown
                    val overlayNode = findNodeInPath(event.target, "layout.stack.overlay") ?: return@onMouseDown
                    layoutOverlayDragging = true
                    overlayDragAnchorXRef.current =
                        (event.mouseX - overlayNode.bounds.x).coerceIn(0, overlayNode.bounds.width.coerceAtLeast(1))
                    overlayDragAnchorYRef.current =
                        (event.mouseY - overlayNode.bounds.y).coerceIn(0, overlayNode.bounds.height.coerceAtLeast(1))
                    overlayDragMovedRef.current = false
                }
                onMouseDrag = { event ->
                    updateOverlayDrag(
                        event = event,
                        overlayWidth = overlayWidth,
                        overlayHeight = overlayHeight,
                        isDragging = layoutOverlayDragging,
                        currentX = layoutOverlayX,
                        currentY = layoutOverlayY,
                        anchorX = overlayDragAnchorXRef.current ?: 0,
                        anchorY = overlayDragAnchorYRef.current ?: 0
                    ) { nextX, nextY, moved ->
                        if (moved) {
                            overlayDragMovedRef.current = true
                        }
                        if (nextX != layoutOverlayX) {
                            layoutOverlayX = nextX
                        }
                        if (nextY != layoutOverlayY) {
                            layoutOverlayY = nextY
                        }
                    }
                }
                onMouseUp = onMouseUp@{ event ->
                    if (!layoutOverlayDragging) return@onMouseUp
                    if (event.mouseButton == MouseButton.LEFT && !(overlayDragMovedRef.current ?: false)) {
                        overlayClicks += 1
                        onLogHook("overlay.onMouseClick", event, "overlayClicks=$overlayClicks")
                    }
                    layoutOverlayDragging = false
                    overlayDragMovedRef.current = false
                }
                style = {
                    width = overlayWidth.px
                    height = overlayHeight.px
                    backgroundColor = 0xCC5A3131.toInt()
                    margin(layoutOverlayY.px, 0.px, 0.px, layoutOverlayX.px)
                    padding(4.px)
                    border(1.px, 0xFF8D4848.toInt())
                }
            }) {
                text(
                    if (layoutOverlayDragging) "Overlay (dragging...)" else "Overlay (drag me)",
                    { style = { color = 0xFFF5F7FA.toInt() } }
                )
            }
        }
    }
}

private fun updateOverlayDrag(
    event: MouseDragEvent,
    overlayWidth: Int,
    overlayHeight: Int,
    isDragging: Boolean,
    currentX: Int,
    currentY: Int,
    anchorX: Int,
    anchorY: Int,
    onUpdate: (nextX: Int, nextY: Int, moved: Boolean) -> Unit
) {
    if (!isDragging) return
    val stackNode = findNodeInPath(event.target, "section.layoutStyle.stack") ?: return
    val currentMouseX = event.lastMouseX + event.dx
    val currentMouseY = event.lastMouseY + event.dy
    val maxX = (stackNode.bounds.width - overlayWidth - 2).coerceAtLeast(0)
    val maxY = (stackNode.bounds.height - overlayHeight - 2).coerceAtLeast(0)
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
