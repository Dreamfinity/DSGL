package org.dreamfinity.dsgl.mc1710.demo.sections

import org.dreamfinity.dsgl.core.ButtonProps
import org.dreamfinity.dsgl.core.ComponentProps
import org.dreamfinity.dsgl.core.DynamicTextProps
import org.dreamfinity.dsgl.core.TextProps
import org.dreamfinity.dsgl.core.UiScope
import org.dreamfinity.dsgl.mc1710.demo.ShowcaseWindow
import org.dreamfinity.dsgl.mc1710.demo.support.DEMO_MUTED
import org.dreamfinity.dsgl.mc1710.demo.support.DEMO_SURFACE_ALT

fun UiScope.renderLayoutStyleSection(window: ShowcaseWindow, contentWidth: Int, contentHeight: Int) {
    val demoGap = if (window.styleLargeGap) 10 else 3
    val fixedSize = if (window.styleFixedSize) 24 else null

    column(
        ComponentProps(
            key = "section.layoutStyle",
            width = contentWidth,
            height = contentHeight,
            gap = 4
        )
    ) {
        text(TextProps("Toggle values and click boxes to verify row/column behavior.").apply {
            color = DEMO_MUTED
        })

        row(ComponentProps(gap = 4)) {
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

        row(ComponentProps(gap = demoGap, key = "layout.row.demo")) {
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

        column(ComponentProps(gap = demoGap, key = "layout.column.demo")) {
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

        row(ComponentProps(gap = 4)) {
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
            dynamicText(
                DynamicTextProps {
                    "margin=${window.styleUseMargin} padding=${window.styleUsePadding} border=${window.styleUseBorder}"
                }.apply { color = DEMO_MUTED }
            )
        }

        row(ComponentProps(gap = 4)) {
            button(
                ButtonProps(if (window.stackOverlayEnabled) "Stack Overlay ON" else "Stack Overlay OFF").apply {
                    width = 116
                    onMouseClick = {
                        window.stackOverlayEnabled = !window.stackOverlayEnabled
                        window.appendInfo("Layout: stackOverlay=${window.stackOverlayEnabled}")
                    }
                }
            )
            dynamicText(
                DynamicTextProps {
                    "Overlay clicks: ${window.overlayClicks}"
                }.apply { color = DEMO_MUTED }
            )
        }
    }
}

