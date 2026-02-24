package org.dreamfinity.dsgl.mc1710.demo.sections

import org.dreamfinity.dsgl.core.ButtonProps
import org.dreamfinity.dsgl.core.ComponentProps
import org.dreamfinity.dsgl.core.TextProps
import org.dreamfinity.dsgl.core.UiScope
import org.dreamfinity.dsgl.mc1710.demo.ShowcaseWindow
import org.dreamfinity.dsgl.mc1710.demo.support.DEMO_MUTED
import org.dreamfinity.dsgl.mc1710.demo.support.DEMO_SURFACE_ALT

fun UiScope.renderLayoutStyleSection(window: ShowcaseWindow, contentWidth: Int, contentHeight: Int) {
    val demoGap = if (window.styleLargeGap) 10 else 3
    val fixedSize = if (window.styleFixedSize) 24 else null
    val overlayWidth = 148
    val overlayHeight = 26
    val overlayMaxX = (contentWidth - overlayWidth - 2).coerceAtLeast(0)
    val overlayMaxY = (contentHeight - overlayHeight - 2).coerceAtLeast(0)
    val overlayX = window.layoutOverlayX.coerceIn(0, overlayMaxX)
    val overlayY = window.layoutOverlayY.coerceIn(0, overlayMaxY)

    overlay(
        ComponentProps(
            key = "section.layoutStyle.stack",
            width = contentWidth,
            height = contentHeight,
            gap = 0
        )
    ) {
        div(
            ComponentProps(
                key = "section.layoutStyle",
                width = contentWidth,
                height = contentHeight,
                gap = 4
            ).asFlexColumn()
        ) {
            text(TextProps("Toggle values and click boxes to verify row/column behavior.").apply {
                color = DEMO_MUTED
            })

            div(ComponentProps(gap = 4).asFlexRow()) {
                button(
                    ButtonProps(if (window.styleLargeGap) "Gap: Large" else "Gap: Compact").apply {
                        width = 82
                        onMouseClick = {
                            window.styleLargeGap = !window.styleLargeGap
                            window.appendInfo("Layout: gap=${if (window.styleLargeGap) "large" else "compact"}")
                        }
                    }
                )
                button(
                    ButtonProps(if (window.styleFixedSize) "Size: Fixed" else "Size: Auto").apply {
                        width = 82
                        onMouseClick = {
                            window.styleFixedSize = !window.styleFixedSize
                            window.appendInfo("Layout: fixedSize=${window.styleFixedSize}")
                        }
                    }
                )
            }

            div(ComponentProps(gap = demoGap, key = "layout.row.demo").asFlexRow()) {
                repeat(3) { index ->
                    div(
                        ComponentProps(
                            key = "layout.row.box.$index",
                            width = fixedSize,
                            height = fixedSize,
                            padding = 2,
                            backgroundColor = 0xFF3A4A5A.toInt(),
                            onMouseClick = { event ->
                                window.logHook("layout.row.onMouseClick", event, "box=$index")
                            }
                        ).apply {
                            style = { border(1, 0xFF5E89B5.toInt()) }
                        }
                    ) {
                        text(TextProps("R${index + 1}"))
                    }
                }
            }

            div(ComponentProps(gap = demoGap, key = "layout.column.demo").asFlexColumn()) {
                repeat(3) { index ->
                    div(
                        ComponentProps(
                            key = "layout.column.box.$index",
                            width = if (window.styleFixedSize) 72 else null,
                            padding = 2,
                            backgroundColor = 0xFF43404F.toInt(),
                            onMouseClick = { event ->
                                window.logHook("layout.column.onMouseClick", event, "box=$index")
                            }
                        ).apply {
                            style = { border(1, 0xFF786AA6.toInt()) }
                        }
                    ) {
                        text(TextProps("Column box ${index + 1}"))
                    }
                }
            }

            div(ComponentProps(gap = 4).asFlexRow()) {
                button(
                    ButtonProps(if (window.styleUseMargin) "Margin ON" else "Margin OFF").apply {
                        width = 62
                        onMouseClick = { window.styleUseMargin = !window.styleUseMargin }
                    }
                )
                button(
                    ButtonProps(if (window.styleUsePadding) "Padding ON" else "Padding OFF").apply {
                        width = 66
                        onMouseClick = { window.styleUsePadding = !window.styleUsePadding }
                    }
                )
                button(
                    ButtonProps(if (window.styleUseBorder) "Border ON" else "Border OFF").apply {
                        width = 62
                        onMouseClick = { window.styleUseBorder = !window.styleUseBorder }
                    }
                )
            }

            div(
                ComponentProps(
                    key = "layout.style.target",
                    width = 168,
                    backgroundColor = DEMO_SURFACE_ALT,
                    onMouseClick = { event ->
                        window.logHook("layout.style.onMouseClick", event)
                    },
                    style = {
                        if (window.styleUseMargin) margin(4, 0, 0, 8)
                        if (window.styleUsePadding) padding(4)
                        if (window.styleUseBorder) border(1, 0xFF90A4AE.toInt())
                    }
                )
            ) {
                text(TextProps("Style target (margin/padding/border)"))
                text(
                    TextProps {
                        "margin=${window.styleUseMargin} padding=${window.styleUsePadding} border=${window.styleUseBorder}"
                    }.apply { color = DEMO_MUTED }
                )
            }

            div(ComponentProps(gap = 4).asFlexRow()) {
                button(
                    ButtonProps(if (window.stackOverlayEnabled) "Stack Overlay ON" else "Stack Overlay OFF").apply {
                        width = 116
                        onMouseClick = {
                            window.stackOverlayEnabled = !window.stackOverlayEnabled
                            window.appendInfo("Layout: stackOverlay=${window.stackOverlayEnabled}")
                        }
                    }
                )
                button(
                    ButtonProps("Reset Overlay").apply {
                        width = 86
                        onMouseClick = {
                            window.layoutOverlayX = 8
                            window.layoutOverlayY = 92
                            window.layoutOverlayDragging = false
                            window.appendInfo("Layout: overlay reset")
                        }
                    }
                )
                text(
                    TextProps {
                        "Overlay: ${overlayX},${overlayY} clicks=${window.overlayClicks}"
                    }.apply { color = DEMO_MUTED }
                )
            }
        }

        if (window.stackOverlayEnabled) {
            div(
                ComponentProps(
                    key = "layout.stack.overlay",
                    width = overlayWidth,
                    height = overlayHeight,
                    backgroundColor = 0xCC5A3131.toInt(),
                    onMouseDown = { event ->
                        window.beginLayoutOverlayDrag(event)
                    },
                    onMouseDrag = { event ->
                        window.updateLayoutOverlayDrag(event, overlayMaxX, overlayMaxY)
                    },
                    onMouseUp = { event ->
                        window.finishLayoutOverlayDrag(event)
                    },
                    style = {
                        margin(overlayY, 0, 0, overlayX)
                        padding(4)
                        border(1, 0xFF8D4848.toInt())
                    }
                )
            )
            {
                text(
                    TextProps(
                        if (window.layoutOverlayDragging) "Overlay (dragging...)" else "Overlay (drag me)"
                    ).apply { color = 0xFFF5F7FA.toInt() }
                )
            }
        }
    }
}