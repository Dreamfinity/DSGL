package org.dreamfinity.dsgl.mc1710.demo.sections

import org.dreamfinity.dsgl.core.UiScope
import org.dreamfinity.dsgl.core.style.Display
import org.dreamfinity.dsgl.core.style.FlexDirection
import org.dreamfinity.dsgl.mc1710.demo.ShowcaseWindow
import org.dreamfinity.dsgl.mc1710.demo.support.DEMO_MUTED
import org.dreamfinity.dsgl.mc1710.demo.support.DEMO_SURFACE_ALT

fun UiScope.layoutStyleSection(window: ShowcaseWindow, contentWidth: Int, contentHeight: Int) {
    val demoGap = if (window.styleLargeGap) 10 else 3
    val fixedSize = if (window.styleFixedSize) 24 else null
    val overlayWidth = 148
    val overlayHeight = 26
    val overlayMaxX = (contentWidth - overlayWidth - 2).coerceAtLeast(0)
    val overlayMaxY = (contentHeight - overlayHeight - 2).coerceAtLeast(0)
    val overlayX = window.layoutOverlayX.coerceIn(0, overlayMaxX)
    val overlayY = window.layoutOverlayY.coerceIn(0, overlayMaxY)

    overlay({
        key = "section.layoutStyle.stack"
        style = {
            width = contentWidth
            height = contentHeight
            gap = 0
        }
    }) {
        div({
            key = "section.layoutStyle"
            style = {
                width = contentWidth
                height = contentHeight
                gap = 4

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
                    gap = 4
                    display = Display.Flex
                    flexDirection = FlexDirection.Row
                }
            }) {
                button(
                    if (window.styleLargeGap) "Gap: Large" else "Gap: Compact",
                    {
                        style = { width = 82 }
                        onMouseClick = {
                            window.styleLargeGap = !window.styleLargeGap
                            window.appendInfo("Layout: gap=${if (window.styleLargeGap) "large" else "compact"}")
                        }
                    })
                button(
                    if (window.styleFixedSize) "Size: Fixed" else "Size: Auto",
                    {
                        style = { width = 82 }
                        onMouseClick = {
                            window.styleFixedSize = !window.styleFixedSize
                            window.appendInfo("Layout: fixedSize=${window.styleFixedSize}")
                        }
                    }
                )
            }

            div({
                style = {
                    gap = demoGap; key = "layout.row.demo"
                    display = Display.Flex
                    flexDirection = FlexDirection.Row
                }
            }) {
                repeat(3) { index ->
                    div({
                        key = "layout.row.box.$index"
                        onMouseClick = { event ->
                            window.logHook("layout.row.onMouseClick", event, "box=$index")
                        }
                        style = {
                            width = fixedSize
                            height = fixedSize
                            padding = 2
                            backgroundColor = 0xFF3A4A5A.toInt()
                            border(1, 0xFF5E89B5.toInt())
                        }
                    }) {
                        text("R${index + 1}")
                    }
                }
            }

            div({
                style = {
                    gap = demoGap; key = "layout.column.demo"
                    display = Display.Flex
                    flexDirection = FlexDirection.Column
                }
            }) {
                repeat(3) { index ->
                    div({
                        key = "layout.column.box.$index"
                        onMouseClick = { event ->
                            window.logHook("layout.column.onMouseClick", event, "box=$index")
                        }
                        style = {
                            width = if (window.styleFixedSize) 72 else null
                            padding = 2
                            backgroundColor = 0xFF43404F.toInt()
                            border(1, 0xFF786AA6.toInt())
                        }
                    }) {
                        text("Column box ${index + 1}")
                    }
                }
            }

            div({
                style = {
                    gap = 4
                    display = Display.Flex
                    flexDirection = FlexDirection.Row
                }
            }) {
                button(
                    if (window.styleUseMargin) "Margin ON" else "Margin OFF",
                    {
                        style = { width = 62 }
                        onMouseClick = { window.styleUseMargin = !window.styleUseMargin }
                    }
                )
                button(
                    if (window.styleUsePadding) "Padding ON" else "Padding OFF",
                    {
                        style = { width = 66 }
                        onMouseClick = { window.styleUsePadding = !window.styleUsePadding }
                    }
                )
                button(
                    if (window.styleUseBorder) "Border ON" else "Border OFF",
                    {
                        style = { width = 62 }
                        onMouseClick = { window.styleUseBorder = !window.styleUseBorder }
                    }
                )
            }

            div({
                key = "layout.style.target"
                onMouseClick = { event ->
                    window.logHook("layout.style.onMouseClick", event)
                }
                style = {
                    width = 168
                    backgroundColor = DEMO_SURFACE_ALT
                    if (window.styleUseMargin) margin(4, 0, 0, 8)
                    if (window.styleUsePadding) padding(4)
                    if (window.styleUseBorder) border(1, 0xFF90A4AE.toInt())
                }
            }) {
                text("Style target (margin/padding/border)")
                text(
                    "margin=${window.styleUseMargin} padding=${window.styleUsePadding} border=${window.styleUseBorder}",
                    { style = { color = DEMO_MUTED } }
                )
            }

            div({
                style = {
                    gap = 4
                    display = Display.Flex
                    flexDirection = FlexDirection.Row
                }
            }) {
                button(
                    if (window.stackOverlayEnabled) "Stack Overlay ON" else "Stack Overlay OFF",
                    {
                        style = { width = 116 }
                        onMouseClick = {
                            window.stackOverlayEnabled = !window.stackOverlayEnabled
                            window.appendInfo("Layout: stackOverlay=${window.stackOverlayEnabled}")
                        }
                    }
                )
                button("Reset Overlay", {
                    style = { width = 86 }
                    onMouseClick = {
                        window.layoutOverlayX = 8
                        window.layoutOverlayY = 92
                        window.layoutOverlayDragging = false
                        window.appendInfo("Layout: overlay reset")
                    }
                })
                text(
                    "Overlay: ${overlayX},${overlayY} clicks=${window.overlayClicks}",
                    { style = { color = DEMO_MUTED } }
                )
            }
        }

        if (window.stackOverlayEnabled) {
            div({
                key = "layout.stack.overlay"
                onMouseDown = { event ->
                    window.beginLayoutOverlayDrag(event)
                }
                onMouseDrag = { event ->
                    window.updateLayoutOverlayDrag(event, overlayMaxX, overlayMaxY)
                }
                onMouseUp = { event ->
                    window.finishLayoutOverlayDrag(event)
                }
                style = {
                    width = overlayWidth
                    height = overlayHeight
                    backgroundColor = 0xCC5A3131.toInt()
                    margin(overlayY, 0, 0, overlayX)
                    padding(4)
                    border(1, 0xFF8D4848.toInt())
                }
            }) {
                text(
                    if (window.layoutOverlayDragging) "Overlay (dragging...)" else "Overlay (drag me)",
                    { style = { color = 0xFFF5F7FA.toInt() } }
                )
            }
        }
    }
}